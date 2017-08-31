/**
 * Copyright (C) 2017 The Android Open Source Project
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.voicemail.impl.settings;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import com.android.dialer.common.Assert;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.notification.NotificationChannelManager;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.VoicemailComponent;
import com.android.voicemail.impl.OmtpVvmCarrierConfigHelper;
import com.android.voicemail.impl.R;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.sync.VvmAccountManager;

/**
 * Fragment for voicemail settings. Requires {@link VoicemailClient#PARAM_PHONE_ACCOUNT_HANDLE} set
 * in arguments.
 */
@TargetApi(VERSION_CODES.O)
public class VoicemailSettingsFragment extends PreferenceFragment
    implements Preference.OnPreferenceChangeListener, VvmAccountManager.Listener {

  private static final String TAG = "VmSettingsActivity";

  @Nullable private PhoneAccountHandle phoneAccountHandle;
  private OmtpVvmCarrierConfigHelper omtpVvmCarrierConfigHelper;

  private Preference voicemailNotificationPreference;
  private SwitchPreference voicemailVisualVoicemail;
  private SwitchPreference autoArchiveSwitchPreference;
  private Preference voicemailChangePinPreference;
  private PreferenceScreen advancedSettings;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    phoneAccountHandle =
        Assert.isNotNull(getArguments().getParcelable(VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE));

    omtpVvmCarrierConfigHelper = new OmtpVvmCarrierConfigHelper(getContext(), phoneAccountHandle);
  }

  @Override
  public void onResume() {
    super.onResume();
    Logger.get(getContext()).logImpression(DialerImpression.Type.VVM_SETTINGS_VIEWED);
    VvmAccountManager.addListener(this);
    PreferenceScreen preferenceScreen = getPreferenceScreen();
    if (preferenceScreen != null) {
      preferenceScreen.removeAll();
    }

    addPreferencesFromResource(R.xml.voicemail_settings);

    PreferenceScreen prefSet = getPreferenceScreen();

    voicemailNotificationPreference =
        findPreference(getString(R.string.voicemail_notifications_key));
    voicemailNotificationPreference.setIntent(getNotificationSettingsIntent());

    voicemailNotificationPreference.setOnPreferenceClickListener(
        new OnPreferenceClickListener() {
          @Override
          public boolean onPreferenceClick(Preference preference) {
            Logger.get(getContext())
                .logImpression(DialerImpression.Type.VVM_CHANGE_RINGTONE_CLICKED);
            // Let the preference handle the click.
            return false;
          }
        });

    voicemailVisualVoicemail =
        (SwitchPreference) findPreference(getString(R.string.voicemail_visual_voicemail_key));

    autoArchiveSwitchPreference =
        (SwitchPreference)
            findPreference(getString(R.string.voicemail_visual_voicemail_archive_key));

    if (!VoicemailComponent.get(getContext())
        .getVoicemailClient()
        .isVoicemailArchiveAvailable(getContext())) {
      getPreferenceScreen().removePreference(autoArchiveSwitchPreference);
    }

    voicemailChangePinPreference = findPreference(getString(R.string.voicemail_change_pin_key));

    if (omtpVvmCarrierConfigHelper.isValid()) {
      Assert.isNotNull(phoneAccountHandle);
      Intent changePinIntent =
          new Intent(new Intent(getContext(), VoicemailChangePinActivity.class));
      changePinIntent.putExtra(
          VoicemailChangePinActivity.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);

      voicemailChangePinPreference.setIntent(changePinIntent);
      voicemailChangePinPreference.setOnPreferenceClickListener(
          new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
              Logger.get(getContext()).logImpression(DialerImpression.Type.VVM_CHANGE_PIN_CLICKED);
              // Let the preference handle the click.
              return false;
            }
          });
      if (VoicemailChangePinActivity.isDefaultOldPinSet(getContext(), phoneAccountHandle)) {
        voicemailChangePinPreference.setTitle(R.string.voicemail_set_pin_preference_title);
      } else {
        voicemailChangePinPreference.setTitle(R.string.voicemail_change_pin_preference_title);
      }
      updateChangePin();

      voicemailVisualVoicemail.setOnPreferenceChangeListener(this);
      voicemailVisualVoicemail.setChecked(
          VisualVoicemailSettingsUtil.isEnabled(getContext(), phoneAccountHandle));

      autoArchiveSwitchPreference.setOnPreferenceChangeListener(this);
      autoArchiveSwitchPreference.setChecked(
          VisualVoicemailSettingsUtil.isArchiveEnabled(getContext(), phoneAccountHandle));
    } else {
      prefSet.removePreference(voicemailVisualVoicemail);
      prefSet.removePreference(autoArchiveSwitchPreference);
      prefSet.removePreference(voicemailChangePinPreference);
    }

    advancedSettings =
        (PreferenceScreen) findPreference(getString(R.string.voicemail_advanced_settings_key));
    Intent advancedSettingsIntent = new Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL);
    advancedSettingsIntent.putExtra(TelephonyManager.EXTRA_HIDE_PUBLIC_SETTINGS, true);
    advancedSettings.setIntent(advancedSettingsIntent);
    voicemailChangePinPreference.setOnPreferenceClickListener(
        new OnPreferenceClickListener() {
          @Override
          public boolean onPreferenceClick(Preference preference) {
            Logger.get(getContext())
                .logImpression(DialerImpression.Type.VVM_ADVANCED_SETINGS_CLICKED);
            // Let the preference handle the click.
            return false;
          }
        });
  }

  @Override
  public void onPause() {
    VvmAccountManager.removeListener(this);
    super.onPause();
  }

  /**
   * Implemented to support onPreferenceChangeListener to look for preference changes.
   *
   * @param preference is the preference to be changed
   * @param objValue should be the value of the selection, NOT its localized display value.
   */
  @Override
  public boolean onPreferenceChange(Preference preference, Object objValue) {
    VvmLog.d(TAG, "onPreferenceChange: \"" + preference + "\" changed to \"" + objValue + "\"");
    if (preference.getKey().equals(voicemailVisualVoicemail.getKey())) {
      boolean isEnabled = (boolean) objValue;
      VisualVoicemailSettingsUtil.setEnabled(getContext(), phoneAccountHandle, isEnabled);

      if (isEnabled) {
        Logger.get(getContext()).logImpression(DialerImpression.Type.VVM_USER_ENABLED_IN_SETTINGS);
      } else {
        Logger.get(getContext()).logImpression(DialerImpression.Type.VVM_USER_DISABLED_IN_SETTINGS);
      }

      updateChangePin();
    } else if (preference.getKey().equals(autoArchiveSwitchPreference.getKey())) {
      logArchiveToggle((boolean) objValue);
      VisualVoicemailSettingsUtil.setArchiveEnabled(
          getContext(), phoneAccountHandle, (boolean) objValue);
    }

    // Always let the preference setting proceed.
    return true;
  }

  private void updateChangePin() {
    if (!VisualVoicemailSettingsUtil.isEnabled(getContext(), phoneAccountHandle)) {
      voicemailChangePinPreference.setSummary(
          R.string.voicemail_change_pin_preference_summary_disable);
      voicemailChangePinPreference.setEnabled(false);
    } else if (!VvmAccountManager.isAccountActivated(getContext(), phoneAccountHandle)) {
      voicemailChangePinPreference.setSummary(
          R.string.voicemail_change_pin_preference_summary_not_activated);
      voicemailChangePinPreference.setEnabled(false);
    } else {
      voicemailChangePinPreference.setSummary(null);
      voicemailChangePinPreference.setEnabled(true);
    }
  }

  private void logArchiveToggle(boolean userTurnedOn) {
    if (userTurnedOn) {
      Logger.get(getContext())
          .logImpression(DialerImpression.Type.VVM_USER_TURNED_ARCHIVE_ON_FROM_SETTINGS);
    } else {
      Logger.get(getContext())
          .logImpression(DialerImpression.Type.VVM_USER_TURNED_ARCHIVE_OFF_FROM_SETTINGS);
    }
  }

  @Override
  public void onActivationStateChanged(PhoneAccountHandle phoneAccountHandle, boolean isActivated) {
    if (this.phoneAccountHandle.equals(phoneAccountHandle)) {
      updateChangePin();
    }
  }

  private Intent getNotificationSettingsIntent() {
    String channelId =
        NotificationChannelManager.getVoicemailChannelId(getContext(), phoneAccountHandle);
    return new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        .putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
  }
}
