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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceClickListener;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.dialer.R;
import com.android.dialer.app.settings.DialerPreferenceFragment;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.notification.NotificationChannelManager;
import com.android.dialer.telecom.TelecomUtil;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.VoicemailClient.ActivationStateListener;
import com.android.voicemail.VoicemailComponent;
import com.google.common.base.Optional;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Fragment for voicemail settings. Requires {@link VoicemailClient#PARAM_PHONE_ACCOUNT_HANDLE} set
 * in arguments.
 */
public class VoicemailSettingsFragment extends DialerPreferenceFragment
    implements Preference.OnPreferenceChangeListener, ActivationStateListener {

  // Extras copied from com.android.phone.settings.VoicemailSettingsActivity,
  // it does not recognize EXTRA_PHONE_ACCOUNT_HANDLE in O.
  private static final String SUB_ID_EXTRA =
      "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionId";
  // Extra on intent containing the label of a subscription.
  private static final String SUB_LABEL_EXTRA =
      "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionLabel";

  private static final String TAG = "VmSettingsActivity";
  @Nullable
  private PhoneAccountHandle phoneAccountHandle;
  private VoicemailClient voicemailClient;
  // Settings that are independent of the carrier configurations
  private Preference voicemailNotificationPreference;
  private PreferenceScreen advancedSettingsPreference;
  // Settings that are supported by dialer only if the carrier configurations are valid.
  private SwitchPreferenceCompat visualVoicemailPreference;
  private SwitchPreferenceCompat voicemailAutoArchivePreference;
  private Preference voicemailChangePinPreference;

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
    voicemailClient.addActivationStateListener(this);
    PreferenceScreen preferenceScreen = getPreferenceScreen();
    if (preferenceScreen != null) {
      preferenceScreen.removeAll();
    }

    addPreferencesFromResource(R.xml.voicemail_settings);

    initializeXmlPreferences();

    setupVisualVoicemailPreferences();

    setupNotificationsPreference();
    setupAdvancedSettingsPreference();
  }

  private void setupVisualVoicemailPreferences() {
    if (!voicemailClient.hasCarrierSupport(getContext(), phoneAccountHandle)) {
      removeAllVisualVoicemailPreferences();
      return;
    }

    setupVisualVoicemailPreference();

    setupVisualVoicemailFeaturePreferences();

    setupVoicemailChangePinPreference();
  }

  private void setupVisualVoicemailFeaturePreferences() {
    if (!voicemailClient.isVoicemailEnabled(getContext(), phoneAccountHandle)
        || !voicemailClient.isActivated(getContext(), phoneAccountHandle)) {
      getPreferenceScreen().removePreference(voicemailAutoArchivePreference);
      return;
    }
    setupAutoArchivePreference();
  }

  private void setupAutoArchivePreference() {
    if (!VoicemailComponent.get(getContext())
        .getVoicemailClient()
        .isVoicemailArchiveAvailable(getContext())) {
      getPreferenceScreen().removePreference(voicemailAutoArchivePreference);
    } else {
      voicemailAutoArchivePreference.setOnPreferenceChangeListener(this);
      voicemailAutoArchivePreference.setChecked(
          voicemailClient.isVoicemailArchiveEnabled(getContext(), phoneAccountHandle));
    }
  }

  private void setupVisualVoicemailPreference() {
    visualVoicemailPreference.setOnPreferenceChangeListener(this);
    visualVoicemailPreference.setChecked(
        voicemailClient.isVoicemailEnabled(getContext(), phoneAccountHandle));
    updateVoicemailSummaryMessage();
  }

  /** The preferences that are present in the voicemail_settings.xml file are initialized here. */
  private void initializeXmlPreferences() {
    voicemailNotificationPreference =
        findPreference(getString(R.string.voicemail_notifications_key));
    voicemailNotificationPreference.setOrder(VMSettingOrdering.NOTIFICATIONS);

    advancedSettingsPreference =
        (PreferenceScreen) findPreference(getString(R.string.voicemail_advanced_settings_key));
    advancedSettingsPreference.setOrder(VMSettingOrdering.ADVANCED_SETTING);

    visualVoicemailPreference =
        (SwitchPreferenceCompat) findPreference(getString(R.string.voicemail_visual_voicemail_key));
    visualVoicemailPreference.setOrder(VMSettingOrdering.VISUAL_VOICEMAIL);

    voicemailAutoArchivePreference =
        (SwitchPreferenceCompat)
            findPreference(getString(R.string.voicemail_visual_voicemail_archive_key));
    voicemailAutoArchivePreference.setOrder(VMSettingOrdering.VOICEMAIL_AUTO_ARCHIVE);

    voicemailChangePinPreference = findPreference(getString(R.string.voicemail_change_pin_key));
    voicemailChangePinPreference.setOrder(VMSettingOrdering.VOICEMAIL_CHANGE_PIN);
  }

  /** Removes vvm settings since the carrier setup is not supported by Dialer */
  private void removeAllVisualVoicemailPreferences() {
    PreferenceScreen prefSet = getPreferenceScreen();
    prefSet.removePreference(visualVoicemailPreference);
    prefSet.removePreference(voicemailAutoArchivePreference);
    prefSet.removePreference(voicemailChangePinPreference);
  }

  private void setupVoicemailChangePinPreference() {
    Intent changePinIntent = new Intent(new Intent(getContext(), VoicemailChangePinActivity.class));
    changePinIntent.putExtra(VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);

    voicemailChangePinPreference.setIntent(changePinIntent);
    if (VoicemailChangePinActivity.isPinScrambled(getContext(), phoneAccountHandle)) {
      voicemailChangePinPreference.setTitle(R.string.voicemail_set_pin_preference_title);
    } else {
      voicemailChangePinPreference.setTitle(R.string.voicemail_change_pin_preference_title);
    }
    updateChangePinPreference();
  }

  private void setupNotificationsPreference() {
    voicemailNotificationPreference.setIntent(getNotificationSettingsIntent());
  }

  private void setupAdvancedSettingsPreference() {
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

    advancedSettingsPreference.setIntent(advancedSettingsIntent);
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
    if (preference.getKey().equals(visualVoicemailPreference.getKey())) {
      boolean isEnabled = (boolean) objValue;
      if (!isEnabled) {
        showDisableConfirmationDialog();
        // Don't let the preference setting proceed.
        return false;
      } else {
        updateVoicemailEnabled(true);
      }
    } else if (preference.getKey().equals(voicemailAutoArchivePreference.getKey())) {
      voicemailClient.setVoicemailArchiveEnabled(
          getContext(), phoneAccountHandle, (boolean) objValue);
    }

    // Let the preference setting proceed.
    return true;
  }

  private void updateVoicemailEnabled(boolean isEnabled) {
    voicemailClient.setVoicemailEnabled(getContext(), phoneAccountHandle, isEnabled);
    visualVoicemailPreference.setChecked(isEnabled);

    updateVoicemailSummaryMessage();
    updateChangePinPreference();
  }

  private void updateChangePinPreference() {
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

  @Override
  public void onActivationStateChanged(PhoneAccountHandle phoneAccountHandle, boolean isActivated) {
    if (this.phoneAccountHandle.equals(phoneAccountHandle)) {
      updateVoicemailSummaryMessage();
      updateChangePinPreference();
    }
  }

  /**
   * Shows the activating message while visual voicemail is being activated. This is useful, since
   * some toggles do not show up, until voicemail is activated e.g transcription and rating.
   */
  private void updateVoicemailSummaryMessage() {
    if (voicemailClient.isVoicemailEnabled(getContext(), phoneAccountHandle)
        && !voicemailClient.isActivated(getContext(), phoneAccountHandle)) {
      visualVoicemailPreference.setSummary(R.string.voicemail_activating_summary_info);
    } else {
      visualVoicemailPreference.setSummary("");
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

  /** The ordering in which to show the voicemail settings */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    VMSettingOrdering.NOTIFICATIONS,
    VMSettingOrdering.VISUAL_VOICEMAIL,
    VMSettingOrdering.VOICEMAIL_TRANSCRIPTION,
    VMSettingOrdering.VOICEMAIL_TRANSCRIPTION_DONATION,
    VMSettingOrdering.VOICEMAIL_CHANGE_PIN,
    VMSettingOrdering.VOICEMAIL_AUTO_ARCHIVE,
    VMSettingOrdering.ADVANCED_SETTING
  })
  private @interface VMSettingOrdering {
    int NOTIFICATIONS = 1;
    int VISUAL_VOICEMAIL = 2;
    int VOICEMAIL_TRANSCRIPTION = 3;
    int VOICEMAIL_TRANSCRIPTION_DONATION = 4;
    int VOICEMAIL_CHANGE_PIN = 5;
    int VOICEMAIL_AUTO_ARCHIVE = 6;
    int ADVANCED_SETTING = 7;
  }
}
