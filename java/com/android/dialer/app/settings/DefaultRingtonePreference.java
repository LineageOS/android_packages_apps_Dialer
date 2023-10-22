/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.dialer.app.settings;

import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.dialer.R;

/** RingtonePreference which doesn't show default ringtone setting. */
public class DefaultRingtonePreference extends Preference {

  private final Intent mRingtonePickerIntent;

  public DefaultRingtonePreference(Context context, AttributeSet attrs) {
    super(context, attrs);

    mRingtonePickerIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
    mRingtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, getRingtoneType());
    mRingtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
    mRingtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
    mRingtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
            Settings.System.DEFAULT_NOTIFICATION_URI);
    mRingtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getTitle());
  }

  public Intent getRingtonePickerIntent() {
    mRingtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
            onRestoreRingtone());
    return mRingtonePickerIntent;
  }

  protected void onSaveRingtone(Uri ringtoneUri) {
    if (!Settings.System.canWrite(getContext())) {
      Toast.makeText(
              getContext(),
              getContext().getResources().getString(R.string.toast_cannot_write_system_settings),
              Toast.LENGTH_SHORT)
          .show();
      return;
    }
    RingtoneManager.setActualDefaultRingtoneUri(getContext(), getRingtoneType(), ringtoneUri);
  }

  protected Uri onRestoreRingtone() {
    return RingtoneManager.getActualDefaultRingtoneUri(getContext(), getRingtoneType());
  }

  private int getRingtoneType() {
    return RingtoneManager.TYPE_RINGTONE;
  }
}
