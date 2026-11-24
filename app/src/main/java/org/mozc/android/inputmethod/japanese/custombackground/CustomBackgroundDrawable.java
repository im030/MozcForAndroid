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
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Draws a bitmap so that it covers its bounds (center crop) and applies a brightness filter.
 */
public class CustomBackgroundDrawable extends Drawable {

  private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
  private final Rect sourceRect = new Rect();
  private final Bitmap bitmap;
  private float brightness;

  public CustomBackgroundDrawable(Bitmap bitmap, float brightness) {
    this.bitmap = bitmap;
    this.brightness = brightness;
    updateColorFilter();
  }

  public void setBrightness(float brightness) {
    if (this.brightness == brightness) {
      return;
    }
    this.brightness = brightness;
    updateColorFilter();
    invalidateSelf();
  }

  public float getBrightness() {
    return brightness;
  }

  @Override
  public void draw(Canvas canvas) {
    if (bitmap == null || bitmap.isRecycled()) {
      return;
    }
    Rect bounds = getBounds();
    if (bounds.isEmpty()) {
      return;
    }

    int bitmapWidth = bitmap.getWidth();
    int bitmapHeight = bitmap.getHeight();
    if (bitmapWidth <= 0 || bitmapHeight <= 0) {
      return;
    }

    float scale = Math.max(
        (float) bounds.width() / bitmapWidth, (float) bounds.height() / bitmapHeight);
    float sourceWidth = bounds.width() / scale;
    float sourceHeight = bounds.height() / scale;
    float left = (bitmapWidth - sourceWidth) / 2.0f;
    float top = (bitmapHeight - sourceHeight) / 2.0f;

    sourceRect.set(
        Math.round(left), Math.round(top),
        Math.round(left + sourceWidth), Math.round(top + sourceHeight));
    if (!sourceRect.intersect(0, 0, bitmapWidth, bitmapHeight)) {
      return;
    }

    canvas.drawBitmap(bitmap, sourceRect, bounds, paint);
  }

  @Override
  public void setAlpha(int alpha) {
    if (paint.getAlpha() != alpha) {
      paint.setAlpha(alpha);
      invalidateSelf();
    }
  }

  @Override
  public void setColorFilter(ColorFilter colorFilter) {
    paint.setColorFilter(colorFilter);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }

  @Override
  public int getIntrinsicWidth() {
    return bitmap == null ? -1 : bitmap.getWidth();
  }

  @Override
  public int getIntrinsicHeight() {
    return bitmap == null ? -1 : bitmap.getHeight();
  }

  private void updateColorFilter() {
    if (Math.abs(brightness - 1.0f) < 0.01f) {
      paint.setColorFilter(null);
      return;
    }
    ColorMatrix colorMatrix = new ColorMatrix();
    colorMatrix.setScale(brightness, brightness, brightness, 1.0f);
    paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
  }
}
