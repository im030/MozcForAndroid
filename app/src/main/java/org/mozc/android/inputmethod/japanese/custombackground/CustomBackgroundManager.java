// Copyright 2010-2018, Google Inc.
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

package org.mozc.android.inputmethod.japanese.custombackground;

import com.google.common.base.Optional;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Stores and loads the user selected keyboard background.
 *
 * <p>The original image is copied into app private storage so the keyboard never depends on a
 * short lived content URI. A cropped version is stored separately and used for rendering.
 */
@SuppressLint("ExifInterface")
public final class CustomBackgroundManager {

  public static final String PREF_ENABLED = "pref_custom_background_enabled";
  public static final String PREF_BRIGHTNESS = "pref_custom_background_brightness";
  public static final String PREF_VERSION = "pref_custom_background_version";

  public static final float MIN_BRIGHTNESS = 0.3f;
  public static final float MAX_BRIGHTNESS = 1.0f;
  public static final float DEFAULT_BRIGHTNESS = 1.0f;

  public static final float KEY_BACKGROUND_ALPHA = 0.72f;
  public static final float PANEL_BACKGROUND_ALPHA = 0.85f;

  private static final String DIRECTORY_NAME = "custom_background";
  private static final String SOURCE_FILE_NAME = "source.img";
  private static final String PROCESSED_FILE_NAME = "background.jpg";
  private static final int MAX_DECODE_DIMENSION = 2048;
  private static final int JPEG_QUALITY = 92;

  private CustomBackgroundManager() {
  }

  public static SharedPreferences getSharedPreferences(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
  }

  public static boolean isCustomBackgroundPreference(String key) {
    return PREF_ENABLED.equals(key) || PREF_BRIGHTNESS.equals(key) || PREF_VERSION.equals(key);
  }

  public static boolean isEnabled(Context context) {
    return getSharedPreferences(context).getBoolean(PREF_ENABLED, false) && hasProcessedImage(context);
  }

  public static void setEnabled(Context context, boolean enabled) {
    getSharedPreferences(context).edit().putBoolean(PREF_ENABLED, enabled).apply();
  }

  public static float getBrightness(Context context) {
    float brightness = getSharedPreferences(context)
        .getFloat(PREF_BRIGHTNESS, DEFAULT_BRIGHTNESS);
    return clampBrightness(brightness);
  }

  public static void setBrightness(Context context, float brightness) {
    getSharedPreferences(context).edit()
        .putFloat(PREF_BRIGHTNESS, clampBrightness(brightness))
        .apply();
  }

  public static long getVersion(Context context) {
    return getSharedPreferences(context).getLong(PREF_VERSION, 0L);
  }

  public static void bumpVersion(Context context) {
    getSharedPreferences(context).edit()
        .putLong(PREF_VERSION, getVersion(context) + 1L)
        .apply();
  }

  public static File getDirectory(Context context) {
    File directory = new File(context.getApplicationContext().getFilesDir(), DIRECTORY_NAME);
    if (!directory.exists()) {
      directory.mkdirs();
    }
    return directory;
  }

  public static File getSourceFile(Context context) {
    return new File(getDirectory(context), SOURCE_FILE_NAME);
  }

  public static File getProcessedFile(Context context) {
    return new File(getDirectory(context), PROCESSED_FILE_NAME);
  }

  public static boolean hasSourceImage(Context context) {
    File file = getSourceFile(context);
    return file.exists() && file.length() > 0L;
  }

  public static boolean hasProcessedImage(Context context) {
    File file = getProcessedFile(context);
    return file.exists() && file.length() > 0L;
  }

  public static Optional<Drawable> loadDrawable(Context context) {
    if (!isEnabled(context)) {
      return Optional.absent();
    }
    Bitmap bitmap = loadProcessedBitmap(context);
    if (bitmap == null) {
      return Optional.absent();
    }
    return Optional.<Drawable>of(new CustomBackgroundDrawable(bitmap, getBrightness(context)));
  }

  public static Bitmap loadSourceBitmap(Context context) {
    if (!hasSourceImage(context)) {
      return null;
    }
    return decodeFile(getSourceFile(context), MAX_DECODE_DIMENSION);
  }

  public static Bitmap loadProcessedBitmap(Context context) {
    if (!hasProcessedImage(context)) {
      return null;
    }
    return decodeFile(getProcessedFile(context), MAX_DECODE_DIMENSION);
  }

  public static boolean copyUriToSource(Context context, Uri uri) {
    InputStream input = null;
    OutputStream output = null;
    File temporaryFile = new File(getDirectory(context), SOURCE_FILE_NAME + ".tmp");
    try {
      input = context.getContentResolver().openInputStream(uri);
      if (input == null) {
        return false;
      }
      output = new FileOutputStream(temporaryFile);
      byte[] buffer = new byte[16 * 1024];
      int read;
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      output.flush();
      output.close();
      output = null;
      File target = getSourceFile(context);
      target.delete();
      return temporaryFile.renameTo(target);
    } catch (IOException e) {
      return false;
    } finally {
      closeQuietly(input);
      closeQuietly(output);
      if (temporaryFile.exists()) {
        temporaryFile.delete();
      }
    }
  }

  public static boolean saveProcessedBitmap(Context context, Bitmap bitmap) {
    if (bitmap == null || bitmap.isRecycled()) {
      return false;
    }
    File temporaryFile = new File(getDirectory(context), PROCESSED_FILE_NAME + ".tmp");
    OutputStream output = null;
    try {
      output = new FileOutputStream(temporaryFile);
      Bitmap.CompressFormat format = bitmap.hasAlpha()
          ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
      if (!bitmap.compress(format, JPEG_QUALITY, output)) {
        return false;
      }
      output.flush();
      output.close();
      output = null;
      File target = getProcessedFile(context);
      target.delete();
      return temporaryFile.renameTo(target);
    } catch (IOException e) {
      return false;
    } finally {
      closeQuietly(output);
      if (temporaryFile.exists()) {
        temporaryFile.delete();
      }
    }
  }

  public static void delete(Context context) {
    deleteRecursively(getDirectory(context));
    getSharedPreferences(context).edit()
        .remove(PREF_ENABLED)
        .remove(PREF_BRIGHTNESS)
        .remove(PREF_VERSION)
        .apply();
  }

  private static Bitmap decodeFile(File file, int maxDimension) {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
      return null;
    }

    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension);
    Bitmap bitmap;
    try {
      bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    } catch (OutOfMemoryError e) {
      return null;
    }
    if (bitmap == null) {
      return null;
    }
    return applyExifOrientation(file, bitmap);
  }

  private static int calculateInSampleSize(int width, int height, int maxDimension) {
    if (maxDimension <= 0) {
      return 1;
    }
    int sampleSize = 1;
    while ((width / (sampleSize * 2)) >= maxDimension
        || (height / (sampleSize * 2)) >= maxDimension) {
      sampleSize *= 2;
    }
    return sampleSize;
  }

  @SuppressLint("ExifInterface")
  private static Bitmap applyExifOrientation(File file, Bitmap bitmap) {
    int orientation = ExifInterface.ORIENTATION_NORMAL;
    try {
      ExifInterface exif = new ExifInterface(file.getAbsolutePath());
      orientation = exif.getAttributeInt(
          ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
    } catch (IOException e) {
      // Ignore and use the decoded bitmap as is.
    }

    Matrix matrix = new Matrix();
    switch (orientation) {
      case ExifInterface.ORIENTATION_ROTATE_90:
        matrix.postRotate(90);
        break;
      case ExifInterface.ORIENTATION_ROTATE_180:
        matrix.postRotate(180);
        break;
      case ExifInterface.ORIENTATION_ROTATE_270:
        matrix.postRotate(270);
        break;
      case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
        matrix.postScale(-1.0f, 1.0f);
        break;
      case ExifInterface.ORIENTATION_FLIP_VERTICAL:
        matrix.postScale(1.0f, -1.0f);
        break;
      case ExifInterface.ORIENTATION_TRANSPOSE:
        matrix.postRotate(90);
        matrix.postScale(-1.0f, 1.0f);
        break;
      case ExifInterface.ORIENTATION_TRANSVERSE:
        matrix.postRotate(270);
        matrix.postScale(-1.0f, 1.0f);
        break;
      default:
        return bitmap;
    }

    try {
      Bitmap rotated = Bitmap.createBitmap(
          bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
      if (rotated != bitmap) {
        bitmap.recycle();
      }
      return rotated;
    } catch (OutOfMemoryError e) {
      return bitmap;
    }
  }

  private static float clampBrightness(float brightness) {
    return Math.max(MIN_BRIGHTNESS, Math.min(MAX_BRIGHTNESS, brightness));
  }

  private static void closeQuietly(InputStream input) {
    if (input != null) {
      try {
        input.close();
      } catch (IOException e) {
        // Ignore.
      }
    }
  }

  private static void closeQuietly(OutputStream output) {
    if (output != null) {
      try {
        output.close();
      } catch (IOException e) {
        // Ignore.
      }
    }
  }

  private static void deleteRecursively(File file) {
    if (file == null || !file.exists()) {
      return;
    }
    File[] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        deleteRecursively(child);
      }
    }
    file.delete();
  }
}
