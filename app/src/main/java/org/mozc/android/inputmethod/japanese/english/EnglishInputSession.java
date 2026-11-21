// Copyright 2026 MozcForAndroid contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.mozc.android.inputmethod.japanese.english;

import org.mozc.android.inputmethod.japanese.KeycodeConverter.KeyEventInterface;
import org.mozc.android.inputmethod.japanese.MozcUtil;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands;

import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Client-side English word-hint session, modeled after fcitx5-android's androidkeyboard addon.
 *
 * <p>While active (alphabet keyboard mode, suggestions not suppressed by the editor, preference
 * enabled), letter keys are buffered here instead of being sent to the mozc server. The buffer is
 * shown as composing text and dictionary hints are rendered through the normal candidate views.
 * Selecting a hint commits the word; space/punctuation commit the buffer plus the key; enter
 * commits the buffer and performs the editor action (or inserts a newline in multiline fields).
 *
 * <p>Never active for {@code TYPE_NULL} editors (direct input mode): those must receive raw key
 * events / commits without any composing text.
 */
public class EnglishInputSession {
  /** Callbacks into the hosting IME service. */
  public interface Host {
    @Nullable InputConnection getInputConnection();
    /** Replaces the composing region, keeping the host's selection tracking in sync. */
    void setComposingText(CharSequence text);
    /** Commits text, keeping the host's selection tracking in sync. */
    void commitText(CharSequence text);
    boolean isDirectInputMode();
    boolean isEnglishSuggestionsEnabledByPreference();
    boolean isAlphabetKeyboardMode();
    /** Renders the given words as SUGGESTION candidates (ids are page-relative indices). */
    void renderCandidates(List<String> words);
    /** Renders the full hint list as an EXPAND_SUGGESTION response (expanded candidate view). */
    void renderExpandedCandidates(List<String> words);
    void clearCandidates();
    /** Performs the editor action for the enter key. Returns false if not handled. */
    boolean performEnterAction();
    void sendRawKey(@Nullable KeyEventInterface keyEvent);
    void sendEnterKey();
    int getCandidatePageSize();
  }

  private static final int MAX_HINTS = 20;

  private final Host host;
  @Nullable private volatile EnglishDictionary dictionary;
  @Nullable private EditorInfo editorInfo;
  private final StringBuilder buffer = new StringBuilder();
  private List<String> hints = Collections.emptyList();
  private int pageStart = 0;
  private boolean renderingCandidates = false;
  // True while the full hint list is shown in the expanded candidate view (keyboard folded).
  private boolean expanded = false;

  public EnglishInputSession(Host host) {
    this.host = host;
  }

  /** Called from the background loader thread once the dictionary is parsed. */
  public void setDictionary(EnglishDictionary dictionary) {
    this.dictionary = dictionary;
  }

  private boolean shouldBeActive() {
    return dictionary != null
        && editorInfo != null
        && host.isEnglishSuggestionsEnabledByPreference()
        && !host.isDirectInputMode()
        && !MozcUtil.isSuggestionSuppressed(editorInfo)
        && host.isAlphabetKeyboardMode();
  }

  public boolean isActive() {
    return shouldBeActive();
  }

  public boolean isComposing() {
    return buffer.length() > 0;
  }

  public boolean hasCandidates() {
    return renderingCandidates;
  }

  /** Called on (re)binding to an editor. The framework finishes composing text on field switch. */
  public void onEditorInfo(@Nullable EditorInfo info) {
    editorInfo = info;
    if (!shouldBeActive()) {
      reset();
    }
  }

  /** Called when the software keyboard mode may have changed (e.g. 英数 → かな). */
  public void onKeyboardModeChanged(boolean alphabetMode) {
    if (!alphabetMode) {
      commitPendingInput();
    }
  }

  /** Called when the English-suggestions preference is toggled. */
  public void onPreferenceChanged(boolean enabled) {
    if (!enabled) {
      commitPendingInput();
    }
  }

  /** Commits any pending buffer as-is and clears candidates. Safe to call when idle. */
  public void commitPendingInput() {
    if (buffer.length() > 0) {
      host.commitText(buffer.toString());
    }
    reset();
  }

  /** Clears all state without committing (used on finishInput; framework commits composing). */
  public void reset() {
    buffer.setLength(0);
    hints = Collections.emptyList();
    pageStart = 0;
    expanded = false;
    if (renderingCandidates) {
      renderingCandidates = false;
      host.clearCandidates();
    }
  }

  /**
   * Handles a software-keyboard key event. Returns true if the event was consumed.
   * When not active, any stale composing buffer is committed first so no text is lost.
   */
  public boolean onKeyEvent(
      @Nullable ProtoCommands.KeyEvent mozcKeyEvent, @Nullable KeyEventInterface keyEvent) {
    if (!shouldBeActive()) {
      if (isComposing()) {
        commitPendingInput();
      }
      return false;
    }
    if (mozcKeyEvent == null) {
      return false;
    }
    if (mozcKeyEvent.hasKeyCode()) {
      int code = mozcKeyEvent.getKeyCode();
      if (code <= 0 || code > Character.MAX_VALUE) {
        return false;
      }
      char c = (char) code;
      if (isWordChar(c)) {
        appendChar(c);
        return true;
      }
      // Digits / symbols: commit the pending word followed by the character.
      commitWithSuffix(new String(Character.toChars(code)));
      return true;
    }
    if (mozcKeyEvent.hasSpecialKey()) {
      switch (mozcKeyEvent.getSpecialKey()) {
        case SPACE:
          commitWithSuffix(" ");
          return true;
        case BACKSPACE:
          return onBackspace();
        case ENTER:
        case VIRTUAL_ENTER:
          return onEnter(keyEvent);
        case LEFT:
        case RIGHT:
        case VIRTUAL_LEFT:
        case VIRTUAL_RIGHT:
        case UP:
        case DOWN:
        case VIRTUAL_UP:
        case VIRTUAL_DOWN:
        case HOME:
        case END:
          // Cursor movement: commit the word, let the normal path move the cursor.
          commitPendingInput();
          return false;
        default:
          commitPendingInput();
          return false;
      }
    }
    return false;
  }

  /**
   * Handles a hardware key event. Returns true if consumed. Only simple typing keys are
   * handled here; everything else falls through to the normal mozc path (after committing
   * any pending buffer).
   */
  public boolean onHardwareKeyEvent(KeyEvent event) {
    if (!shouldBeActive()) {
      if (isComposing()) {
        commitPendingInput();
      }
      return false;
    }
    int keyCode = event.getKeyCode();
    if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
      int unicode = event.getUnicodeChar();
      if (unicode == 0) {
        unicode = 'a' + (keyCode - KeyEvent.KEYCODE_A);
      }
      appendChar((char) unicode);
      return true;
    }
    switch (keyCode) {
      case KeyEvent.KEYCODE_DEL:
        return onBackspace();
      case KeyEvent.KEYCODE_SPACE:
        commitWithSuffix(" ");
        return true;
      case KeyEvent.KEYCODE_ENTER:
      case KeyEvent.KEYCODE_NUMPAD_ENTER:
        return onEnter(null);
      default:
        if (isComposing()) {
          commitPendingInput();
        }
        return false;
    }
  }

  /** Selects a rendered hint. {@code id} is the page-relative candidate id. */
  public void selectCandidate(int id) {
    int index = pageStart + id;
    if (index < 0 || index >= hints.size()) {
      return;
    }
    String word = hints.get(index);
    // commitText replaces the composing region with the selected word.
    host.commitText(word);
    reset();
  }

  public void pageDown() {
    if (expanded) {
      return;  // expanded view scrolls natively; paging would collapse it
    }
    int pageSize = Math.max(1, host.getCandidatePageSize());
    if (pageStart + pageSize < hints.size()) {
      pageStart += pageSize;
      renderPage();
    }
  }

  public void pageUp() {
    if (expanded) {
      return;
    }
    int pageSize = Math.max(1, host.getCandidatePageSize());
    if (pageStart > 0) {
      pageStart = Math.max(0, pageStart - pageSize);
      renderPage();
    }
  }

  /**
   * Shows the full hint list in the expanded candidate view (fold button pressed or the
   * view scrolled near its bottom). Must be rendered as an EXPAND_SUGGESTION response so
   * CandidateView keeps its expanded state; the mozc server knows nothing about these
   * client-side hints and would otherwise return an empty list (blank white view).
   */
  public void expandCandidates() {
    if (hints.isEmpty()) {
      return;
    }
    pageStart = 0;
    expanded = true;
    renderingCandidates = true;
    host.renderExpandedCandidates(hints);
  }

  private static boolean isWordChar(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-' || c == '\'';
  }

  private void appendChar(char c) {
    if (buffer.length() == 0) {
      c = applyAutoCapitalization(c);
    } else if (hasCapFlag(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)) {
      c = Character.toUpperCase(c);
    }
    buffer.append(c);
    host.setComposingText(buffer.toString());
    updateHints();
  }

  private boolean hasCapFlag(int flag) {
    return editorInfo != null
        && (editorInfo.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        && (editorInfo.inputType & flag) != 0;
  }

  /** Applies editor-requested capitalization (CAP_CHARACTERS / CAP_WORDS / CAP_SENTENCES). */
  private char applyAutoCapitalization(char c) {
    if (!Character.isLowerCase(c)) {
      return c;  // explicit shift input wins
    }
    if (hasCapFlag(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        || hasCapFlag(InputType.TYPE_TEXT_FLAG_CAP_WORDS)) {
      return Character.toUpperCase(c);
    }
    if (hasCapFlag(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)) {
      InputConnection ic = host.getInputConnection();
      CharSequence before = ic == null ? null : ic.getTextBeforeCursor(3, 0);
      if (before == null || before.length() == 0) {
        return Character.toUpperCase(c);
      }
      // Sentence start when the last non-space character terminates a sentence.
      int i = before.length() - 1;
      while (i >= 0 && Character.isWhitespace(before.charAt(i))) {
        i--;
      }
      if (i < 0) {
        return Character.toUpperCase(c);
      }
      char last = before.charAt(i);
      if (last == '.' || last == '!' || last == '?' || last == '\n') {
        return Character.toUpperCase(c);
      }
    }
    return c;
  }

  private void updateHints() {
    EnglishDictionary dict = dictionary;
    if (dict == null) {
      return;
    }
    hints = dict.hint(buffer.toString(), MAX_HINTS);
    pageStart = 0;
    renderPage();
  }

  private void renderPage() {
    expanded = false;
    int pageSize = Math.max(1, host.getCandidatePageSize());
    if (hints.isEmpty()) {
      if (renderingCandidates) {
        renderingCandidates = false;
        host.clearCandidates();
      }
      return;
    }
    int end = Math.min(pageStart + pageSize, hints.size());
    renderingCandidates = true;
    host.renderCandidates(hints.subList(pageStart, end));
  }

  private boolean onBackspace() {
    if (buffer.length() == 0) {
      return false;  // let the normal path delete app text
    }
    buffer.deleteCharAt(buffer.length() - 1);
    host.setComposingText(buffer.toString());
    if (buffer.length() == 0) {
      reset();
    } else {
      updateHints();
    }
    return true;
  }

  private boolean onEnter(@Nullable KeyEventInterface keyEvent) {
    if (editorInfo != null && MozcUtil.isMultilineInput(editorInfo)) {
      commitWithSuffix("\n");
      return true;
    }
    commitPendingInput();
    if (!host.performEnterAction()) {
      if (keyEvent != null) {
        host.sendRawKey(keyEvent);
      } else {
        host.sendEnterKey();
      }
    }
    return true;
  }

  private void commitWithSuffix(String suffix) {
    host.commitText(buffer.toString() + suffix);
    reset();
  }
}
