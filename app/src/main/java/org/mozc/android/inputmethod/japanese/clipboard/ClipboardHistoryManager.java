package org.mozc.android.inputmethod.japanese.clipboard;

import org.mozc.android.inputmethod.japanese.preference.PreferenceUtil;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;

import java.util.List;

/**
 * Listens to the system clipboard and keeps a configurable internal history.
 */
public class ClipboardHistoryManager {
  public static final int DEFAULT_HISTORY_LIMIT = 10;

  private static ClipboardHistoryManager instance;

  public static synchronized ClipboardHistoryManager getInstance(Context context) {
    if (instance == null) {
      instance = new ClipboardHistoryManager(context.getApplicationContext());
    }
    return instance;
  }

  private final Context context;
  private final ClipboardDatabase database;
  private final ClipboardManager clipboardManager;
  private boolean listening;

  private final ClipboardManager.OnPrimaryClipChangedListener clipChangedListener =
      new ClipboardManager.OnPrimaryClipChangedListener() {
        @Override
        public void onPrimaryClipChanged() {
          handlePrimaryClipChanged();
        }
      };

  private ClipboardHistoryManager(Context context) {
    this.context = context;
    this.database = new ClipboardDatabase(context);
    this.clipboardManager =
        ClipboardManager.class.cast(context.getSystemService(Context.CLIPBOARD_SERVICE));
  }

  public synchronized void start() {
    if (listening || clipboardManager == null) {
      return;
    }
    clipboardManager.addPrimaryClipChangedListener(clipChangedListener);
    listening = true;
  }

  public synchronized void stop() {
    if (!listening || clipboardManager == null) {
      return;
    }
    clipboardManager.removePrimaryClipChangedListener(clipChangedListener);
    listening = false;
  }

  public void add(String text) {
    if (text == null || text.length() == 0) {
      return;
    }
    database.addEntry(text, getHistoryLimit());
  }

  public List<ClipboardEntry> getEntries() {
    return database.getEntries(getHistoryLimit());
  }

  public void clear() {
    database.clear();
  }

  private int getHistoryLimit() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    int limit = preferences.getInt(
        PreferenceUtil.PREF_CLIPBOARD_HISTORY_LIMIT, DEFAULT_HISTORY_LIMIT);
    return Math.max(1, Math.min(limit, 100));
  }

  private void handlePrimaryClipChanged() {
    if (clipboardManager == null) {
      return;
    }
    ClipData clip = clipboardManager.getPrimaryClip();
    if (clip == null || clip.getItemCount() == 0) {
      return;
    }
    if (isSensitive(clip.getDescription())) {
      return;
    }
    ClipData.Item item = clip.getItemAt(0);
    CharSequence text = item.getText();
    if (text == null || text.length() == 0) {
      return;
    }
    add(text.toString());
  }

  private static boolean isSensitive(ClipDescription description) {
    if (description == null) {
      return false;
    }
    Object extras;
    try {
      extras = ClipDescription.class.getMethod("getExtras").invoke(description);
    } catch (Exception e) {
      return false;
    }
    if (extras instanceof Bundle) {
      return ((Bundle) extras).getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false);
    }
    if (extras instanceof PersistableBundle) {
      return ((PersistableBundle) extras).getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false);
    }
    return false;
  }
}
