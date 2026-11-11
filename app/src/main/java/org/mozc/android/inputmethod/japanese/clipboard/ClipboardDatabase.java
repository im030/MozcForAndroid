package org.mozc.android.inputmethod.japanese.clipboard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite storage for the internal clipboard history.
 */
class ClipboardDatabase extends SQLiteOpenHelper {
  private static final String DATABASE_NAME = "mozc_clipboard.db";
  private static final int DATABASE_VERSION = 1;

  private static final String TABLE_NAME = "clipboard_entry";
  private static final String COLUMN_ID = "_id";
  private static final String COLUMN_TEXT = "text";
  private static final String COLUMN_TIMESTAMP = "timestamp";

  ClipboardDatabase(Context context) {
    super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE " + TABLE_NAME + " ("
        + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + COLUMN_TEXT + " TEXT NOT NULL, "
        + COLUMN_TIMESTAMP + " INTEGER NOT NULL)");
    db.execSQL("CREATE INDEX " + TABLE_NAME + "_timestamp_index ON "
        + TABLE_NAME + " (" + COLUMN_TIMESTAMP + " DESC)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
    onCreate(db);
  }

  synchronized void addEntry(String text, int limit) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      db.delete(TABLE_NAME, COLUMN_TEXT + " = ?", new String[] {text});
      ContentValues values = new ContentValues();
      values.put(COLUMN_TEXT, text);
      values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
      db.insertOrThrow(TABLE_NAME, null, values);
      if (limit <= 0) {
        db.delete(TABLE_NAME, null, null);
      } else {
        db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + COLUMN_ID + " NOT IN ("
                + "SELECT " + COLUMN_ID + " FROM " + TABLE_NAME
                + " ORDER BY " + COLUMN_TIMESTAMP + " DESC, " + COLUMN_ID + " DESC LIMIT ?)",
            new Object[] {Integer.valueOf(limit)});
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  synchronized List<ClipboardEntry> getEntries(int limit) {
    List<ClipboardEntry> result = new ArrayList<ClipboardEntry>();
    if (limit <= 0) {
      return result;
    }
    Cursor cursor = getReadableDatabase().query(
        TABLE_NAME,
        new String[] {COLUMN_ID, COLUMN_TEXT, COLUMN_TIMESTAMP},
        null, null, null, null,
        COLUMN_TIMESTAMP + " DESC, " + COLUMN_ID + " DESC",
        Integer.toString(limit));
    try {
      while (cursor.moveToNext()) {
        result.add(new ClipboardEntry(
            cursor.getLong(0), cursor.getString(1), cursor.getLong(2)));
      }
    } finally {
      cursor.close();
    }
    return result;
  }

  synchronized void clear() {
    getWritableDatabase().delete(TABLE_NAME, null, null);
  }
}
