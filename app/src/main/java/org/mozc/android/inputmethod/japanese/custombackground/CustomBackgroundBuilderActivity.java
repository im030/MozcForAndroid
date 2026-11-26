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

import org.mozc.android.inputmethod.japanese.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lets the user pick an image, crop it to the keyboard area, and adjust its brightness.
 */
public class CustomBackgroundBuilderActivity extends Activity {

  private static final int REQUEST_PICK_IMAGE = 1;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());

  private CropImageView cropView;
  private View emptyState;
  private View cropControls;
  private View brightnessContainer;
  private ImageView previewView;
  private SeekBar brightnessSeekBar;
  private Button chooseButton;
  private Button removeButton;

  private Bitmap sourceBitmap;
  private Bitmap croppedBitmap;
  private boolean showingBrightness;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.custom_background_builder);
    setTitle(R.string.custom_background_title);

    cropView = findViewById(R.id.custom_background_crop_view);
    emptyState = findViewById(R.id.custom_background_empty_state);
    cropControls = findViewById(R.id.custom_background_crop_controls);
    brightnessContainer = findViewById(R.id.custom_background_brightness_container);
    previewView = findViewById(R.id.custom_background_preview);
    brightnessSeekBar = findViewById(R.id.custom_background_brightness_seek_bar);
    chooseButton = findViewById(R.id.custom_background_choose_button);
    removeButton = findViewById(R.id.custom_background_remove_button);

    cropView.setCropAspect(computeCropAspect());

    View.OnClickListener chooseListener = new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        pickImage();
      }
    };
    chooseButton.setOnClickListener(chooseListener);
    findViewById(R.id.custom_background_change_button).setOnClickListener(chooseListener);

    findViewById(R.id.custom_background_next_button).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        showBrightnessStage();
      }
    });
    findViewById(R.id.custom_background_back_button).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        showCropStage(sourceBitmap);
      }
    });
    findViewById(R.id.custom_background_save_button).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        save();
      }
    });
    removeButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        confirmRemove();
      }
    });

    brightnessSeekBar.setMax(100);
    brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        applyPreviewBrightness(progressToBrightness(progress));
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
      }
    });

    float brightness = CustomBackgroundManager.getBrightness(this);
    brightnessSeekBar.setProgress(brightnessToProgress(brightness));

    loadExistingSource();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    executor.shutdownNow();
    // Do not recycle bitmaps that were handed to a View. Android may still be drawing the last
    // frame on RenderThread after this Activity starts being destroyed; recycling here can race
    // that draw and abort the process in native bitmap code.
  }

  @Override
  public void onBackPressed() {
    if (showingBrightness) {
      showCropStage(sourceBitmap);
      return;
    }
    super.onBackPressed();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != REQUEST_PICK_IMAGE || resultCode != RESULT_OK || data == null) {
      return;
    }
    final Uri uri = data.getData();
    if (uri == null) {
      return;
    }

    setLoading(true);
    executor.execute(new Runnable() {
      @Override
      public void run() {
        final boolean copied = CustomBackgroundManager.copyUriToSource(
            getApplicationContext(), uri);
        final Bitmap bitmap = copied
            ? CustomBackgroundManager.loadSourceBitmap(getApplicationContext()) : null;
        handler.post(new Runnable() {
          @Override
          public void run() {
            if (isActivityGone()) {
              if (bitmap != null) {
                bitmap.recycle();
              }
              return;
            }
            setLoading(false);
            if (bitmap == null) {
              Toast.makeText(CustomBackgroundBuilderActivity.this,
                  R.string.custom_background_error_load, Toast.LENGTH_SHORT).show();
              if (sourceBitmap != null) {
                showCropStage(sourceBitmap);
              } else {
                showEmptyStage();
              }
              return;
            }
            croppedBitmap = null;
            sourceBitmap = bitmap;
            showCropStage(bitmap);
          }
        });
      }
    });
  }

  private boolean isActivityGone() {
    return isFinishing() || isDestroyed();
  }

  private void pickImage() {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.setType("image/*");
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    try {
      startActivityForResult(intent, REQUEST_PICK_IMAGE);
    } catch (android.content.ActivityNotFoundException e) {
      Toast.makeText(this, R.string.custom_background_error_load, Toast.LENGTH_SHORT).show();
    }
  }

  private void loadExistingSource() {
    if (!CustomBackgroundManager.hasSourceImage(this)) {
      showEmptyStage();
      return;
    }
    setLoading(true);
    executor.execute(new Runnable() {
      @Override
      public void run() {
        final Bitmap bitmap = CustomBackgroundManager.loadSourceBitmap(getApplicationContext());
        handler.post(new Runnable() {
          @Override
          public void run() {
            if (isActivityGone()) {
              if (bitmap != null) {
                bitmap.recycle();
              }
              return;
            }
            setLoading(false);
            if (bitmap == null) {
              showEmptyStage();
              return;
            }
            sourceBitmap = bitmap;
            showCropStage(bitmap);
          }
        });
      }
    });
  }

  private void showEmptyStage() {
    showingBrightness = false;
    cropView.setVisibility(View.GONE);
    emptyState.setVisibility(View.VISIBLE);
    cropControls.setVisibility(View.GONE);
    brightnessContainer.setVisibility(View.GONE);
    removeButton.setVisibility(CustomBackgroundManager.hasSourceImage(this)
        ? View.VISIBLE : View.GONE);
  }

  private void showCropStage(Bitmap bitmap) {
    showingBrightness = false;
    if (bitmap == null) {
      showEmptyStage();
      return;
    }
    cropView.setBitmap(bitmap);
    cropView.setVisibility(View.VISIBLE);
    emptyState.setVisibility(View.GONE);
    cropControls.setVisibility(View.VISIBLE);
    brightnessContainer.setVisibility(View.GONE);
    removeButton.setVisibility(View.VISIBLE);
  }

  private void showBrightnessStage() {
    if (!cropView.hasBitmap()) {
      return;
    }
    Bitmap cropped = cropView.getCroppedBitmap();
    if (cropped == null) {
      Toast.makeText(this, R.string.custom_background_error_save, Toast.LENGTH_SHORT).show();
      return;
    }
    croppedBitmap = cropped;
    previewView.setImageBitmap(cropped);
    applyPreviewBrightness(progressToBrightness(brightnessSeekBar.getProgress()));

    showingBrightness = true;
    cropView.setVisibility(View.GONE);
    emptyState.setVisibility(View.GONE);
    cropControls.setVisibility(View.GONE);
    brightnessContainer.setVisibility(View.VISIBLE);
    removeButton.setVisibility(View.GONE);
  }

  private void save() {
    if (croppedBitmap == null || croppedBitmap.isRecycled()) {
      return;
    }
    int themeColor = ColorSampler.sample(croppedBitmap);
    if (!CustomBackgroundManager.saveProcessedBitmap(this, croppedBitmap)) {
      Toast.makeText(this, R.string.custom_background_error_save, Toast.LENGTH_SHORT).show();
      return;
    }
    if (themeColor != ColorSampler.NO_COLOR) {
      CustomBackgroundManager.setThemeColor(this, themeColor);
    } else {
      CustomBackgroundManager.getSharedPreferences(this).edit()
          .remove(CustomBackgroundManager.PREF_THEME_COLOR).apply();
    }
    CustomBackgroundManager.setBrightness(
        this, progressToBrightness(brightnessSeekBar.getProgress()));
    CustomBackgroundManager.setEnabled(this, true);
    CustomBackgroundManager.bumpVersion(this);
    setResult(RESULT_OK);
    finish();
  }

  private void confirmRemove() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.custom_background_remove)
        .setMessage(R.string.custom_background_remove_message)
        .setPositiveButton(R.string.custom_background_remove,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                CustomBackgroundManager.delete(CustomBackgroundBuilderActivity.this);
                setResult(RESULT_OK);
                finish();
              }
            })
        .setNegativeButton(R.string.custom_background_cancel, null)
        .show();
  }

  private void setLoading(boolean loading) {
    chooseButton.setEnabled(!loading);
    if (loading) {
      emptyState.setVisibility(View.VISIBLE);
      cropView.setVisibility(View.GONE);
      cropControls.setVisibility(View.GONE);
    }
  }

  private void applyPreviewBrightness(float brightness) {
    ColorMatrix colorMatrix = new ColorMatrix();
    colorMatrix.setScale(brightness, brightness, brightness, 1.0f);
    previewView.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
  }

  private float computeCropAspect() {
    int width = getResources().getDisplayMetrics().widthPixels;
    int keyboardHeight = Math.max(1,
        getResources().getDimensionPixelSize(R.dimen.ime_window_height));
    float aspect = (float) width / keyboardHeight;
    return Math.max(1.2f, Math.min(4.0f, aspect));
  }

  private static float progressToBrightness(int progress) {
    float ratio = progress / 100.0f;
    return CustomBackgroundManager.MIN_BRIGHTNESS
        + (CustomBackgroundManager.MAX_BRIGHTNESS - CustomBackgroundManager.MIN_BRIGHTNESS) * ratio;
  }

  private static int brightnessToProgress(float brightness) {
    float ratio = (brightness - CustomBackgroundManager.MIN_BRIGHTNESS)
        / (CustomBackgroundManager.MAX_BRIGHTNESS - CustomBackgroundManager.MIN_BRIGHTNESS);
    return Math.round(Math.max(0.0f, Math.min(1.0f, ratio)) * 100.0f);
  }
}
