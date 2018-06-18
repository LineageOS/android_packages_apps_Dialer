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
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.preference.SwitchPreferenceWithClickableSummary;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.notification.NotificationChannelManager;
import com.android.dialer.spannable.ContentWithLearnMoreSpanner;
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
@TargetApi(VERSION_CODES.O)
public class VoicemailSettingsFragment extends PreferenceFragment
    implements Preference.OnPreferenceChangeListener, ActivationStateListener {

  // Extras copied from com.android.phone.settings.VoicemailSettingsActivity,
  // it does not recognize EXTRA_PHONE_ACCOUNT_HANDLE in O.
  @VisibleForTesting
  static final String SUB_ID_EXTRA =
      "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionId";
  // Extra on intent containing the label of a subscription.
  @VisibleForTesting
  static final String SUB_LABEL_EXTRA =
      "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionLabel";

  private static final String TAG = "VmSettingsActivity";
  @Nullable private PhoneAccountHandle phoneAccountHandle;
  private VoicemailClient voicemailClient;
  // Settings that are independent of the carrier configurations
  private Preference voicemailNotificationPreference;
  private Preference changeGreetingPreference;
  private PreferenceScreen advancedSettingsPreference;
  // Settings that are supported by dialer only if the carrier configurations are valid.
  private SwitchPreference visualVoicemailPreference;
  private SwitchPreference voicemailAutoArchivePreference;
  private SwitchPreferenceWithClickableSummary transcribeVoicemailPreference;
  // Voicemail transcription analysis toggle
  private SwitchPreferenceWithClickableSummary donateTranscribedVoicemailPreference;
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
    Logger.get(getContext()).logImpression(DialerImpression.Type.VVM_SETTINGS_VIEWED);
    voicemailClient.addActivationStateListener(this);
    PreferenceScreen preferenceScreen = getPreferenceScreen();
    if (preferenceScreen != null) {
      preferenceScreen.removeAll();
    }

    addPreferencesFromResource(R.xml.voicemail_settings);

    initializeXmlPreferences();

    setupVisualVoicemailPreferences();

    setupNotificationsPreference();
    setupChangeGreetingPreference();
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
      removeAllTranscriptionPreferences();
      getPreferenceScreen().removePreference(voicemailAutoArchivePreference);
      return;
    }
    setupAutoArchivePreference();
    updateTranscriptionPreferences();
  }

  private void updateTranscriptionPreferences() {
    if (!VoicemailComponent.get(getContext())
        .getVoicemailClient()
        .isVoicemailTranscriptionAvailable(getContext(), phoneAccountHandle)) {
      removeAllTranscriptionPreferences();
      return;
    } else {
      showTranscriptionEnabledPreference();
      updateTranscriptionDonationPreference();
    }
  }

  private void showTranscriptionEnabledPreference() {
    transcribeVoicemailPreference.setOnPreferenceChangeListener(this);
    transcribeVoicemailPreference.setChecked(
        voicemailClient.isVoicemailTranscriptionEnabled(getContext(), phoneAccountHandle));
    transcribeVoicemailPreference.setSummary(getVoicemailTranscriptionInformationalText());
    transcribeVoicemailPreference.setEnabled(true);
    getPreferenceScreen().addPreference(transcribeVoicemailPreference);
  }

  /**
   * Builds a spannable string containing the voicemail transcription informational text containing
   * the appropriate "Learn More" urls.
   *
   * @return The voicemail transcription information text.
   */
  private CharSequence getVoicemailTranscriptionInformationalText() {
    return new ContentWithLearnMoreSpanner(getContext())
        .create(
            getContext().getString(R.string.voicemail_transcription_preference_summary_info),
            getContext().getString(R.string.transcription_learn_more_url));
  }

  private void updateTranscriptionDonationPreference() {
    if (!VoicemailComponent.get(getContext())
        .getVoicemailClient()
        .isVoicemailDonationAvailable(getContext(), phoneAccountHandle)) {
      getPreferenceScreen().removePreference(donateTranscribedVoicemailPreference);
    } else {
      showTranscriptionDonationEnabledPreferences();
    }
  }

  private void showTranscriptionDonationEnabledPreferences() {
    donateTranscribedVoicemailPreference.setEnabled(true);
    donateTranscribedVoicemailPreference.setChecked(
        voicemailClient.isVoicemailDonationEnabled(getContext(), phoneAccountHandle));
    donateTranscribedVoicemailPreference.setOnPreferenceChangeListener(this);
    donateTranscribedVoicemailPreference.setSummary(
        getVoicemailTranscriptionDonationInformationalText());
    getPreferenceScreen().addPreference(donateTranscribedVoicemailPreference);
  }

  /**
   * Builds a spannable string containing the voicemail donation informational text containing the
   * appropriate "Learn More" urls.
   *
   * @return The voicemail donation information text.
   */
  private CharSequence getVoicemailTranscriptionDonationInformationalText() {
    return new ContentWithLearnMoreSpanner(getContext())
        .create(
            getContext().getString(R.string.voicemail_donate_preference_summary_info),
            getContext().getString(R.string.donation_learn_more_url));
  }

  private void removeAllTranscriptionPreferences() {
    getPreferenceScreen().removePreference(transcribeVoicemailPreference);
    getPreferenceScreen().removePreference(donateTranscribedVoicemailPreference);
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

    changeGreetingPreference = findPreference(getString(R.string.voicemail_change_greeting_key));
    changeGreetingPreference.setOrder(VMSettingOrdering.CHANGE_GREETING);

    advancedSettingsPreference =
        (PreferenceScreen) findPreference(getString(R.string.voicemail_advanced_settings_key));
    advancedSettingsPreference.setOrder(VMSettingOrdering.ADVANCED_SETTING);

    visualVoicemailPreference =
        (SwitchPreference) findPreference(getString(R.string.voicemail_visual_voicemail_key));
    visualVoicemailPreference.setOrder(VMSettingOrdering.VISUAL_VOICEMAIL);

    voicemailAutoArchivePreference =
        (SwitchPreference)
            findPreference(getString(R.string.voicemail_visual_voicemail_archive_key));
    voicemailAutoArchivePreference.setOrder(VMSettingOrdering.VOICEMAIL_AUTO_ARCHIVE);

    transcribeVoicemailPreference =
        (SwitchPreferenceWithClickableSummary)
            findPreference(getString(R.string.voicemail_visual_voicemail_transcription_key));
    transcribeVoicemailPreference.setOrder(VMSettingOrdering.VOICEMAIL_TRANSCRIPTION);

    donateTranscribedVoicemailPreference =
        (SwitchPreferenceWithClickableSummary)
            findPreference(getString(R.string.voicemail_visual_voicemail_donation_key));
    donateTranscribedVoicemailPreference.setOrder(
        VMSettingOrdering.VOICEMAIL_TRANSCRIPTION_DONATION);

    voicemailChangePinPreference = findPreference(getString(R.string.voicemail_change_pin_key));
    voicemailChangePinPreference.setOrder(VMSettingOrdering.VOICEMAIL_CHANGE_PIN);
  }

  /** Removes vvm settings since the carrier setup is not supported by Dialer */
  private void removeAllVisualVoicemailPreferences() {
    PreferenceScreen prefSet = getPreferenceScreen();
    prefSet.removePreference(visualVoicemailPreference);
    prefSet.removePreference(voicemailAutoArchivePreference);
    prefSet.removePreference(transcribeVoicemailPreference);
    prefSet.removePreference(donateTranscribedVoicemailPreference);
    prefSet.removePreference(voicemailChangePinPreference);
  }

  private void setupVoicemailChangePinPreference() {
    Intent changePinIntent = new Intent(new Intent(getContext(), VoicemailChangePinActivity.class));
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
    updateChangePinPreference();
  }

  private void setupNotificationsPreference() {
    voicemailNotificationPreference.setIntent(getNotificationSettingsIntent());

    voicemailNotificationPreference.setOnPreferenceClickListener(
        new OnPreferenceClickListener() {
          @Override
          public boolean onPreferenceClick(Preference preference) {
            Logger.get(getContext())
                .logImpression(DialerImpression.Type.VVM_NOTIFICATIONS_SETTING_CLICKED);
            // Let the preference handle the click.
            return false;
          }
        });
  }

  private void setupChangeGreetingPreference() {
    if (!ConfigProviderComponent.get(getContext())
        .getConfigProvider()
        .getBoolean("voicemail_change_greeting_enabled", false)) {
      getPreferenceScreen().removePreference(changeGreetingPreference);
      return;
    }

    Intent changeGreetingIntent = new Intent(getContext(), CurrentVoicemailGreetingActivity.class);
    changeGreetingPreference.setIntent(changeGreetingIntent);
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

    advancedSettingsPreference.setOnPreferenceClickListener(
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
      logArchiveToggle((boolean) objValue);
      voicemailClient.setVoicemailArchiveEnabled(
          getContext(), phoneAccountHandle, (boolean) objValue);
    } else if (preference.getKey().equals(transcribeVoicemailPreference.getKey())) {
      logTranscribeToggle((boolean) objValue);
      voicemailClient.setVoicemailTranscriptionEnabled(
          getContext(), phoneAccountHandle, (boolean) objValue);
      updateTranscriptionDonationPreference();
    } else if (preference.getKey().equals(donateTranscribedVoicemailPreference.getKey())) {
      logDonationToggle((boolean) objValue);
      voicemailClient.setVoicemailDonationEnabled(
          getContext(), phoneAccountHandle, (boolean) objValue);
    }

    // Let the preference setting proceed.
    return true;
  }

  private void updateVoicemailEnabled(boolean isEnabled) {
    voicemailClient.setVoicemailEnabled(getContext(), phoneAccountHandle, isEnabled);
    visualVoicemailPreference.setChecked(isEnabled);

    if (isEnabled) {
      Logger.get(getContext()).logImpression(DialerImpression.Type.VVM_USER_ENABLED_IN_SETTINGS);
    } else {
      Logger.get(getContext()).logImpression(DialerImpression.Type.VVM_USER_DISABLED_IN_SETTINGS);
    }
    updateVoicemailSummaryMessage();
    updateTranscriptionPreferences();
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

  private void logArchiveToggle(boolean userTurnedOn) {
    if (userTurnedOn) {
      Logger.get(getContext())
          .logImpression(DialerImpression.Type.VVM_USER_TURNED_ARCHIVE_ON_FROM_SETTINGS);
    } else {
      Logger.get(getContext())
          .logImpression(DialerImpression.Type.VVM_USER_TURNED_ARCHIVE_OFF_FROM_SETTINGS);
    }
  }

  private void logTranscribeToggle(boolean userTurnedOn) {
    if (userTurnedOn) {
      Logger.get(getContext())
          .logImpression(DialerImpression.Type.VVM_USER_TURNED_TRANSCRIBE_ON_FROM_SETTINGS);
    } else {
      Logger.get(getContext())
          .logImpression(DialerImpression.Type.VVM_USER_TURNED_TRANSCRIBE_OFF_FROM_SETTINGS);
    }
  }

  private void logDonationToggle(boolean userTurnedOn) {
    if (userTurnedOn) {
      Logger.get(getContext())
          .logImpression(DialerImpression.Type.VVM_USER_TURNED_TRANSCRIBE_ON_FROM_SETTINGS);
    } else {
      Logger.get(getContext())
          .logImpression(DialerImpression.Type.VVM_USER_TURNED_TRANSCRIBE_OFF_FROM_SETTINGS);
    }
  }

  @Override
  public void onActivationStateChanged(PhoneAccountHandle phoneAccountHandle, boolean isActivated) {
    if (this.phoneAccountHandle.equals(phoneAccountHandle)) {
      updateVoicemailSummaryMessage();
      updateTranscriptionPreferences();
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
    VMSettingOrdering.ADVANCED_SETTING,
    VMSettingOrdering.CHANGE_GREETING
  })
  private @interface VMSettingOrdering {
    int NOTIFICATIONS = 1;
    int VISUAL_VOICEMAIL = 2;
    int VOICEMAIL_TRANSCRIPTION = 3;
    int VOICEMAIL_TRANSCRIPTION_DONATION = 4;
    int VOICEMAIL_CHANGE_PIN = 5;
    int VOICEMAIL_AUTO_ARCHIVE = 6;
    int ADVANCED_SETTING = 7;
    int CHANGE_GREETING = 8;
  }
}
