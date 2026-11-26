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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;

/**
 * Samples the dominant color of a bitmap by quantizing pixels into RGB buckets and picking the
 * most populated one. Transparent pixels are skipped.
 */
public final class ColorSampler {

  static final int NO_COLOR = -1;

  private static final int SAMPLE_SIZE = 48;
  private static final int BUCKET_COUNT = 16 * 16 * 16;
  private static final int MIN_ALPHA = 0x40;

  private ColorSampler() {
  }

  public static int sample(Bitmap bitmap) {
    if (bitmap == null || bitmap.isRecycled()
        || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
      return NO_COLOR;
    }
    try {
      int sourceWidth = bitmap.getWidth();
      int sourceHeight = bitmap.getHeight();
      float scale = Math.min(1.0f,
          (float) SAMPLE_SIZE / Math.max(sourceWidth, sourceHeight));
      int sampleWidth = Math.max(1, Math.round(sourceWidth * scale));
      int sampleHeight = Math.max(1, Math.round(sourceHeight * scale));
      Bitmap small = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888);
      try {
        Canvas canvas = new Canvas(small);
        Rect target = new Rect(0, 0, sampleWidth, sampleHeight);
        canvas.drawBitmap(bitmap, null, target, null);
        int[] pixels = new int[sampleWidth * sampleHeight];
        small.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight);

        int[] bucketCount = new int[BUCKET_COUNT];
        int[] bucketRed = new int[BUCKET_COUNT];
        int[] bucketGreen = new int[BUCKET_COUNT];
        int[] bucketBlue = new int[BUCKET_COUNT];

        for (int i = 0; i < pixels.length; i++) {
          int pixel = pixels[i];
          if ((pixel >>> 24) < MIN_ALPHA) {
            continue;
          }
          int red = Color.red(pixel);
          int green = Color.green(pixel);
          int blue = Color.blue(pixel);
          int bucket = ((red >>> 4) << 8) | ((green >>> 4) << 4) | (blue >>> 4);
          bucketCount[bucket]++;
          bucketRed[bucket] += red;
          bucketGreen[bucket] += green;
          bucketBlue[bucket] += blue;
        }

        int maxBucket = NO_COLOR;
        int maxCount = 0;
        for (int bucket = 0; bucket < BUCKET_COUNT; bucket++) {
          if (bucketCount[bucket] > maxCount) {
            maxCount = bucketCount[bucket];
            maxBucket = bucket;
          }
        }
        if (maxBucket < 0 || maxCount <= 0) {
          return NO_COLOR;
        }
        int red = bucketRed[maxBucket] / maxCount;
        int green = bucketGreen[maxBucket] / maxCount;
        int blue = bucketBlue[maxBucket] / maxCount;
        return Color.rgb(red, green, blue);
      } finally {
        if (small != bitmap) {
          small.recycle();
        }
      }
    } catch (OutOfMemoryError e) {
      return NO_COLOR;
    } catch (IllegalArgumentException e) {
      return NO_COLOR;
    }
  }
}
