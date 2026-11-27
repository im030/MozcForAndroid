// Copyright 2010-2021, Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package org.mozc.android.inputmethod.japanese.userdictionary;

import org.mozc.android.inputmethod.japanese.MozcLog;
import org.mozc.android.inputmethod.japanese.MozcUtil;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionary;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionary.Entry;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionary.PosType;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionaryStorage;
import org.mozc.android.inputmethod.japanese.session.SessionExecutor;
import org.mozc.android.inputmethod.japanese.userdictionary.UserDictionaryStatus.Status;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.AbstractList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Model for the user dictionary tool.
 *
 * <p>The user dictionary storage is kept in an in-memory immutable protobuf
 * snapshot which is persisted to a file in the app's private directory when
 * the tool is paused. Validation and limits follow the same rules as the
 * native user dictionary storage.
 */
public class UserDictionaryToolModel {

  private static final String STORAGE_FILE_NAME = "user_dictionary.db";
  private static final int MAX_DICTIONARY_COUNT = 100;
  private static final int MAX_ENTRY_COUNT = 1000000;
  private static final int MAX_STRING_SIZE = 300;
  private static final String INVALID_CHARS = "\n\r\t";

  private final Context applicationContext;
  private final SessionExecutor sessionExecutor;

  private UserDictionaryStorage storage = UserDictionaryStorage.getDefaultInstance();
  private long selectedDictionaryId = 0;
  private String defaultDictionaryName = "";
  private boolean dirty = false;
  private int editTargetIndex = 0;

  // Pending import state.
  private Uri importUri;
  private String importData;
  private ZipFile zipFile;

  // In-memory undo history (immutable protobuf snapshots).
  private final Deque<UserDictionaryStorage> undoStack = new ArrayDeque<>();

  // List "view" by proxying dictionary names in the storage.
  private final List<String> dictionaryNameList = new AbstractList<String>() {
    @Override
    public String get(int index) {
      return storage.getDictionaries(index).getName();
    }

    @Override
    public int size() {
      return storage.getDictionariesCount();
    }
  };

  public UserDictionaryToolModel(Context applicationContext, SessionExecutor sessionExecutor) {
    this.applicationContext = applicationContext;
    this.sessionExecutor = sessionExecutor;
  }

  /**
   * Returns {@code true} if the storage is changed after the last save.
   */
  public boolean isDirty() {
    return dirty;
  }

  /**
   * Creates the model instance by loading the storage from disk.
   * This method should be called before any other methods.
   */
  public void createSession() {
    storage = loadStorage();
    if (storage == null) {
      storage = UserDictionaryStorage.getDefaultInstance();
    }
    selectedDictionaryId = 0;
    dirty = false;
    undoStack.clear();
  }

  /**
   * Releases the current session state, saving the storage if necessary.
   */
  public void deleteSession() {
    if (dirty) {
      saveStorage();
      dirty = false;
    }
  }

  /**
   * Loads the storage from the file, and updates both the storage and the selected dictionary.
   *
   * @param defaultDictionaryName the name of the default dictionary, which is created when the
   *   storage is empty.
   * @return the load status.
   */
  public Status resumeSession(String defaultDictionaryName) {
    this.defaultDictionaryName = defaultDictionaryName;

    UserDictionaryStorage loaded = loadStorage();
    if (loaded == null) {
      if (getStorageFile().exists()) {
        // The file exists but could not be parsed.
        storage = UserDictionaryStorage.getDefaultInstance();
        selectedDictionaryId = 0;
        undoStack.clear();
        return Status.INVALID_FILE_FORMAT;
      }
      // First time; no storage file so far.
      storage = UserDictionaryStorage.getDefaultInstance();
    } else {
      storage = loaded;
    }

    if (storage.getDictionariesCount() == 0) {
      storage = createDefaultStorage(storage, defaultDictionaryName);
      selectedDictionaryId = storage.getDictionaries(0).getId();
      dirty = true;
      undoStack.clear();
      return Status.USER_DICTIONARY_COMMAND_SUCCESS;
    }

    selectedDictionaryId = storage.getDictionaries(0).getId();
    dirty = false;
    undoStack.clear();
    return Status.USER_DICTIONARY_COMMAND_SUCCESS;
  }

  /**
   * Saves the storage if necessary, and reloads the server if the storage was modified.
   */
  public Status pauseSession() {
    if (dirty) {
      saveStorage();
      dirty = false;
      sessionExecutor.reload();
    }
    return Status.USER_DICTIONARY_COMMAND_SUCCESS;
  }

  /**
   * Returns the list "view" of dictionary names in the storage.
   * The contents of the returned instance are updated automatically when the storage
   * is updated.
   */
  public List<String> getDictionaryNameList() {
    return dictionaryNameList;
  }

  /**
   * Sets the current selected dictionary based on the given index.
   */
  public void setSelectedDictionaryByIndex(int index) {
    if (index < 0 || storage.getDictionariesCount() <= index) {
      selectedDictionaryId = 0;
      return;
    }
    selectedDictionaryId = storage.getDictionaries(index).getId();
  }

  /**
   * Returns the index of the current selected dictionary.
   */
  public int getSelectedDictionaryIndex() {
    int index = getSelectedDictionaryIndexInternal();
    return index < 0 ? 0 : index;
  }

  /**
   * Returns the name of the current selected dictionary.
   */
  public String getSelectedDictionaryName() {
    int index = getSelectedDictionaryIndexInternal();
    if (index < 0) {
      return null;
    }
    return storage.getDictionaries(index).getName();
  }

  private int getSelectedDictionaryIndexInternal() {
    if (storage != null && selectedDictionaryId != 0) {
      for (int i = 0; i < storage.getDictionariesCount(); ++i) {
        if (storage.getDictionaries(i).getId() == selectedDictionaryId) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Checks if a new dictionary can be added to the storage.
   */
  public Status checkNewDictionaryAvailability() {
    if (storage.getDictionariesCount() >= MAX_DICTIONARY_COUNT) {
      return Status.DICTIONARY_SIZE_LIMIT_EXCEEDED;
    }
    return Status.USER_DICTIONARY_COMMAND_SUCCESS;
  }

  /**
   * Checks if there is an undo history to replay.
   */
  public Status checkUndoability() {
    return undoStack.isEmpty() ? Status.NO_UNDO_HISTORY : Status.USER_DICTIONARY_COMMAND_SUCCESS;
  }

  /**
   * Restores the storage to the state before the last change.
   */
  public Status undo() {
    if (undoStack.isEmpty()) {
      return Status.NO_UNDO_HISTORY;
    }
    int index = getSelectedDictionaryIndex();
    storage = undoStack.pop();
    dirty = true;

    boolean found = false;
    for (int i = 0; i < storage.getDictionariesCount(); ++i) {
      if (storage.getDictionaries(i).getId() == selectedDictionaryId) {
        found = true;
        break;
      }
    }
    if (!found) {
      setSelectedDictionaryByIndex(Math.min(index, storage.getDictionariesCount() - 1));
    }
    return Status.USER_DICTIONARY_COMMAND_SUCCESS;
  }

  /**
   * Creates a new empty dictionary with the given name and selects it.
   */
  public Status createDictionary(String dictionaryName) {
    Status status = validateDictionaryName(dictionaryName, -1);
    if (status != Status.USER_DICTIONARY_COMMAND_SUCCESS) {
      return status;
    }
    if (storage.getDictionariesCount() >= MAX_DICTIONARY_COUNT) {
      return Status.DICTIONARY_SIZE_LIMIT_EXCEEDED;
    }

    UserDictionary dictionary = UserDictionary.newBuilder()
        .setId(generateNewDictionaryId())
        .setName(dictionaryName)
        .build();

    pushUndo();
    storage = storage.toBuilder().addDictionaries(dictionary).build();
    selectedDictionaryId = dictionary.getId();
    dirty = true;
    return Status.USER_DICTIONARY_COMMAND_SUCCESS;
  }

  /**
   * Renames the currently selected dictionary to the given name.
   */
  public Status renameSelectedDictionary(String dictionaryName) {
    int index = getSelectedDictionaryIndexInternal();
    if (index < 0) {
      return Status.UNKNOWN_DICTIONARY_ID;
    }
    String currentName = storage.getDictionaries(index).getName();
    if (currentName.equals(dictionaryName)) {
      return Status.USER_DICTIONARY_COMMAND_SUCCESS;
    }

    Status status = validateDictionaryName(dictionaryName, index);
    if (status != Status.USER_DICTIONARY_COMMAND_SUCCESS) {
      return status;
    }

    pushUndo();
    UserDictionary updated = storage.getDictionaries(index).toBuilder()
        .setName(dictionaryName).build();
    storage = replaceDictionary(index, updated);
    dirty = true;
    return Status.USER_DICTIONARY_COMMAND_SUCCESS;
  }

  /**
   * Deletes the currently selected dictionary.
   * The new selected dictionary is the one at the same position in the storage,
   * or the last one if the deleted dictionary was the last one.
   * If the storage would become empty, a new default dictionary is created.
   */
  public Status deleteSelectedDictionary() {
    int index = getSelectedDictionaryIndexInternal();
    if (index < 0) {
      return Status.UNKNOWN_DICTIONARY_ID;
    }

    pushUndo();

    List<UserDictionary> dictionaries = new ArrayList<UserDictionary>();
    for (int i = 0; i < storage.getDictionariesCount(); ++i) {
      if (i != index) {
        dictionaries.add(storage.getDictionaries(i));
      }
    }
    storage = createStorageFromDictionaries(dictionaries);

    if (storage.getDictionariesCount() == 0) {
      storage = createDefaultStorage(storage, defaultDictionaryName);
    }

    selectedDictionaryId = storage.getDictionaries(
        Math.min(index, storage.getDictionariesCount() - 1)).getId();
    dirty = true;
    return Status.USER_DICTIONARY_COMMAND_SUCCESS;
  }

  /**
   * Creates a zip file containing the export of the currently selected dictionary,
   * named {@code dictionaryName}. The file is created in {@code tempDirectory}.
   */
  public Optional<File> createExportFile(
      Resources resources, String dictionaryName, File tempDirectory) {
    Preconditions.checkNotNull(resources);
    Preconditions.checkNotNull(dictionaryName);
    Preconditions.checkNotNull(tempDirectory);
    Preconditions.checkArgument(dictionaryName.length() > 0);
    Preconditions.checkArgument(tempDirectory.isDirectory());

    File exportFile = null;
    ZipOutputStream zipStream = null;
    try {
      exportFile = File.createTempFile("export_temp_", ".zip", tempDirectory);
      exportFile.deleteOnExit();
      zipStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(exportFile)));
      zipStream.putNextEntry(new ZipEntry(dictionaryName + ".txt"));

      int entrySize = getEntrySize();
      int fetchSize = 1000;
      int queryNum = (int) Math.ceil((double) entrySize / fetchSize);
      for (int i = 0; i < queryNum; ++i) {
        String exportString = getSelectedDictionaryAsString(
            resources, i * fetchSize, Math.min((i + 1) * fetchSize, entrySize));
        // getBytes() always returns UTF-8 encoded byte stream because the default charset on
        // Android is always UTF-8 and is immutable.
        zipStream.write(exportString.getBytes());
      }
      zipStream.close();
    } catch (IOException e) {
      MozcLog.e("Failed to prepare export data. " + e.getMessage());
      if (zipStream != null) {
        MozcUtil.closeIgnoringIOException(zipStream);
      }
      if (exportFile != null) {
        exportFile.delete();
      }
      return Optional.absent();
    }
    return Optional.of(exportFile);
  }

  /**
   * Dumps the dictionary entries in [beginIndex, endIndex) as string.
   */
  private String getSelectedDictionaryAsString(Resources resources, int beginIndex, int endIndex) {
    int entrySize = getEntrySize();
    Preconditions.checkElementIndex(beginIndex, endIndex);
    Preconditions.checkArgument(endIndex <= entrySize);
    StringBuilder builder = new StringBuilder();

    Map<PosType, String> posNameCache = new EnumMap<PosType, String>(PosType.class);
    for (Entry entry : getEntriesInternal(beginIndex, endIndex)) {
      String posName = posNameCache.get(entry.getPos());
      if (posName == null) {
        posName = resources.getString(
            UserDictionaryUtil.getPosStringResourceIdForDictionaryExport(entry.getPos()));
        posNameCache.put(entry.getPos(), posName);
      }

      builder.append(entry.getKey());
      builder.append('\t');
      builder.append(entry.getValue());
      builder.append('\t');
      builder.append(posName);
      builder.append('\t');
      builder.append(entry.getComment());
      builder.append('\n');
    }
    return builder.toString();
  }

  /**
   * Returns the list "view" of entries in the currently selected dictionary.
   * The contents of the returned instance are updated automatically when the storage
   * is updated.
   */
  public List<Entry> getEntryList() {
    return new AbstractList<Entry>() {
      @Override
      public Entry get(int index) {
        return getEntryInternal(index);
      }

      @Override
      public int size() {
        return getEntrySize();
      }
    };
  }

  public void setEditTargetIndex(int editTargetIndex) {
    this.editTargetIndex = editTargetIndex;
  }

  public int getEditTargetIndex() {
    return this.editTargetIndex;
  }

  /**
   * Returns the entry at the current edit target index in the selected dictionary.
   */
  public Entry getEditTargetEntry() {
    return getEntryInternal(editTargetIndex);
  }

  private int getEntrySize() {
    int index = getSelectedDictionaryIndexInternal();
    if (index < 0) {
      return 0;
    }
    return storage.getDictionaries(index).getEntriesCount();
  }

  private Entry getEntryInternal(int index) {
    return getEntriesInternal(index, index + 1).get(0);
  }

  /**
   * Returns dictionary entries of which the index is in [beginIndex, endIndex).
   */
  private List<Entry> getEntriesInternal(int beginIndex, int endIndex) {
    int index = getSelectedDictionaryIndexInternal();
    if (index < 0) {
      return new ArrayList<Entry>();
    }
    UserDictionary dictionary = storage.getDictionaries(index);
    int size = dictionary.getEntriesCount();
    int actualBegin = Math.max(0, beginIndex);
    int actualEnd = Math.min(size, Math.max(actualBegin, endIndex));
    List<Entry> result = new ArrayList<Entry>();
    for (int i = actualBegin; i < actualEnd; ++i) {
      result.add(dictionary.getEntries(i));
    }
    return result;
  }

  /**
   * Checks if a new entry can be added to the selected dictionary.
   */
  public Status checkNewEntryAvailability() {
    int index = getSelectedDictionaryIndexInternal();
    if (index < 0) {
      return Status.UNKNOWN_DICTIONARY_ID;
    }
    if (storage.getDictionaries(index).getEntriesCount() >= MAX_ENTRY_COUNT) {
      return Status.ENTRY_SIZE_LIMIT_EXCEEDED;
    }
    return Status.USER_DICTIONARY_COMMAND_SUCCESS;
  }

  /**
   * Adds an entry to the selected dictionary.
   */
  public Status addEntry(String word, String reading, PosType pos) {
    String normalizedReading = normalizeReading(reading);
    Status status = validateEntryFields(normalizedReading, word, null);
    if (status != Status.USER_DICTIONARY_COMMAND_SUCCESS) {
      return status;
    }
    int index = getSelectedDictionaryIndexInternal();
    if (index < 0) {
      return Status.UNKNOWN_DICTIONARY_ID;
    }
    UserDictionary dictionary = storage.getDictionaries(index);
    if (dictionary.getEntriesCount() >= MAX_ENTRY_COUNT) {
      return Status.ENTRY_SIZE_LIMIT_EXCEEDED;
    }

    Entry entry = Entry.newBuilder()
        .setKey(normalizedReading)
        .setValue(word)
        .setPos(pos)
        .build();

    pushUndo();
    UserDictionary updated = dictionary.toBuilder().addEntries(entry).build();
    storage = replaceDictionary(index, updated);
    dirty = true;
    return Status.USER_DICTIONARY_COMMAND_SUCCESS;
  }

  /**
   * Edits the entry at the current edit target index in the selected dictionary.
   */
  public Status editEntry(String word, String reading, PosType pos) {
    String normalizedReading = normalizeReading(reading);
    Status status = validateEntryFields(normalizedReading, word, null);
    if (status != Status.USER_DICTIONARY_COMMAND_SUCCESS) {
      return status;
    }
    int index = getSelectedDictionaryIndexInternal();
    if (index < 0) {
      return Status.UNKNOWN_DICTIONARY_ID;
    }
    UserDictionary dictionary = storage.getDictionaries(index);
    int target = editTargetIndex;
    if (target < 0 || target >= dictionary.getEntriesCount()) {
      return Status.ENTRY_INDEX_OUT_OF_RANGE;
    }

    Entry entry = Entry.newBuilder()
        .setKey(normalizedReading)
        .setValue(word)
        .setPos(pos)
        .build();

    pushUndo();
    UserDictionary updated = dictionary.toBuilder().setEntries(target, entry).build();
    storage = replaceDictionary(index, updated);
    dirty = true;
    return Status.USER_DICTIONARY_COMMAND_SUCCESS;
  }

  /**
   * Deletes the entries specified by the given indices in the selected dictionary.
   */
  public Status deleteEntry(List<Integer> indexList) {
    int index = getSelectedDictionaryIndexInternal();
    if (index < 0) {
      return Status.UNKNOWN_DICTIONARY_ID;
    }
    UserDictionary dictionary = storage.getDictionaries(index);
    int size = dictionary.getEntriesCount();

    if (indexList == null || indexList.isEmpty()) {
      return Status.INVALID_ARGUMENT;
    }

    List<Integer> sorted = new ArrayList<Integer>();
    for (Integer i : new HashSet<Integer>(indexList)) {
      if (i == null || i < 0 || i >= size) {
        return Status.ENTRY_INDEX_OUT_OF_RANGE;
      }
      sorted.add(i.intValue());
    }
    // Delete from the highest index down to avoid index shifting.
    sorted.sort(new Comparator<Integer>() {
      @Override
      public int compare(Integer a, Integer b) {
        return b.intValue() - a.intValue();
      }
    });

    List<Entry> entries = new ArrayList<Entry>(dictionary.getEntriesList());
    pushUndo();
    for (Integer i : sorted) {
      entries.remove(i.intValue());
    }
    UserDictionary updated = dictionary.toBuilder().clearEntries().build();
    for (Entry e : entries) {
      updated = updated.toBuilder().addEntries(e).build();
    }
    storage = replaceDictionary(index, updated);
    dirty = true;
    return Status.USER_DICTIONARY_COMMAND_SUCCESS;
  }

  public Uri getImportUri() {
    return importUri;
  }

  public void setImportUri(Uri uri) {
    this.importUri = uri;
  }

  public String getImportData() {
    return importData;
  }

  public void setImportData(String data) {
    this.importData = data;
  }

  public void resetImportState() {
    importUri = null;
    importData = null;
    clearZipFile();
  }

  public ZipFile getZipFile() {
    return zipFile;
  }

  /**
   * By the method, this instance takes the ownership of resource management of {@code zipFile}.
   * (i.e., it must not invoke close for the zipFile by the caller).
   */
  public void setZipFile(ZipFile zipFile) {
    clearZipFile();
    this.zipFile = zipFile;
  }

  /**
   * By the method, this instance releases the ownership of resource management of the
   * current ZipFile. I.e., the caller needs to invoke close method on the returned ZipFile.
   */
  public ZipFile releaseZipFile() {
    ZipFile zipFile = this.zipFile;
    this.zipFile = null;
    return zipFile;
  }

  private void clearZipFile() {
    if (zipFile != null) {
      MozcUtil.closeIgnoringIOException(zipFile);
      zipFile = null;
    }
  }

  /**
   * Imports the pending import data into a dictionary.
   * After the operation, regardless of whether it is successful or not,
   * resets the pending import state.
   *
   * @param dictionaryIndex the position of the import destination dictionary in the storage.
   *   If it is set to -1, a new dictionary is created with the name guessed from the import
   *   uri.
   */
  public Status importData(int dictionaryIndex) {
    try {
      if (importData == null || importUri == null) {
        throw new NullPointerException();
      }

      if (dictionaryIndex < 0) {
        String name = UserDictionaryUtil.generateDictionaryNameByUri(importUri, getDictionaryNameList());
        return importDataInternal(-1, name, importData);
      } else {
        if (dictionaryIndex >= storage.getDictionariesCount()) {
          return Status.UNKNOWN_DICTIONARY_ID;
        }
        return importDataInternal(dictionaryIndex, null, importData);
      }
    } finally {
      resetImportState();
    }
  }

  @VisibleForTesting
  Status importDataInternal(int dictionaryIndex, String newDictionaryName, String rawData) {
    Status ret = Status.USER_DICTIONARY_COMMAND_SUCCESS;
    List<Entry> parsed = new ArrayList<Entry>();

    Map<String, PosType> posMap = posStringMap();

    for (String rawLine : rawData.split("\r?\n")) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      // Mozc TSV: key\tvalue\tpos[:locale][\tcomment]
      String[] parts = line.split("\t", -1);
      if (parts.length < 3) {
        ret = Status.IMPORT_INVALID_ENTRIES;
        continue;
      }

      String normalizedPos = normalizePos(parts[2]);
      String locale = "";
      int colon = normalizedPos.indexOf(':');
      if (colon >= 0) {
        locale = normalizedPos.substring(colon + 1).trim();
        normalizedPos = normalizedPos.substring(0, colon).trim();
      }
      PosType pos = posMap.get(normalizedPos);
      if (pos == null) {
        ret = Status.IMPORT_INVALID_ENTRIES;
        continue;
      }

      String key = normalizeReading(parts[0]);
      String value = parts[1];
      String comment = parts.length >= 4 ? parts[3].trim() : "";

      Status entryStatus = validateEntryFields(key, value, comment);
      if (entryStatus != Status.USER_DICTIONARY_COMMAND_SUCCESS) {
        ret = Status.IMPORT_INVALID_ENTRIES;
        continue;
      }

      Entry.Builder entryBuilder = Entry.newBuilder()
          .setKey(key)
          .setValue(value)
          .setPos(pos);
      if (!comment.isEmpty()) {
        entryBuilder.setComment(comment);
      }
      if (!locale.isEmpty()) {
        entryBuilder.setLocale(locale);
      }
      parsed.add(entryBuilder.build());
    }

    // Determine the destination dictionary.
    boolean isNew = dictionaryIndex < 0;
    UserDictionary target;
    if (isNew) {
      String name = newDictionaryName == null ? "" : newDictionaryName.trim();
      if (name.isEmpty()) {
        return Status.DICTIONARY_NAME_EMPTY;
      }
      Status nameStatus = validateDictionaryName(name, -1);
      if (nameStatus != Status.USER_DICTIONARY_COMMAND_SUCCESS) {
        return nameStatus;
      }
      if (storage.getDictionariesCount() >= MAX_DICTIONARY_COUNT) {
        return Status.DICTIONARY_SIZE_LIMIT_EXCEEDED;
      }
      target = UserDictionary.newBuilder()
          .setId(generateNewDictionaryId())
          .setName(name)
          .build();
    } else {
      target = storage.getDictionaries(dictionaryIndex);
    }

    // Deduplicate and apply the entry limit.
    Set<String> seen = new HashSet<String>();
    for (int i = 0; i < target.getEntriesCount(); ++i) {
      Entry e = target.getEntries(i);
      seen.add(entryMarker(e));
    }
    List<Entry> toAdd = new ArrayList<Entry>();
    int remaining = MAX_ENTRY_COUNT - target.getEntriesCount();
    for (Entry e : parsed) {
      if (toAdd.size() >= remaining) {
        ret = Status.IMPORT_TOO_MANY_WORDS;
        break;
      }
      String marker = entryMarker(e);
      if (seen.contains(marker)) {
        continue;
      }
      seen.add(marker);
      toAdd.add(e);
    }

    if (!isNew && toAdd.isEmpty()) {
      return ret;
    }

    // Apply the import.
    pushUndo();
    UserDictionary.Builder targetBuilder = target.toBuilder();
    for (Entry e : toAdd) {
      targetBuilder.addEntries(e);
    }
    UserDictionary updated = targetBuilder.build();
    if (isNew) {
      storage = storage.toBuilder().addDictionaries(updated).build();
      selectedDictionaryId = updated.getId();
    } else {
      storage = replaceDictionary(dictionaryIndex, updated);
    }
    dirty = true;

    return ret;
  }

  private String entryMarker(Entry entry) {
    return entry.getKey() + "\u0001" + entry.getValue() + "\u0001" + entry.getPos().toString();
  }

  private Map<String, PosType> posStringMap() {
    // Lazy map from the Japanese export POS strings to the corresponding PosType.
    // The strings are non-translatable (fixed Japanese).
    Map<String, PosType> map = new HashMap<String, PosType>();
    Resources resources = applicationContext.getResources();
    for (PosType pos : PosType.values()) {
      int resId = UserDictionaryUtil.getPosStringResourceIdForDictionaryExport(pos);
      String value = resources.getString(resId);
      map.put(normalizePos(value), pos);
    }
    return map;
  }

  private UserDictionaryStorage replaceDictionary(int index, UserDictionary dictionary) {
    UserDictionaryStorage.Builder builder = storage.toBuilder();
    builder.setDictionaries(index, dictionary);
    return builder.build();
  }

  private long generateNewDictionaryId() {
    long max = 0;
    for (int i = 0; i < storage.getDictionariesCount(); ++i) {
      max = Math.max(max, storage.getDictionaries(i).getId());
    }
    return max + 1;
  }

  private UserDictionaryStorage createStorageFromDictionaries(List<UserDictionary> dictionaries) {
    UserDictionaryStorage.Builder builder = UserDictionaryStorage.newBuilder();
    for (UserDictionary dict : dictionaries) {
      builder.addDictionaries(dict);
    }
    return builder.build();
  }

  private UserDictionaryStorage createDefaultStorage(UserDictionaryStorage storage, String name) {
    UserDictionary dictionary = UserDictionary.newBuilder()
        .setId(generateNewDictionaryId())
        .setName(name)
        .build();
    return storage.toBuilder().addDictionaries(dictionary).build();
  }

  private void pushUndo() {
    undoStack.push(storage);
  }

  // ------------------------------------------------------------------
  // Serialization
  // ------------------------------------------------------------------

  private File getStorageFile() {
    return new File(applicationContext.getFilesDir(), STORAGE_FILE_NAME);
  }

  private UserDictionaryStorage loadStorage() {
    File file = getStorageFile();
    if (!file.exists()) {
      return null;
    }
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      FileInputStream fis = new FileInputStream(file);
      try {
        byte[] buffer = new byte[65536];
        int read;
        while ((read = fis.read(buffer)) != -1) {
          bos.write(buffer, 0, read);
        }
      } finally {
        MozcUtil.closeIgnoringIOException(fis);
      }
      byte[] bytes = bos.toByteArray();
      if (bytes.length == 0) {
        return null;
      }
      return UserDictionaryStorage.parseFrom(bytes);
    } catch (Exception e) {
      MozcLog.e("Failed to load user dictionary storage: " + e.getMessage());
      return null;
    }
  }

  private void saveStorage() {
    try {
      File file = getStorageFile();
      File tmpFile = new File(file.getParentFile(), STORAGE_FILE_NAME + ".tmp");
      byte[] bytes = storage.toByteArray();

      FileOutputStream fos = new FileOutputStream(tmpFile);
      try {
        fos.write(bytes);
      } finally {
        MozcUtil.closeIgnoringIOException(fos);
      }

      if (!tmpFile.renameTo(file)) {
        throw new IOException("renameTo failed");
      }
    } catch (IOException e) {
      MozcLog.e("Failed to save user dictionary storage: " + e.getMessage());
    }
  }

  // ------------------------------------------------------------------
  // Validation & normalization
  // ------------------------------------------------------------------

  private Status validateDictionaryName(String dictionaryName, int ignoreIndex) {
    if (dictionaryName == null || dictionaryName.isEmpty()) {
      return Status.DICTIONARY_NAME_EMPTY;
    }
    if (dictionaryName.length() > MAX_STRING_SIZE) {
      return Status.DICTIONARY_NAME_TOO_LONG;
    }
    if (dictionaryName.contains(INVALID_CHARS)) {
      return Status.DICTIONARY_NAME_CONTAINS_INVALID_CHARACTER;
    }
    for (int i = 0; i < storage.getDictionariesCount(); ++i) {
      if (i == ignoreIndex) continue;
      if (dictionaryName.equals(storage.getDictionaries(i).getName())) {
        return Status.DICTIONARY_NAME_DUPLICATED;
      }
    }
    return Status.USER_DICTIONARY_COMMAND_SUCCESS;
  }

  private Status validateEntryFields(String reading, String word, String comment) {
    if (reading == null || reading.isEmpty()) {
      return Status.READING_EMPTY;
    }
    if (reading.length() > MAX_STRING_SIZE) {
      return Status.READING_TOO_LONG;
    }
    if (reading.contains(INVALID_CHARS)) {
      return Status.READING_CONTAINS_INVALID_CHARACTER;
    }
    if (word == null || word.isEmpty()) {
      return Status.WORD_EMPTY;
    }
    if (word.length() > MAX_STRING_SIZE) {
      return Status.WORD_TOO_LONG;
    }
    if (word.contains(INVALID_CHARS)) {
      return Status.WORD_CONTAINS_INVALID_CHARACTER;
    }
    if (comment != null) {
      if (comment.length() > MAX_STRING_SIZE) {
        return Status.COMMENT_TOO_LONG;
      }
      if (comment.contains(INVALID_CHARS)) {
        return Status.COMMENT_CONTAINS_INVALID_CHARACTER;
      }
    }
    return Status.USER_DICTIONARY_COMMAND_SUCCESS;
  }

  private static String normalizeReading(String input) {
    if (input == null) {
      return "";
    }
    return katakanaToHiragana(halfWidthKatakanaToFullWidthKatakana(
        fullWidthAsciiToHalfWidthAscii(input)));
  }

  private static String normalizePos(String input) {
    if (input == null) {
      return "";
    }
    return halfWidthKatakanaToFullWidthKatakana(fullWidthAsciiToHalfWidthAscii(input));
  }

  private static String fullWidthAsciiToHalfWidthAscii(String input) {
    StringBuilder result = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); ++i) {
      char c = input.charAt(i);
      if (c >= 0xFF01 && c <= 0xFF5E) {
        result.append((char) (c - 0xFEE0));
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  private static final Map<String, String> HALFWIDTH_KATAKANA_MAP;
  static {
    HALFWIDTH_KATAKANA_MAP = new HashMap<>();
        HALFWIDTH_KATAKANA_MAP.put("｡", "。");
        HALFWIDTH_KATAKANA_MAP.put("ﾞ", "゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾟ", "゜");
        HALFWIDTH_KATAKANA_MAP.put("｢", "「");
        HALFWIDTH_KATAKANA_MAP.put("｣", "」");
        HALFWIDTH_KATAKANA_MAP.put("､", "、");
        HALFWIDTH_KATAKANA_MAP.put("･", "・");
        HALFWIDTH_KATAKANA_MAP.put("ｦ", "ヲ");
        HALFWIDTH_KATAKANA_MAP.put("ｧ", "ァ");
        HALFWIDTH_KATAKANA_MAP.put("ｨ", "ィ");
        HALFWIDTH_KATAKANA_MAP.put("ｩ", "ゥ");
        HALFWIDTH_KATAKANA_MAP.put("ｪ", "ェ");
        HALFWIDTH_KATAKANA_MAP.put("ｫ", "ォ");
        HALFWIDTH_KATAKANA_MAP.put("ｬ", "ャ");
        HALFWIDTH_KATAKANA_MAP.put("ｭ", "ュ");
        HALFWIDTH_KATAKANA_MAP.put("ｮ", "ョ");
        HALFWIDTH_KATAKANA_MAP.put("ｯ", "ッ");
        HALFWIDTH_KATAKANA_MAP.put("ｰ", "ー");
        HALFWIDTH_KATAKANA_MAP.put("ｱ", "ア");
        HALFWIDTH_KATAKANA_MAP.put("ｱﾞ", "ア゛");
        HALFWIDTH_KATAKANA_MAP.put("ｱﾟ", "ア゜");
        HALFWIDTH_KATAKANA_MAP.put("ｲ", "イ");
        HALFWIDTH_KATAKANA_MAP.put("ｲﾞ", "イ゛");
        HALFWIDTH_KATAKANA_MAP.put("ｲﾟ", "イ゜");
        HALFWIDTH_KATAKANA_MAP.put("ｳ", "ウ");
        HALFWIDTH_KATAKANA_MAP.put("ｳﾞ", "ヴ");
        HALFWIDTH_KATAKANA_MAP.put("ｳﾟ", "ウ゜");
        HALFWIDTH_KATAKANA_MAP.put("ｴ", "エ");
        HALFWIDTH_KATAKANA_MAP.put("ｴﾞ", "エ゛");
        HALFWIDTH_KATAKANA_MAP.put("ｴﾟ", "エ゜");
        HALFWIDTH_KATAKANA_MAP.put("ｵ", "オ");
        HALFWIDTH_KATAKANA_MAP.put("ｵﾞ", "オ゛");
        HALFWIDTH_KATAKANA_MAP.put("ｵﾟ", "オ゜");
        HALFWIDTH_KATAKANA_MAP.put("ｶ", "カ");
        HALFWIDTH_KATAKANA_MAP.put("ｶﾞ", "ガ");
        HALFWIDTH_KATAKANA_MAP.put("ｶﾟ", "カ゜");
        HALFWIDTH_KATAKANA_MAP.put("ｷ", "キ");
        HALFWIDTH_KATAKANA_MAP.put("ｷﾞ", "ギ");
        HALFWIDTH_KATAKANA_MAP.put("ｷﾟ", "キ゜");
        HALFWIDTH_KATAKANA_MAP.put("ｸ", "ク");
        HALFWIDTH_KATAKANA_MAP.put("ｸﾞ", "グ");
        HALFWIDTH_KATAKANA_MAP.put("ｸﾟ", "ク゜");
        HALFWIDTH_KATAKANA_MAP.put("ｹ", "ケ");
        HALFWIDTH_KATAKANA_MAP.put("ｹﾞ", "ゲ");
        HALFWIDTH_KATAKANA_MAP.put("ｹﾟ", "ケ゜");
        HALFWIDTH_KATAKANA_MAP.put("ｺ", "コ");
        HALFWIDTH_KATAKANA_MAP.put("ｺﾞ", "ゴ");
        HALFWIDTH_KATAKANA_MAP.put("ｺﾟ", "コ゜");
        HALFWIDTH_KATAKANA_MAP.put("ｻ", "サ");
        HALFWIDTH_KATAKANA_MAP.put("ｻﾞ", "ザ");
        HALFWIDTH_KATAKANA_MAP.put("ｻﾟ", "サ゜");
        HALFWIDTH_KATAKANA_MAP.put("ｼ", "シ");
        HALFWIDTH_KATAKANA_MAP.put("ｼﾞ", "ジ");
        HALFWIDTH_KATAKANA_MAP.put("ｼﾟ", "シ゜");
        HALFWIDTH_KATAKANA_MAP.put("ｽ", "ス");
        HALFWIDTH_KATAKANA_MAP.put("ｽﾞ", "ズ");
        HALFWIDTH_KATAKANA_MAP.put("ｽﾟ", "ス゜");
        HALFWIDTH_KATAKANA_MAP.put("ｾ", "セ");
        HALFWIDTH_KATAKANA_MAP.put("ｾﾞ", "ゼ");
        HALFWIDTH_KATAKANA_MAP.put("ｾﾟ", "セ゜");
        HALFWIDTH_KATAKANA_MAP.put("ｿ", "ソ");
        HALFWIDTH_KATAKANA_MAP.put("ｿﾞ", "ゾ");
        HALFWIDTH_KATAKANA_MAP.put("ｿﾟ", "ソ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾀ", "タ");
        HALFWIDTH_KATAKANA_MAP.put("ﾀﾞ", "ダ");
        HALFWIDTH_KATAKANA_MAP.put("ﾀﾟ", "タ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾁ", "チ");
        HALFWIDTH_KATAKANA_MAP.put("ﾁﾞ", "ヂ");
        HALFWIDTH_KATAKANA_MAP.put("ﾁﾟ", "チ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾂ", "ツ");
        HALFWIDTH_KATAKANA_MAP.put("ﾂﾞ", "ヅ");
        HALFWIDTH_KATAKANA_MAP.put("ﾂﾟ", "ツ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾃ", "テ");
        HALFWIDTH_KATAKANA_MAP.put("ﾃﾞ", "デ");
        HALFWIDTH_KATAKANA_MAP.put("ﾃﾟ", "テ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾄ", "ト");
        HALFWIDTH_KATAKANA_MAP.put("ﾄﾞ", "ド");
        HALFWIDTH_KATAKANA_MAP.put("ﾄﾟ", "ト゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾅ", "ナ");
        HALFWIDTH_KATAKANA_MAP.put("ﾅﾞ", "ナ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾅﾟ", "ナ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾆ", "ニ");
        HALFWIDTH_KATAKANA_MAP.put("ﾆﾞ", "ニ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾆﾟ", "ニ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾇ", "ヌ");
        HALFWIDTH_KATAKANA_MAP.put("ﾇﾞ", "ヌ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾇﾟ", "ヌ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾈ", "ネ");
        HALFWIDTH_KATAKANA_MAP.put("ﾈﾞ", "ネ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾈﾟ", "ネ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾉ", "ノ");
        HALFWIDTH_KATAKANA_MAP.put("ﾉﾞ", "ノ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾉﾟ", "ノ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾊ", "ハ");
        HALFWIDTH_KATAKANA_MAP.put("ﾊﾞ", "バ");
        HALFWIDTH_KATAKANA_MAP.put("ﾊﾟ", "パ");
        HALFWIDTH_KATAKANA_MAP.put("ﾋ", "ヒ");
        HALFWIDTH_KATAKANA_MAP.put("ﾋﾞ", "ビ");
        HALFWIDTH_KATAKANA_MAP.put("ﾋﾟ", "ピ");
        HALFWIDTH_KATAKANA_MAP.put("ﾌ", "フ");
        HALFWIDTH_KATAKANA_MAP.put("ﾌﾞ", "ブ");
        HALFWIDTH_KATAKANA_MAP.put("ﾌﾟ", "プ");
        HALFWIDTH_KATAKANA_MAP.put("ﾍ", "ヘ");
        HALFWIDTH_KATAKANA_MAP.put("ﾍﾞ", "ベ");
        HALFWIDTH_KATAKANA_MAP.put("ﾍﾟ", "ペ");
        HALFWIDTH_KATAKANA_MAP.put("ﾎ", "ホ");
        HALFWIDTH_KATAKANA_MAP.put("ﾎﾞ", "ボ");
        HALFWIDTH_KATAKANA_MAP.put("ﾎﾟ", "ポ");
        HALFWIDTH_KATAKANA_MAP.put("ﾏ", "マ");
        HALFWIDTH_KATAKANA_MAP.put("ﾏﾞ", "マ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾏﾟ", "マ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾐ", "ミ");
        HALFWIDTH_KATAKANA_MAP.put("ﾐﾞ", "ミ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾐﾟ", "ミ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾑ", "ム");
        HALFWIDTH_KATAKANA_MAP.put("ﾑﾞ", "ム゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾑﾟ", "ム゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾒ", "メ");
        HALFWIDTH_KATAKANA_MAP.put("ﾒﾞ", "メ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾒﾟ", "メ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾓ", "モ");
        HALFWIDTH_KATAKANA_MAP.put("ﾓﾞ", "モ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾓﾟ", "モ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾔ", "ヤ");
        HALFWIDTH_KATAKANA_MAP.put("ﾔﾞ", "ヤ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾔﾟ", "ヤ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾕ", "ユ");
        HALFWIDTH_KATAKANA_MAP.put("ﾕﾞ", "ユ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾕﾟ", "ユ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾖ", "ヨ");
        HALFWIDTH_KATAKANA_MAP.put("ﾖﾞ", "ヨ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾖﾟ", "ヨ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾗ", "ラ");
        HALFWIDTH_KATAKANA_MAP.put("ﾗﾞ", "ラ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾗﾟ", "ラ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾘ", "リ");
        HALFWIDTH_KATAKANA_MAP.put("ﾘﾞ", "リ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾘﾟ", "リ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾙ", "ル");
        HALFWIDTH_KATAKANA_MAP.put("ﾙﾞ", "ル゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾙﾟ", "ル゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾚ", "レ");
        HALFWIDTH_KATAKANA_MAP.put("ﾚﾞ", "レ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾚﾟ", "レ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾛ", "ロ");
        HALFWIDTH_KATAKANA_MAP.put("ﾛﾞ", "ロ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾛﾟ", "ロ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾜ", "ワ");
        HALFWIDTH_KATAKANA_MAP.put("ﾜﾞ", "ワ゛");
        HALFWIDTH_KATAKANA_MAP.put("ﾜﾟ", "ワ゜");
        HALFWIDTH_KATAKANA_MAP.put("ﾝ", "ン");
        HALFWIDTH_KATAKANA_MAP.put("ｦ゛", "ヲ゛");
    }

  private static String halfWidthKatakanaToFullWidthKatakana(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    StringBuilder result = new StringBuilder(input.length());
    int i = 0;
    while (i < input.length()) {
      String mapped;
      if (i + 1 < input.length()) {
        mapped = HALFWIDTH_KATAKANA_MAP.get(input.substring(i, i + 2));
        if (mapped != null) {
          result.append(mapped);
          i += 2;
          continue;
        }
      }
      mapped = HALFWIDTH_KATAKANA_MAP.get(String.valueOf(input.charAt(i)));
      if (mapped != null) {
        result.append(mapped);
      } else {
        result.append(input.charAt(i));
      }
      i++;
    }
    return result.toString();
  }

  private static String katakanaToHiragana(String input) {
    StringBuilder result = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); ++i) {
      char c = input.charAt(i);
      if (c >= 0x30A1 && c <= 0x30FA) {
        result.append((char) (c - 0x60));
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }
}
