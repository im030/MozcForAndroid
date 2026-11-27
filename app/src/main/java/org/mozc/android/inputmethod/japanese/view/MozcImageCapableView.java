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

package org.mozc.android.inputmethod.japanese.view;

import org.mozc.android.inputmethod.japanese.R;
import com.google.common.base.Preconditions;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Views which support MozcDrawable should implement this interface
 * delegate some methods.
 */
public interface MozcImageCapableView {

  public void setMaxImageWidth(int maxImageWidth);
  public void setMaxImageHeight(int maxImageHeight);
  public ImageView asImageView();

  /**
   * Delagate object used by the views implementing MozcImageCapableView.
   */
  class MozcImageCapableViewDelegate {

    private static final int INVALID_RESOURCE_ID = 0;
    private static final int UNSPECIFIED_SIZE = -1;
    private static final String XML_NAMESPACE_ANDROID =
        "http://schemas.android.com/apk/res/android";
    private static final String XML_NAMESPACE_MOZC =
        "http://schemas.android.com/apk/res-auto";
    private int derivedTopPadding = 0;
    private int derivedBottomPadding = 0;
    private int derivedLeftPadding = 0;
    private int derivedRightPadding = 0;
    private Skin skin = Skin.getFallbackInstance();
    private int rawId = INVALID_RESOURCE_ID;
    private int maxImageWidth = UNSPECIFIED_SIZE;
    private int maxImageHeight = UNSPECIFIED_SIZE;
    private final MozcImageCapableView baseView;

    MozcImageCapableViewDelegate(MozcImageCapableView baseView) {
      this.baseView = Preconditions.checkNotNull(baseView);
    }

    static boolean assertSrcAttribute(Resources resources, AttributeSet attrs) {
      int srcId = attrs.getAttributeResourceValue(
          XML_NAMESPACE_ANDROID, "src", INVALID_RESOURCE_ID);
      if (srcId == INVALID_RESOURCE_ID) {
        return true;
      }
      return !"raw".equals(resources.getResourceTypeName(srcId));
    }

    void loadMozcImageSource(AttributeSet attrs) {
      rawId = attrs.getAttributeResourceValue(
          XML_NAMESPACE_MOZC, "rawSrc", INVALID_RESOURCE_ID);
      TypedArray typedArray =
          baseView.asImageView().getContext().obtainStyledAttributes(attrs,
                                                                     R.styleable.MozcImageView);
      try {
        maxImageWidth =
            typedArray.getDimensionPixelOffset(R.styleable.MozcImageView_maxImageWidth,
                                               UNSPECIFIED_SIZE);
        maxImageHeight =
            typedArray.getDimensionPixelOffset(R.styleable.MozcImageView_maxImageHeight,
                                               UNSPECIFIED_SIZE);
      } finally {
        typedArray.recycle();
      }
    }

    private void updateMozcDrawable() {
      if (rawId != INVALID_RESOURCE_ID) {
        ImageView view = baseView.asImageView();
        view.setImageDrawable(
            skin.getDrawable(baseView.asImageView().getContext().getResources(), rawId)
                .getConstantState().newDrawable());
        view.invalidate();
      }
    }

    void setRawId(int rawId) {
      this.rawId = rawId;
      updateMozcDrawable();
    }

    @SuppressWarnings("deprecation")
    void setSkin(Skin skin) {
      this.skin = Preconditions.checkNotNull(skin);
      updateMozcDrawable();
    }

    void updateAdditionalPadding() {
      updateAdditionalHorizontalPadding();
      updateAdditionalVerticalPadding();
    }

    void setMaxImageWidth(int maxImageWidth) {
      this.maxImageWidth = maxImageWidth;
      updateAdditionalHorizontalPadding();
    }

    void setMaxImageHeight(int maxImageHeight) {
      this.maxImageHeight = maxImageHeight;
      updateAdditionalVerticalPadding();
    }

    private void updateAdditionalHorizontalPadding() {
      ImageView imageView = baseView.asImageView();
      int paddingLeft = baseView.asImageView().getPaddingLeft() - derivedLeftPadding;
      int paddingRight = baseView.asImageView().getPaddingRight() - derivedRightPadding;
      int additionalHorizontalPadding = maxImageWidth < 0 ? 0 : Math.max(
          0, baseView.asImageView().getWidth() - paddingLeft - paddingRight - maxImageWidth);
      if (additionalHorizontalPadding == 0) {
        derivedLeftPadding = 0;
        derivedRightPadding = 0;
        baseView.asImageView().setPadding(
            paddingLeft, baseView.asImageView().getPaddingTop(), paddingRight,
            baseView.asImageView().getPaddingBottom());
        return;
      }
      int additionalLeftPadding = additionalHorizontalPadding / 2;
      int additionalRightPadding = additionalHorizontalPadding - additionalLeftPadding;
      derivedLeftPadding = additionalLeftPadding;
      derivedRightPadding = additionalRightPadding;
      baseView.asImageView().setPadding(
          paddingLeft + additionalLeftPadding,
          imageView.getPaddingTop(),
          paddingRight + additionalRightPadding,
          imageView.getPaddingBottom());
      imageView.invalidate();
    }

    private void updateAdditionalVerticalPadding() {
      ImageView imageView = baseView.asImageView();
      int paddingTop = imageView.getPaddingTop() - derivedTopPadding;
      int paddingBottom = imageView.getPaddingBottom() - derivedBottomPadding;
      int additionalVerticalPadding = maxImageHeight < 0
          ? 0 : Math.max(0, imageView.getHeight() - paddingTop - paddingBottom - maxImageHeight);
      if (additionalVerticalPadding == 0) {
        derivedTopPadding = 0;
        derivedBottomPadding = 0;
        baseView.asImageView().setPadding(
            imageView.getPaddingLeft(), paddingTop, imageView.getPaddingRight(), paddingBottom);
        return;
      }
      int additionalTopPadding = additionalVerticalPadding / 2;
      int additionalBottomPadding = additionalVerticalPadding - additionalTopPadding;
      derivedTopPadding = additionalTopPadding;
      derivedBottomPadding = additionalBottomPadding;
      baseView.asImageView().setPadding(
          imageView.getPaddingLeft(),
          paddingTop + additionalTopPadding,
          imageView.getPaddingRight(),
          paddingBottom + additionalBottomPadding);
      imageView.invalidate();
    }
  }
}
