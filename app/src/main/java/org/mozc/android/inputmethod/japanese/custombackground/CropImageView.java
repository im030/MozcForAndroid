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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Simple crop view: shows the image, lets the user pan/zoom it, and returns the region inside a
 * fixed aspect ratio frame.
 */
public class CropImageView extends View {

  private static final float MAX_SCALE_FACTOR = 5.0f;

  private final Matrix matrix = new Matrix();
  private final Matrix inverseMatrix = new Matrix();
  private final float[] matrixValues = new float[9];
  private final RectF cropRect = new RectF();
  private final RectF mappedRect = new RectF();
  private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
  private final Paint scrimPaint = new Paint();
  private final Paint borderPaint = new Paint();

  private final ScaleGestureDetector scaleDetector;

  private Bitmap bitmap;
  private float cropAspect = 2.5f;
  private float minScale = 1.0f;
  private float maxScale = 1.0f;
  private float lastX;
  private float lastY;

  public CropImageView(Context context) {
    this(context, null);
  }

  public CropImageView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public CropImageView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    scrimPaint.setColor(0x99000000);
    borderPaint.setColor(0xFFFFFFFF);
    borderPaint.setStyle(Paint.Style.STROKE);
    borderPaint.setStrokeWidth(context.getResources().getDisplayMetrics().density * 2.0f);
    scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    setFocusable(true);
  }

  public void setCropAspect(float cropAspect) {
    this.cropAspect = Math.max(0.5f, cropAspect);
    computeCropRect();
    resetMatrix();
    invalidate();
  }

  public void setBitmap(Bitmap bitmap) {
    this.bitmap = bitmap;
    resetMatrix();
    invalidate();
  }

  public boolean hasBitmap() {
    return bitmap != null && !bitmap.isRecycled();
  }

  public Bitmap getCroppedBitmap() {
    if (!hasBitmap() || cropRect.isEmpty()) {
      return null;
    }
    if (!matrix.invert(inverseMatrix)) {
      return null;
    }

    RectF sourceRect = new RectF(cropRect);
    inverseMatrix.mapRect(sourceRect);
    if (!sourceRect.intersect(0, 0, bitmap.getWidth(), bitmap.getHeight())) {
      return null;
    }

    int left = Math.max(0, (int) Math.floor(sourceRect.left));
    int top = Math.max(0, (int) Math.floor(sourceRect.top));
    int right = Math.min(bitmap.getWidth(), (int) Math.ceil(sourceRect.right));
    int bottom = Math.min(bitmap.getHeight(), (int) Math.ceil(sourceRect.bottom));
    if (right <= left || bottom <= top) {
      return null;
    }
    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    computeCropRect();
    resetMatrix();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (!hasBitmap()) {
      return;
    }

    canvas.drawBitmap(bitmap, matrix, bitmapPaint);

    int width = getWidth();
    int height = getHeight();
    canvas.drawRect(0, 0, width, cropRect.top, scrimPaint);
    canvas.drawRect(0, cropRect.bottom, width, height, scrimPaint);
    canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, scrimPaint);
    canvas.drawRect(cropRect.right, cropRect.top, width, cropRect.bottom, scrimPaint);

    canvas.drawRect(cropRect, borderPaint);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!hasBitmap()) {
      return super.onTouchEvent(event);
    }

    scaleDetector.onTouchEvent(event);

    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        lastX = event.getX();
        lastY = event.getY();
        if (getParent() != null) {
          getParent().requestDisallowInterceptTouchEvent(true);
        }
        return true;
      case MotionEvent.ACTION_MOVE:
        if (!scaleDetector.isInProgress()) {
          float x = event.getX();
          float y = event.getY();
          matrix.postTranslate(x - lastX, y - lastY);
          clampMatrix();
          lastX = x;
          lastY = y;
          invalidate();
        }
        return true;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (getParent() != null) {
          getParent().requestDisallowInterceptTouchEvent(false);
        }
        performClick();
        return true;
      default:
        return true;
    }
  }

  @Override
  public boolean performClick() {
    return super.performClick();
  }

  private void computeCropRect() {
    int width = getWidth();
    int height = getHeight();
    if (width <= 0 || height <= 0) {
      return;
    }

    float availableWidth = width * 0.9f;
    float availableHeight = height * 0.9f;
    float cropWidth;
    float cropHeight;
    if (availableWidth / availableHeight > cropAspect) {
      cropHeight = availableHeight;
      cropWidth = cropHeight * cropAspect;
    } else {
      cropWidth = availableWidth;
      cropHeight = cropWidth / cropAspect;
    }

    float left = (width - cropWidth) / 2.0f;
    float top = (height - cropHeight) / 2.0f;
    cropRect.set(left, top, left + cropWidth, top + cropHeight);
  }

  private void resetMatrix() {
    if (!hasBitmap() || cropRect.isEmpty()) {
      return;
    }

    int bitmapWidth = bitmap.getWidth();
    int bitmapHeight = bitmap.getHeight();
    minScale = Math.max(
        cropRect.width() / bitmapWidth, cropRect.height() / bitmapHeight);
    maxScale = minScale * MAX_SCALE_FACTOR;

    matrix.reset();
    matrix.setScale(minScale, minScale);
    matrix.postTranslate(
        cropRect.centerX() - bitmapWidth * minScale / 2.0f,
        cropRect.centerY() - bitmapHeight * minScale / 2.0f);
    clampMatrix();
  }

  private void clampMatrix() {
    if (!hasBitmap() || cropRect.isEmpty()) {
      return;
    }

    mappedRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
    matrix.mapRect(mappedRect);

    float dx = 0;
    float dy = 0;
    if (mappedRect.left > cropRect.left) {
      dx = cropRect.left - mappedRect.left;
    } else if (mappedRect.right < cropRect.right) {
      dx = cropRect.right - mappedRect.right;
    }
    if (mappedRect.top > cropRect.top) {
      dy = cropRect.top - mappedRect.top;
    } else if (mappedRect.bottom < cropRect.bottom) {
      dy = cropRect.bottom - mappedRect.bottom;
    }
    if (dx != 0 || dy != 0) {
      matrix.postTranslate(dx, dy);
    }
  }

  private float getCurrentScale() {
    matrix.getValues(matrixValues);
    return matrixValues[Matrix.MSCALE_X];
  }

  private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
    @Override
    public boolean onScale(ScaleGestureDetector detector) {
      float currentScale = getCurrentScale();
      if (currentScale <= 0) {
        return false;
      }
      float factor = detector.getScaleFactor();
      float targetScale = currentScale * factor;
      if (targetScale < minScale) {
        factor = minScale / currentScale;
      } else if (targetScale > maxScale) {
        factor = maxScale / currentScale;
      }
      matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
      clampMatrix();
      invalidate();
      return true;
    }
  }
}
