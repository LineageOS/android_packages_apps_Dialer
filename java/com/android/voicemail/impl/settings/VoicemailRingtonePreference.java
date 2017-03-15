/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.voicemail.impl.settings;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Message;
import android.preference.RingtonePreference;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import com.android.dialer.common.Assert;
import com.android.dialer.util.SettingsUtil;

/**
 * Looks up the voicemail ringtone's name asynchronously and updates the preference's summary when
 * it is created or updated.
 */
@TargetApi(VERSION_CODES.O)
public class VoicemailRingtonePreference extends RingtonePreference {

  /** Callback when the ringtone name has been fetched. */
  public interface VoicemailRingtoneNameChangeListener {
    void onVoicemailRingtoneNameChanged(CharSequence name);
  }

  private static final int MSG_UPDATE_VOICEMAIL_RINGTONE_SUMMARY = 1;

  private PhoneAccountHandle phoneAccountHandle;
  private final TelephonyManager telephonyManager;

  private VoicemailRingtoneNameChangeListener mVoicemailRingtoneNameChangeListener;
  private Runnable mVoicemailRingtoneLookupRunnable;
  private final Handler mVoicemailRingtoneLookupComplete =
      new Handler() {
        @Override
        public void handleMessage(Message msg) {
          switch (msg.what) {
            case MSG_UPDATE_VOICEMAIL_RINGTONE_SUMMARY:
              if (mVoicemailRingtoneNameChangeListener != null) {
                mVoicemailRingtoneNameChangeListener.onVoicemailRingtoneNameChanged(
                    (CharSequence) msg.obj);
              }
              setSummary((CharSequence) msg.obj);
              break;
            default:
              Assert.fail();
          }
        }
      };

  public VoicemailRingtonePreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    telephonyManager = context.getSystemService(TelephonyManager.class);
  }

  public void init(PhoneAccountHandle phoneAccountHandle, CharSequence oldRingtoneName) {
    this.phoneAccountHandle = phoneAccountHandle;
    setSummary(oldRingtoneName);
    mVoicemailRingtoneLookupRunnable =
        new Runnable() {
          @Override
          public void run() {
            SettingsUtil.getRingtoneName(
                getContext(),
                mVoicemailRingtoneLookupComplete,
                telephonyManager.getVoicemailRingtoneUri(phoneAccountHandle),
                MSG_UPDATE_VOICEMAIL_RINGTONE_SUMMARY);
          }
        };

    updateRingtoneName();
  }

  public void setVoicemailRingtoneNameChangeListener(VoicemailRingtoneNameChangeListener l) {
    mVoicemailRingtoneNameChangeListener = l;
  }

  @Override
  protected Uri onRestoreRingtone() {
    return telephonyManager.getVoicemailRingtoneUri(phoneAccountHandle);
  }

  @Override
  protected void onSaveRingtone(Uri ringtoneUri) {
    telephonyManager.setVoicemailRingtoneUri(phoneAccountHandle, ringtoneUri);
    updateRingtoneName();
  }

  private void updateRingtoneName() {
    new Thread(mVoicemailRingtoneLookupRunnable).start();
  }
}
