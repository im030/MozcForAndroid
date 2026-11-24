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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;

/**
 * Preference entry that launches the custom background builder and shows its state.
 */
public class CustomBackgroundPreference extends Preference {

  private final SharedPreferences.OnSharedPreferenceChangeListener listener =
      new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
          if (CustomBackgroundManager.isCustomBackgroundPreference(key)) {
            notifyChanged();
          }
        }
      };

  public CustomBackgroundPreference(Context context) {
    this(context, null);
  }

  public CustomBackgroundPreference(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public CustomBackgroundPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setIntent(new Intent(context, CustomBackgroundBuilderActivity.class));
  }

  @Override
  protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
    super.onAttachedToHierarchy(preferenceManager);
    preferenceManager.getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);
  }

  @Override
  protected void onPrepareForRemoval() {
    PreferenceManager preferenceManager = getPreferenceManager();
    if (preferenceManager != null) {
      preferenceManager.getSharedPreferences()
          .unregisterOnSharedPreferenceChangeListener(listener);
    }
    super.onPrepareForRemoval();
  }

  @Override
  public void onBindView(View view) {
    super.onBindView(view);
    setSummary(CustomBackgroundManager.isEnabled(getContext())
        ? R.string.custom_background_summary_enabled
        : R.string.custom_background_summary_disabled);
  }
}
