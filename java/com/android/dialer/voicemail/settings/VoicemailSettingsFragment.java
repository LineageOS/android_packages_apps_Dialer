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
package com.android.dialer.voicemail.settings;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
import android.support.annotation.VisibleForTesting;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.notification.NotificationChannelManager;
import com.android.dialer.telecom.TelecomUtil;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.VoicemailClient.ActivationStateListener;
import com.android.voicemail.VoicemailComponent;
import com.google.common.base.Optional;

/**
 * Fragment for voicemail settings. Requires {@link VoicemailClient#PARAM_PHONE_ACCOUNT_HANDLE} set
 * in arguments.
 */
@TargetApi(VERSION_CODES.O)
public class VoicemailSettingsFragment extends PreferenceFragment
    implements Preference.OnPreferenceChangeListener, ActivationStateListener {

  private static final String TAG = "VmSettingsActivity";

  // Extras copied from com.android.phone.settings.VoicemailSettingsActivity,
  // it does not recognize EXTRA_PHONE_ACCOUNT_HANDLE in O.
  @VisibleForTesting
  static final String SUB_ID_EXTRA =
      "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionId";
  // Extra on intent containing the label of a subscription.
  @VisibleForTesting
  static final String SUB_LABEL_EXTRA =
      "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionLabel";

  @Nullable private PhoneAccountHandle phoneAccountHandle;

  private VoicemailClient voicemailClient;

  private Preference voicemailNotificationPreference;
  private SwitchPreference voicemailVisualVoicemail;
  private SwitchPreference autoArchiveSwitchPreference;
  private SwitchPreference donateVoicemailSwitchPreference;
  private Preference voicemailChangePinPreference;
  private PreferenceScreen advancedSettings;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    phoneAccountHandle =
        Assert.isNotNull(getArguments().getParcelable(VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE));
    voicemailClient = VoicemailComponent.get(getContext()).getVoicemailClient();
  }

  @Override
  public void onResume() {
    super.onResume();
    Logger.get(getContext()).logImpression(DialerImpression.Type.VVM_SETTINGS_VIEWED);
    voicemailClient.addActivationStateListener(this);
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

    donateVoicemailSwitchPreference =
        (SwitchPreference)
            findPreference(getString(R.string.voicemail_visual_voicemail_donation_key));

    if (!VoicemailComponent.get(getContext())
        .getVoicemailClient()
        .isVoicemailArchiveAvailable(getContext())) {
      getPreferenceScreen().removePreference(autoArchiveSwitchPreference);
    }

    if (!VoicemailComponent.get(getContext())
        .getVoicemailClient()
        .isVoicemailDonationAvailable(getContext())) {
      getPreferenceScreen().removePreference(donateVoicemailSwitchPreference);
    }

    voicemailChangePinPreference = findPreference(getString(R.string.voicemail_change_pin_key));

    if (voicemailClient.hasCarrierSupport(getContext(), phoneAccountHandle)) {
      Assert.isNotNull(phoneAccountHandle);
      Intent changePinIntent =
          new Intent(new Intent(getContext(), VoicemailChangePinActivity.class));
      changePinIntent.putExtra(VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);

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
      if (VoicemailChangePinActivity.isPinScrambled(getContext(), phoneAccountHandle)) {
        voicemailChangePinPreference.setTitle(R.string.voicemail_set_pin_preference_title);
      } else {
        voicemailChangePinPreference.setTitle(R.string.voicemail_change_pin_preference_title);
      }
      updateChangePin();

      voicemailVisualVoicemail.setOnPreferenceChangeListener(this);
      voicemailVisualVoicemail.setChecked(
          voicemailClient.isVoicemailEnabled(getContext(), phoneAccountHandle));

      autoArchiveSwitchPreference.setOnPreferenceChangeListener(this);
      autoArchiveSwitchPreference.setChecked(
          voicemailClient.isVoicemailArchiveEnabled(getContext(), phoneAccountHandle));

      donateVoicemailSwitchPreference.setOnPreferenceChangeListener(this);
      donateVoicemailSwitchPreference.setChecked(
          voicemailClient.isVoicemailDonationEnabled(getContext(), phoneAccountHandle));
      updateDonateVoicemail();
    } else {
      prefSet.removePreference(voicemailVisualVoicemail);
      prefSet.removePreference(autoArchiveSwitchPreference);
      prefSet.removePreference(donateVoicemailSwitchPreference);
      prefSet.removePreference(voicemailChangePinPreference);
    }

    advancedSettings =
        (PreferenceScreen) findPreference(getString(R.string.voicemail_advanced_settings_key));
    Intent advancedSettingsIntent = new Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL);
    advancedSettingsIntent.putExtra(TelephonyManager.EXTRA_HIDE_PUBLIC_SETTINGS, true);
    advancedSettingsIntent.putExtra(
        TelephonyManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);

    // (a bug): EXTRA_PHONE_ACCOUNT_HANDLE not implemented in telephony in O.
    Optional<SubscriptionInfo> subscriptionInfo =
        TelecomUtil.getSubscriptionInfo(getContext(), phoneAccountHandle);
    if (subscriptionInfo.isPresent()) {
      advancedSettingsIntent.putExtra(SUB_ID_EXTRA, subscriptionInfo.get().getSubscriptionId());
      PhoneAccount phoneAccount =
          getContext().getSystemService(TelecomManager.class).getPhoneAccount(phoneAccountHandle);
      if (phoneAccount != null) {
        advancedSettingsIntent.putExtra(SUB_LABEL_EXTRA, phoneAccount.getLabel());
      }
    }

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
    voicemailClient.removeActivationStateListener(this);
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
    LogUtil.d(TAG, "onPreferenceChange: \"" + preference + "\" changed to \"" + objValue + "\"");
    if (preference.getKey().equals(voicemailVisualVoicemail.getKey())) {
      boolean isEnabled = (boolean) objValue;
      if (!isEnabled) {
        showDisableConfirmationDialog();
        // Don't let the preference setting proceed.
        return false;
      } else {
        updateVoicemailEnabled(true);
      }
    } else if (preference.getKey().equals(autoArchiveSwitchPreference.getKey())) {
      logArchiveToggle((boolean) objValue);
      voicemailClient.setVoicemailArchiveEnabled(
          getContext(), phoneAccountHandle, (boolean) objValue);
    } else if (preference.getKey().equals(donateVoicemailSwitchPreference.getKey())) {
      logArchiveToggle((boolean) objValue);
      voicemailClient.setVoicemailDonationEnabled(
          getContext(), phoneAccountHandle, (boolean) objValue);
    }

    // Let the preference setting proceed.
    return true;
  }

  private void updateVoicemailEnabled(boolean isEnabled) {
    voicemailClient.setVoicemailEnabled(getContext(), phoneAccountHandle, isEnabled);
    voicemailVisualVoicemail.setChecked(isEnabled);

    if (isEnabled) {
      Logger.get(getContext()).logImpression(DialerImpression.Type.VVM_USER_ENABLED_IN_SETTINGS);
    } else {
      Logger.get(getContext()).logImpression(DialerImpression.Type.VVM_USER_DISABLED_IN_SETTINGS);
    }

    updateChangePin();
    updateDonateVoicemail();
  }

  private void updateChangePin() {
    if (!voicemailClient.isVoicemailEnabled(getContext(), phoneAccountHandle)) {
      voicemailChangePinPreference.setSummary(
          R.string.voicemail_change_pin_preference_summary_disable);
      voicemailChangePinPreference.setEnabled(false);
    } else if (!voicemailClient.isActivated(getContext(), phoneAccountHandle)) {
      voicemailChangePinPreference.setSummary(
          R.string.voicemail_change_pin_preference_summary_not_activated);
      voicemailChangePinPreference.setEnabled(false);
    } else {
      voicemailChangePinPreference.setSummary(null);
      voicemailChangePinPreference.setEnabled(true);
    }
  }

  private void updateDonateVoicemail() {
    if (!voicemailClient.isVoicemailEnabled(getContext(), phoneAccountHandle)) {
      donateVoicemailSwitchPreference.setSummary(
          R.string.voicemail_donate_preference_summary_disable);
      donateVoicemailSwitchPreference.setEnabled(false);
    } else if (!voicemailClient.isActivated(getContext(), phoneAccountHandle)) {
      donateVoicemailSwitchPreference.setSummary(
          R.string.voicemail_donate_preference_summary_not_activated);
      donateVoicemailSwitchPreference.setEnabled(false);
    } else {
      donateVoicemailSwitchPreference.setSummary(R.string.voicemail_donate_preference_summary_info);
      donateVoicemailSwitchPreference.setEnabled(true);
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
      updateDonateVoicemail();
    }
  }

  private Intent getNotificationSettingsIntent() {
    String channelId =
        NotificationChannelManager.getVoicemailChannelId(getContext(), phoneAccountHandle);
    return new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        .putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
  }

  private void showDisableConfirmationDialog() {
    LogUtil.i(TAG, "showDisableConfirmationDialog");
    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
    builder.setTitle(R.string.confirm_disable_voicemail_dialog_title);
    builder.setMessage(R.string.confirm_disable_voicemail_dialog_message);
    builder.setPositiveButton(
        R.string.confirm_disable_voicemail_accept_dialog_label,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            LogUtil.i(TAG, "showDisableConfirmationDialog, confirmed");
            updateVoicemailEnabled(false);
            dialog.dismiss();
          }
        });

    builder.setNegativeButton(
        android.R.string.cancel,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            LogUtil.i(TAG, "showDisableConfirmationDialog, cancelled");
            dialog.dismiss();
          }
        });

    builder.setCancelable(true);
    builder.show();
  }
}
