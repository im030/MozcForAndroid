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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;


/**
 * Skin data.
 */
public class Skin {

  private static final Skin FALLBACK_INSTANCE = new Skin();

  private static final Field[] COPY_FIELDS = collectCopyFields();

  @VisibleForTesting static final int STYLE_CATEGORY_KEYICON_MAIN = 0;
  @VisibleForTesting static final int STYLE_CATEGORY_KEYICON_GUIDE = 1;
  @VisibleForTesting static final int STYLE_CATEGORY_KEYICON_GUIDE_LIGHT = 2;
  @VisibleForTesting static final int STYLE_CATEGORY_KEYICON_MAIN_HIGHLIGHT = 3;
  @VisibleForTesting static final int STYLE_CATEGORY_KEYICON_GUIDE_HIGHLIGHT = 4;
  @VisibleForTesting static final int STYLE_CATEGORY_KEYICON_BOUND = 5;
  @VisibleForTesting static final int STYLE_CATEGORY_KEYICON_TWELVEKEYS_FUNCTION = 6;
  @VisibleForTesting static final int STYLE_CATEGORY_KEYICON_TWELVEKEYS_GLOBE = 7;
  @VisibleForTesting static final int STYLE_CATEGORY_KEYICON_QWERTY_FUNCTION = 8;
  @VisibleForTesting static final int STYLE_CATEGORY_KEYICON_FUNCTION_DARK = 9;
  @VisibleForTesting static final int STYLE_CATEGORY_KEYICON_ENTER = 10;
  @VisibleForTesting static final int STYLE_CATEGORY_KEYICON_ENTER_CIRCLE = 11;
  @VisibleForTesting static final int STYLE_CATEGORY_KEYICON_QWERTY_SHIFT_ON_ARROW = 12;
  @VisibleForTesting static final int STYLE_CATEGORY_KEYPOPUP_HIGHLIGHT = 13;
  @VisibleForTesting static final int STYLE_CATEGORY_SYMBOL_MAJOR = 14;
  @VisibleForTesting static final int STYLE_CATEGORY_SYMBOL_MAJOR_SELECTED = 15;
  @VisibleForTesting static final int STYLE_CATEGORY_SYMBOL_MAJOR_EMOJI_DISABLE_CIRCLE = 16;
  @VisibleForTesting static final int STYLE_CATEGORY_SYMBOL_MINOR = 17;
  @VisibleForTesting static final int STYLE_CATEGORY_SYMBOL_MINOR_SELECTED = 18;
  @VisibleForTesting
  static final int STYLE_CATEGORY_KEYBOARD_FOLDING_BUTTON_BACKGROUND_DEFAULT = 19;
  @VisibleForTesting
  static final int STYLE_CATEGORY_KEYBOARD_FOLDING_BUTTON_BACKGROUND_SCROLLED = 20;

  public int keyboardSeparatorColor;
  public int twelvekeysLayoutReleasedKeyTopColor;
  public int twelvekeysLayoutReleasedKeyBottomColor;
  public int twelvekeysLayoutReleasedKeyHighlightColor;
  public int twelvekeysLayoutReleasedKeyLightShadeColor;
  public int twelvekeysLayoutReleasedKeyDarkShadeColor;
  public int twelvekeysLayoutReleasedKeyShadowColor;

  public int twelvekeysLayoutPressedKeyTopColor;
  public int twelvekeysLayoutPressedKeyBottomColor;
  public int twelvekeysLayoutPressedKeyHighlightColor;
  public int twelvekeysLayoutPressedKeyLightShadeColor;
  public int twelvekeysLayoutPressedKeyDarkShadeColor;
  public int twelvekeysLayoutPressedKeyShadowColor;

  public int twelvekeysLayoutReleasedFunctionKeyTopColor;
  public int twelvekeysLayoutReleasedFunctionKeyBottomColor;
  public int twelvekeysLayoutReleasedFunctionKeyHighlightColor;
  public int twelvekeysLayoutReleasedFunctionKeyLightShadeColor;
  public int twelvekeysLayoutReleasedFunctionKeyDarkShadeColor;
  public int twelvekeysLayoutReleasedFunctionKeyShadowColor;

  public int twelvekeysLayoutPressedFunctionKeyTopColor;
  public int twelvekeysLayoutPressedFunctionKeyBottomColor;
  public int twelvekeysLayoutPressedFunctionKeyHighlightColor;
  public int twelvekeysLayoutPressedFunctionKeyLightShadeColor;
  public int twelvekeysLayoutPressedFunctionKeyDarkShadeColor;
  public int twelvekeysLayoutPressedFunctionKeyShadowColor;

  public int qwertyLayoutReleasedKeyTopColor;
  public int qwertyLayoutReleasedKeyBottomColor;
  public int qwertyLayoutReleasedKeyHighlightColor;
  public int qwertyLayoutReleasedKeyShadowColor;

  public int qwertyLayoutPressedKeyTopColor;
  public int qwertyLayoutPressedKeyBottomColor;
  public int qwertyLayoutPressedKeyHighlightColor;
  public int qwertyLayoutPressedKeyShadowColor;

  public int qwertyLayoutReleasedFunctionKeyTopColor;
  public int qwertyLayoutReleasedFunctionKeyBottomColor;
  public int qwertyLayoutReleasedFunctionKeyHighlightColor;
  public int qwertyLayoutReleasedFunctionKeyShadowColor;

  public int qwertyLayoutPressedFunctionKeyTopColor;
  public int qwertyLayoutPressedFunctionKeyBottomColor;
  public int qwertyLayoutPressedFunctionKeyHighlightColor;
  public int qwertyLayoutPressedFunctionKeyShadowColor;

  public int qwertyLayoutReleasedSpaceKeyTopColor;
  public int qwertyLayoutReleasedSpaceKeyBottomColor;
  public int qwertyLayoutReleasedSpaceKeyHighlightColor;
  public int qwertyLayoutReleasedSpaceKeyShadowColor;

  public int qwertyLayoutPressedSpaceKeyTopColor;
  public int qwertyLayoutPressedSpaceKeyBottomColor;
  public int qwertyLayoutPressedSpaceKeyHighlightColor;
  public int qwertyLayoutPressedSpaceKeyShadowColor;

  public int flickBaseColor;
  public int flickShadeColor;

  public int popupFrameWindowTopColor;
  public int popupFrameWindowBottomColor;
  public int popupFrameWindowShadowColor;

  public int candidateScrollBarTopColor;
  public int candidateScrollBarBottomColor;
  public int candidateBackgroundTopColor;
  public int candidateBackgroundBottomColor;
  public int candidateBackgroundSeparatorColor;
  public int candidateBackgroundHighlightColor;
  public int candidateBackgroundBorderColor;
  public int candidateBackgroundFocusedTopColor;
  public int candidateBackgroundFocusedBottomColor;
  public int candidateBackgroundFocusedShadowColor;

  public int symbolReleasedFunctionKeyTopColor;
  public int symbolReleasedFunctionKeyBottomColor;
  public int symbolReleasedFunctionKeyHighlightColor;
  public int symbolReleasedFunctionKeyShadowColor;

  public int symbolPressedFunctionKeyTopColor;
  public int symbolPressedFunctionKeyBottomColor;
  public int symbolPressedFunctionKeyHighlightColor;
  public int symbolPressedFunctionKeyShadowColor;

  public int symbolScrollBarTopColor;
  public int symbolScrollBarBottomColor;

  public int symbolMinorCategoryTabSelectedColor;
  public int symbolMinorCategoryTabPressedColor;

  public int symbolCandidateBackgroundTopColor;
  public int symbolCandidateBackgroundBottomColor;
  public int symbolCandidateBackgroundHighlightColor;
  public int symbolCandidateBackgroundBorderColor;
  public int symbolCandidateBackgroundSeparatorColor;
  public int symbolMinorCategoryVerticalSeparatorColor;
  public int symbolSeparatorColor;

  public int threeDotsColor;

  public int keyIconMainColor;
  public int keyIconGuideColor;
  public int keyIconGuideLightColor;
  public int keyIconMainHighlightColor;
  public int keyIconGuideHighlightColor;
  public int keyIconBoundColor;
  public int keyIconTwelvekeysFunctionColor;
  public int keyIconTwelvekeysGlobeColor;
  public int keyIconQwertyFunctionColor;
  public int keyIconFunctionDarkColor;
  public int keyIconEnterColor;
  public int keyIconEnterCircleColor;
  public int keyIconQwertyShiftOnArrowColor;
  public int keyPopupHighlightColor;
  public int symbolMajorColor;
  public int symbolMajorSelectedColor;
  public int symbolMinorColor;
  public int symbolMinorSelectedColor;
  public int symbolMajorButtonTopColor;
  public int symbolMajorButtonBottomColor;
  public int symbolMajorButtonPressedTopColor;
  public int symbolMajorButtonPressedBottomColor;
  public int symbolMajorButtonSelectedTopColor;
  public int symbolMajorButtonSelectedBottomColor;
  public int symbolMajorButtonShadowColor;
  public int keyboardFoldingButtonBackgroundDefaultColor;
  public int keyboardFoldingButtonBackgroundScrolledColor;
  public int candidateValueTextColor = Color.BLACK;
  public int candidateValueFocusedTextColor = Color.BLACK;
  public int candidateDescriptionTextColor = Color.GRAY;
  public int buttonFrameButtonPressedColor;

  public float twelvekeysLeftOffsetDimension;
  public float twelvekeysRightOffsetDimension;
  public float twelvekeysTopOffsetDimension;
  public float twelvekeysBottomOffsetDimension;
  public float qwertyLeftOffsetDimension;
  public float qwertyRightOffsetDimension;
  public float qwertyTopOffsetDimension;
  public float qwertyBottomOffsetDimension;
  public float qwertyRoundRadiusDimension;
  public float qwertySpaceKeyHeightDimension;
  public float qwertySpaceKeyHorizontalOffsetDimension;
  public float qwertySpaceKeyRoundRadiusDimension;
  public float symbolMajorButtonRoundDimension;
  public float symbolMajorButtonPaddingDimension;
  public float symbolMinorIndicatorHeightDimension;

  public Drawable buttonFrameBackgroundDrawable = DummyDrawable.getInstance();
  public Drawable narrowFrameBackgroundDrawable = DummyDrawable.getInstance();
  public Drawable keyboardFrameSeparatorBackgroundDrawable = DummyDrawable.getInstance();
  public Drawable symbolSeparatorAboveMajorCategoryBackgroundDrawable = DummyDrawable.getInstance();
  public Drawable windowBackgroundDrawable = DummyDrawable.getInstance();
  public Drawable conversionCandidateViewBackgroundDrawable = DummyDrawable.getInstance();
  public Drawable symbolCandidateViewBackgroundDrawable = DummyDrawable.getInstance();
  public Drawable symbolMajorCategoryBackgroundDrawable = DummyDrawable.getInstance();
  public Drawable scrollBarBackgroundDrawable = DummyDrawable.getInstance();

  private Optional<MozcDrawableFactory> drawableFactory = Optional.absent();

  public Skin() {
  }

  public Skin(Skin other) {
    Preconditions.checkNotNull(other);
    for (Field field : COPY_FIELDS) {
      try {
        field.set(this, field.get(other));
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static Field[] collectCopyFields() {
    List<Field> fields = new ArrayList<Field>();
    for (Field field : Skin.class.getFields()) {
      if (!Modifier.isStatic(field.getModifiers())) {
        fields.add(field);
      }
    }
    return fields.toArray(new Field[fields.size()]);
  }

  /**
   * Returns a copy whose keyboard and panel backgrounds are translucent so that a custom
   * background image can be seen through them.
   */
  public Skin createTranslucentCopy(float keyBackgroundAlpha, float panelBackgroundAlpha) {
    Skin result = new Skin(this);

    result.keyboardSeparatorColor = applyAlpha(this.keyboardSeparatorColor, keyBackgroundAlpha);

    result.twelvekeysLayoutReleasedKeyTopColor =
        applyAlpha(this.twelvekeysLayoutReleasedKeyTopColor, keyBackgroundAlpha);
    result.twelvekeysLayoutReleasedKeyBottomColor =
        applyAlpha(this.twelvekeysLayoutReleasedKeyBottomColor, keyBackgroundAlpha);
    result.twelvekeysLayoutReleasedKeyHighlightColor =
        applyAlpha(this.twelvekeysLayoutReleasedKeyHighlightColor, keyBackgroundAlpha);
    result.twelvekeysLayoutReleasedKeyLightShadeColor =
        applyAlpha(this.twelvekeysLayoutReleasedKeyLightShadeColor, keyBackgroundAlpha);
    result.twelvekeysLayoutReleasedKeyDarkShadeColor =
        applyAlpha(this.twelvekeysLayoutReleasedKeyDarkShadeColor, keyBackgroundAlpha);
    result.twelvekeysLayoutReleasedKeyShadowColor =
        applyAlpha(this.twelvekeysLayoutReleasedKeyShadowColor, keyBackgroundAlpha);

    result.twelvekeysLayoutPressedKeyTopColor =
        applyAlpha(this.twelvekeysLayoutPressedKeyTopColor, keyBackgroundAlpha);
    result.twelvekeysLayoutPressedKeyBottomColor =
        applyAlpha(this.twelvekeysLayoutPressedKeyBottomColor, keyBackgroundAlpha);
    result.twelvekeysLayoutPressedKeyHighlightColor =
        applyAlpha(this.twelvekeysLayoutPressedKeyHighlightColor, keyBackgroundAlpha);
    result.twelvekeysLayoutPressedKeyLightShadeColor =
        applyAlpha(this.twelvekeysLayoutPressedKeyLightShadeColor, keyBackgroundAlpha);
    result.twelvekeysLayoutPressedKeyDarkShadeColor =
        applyAlpha(this.twelvekeysLayoutPressedKeyDarkShadeColor, keyBackgroundAlpha);
    result.twelvekeysLayoutPressedKeyShadowColor =
        applyAlpha(this.twelvekeysLayoutPressedKeyShadowColor, keyBackgroundAlpha);

    result.twelvekeysLayoutReleasedFunctionKeyTopColor =
        applyAlpha(this.twelvekeysLayoutReleasedFunctionKeyTopColor, keyBackgroundAlpha);
    result.twelvekeysLayoutReleasedFunctionKeyBottomColor =
        applyAlpha(this.twelvekeysLayoutReleasedFunctionKeyBottomColor, keyBackgroundAlpha);
    result.twelvekeysLayoutReleasedFunctionKeyHighlightColor =
        applyAlpha(this.twelvekeysLayoutReleasedFunctionKeyHighlightColor, keyBackgroundAlpha);
    result.twelvekeysLayoutReleasedFunctionKeyLightShadeColor =
        applyAlpha(this.twelvekeysLayoutReleasedFunctionKeyLightShadeColor, keyBackgroundAlpha);
    result.twelvekeysLayoutReleasedFunctionKeyDarkShadeColor =
        applyAlpha(this.twelvekeysLayoutReleasedFunctionKeyDarkShadeColor, keyBackgroundAlpha);
    result.twelvekeysLayoutReleasedFunctionKeyShadowColor =
        applyAlpha(this.twelvekeysLayoutReleasedFunctionKeyShadowColor, keyBackgroundAlpha);

    result.twelvekeysLayoutPressedFunctionKeyTopColor =
        applyAlpha(this.twelvekeysLayoutPressedFunctionKeyTopColor, keyBackgroundAlpha);
    result.twelvekeysLayoutPressedFunctionKeyBottomColor =
        applyAlpha(this.twelvekeysLayoutPressedFunctionKeyBottomColor, keyBackgroundAlpha);
    result.twelvekeysLayoutPressedFunctionKeyHighlightColor =
        applyAlpha(this.twelvekeysLayoutPressedFunctionKeyHighlightColor, keyBackgroundAlpha);
    result.twelvekeysLayoutPressedFunctionKeyLightShadeColor =
        applyAlpha(this.twelvekeysLayoutPressedFunctionKeyLightShadeColor, keyBackgroundAlpha);
    result.twelvekeysLayoutPressedFunctionKeyDarkShadeColor =
        applyAlpha(this.twelvekeysLayoutPressedFunctionKeyDarkShadeColor, keyBackgroundAlpha);
    result.twelvekeysLayoutPressedFunctionKeyShadowColor =
        applyAlpha(this.twelvekeysLayoutPressedFunctionKeyShadowColor, keyBackgroundAlpha);

    result.qwertyLayoutReleasedKeyTopColor =
        applyAlpha(this.qwertyLayoutReleasedKeyTopColor, keyBackgroundAlpha);
    result.qwertyLayoutReleasedKeyBottomColor =
        applyAlpha(this.qwertyLayoutReleasedKeyBottomColor, keyBackgroundAlpha);
    result.qwertyLayoutReleasedKeyHighlightColor =
        applyAlpha(this.qwertyLayoutReleasedKeyHighlightColor, keyBackgroundAlpha);
    result.qwertyLayoutReleasedKeyShadowColor =
        applyAlpha(this.qwertyLayoutReleasedKeyShadowColor, keyBackgroundAlpha);

    result.qwertyLayoutPressedKeyTopColor =
        applyAlpha(this.qwertyLayoutPressedKeyTopColor, keyBackgroundAlpha);
    result.qwertyLayoutPressedKeyBottomColor =
        applyAlpha(this.qwertyLayoutPressedKeyBottomColor, keyBackgroundAlpha);
    result.qwertyLayoutPressedKeyHighlightColor =
        applyAlpha(this.qwertyLayoutPressedKeyHighlightColor, keyBackgroundAlpha);
    result.qwertyLayoutPressedKeyShadowColor =
        applyAlpha(this.qwertyLayoutPressedKeyShadowColor, keyBackgroundAlpha);

    result.qwertyLayoutReleasedFunctionKeyTopColor =
        applyAlpha(this.qwertyLayoutReleasedFunctionKeyTopColor, keyBackgroundAlpha);
    result.qwertyLayoutReleasedFunctionKeyBottomColor =
        applyAlpha(this.qwertyLayoutReleasedFunctionKeyBottomColor, keyBackgroundAlpha);
    result.qwertyLayoutReleasedFunctionKeyHighlightColor =
        applyAlpha(this.qwertyLayoutReleasedFunctionKeyHighlightColor, keyBackgroundAlpha);
    result.qwertyLayoutReleasedFunctionKeyShadowColor =
        applyAlpha(this.qwertyLayoutReleasedFunctionKeyShadowColor, keyBackgroundAlpha);

    result.qwertyLayoutPressedFunctionKeyTopColor =
        applyAlpha(this.qwertyLayoutPressedFunctionKeyTopColor, keyBackgroundAlpha);
    result.qwertyLayoutPressedFunctionKeyBottomColor =
        applyAlpha(this.qwertyLayoutPressedFunctionKeyBottomColor, keyBackgroundAlpha);
    result.qwertyLayoutPressedFunctionKeyHighlightColor =
        applyAlpha(this.qwertyLayoutPressedFunctionKeyHighlightColor, keyBackgroundAlpha);
    result.qwertyLayoutPressedFunctionKeyShadowColor =
        applyAlpha(this.qwertyLayoutPressedFunctionKeyShadowColor, keyBackgroundAlpha);

    result.qwertyLayoutReleasedSpaceKeyTopColor =
        applyAlpha(this.qwertyLayoutReleasedSpaceKeyTopColor, keyBackgroundAlpha);
    result.qwertyLayoutReleasedSpaceKeyBottomColor =
        applyAlpha(this.qwertyLayoutReleasedSpaceKeyBottomColor, keyBackgroundAlpha);
    result.qwertyLayoutReleasedSpaceKeyHighlightColor =
        applyAlpha(this.qwertyLayoutReleasedSpaceKeyHighlightColor, keyBackgroundAlpha);
    result.qwertyLayoutReleasedSpaceKeyShadowColor =
        applyAlpha(this.qwertyLayoutReleasedSpaceKeyShadowColor, keyBackgroundAlpha);

    result.qwertyLayoutPressedSpaceKeyTopColor =
        applyAlpha(this.qwertyLayoutPressedSpaceKeyTopColor, keyBackgroundAlpha);
    result.qwertyLayoutPressedSpaceKeyBottomColor =
        applyAlpha(this.qwertyLayoutPressedSpaceKeyBottomColor, keyBackgroundAlpha);
    result.qwertyLayoutPressedSpaceKeyHighlightColor =
        applyAlpha(this.qwertyLayoutPressedSpaceKeyHighlightColor, keyBackgroundAlpha);
    result.qwertyLayoutPressedSpaceKeyShadowColor =
        applyAlpha(this.qwertyLayoutPressedSpaceKeyShadowColor, keyBackgroundAlpha);

    result.flickBaseColor = applyAlpha(this.flickBaseColor, keyBackgroundAlpha);
    result.flickShadeColor = applyAlpha(this.flickShadeColor, keyBackgroundAlpha);
    result.keyboardFoldingButtonBackgroundDefaultColor =
        applyAlpha(this.keyboardFoldingButtonBackgroundDefaultColor, keyBackgroundAlpha);
    result.keyboardFoldingButtonBackgroundScrolledColor =
        applyAlpha(this.keyboardFoldingButtonBackgroundScrolledColor, keyBackgroundAlpha);

    result.candidateScrollBarTopColor =
        applyAlpha(this.candidateScrollBarTopColor, panelBackgroundAlpha);
    result.candidateScrollBarBottomColor =
        applyAlpha(this.candidateScrollBarBottomColor, panelBackgroundAlpha);
    result.candidateBackgroundTopColor =
        applyAlpha(this.candidateBackgroundTopColor, panelBackgroundAlpha);
    result.candidateBackgroundBottomColor =
        applyAlpha(this.candidateBackgroundBottomColor, panelBackgroundAlpha);
    result.candidateBackgroundSeparatorColor =
        applyAlpha(this.candidateBackgroundSeparatorColor, panelBackgroundAlpha);
    result.candidateBackgroundHighlightColor =
        applyAlpha(this.candidateBackgroundHighlightColor, panelBackgroundAlpha);
    result.candidateBackgroundBorderColor =
        applyAlpha(this.candidateBackgroundBorderColor, panelBackgroundAlpha);
    result.candidateBackgroundFocusedTopColor =
        applyAlpha(this.candidateBackgroundFocusedTopColor, panelBackgroundAlpha);
    result.candidateBackgroundFocusedBottomColor =
        applyAlpha(this.candidateBackgroundFocusedBottomColor, panelBackgroundAlpha);
    result.candidateBackgroundFocusedShadowColor =
        applyAlpha(this.candidateBackgroundFocusedShadowColor, panelBackgroundAlpha);

    result.symbolScrollBarTopColor =
        applyAlpha(this.symbolScrollBarTopColor, panelBackgroundAlpha);
    result.symbolScrollBarBottomColor =
        applyAlpha(this.symbolScrollBarBottomColor, panelBackgroundAlpha);
    result.symbolMinorCategoryTabSelectedColor =
        applyAlpha(this.symbolMinorCategoryTabSelectedColor, panelBackgroundAlpha);
    result.symbolMinorCategoryTabPressedColor =
        applyAlpha(this.symbolMinorCategoryTabPressedColor, panelBackgroundAlpha);
    result.symbolCandidateBackgroundTopColor =
        applyAlpha(this.symbolCandidateBackgroundTopColor, panelBackgroundAlpha);
    result.symbolCandidateBackgroundBottomColor =
        applyAlpha(this.symbolCandidateBackgroundBottomColor, panelBackgroundAlpha);
    result.symbolCandidateBackgroundHighlightColor =
        applyAlpha(this.symbolCandidateBackgroundHighlightColor, panelBackgroundAlpha);
    result.symbolCandidateBackgroundBorderColor =
        applyAlpha(this.symbolCandidateBackgroundBorderColor, panelBackgroundAlpha);
    result.symbolCandidateBackgroundSeparatorColor =
        applyAlpha(this.symbolCandidateBackgroundSeparatorColor, panelBackgroundAlpha);
    result.symbolMinorCategoryVerticalSeparatorColor =
        applyAlpha(this.symbolMinorCategoryVerticalSeparatorColor, panelBackgroundAlpha);
    result.symbolSeparatorColor = applyAlpha(this.symbolSeparatorColor, panelBackgroundAlpha);
    result.symbolMajorButtonTopColor =
        applyAlpha(this.symbolMajorButtonTopColor, panelBackgroundAlpha);
    result.symbolMajorButtonBottomColor =
        applyAlpha(this.symbolMajorButtonBottomColor, panelBackgroundAlpha);
    result.symbolMajorButtonPressedTopColor =
        applyAlpha(this.symbolMajorButtonPressedTopColor, panelBackgroundAlpha);
    result.symbolMajorButtonPressedBottomColor =
        applyAlpha(this.symbolMajorButtonPressedBottomColor, panelBackgroundAlpha);
    result.symbolMajorButtonSelectedTopColor =
        applyAlpha(this.symbolMajorButtonSelectedTopColor, panelBackgroundAlpha);
    result.symbolMajorButtonSelectedBottomColor =
        applyAlpha(this.symbolMajorButtonSelectedBottomColor, panelBackgroundAlpha);
    result.symbolMajorButtonShadowColor =
        applyAlpha(this.symbolMajorButtonShadowColor, panelBackgroundAlpha);
    result.buttonFrameButtonPressedColor =
        applyAlpha(this.buttonFrameButtonPressedColor, panelBackgroundAlpha);

    result.buttonFrameBackgroundDrawable =
        createAlphaAdjustedDrawable(this.buttonFrameBackgroundDrawable, panelBackgroundAlpha);
    result.narrowFrameBackgroundDrawable =
        createAlphaAdjustedDrawable(this.narrowFrameBackgroundDrawable, panelBackgroundAlpha);
    result.keyboardFrameSeparatorBackgroundDrawable =
        createAlphaAdjustedDrawable(
            this.keyboardFrameSeparatorBackgroundDrawable, panelBackgroundAlpha);
    result.symbolSeparatorAboveMajorCategoryBackgroundDrawable =
        createAlphaAdjustedDrawable(
            this.symbolSeparatorAboveMajorCategoryBackgroundDrawable, panelBackgroundAlpha);
    result.conversionCandidateViewBackgroundDrawable =
        createAlphaAdjustedDrawable(
            this.conversionCandidateViewBackgroundDrawable, panelBackgroundAlpha);
    result.symbolCandidateViewBackgroundDrawable =
        createAlphaAdjustedDrawable(
            this.symbolCandidateViewBackgroundDrawable, panelBackgroundAlpha);
    result.symbolMajorCategoryBackgroundDrawable =
        createAlphaAdjustedDrawable(
            this.symbolMajorCategoryBackgroundDrawable, panelBackgroundAlpha);
    result.scrollBarBackgroundDrawable =
        createAlphaAdjustedDrawable(this.scrollBarBackgroundDrawable, panelBackgroundAlpha);
    result.windowBackgroundDrawable = new ColorDrawable(Color.TRANSPARENT);

    return result;
  }

  /**
   * Returns a copy whose candidate-view accents, IME button tints, and button-frame background
   * use the supplied theme color instead of the skin's original colors. Alpha from the current
   * (possibly translucent) colors is preserved, so the theme color stays see-through over a
   * custom background.
   */
  public Skin withThemeColor(int themeColor) {
    Skin result = new Skin(this);

    result.buttonFrameBackgroundDrawable = createThemedButtonFrameBackgroundDrawable(themeColor);
    result.conversionCandidateViewBackgroundDrawable =
        createThemedButtonFrameBackgroundDrawable(themeColor);
    result.symbolCandidateViewBackgroundDrawable =
        createThemedButtonFrameBackgroundDrawable(themeColor);
    result.symbolMajorCategoryBackgroundDrawable =
        createThemedButtonFrameBackgroundDrawable(themeColor);
    result.candidateBackgroundTopColor =
        withOriginalAlpha(darken(themeColor, 0.42f), this.candidateBackgroundTopColor);
    result.candidateBackgroundBottomColor =
        withOriginalAlpha(darken(themeColor, 0.18f), this.candidateBackgroundBottomColor);
    result.symbolCandidateBackgroundTopColor =
        withOriginalAlpha(darken(themeColor, 0.42f), this.symbolCandidateBackgroundTopColor);
    result.symbolCandidateBackgroundBottomColor =
        withOriginalAlpha(darken(themeColor, 0.18f), this.symbolCandidateBackgroundBottomColor);
    result.candidateValueTextColor = withOriginalAlpha(0xFFFFFF, this.candidateValueTextColor);
    result.candidateDescriptionTextColor =
        withOriginalAlpha(0xFFB0B0B0, this.candidateDescriptionTextColor);
    result.buttonFrameButtonPressedColor =
        withOriginalAlpha(themeColor, this.buttonFrameButtonPressedColor);
    result.candidateBackgroundFocusedTopColor =
        withOriginalAlpha(themeColor, this.candidateBackgroundFocusedTopColor);
    result.candidateBackgroundFocusedBottomColor =
        withOriginalAlpha(darken(themeColor, 0.85f), this.candidateBackgroundFocusedBottomColor);
    result.candidateScrollBarTopColor =
        withOriginalAlpha(themeColor, this.candidateScrollBarTopColor);
    result.candidateScrollBarBottomColor =
        withOriginalAlpha(darken(themeColor, 0.85f), this.candidateScrollBarBottomColor);
    result.symbolScrollBarTopColor =
        withOriginalAlpha(themeColor, this.symbolScrollBarTopColor);
    result.symbolScrollBarBottomColor =
        withOriginalAlpha(darken(themeColor, 0.85f), this.symbolScrollBarBottomColor);
    result.symbolMinorCategoryTabSelectedColor =
        withOriginalAlpha(themeColor, this.symbolMinorCategoryTabSelectedColor);
    result.symbolMinorCategoryTabPressedColor =
        withOriginalAlpha(themeColor, this.symbolMinorCategoryTabPressedColor);
    result.symbolMajorButtonSelectedTopColor =
        withOriginalAlpha(themeColor, this.symbolMajorButtonSelectedTopColor);
    result.symbolMajorButtonSelectedBottomColor =
        withOriginalAlpha(darken(themeColor, 0.85f), this.symbolMajorButtonSelectedBottomColor);
    result.symbolMajorButtonPressedTopColor =
        withOriginalAlpha(themeColor, this.symbolMajorButtonPressedTopColor);
    result.symbolMajorButtonPressedBottomColor =
        withOriginalAlpha(darken(themeColor, 0.85f), this.symbolMajorButtonPressedBottomColor);
    result.keyboardFoldingButtonBackgroundDefaultColor =
        withOriginalAlpha(themeColor, this.keyboardFoldingButtonBackgroundDefaultColor);
    result.keyboardFoldingButtonBackgroundScrolledColor =
        withOriginalAlpha(themeColor, this.keyboardFoldingButtonBackgroundScrolledColor);

    return result;
  }

  private static Drawable createThemedButtonFrameBackgroundDrawable(int themeColor) {
    GradientDrawable drawable = new GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        new int[] { darken(themeColor, 0.42f), darken(themeColor, 0.18f) });
    return drawable;
  }

  private static int withOriginalAlpha(int color, int referenceColor) {
    return Color.argb(Color.alpha(referenceColor), Color.red(color), Color.green(color),
        Color.blue(color));
  }

  private static int darken(int color, float factor) {
    return Color.rgb(
        Math.round(Color.red(color) * factor),
        Math.round(Color.green(color) * factor),
        Math.round(Color.blue(color) * factor));
  }

  private static int applyAlpha(int color, float alpha) {
    int newAlpha = Math.round(Color.alpha(color) * alpha);
    return (color & 0x00FFFFFF) | (newAlpha << 24);
  }

  private static Drawable createAlphaAdjustedDrawable(Drawable drawable, float alpha) {
    if (drawable == null) {
      return null;
    }
    Drawable.ConstantState constantState = drawable.getConstantState();
    if (constantState == null) {
      return drawable;
    }
    try {
      Drawable copy = constantState.newDrawable();
      copy.mutate();
      copy.setAlpha(Math.round(255.0f * alpha));
      return copy;
    } catch (RuntimeException e) {
      return drawable;
    }
  }

  public static Skin getFallbackInstance() {
    return FALLBACK_INSTANCE;
  }

  public void apply(Paint paint, int category) {
    Preconditions.checkNotNull(paint);

    // At the moment, caller has responsibility to reset paint.
    // TODO(matsuzakit): move all style based logic from MozcDrawableFactory to here.
    paint.setStyle(Style.FILL);
    switch (category) {
      case STYLE_CATEGORY_KEYICON_MAIN:
        paint.setColor(keyIconMainColor);
        break;
      case STYLE_CATEGORY_KEYICON_GUIDE:
        paint.setColor(keyIconGuideColor);
        break;
      case STYLE_CATEGORY_KEYICON_GUIDE_LIGHT:
        paint.setColor(keyIconGuideLightColor);
        break;
      case STYLE_CATEGORY_KEYICON_MAIN_HIGHLIGHT:
        paint.setColor(keyIconMainHighlightColor);
        break;
      case STYLE_CATEGORY_KEYICON_GUIDE_HIGHLIGHT:
        paint.setColor(keyIconGuideHighlightColor);
        break;
      case STYLE_CATEGORY_KEYICON_BOUND:
        paint.setColor(keyIconBoundColor);
        paint.setStyle(Style.STROKE);
        break;
      case STYLE_CATEGORY_KEYICON_TWELVEKEYS_FUNCTION:
        paint.setColor(keyIconTwelvekeysFunctionColor);
        break;
      case STYLE_CATEGORY_KEYICON_TWELVEKEYS_GLOBE:
        paint.setColor(keyIconTwelvekeysGlobeColor);
        break;
      case STYLE_CATEGORY_KEYICON_QWERTY_FUNCTION:
        paint.setColor(keyIconQwertyFunctionColor);
        break;
      case STYLE_CATEGORY_KEYICON_FUNCTION_DARK:
        paint.setColor(keyIconFunctionDarkColor);
        break;
      case STYLE_CATEGORY_KEYICON_ENTER:
        paint.setColor(keyIconEnterColor);
        break;
      case STYLE_CATEGORY_KEYICON_ENTER_CIRCLE:
        paint.setColor(keyIconEnterCircleColor);
        break;
      case STYLE_CATEGORY_KEYICON_QWERTY_SHIFT_ON_ARROW:
        paint.setColor(keyIconQwertyShiftOnArrowColor);
        break;
      case STYLE_CATEGORY_KEYPOPUP_HIGHLIGHT:
        paint.setColor(keyPopupHighlightColor);
        break;
      case STYLE_CATEGORY_SYMBOL_MAJOR:
        paint.setColor(symbolMajorColor);
        break;
      case STYLE_CATEGORY_SYMBOL_MAJOR_SELECTED:
        paint.setColor(symbolMajorSelectedColor);
        break;
      case STYLE_CATEGORY_SYMBOL_MAJOR_EMOJI_DISABLE_CIRCLE:
        paint.setStyle(Style.STROKE);
        paint.setColor(symbolMajorColor);
        paint.setStrokeWidth(0.75f);
        paint.setStrokeMiter(10);
        break;
      case STYLE_CATEGORY_SYMBOL_MINOR:
        paint.setColor(symbolMinorColor);
        break;
      case STYLE_CATEGORY_SYMBOL_MINOR_SELECTED:
        paint.setColor(symbolMinorSelectedColor);
        break;
      case STYLE_CATEGORY_KEYBOARD_FOLDING_BUTTON_BACKGROUND_DEFAULT:
        paint.setColor(keyboardFoldingButtonBackgroundDefaultColor);
        break;
      case STYLE_CATEGORY_KEYBOARD_FOLDING_BUTTON_BACKGROUND_SCROLLED:
        paint.setColor(keyboardFoldingButtonBackgroundScrolledColor);
        break;
      default:
        throw new IllegalStateException("Unknown category: " + category);
    }
  }

  public Drawable getDrawable(Resources resources, int resourceId) {
    Preconditions.checkNotNull(resources);
    return getDrawableFactory(resources).getDrawable(resourceId).or(DummyDrawable.getInstance());
  }

  public Drawable getDrawable(Resources resources, String keyCharacter) {
    Preconditions.checkNotNull(resources);
    return getDrawableFactory(resources).getDrawable(keyCharacter).or(DummyDrawable.getInstance());
  }

  private MozcDrawableFactory getDrawableFactory(Resources resources) {
    if (!drawableFactory.isPresent()) {
      drawableFactory = Optional.of(new MozcDrawableFactory(resources, this));
    }
    return drawableFactory.get();
  }

  @VisibleForTesting
  void resetDrawableFactory() {
    drawableFactory = Optional.absent();
  }
}
