package org.mozc.android.inputmethod.japanese.clipboard;

/**
 * One entry in the internal clipboard history.
 */
public class ClipboardEntry {
  private final long id;
  private final String text;
  private final long timestamp;

  public ClipboardEntry(long id, String text, long timestamp) {
    this.id = id;
    this.text = text;
    this.timestamp = timestamp;
  }

  public long getId() {
    return id;
  }

  public String getText() {
    return text;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
