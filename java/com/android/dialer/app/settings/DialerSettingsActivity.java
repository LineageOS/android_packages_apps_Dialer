/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * limitations under the License.
 */
package com.android.dialer.app.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.view.MenuItem;
import android.widget.Toast;
import com.android.dialer.about.AboutPhoneFragment;
import com.android.dialer.app.R;
import com.android.dialer.assisteddialing.ConcreteCreator;
import com.android.dialer.blocking.FilteredNumberCompat;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.dialer.lookup.LookupSettingsFragment;
import com.android.dialer.proguard.UsedByReflection;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.voicemail.settings.VoicemailSettingsFragment;
import com.android.voicemail.VoicemailClient;
import java.util.List;

/** Activity for dialer settings. */
@SuppressWarnings("FragmentInjection") // Activity not exported
@UsedByReflection(value = "AndroidManifest-app.xml")
public class DialerSettingsActivity extends AppCompatPreferenceActivity {

  protected SharedPreferences preferences;
  private boolean migrationStatusOnBuildHeaders;
  private List<Header> headers;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    LogUtil.enterBlock("DialerSettingsActivity.onCreate");
    super.onCreate(savedInstanceState);
    preferences = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());

    Intent intent = getIntent();
    Uri data = intent.getData();
    if (data != null) {
      String headerToOpen = data.getSchemeSpecificPart();
      if (headerToOpen != null && headers != null) {
        for (Header header : headers) {
          if (headerToOpen.equals(header.fragment)) {
            LogUtil.i("DialerSettingsActivity.onCreate", "switching to header: " + headerToOpen);
            switchToHeader(header);
            break;
          }
        }
      }
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    /*
     * The blockedCallsHeader need to be recreated if the migration status changed because
     * the intent needs to be updated.
     */
    if (migrationStatusOnBuildHeaders != FilteredNumberCompat.hasMigratedToNewBlocking(this)) {
      invalidateHeaders();
    }
  }

  @Override
  public void onBuildHeaders(List<Header> target) {
    // Keep a reference to the list of headers (since PreferenceActivity.getHeaders() is @Hide)
    headers = target;

    if (showDisplayOptions()) {
      Header displayOptionsHeader = new Header();
      displayOptionsHeader.titleRes = R.string.display_options_title;
      displayOptionsHeader.fragment = DisplayOptionsSettingsFragment.class.getName();
      target.add(displayOptionsHeader);
    }

    Header soundSettingsHeader = new Header();
    soundSettingsHeader.titleRes = R.string.sounds_and_vibration_title;
    soundSettingsHeader.fragment = SoundSettingsFragment.class.getName();
    soundSettingsHeader.id = R.id.settings_header_sounds_and_vibration;
    target.add(soundSettingsHeader);

    Header quickResponseSettingsHeader = new Header();
    Intent quickResponseSettingsIntent =
        new Intent(TelecomManager.ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS);
    quickResponseSettingsHeader.titleRes = R.string.respond_via_sms_setting_title;
    quickResponseSettingsHeader.intent = quickResponseSettingsIntent;
    target.add(quickResponseSettingsHeader);

    final Header lookupSettingsHeader = new Header();
    lookupSettingsHeader.titleRes = R.string.lookup_settings_label;
    lookupSettingsHeader.fragment = LookupSettingsFragment.class.getName();
    target.add(lookupSettingsHeader);

    TelephonyManager telephonyManager =
        (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

    // "Call Settings" (full settings) is shown if the current user is primary user and there
    // is only one SIM. Otherwise, "Calling accounts" is shown.
    boolean isPrimaryUser = isPrimaryUser();
    if (isPrimaryUser && TelephonyManagerCompat.getPhoneCount(telephonyManager) <= 1) {
      Header callSettingsHeader = new Header();
      Intent callSettingsIntent = new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS);
      callSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

      callSettingsHeader.titleRes = R.string.call_settings_label;
      callSettingsHeader.intent = callSettingsIntent;
      target.add(callSettingsHeader);
    } else {
      Header phoneAccountSettingsHeader = new Header();
      Intent phoneAccountSettingsIntent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
      phoneAccountSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

      phoneAccountSettingsHeader.titleRes = R.string.phone_account_settings_label;
      phoneAccountSettingsHeader.intent = phoneAccountSettingsIntent;
      target.add(phoneAccountSettingsHeader);
    }
    if (FilteredNumberCompat.canCurrentUserOpenBlockSettings(this)) {
      Header blockedCallsHeader = new Header();
      blockedCallsHeader.titleRes = R.string.manage_blocked_numbers_label;
      blockedCallsHeader.intent = FilteredNumberCompat.createManageBlockedNumbersIntent(this);
      target.add(blockedCallsHeader);
      migrationStatusOnBuildHeaders = FilteredNumberCompat.hasMigratedToNewBlocking(this);
    }

    addVoicemailSettings(target, isPrimaryUser);

    if (isPrimaryUser
        && (TelephonyManagerCompat.isTtyModeSupported(telephonyManager)
            || TelephonyManagerCompat.isHearingAidCompatibilitySupported(telephonyManager))) {
      Header accessibilitySettingsHeader = new Header();
      Intent accessibilitySettingsIntent =
          new Intent(TelecomManager.ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS);
      accessibilitySettingsHeader.titleRes = R.string.accessibility_settings_title;
      accessibilitySettingsHeader.intent = accessibilitySettingsIntent;
      target.add(accessibilitySettingsHeader);
    }

    boolean isAssistedDialingEnabled =
        ConcreteCreator.isAssistedDialingEnabled(
            ConfigProviderComponent.get(getApplicationContext()).getConfigProvider());
    LogUtil.i(
        "DialerSettingsActivity.onBuildHeaders",
        "showing assisted dialing header: " + isAssistedDialingEnabled);
    if (isAssistedDialingEnabled) {

      Header assistedDialingSettingsHeader = new Header();
      assistedDialingSettingsHeader.titleRes =
          com.android.dialer.assisteddialing.ui.R.string.assisted_dialing_setting_title;
      assistedDialingSettingsHeader.intent =
          new Intent("com.android.dialer.app.settings.SHOW_ASSISTED_DIALING_SETTINGS");
      target.add(assistedDialingSettingsHeader);
    }

    if (showAbout()) {
      Header aboutPhoneHeader = new Header();
      aboutPhoneHeader.titleRes = R.string.about_phone_label;
      aboutPhoneHeader.fragment = AboutPhoneFragment.class.getName();
      target.add(aboutPhoneHeader);
    }
  }

  private void addVoicemailSettings(List<Header> target, boolean isPrimaryUser) {
    if (!isPrimaryUser) {
      LogUtil.i("DialerSettingsActivity.addVoicemailSettings", "user not primary user");
      return;
    }
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      LogUtil.i(
          "DialerSettingsActivity.addVoicemailSettings",
          "Dialer voicemail settings not supported by system");
      return;
    }

    if (!PermissionsUtil.hasReadPhoneStatePermissions(this)) {
      LogUtil.i("DialerSettingsActivity.addVoicemailSettings", "Missing READ_PHONE_STATE");
      return;
    }

    LogUtil.i("DialerSettingsActivity.addVoicemailSettings", "adding voicemail settings");
    Header voicemailSettings = new Header();
    voicemailSettings.titleRes = R.string.voicemail_settings_label;
    PhoneAccountHandle soleAccount = getSoleSimAccount();
    if (soleAccount == null) {
      LogUtil.i(
          "DialerSettingsActivity.addVoicemailSettings", "showing multi-SIM voicemail settings");
      voicemailSettings.fragment = PhoneAccountSelectionFragment.class.getName();
      Bundle bundle = new Bundle();
      bundle.putString(
          PhoneAccountSelectionFragment.PARAM_TARGET_FRAGMENT,
          VoicemailSettingsFragment.class.getName());
      bundle.putString(
          PhoneAccountSelectionFragment.PARAM_PHONE_ACCOUNT_HANDLE_KEY,
          VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE);
      bundle.putBundle(PhoneAccountSelectionFragment.PARAM_ARGUMENTS, new Bundle());
      bundle.putInt(
          PhoneAccountSelectionFragment.PARAM_TARGET_TITLE_RES, R.string.voicemail_settings_label);
      voicemailSettings.fragmentArguments = bundle;
      target.add(voicemailSettings);
    } else {
      LogUtil.i(
          "DialerSettingsActivity.addVoicemailSettings", "showing single-SIM voicemail settings");
      voicemailSettings.fragment = VoicemailSettingsFragment.class.getName();
      Bundle bundle = new Bundle();
      bundle.putParcelable(VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE, soleAccount);
      voicemailSettings.fragmentArguments = bundle;
      target.add(voicemailSettings);
    }
  }

  /**
   * @return the only SIM phone account, or {@code null} if there are none or more than one. Note:
   *     having a empty SIM slot still count as a PhoneAccountHandle that is "invalid", and
   *     voicemail settings should still be available for it.
   */
  @Nullable
  private PhoneAccountHandle getSoleSimAccount() {
    TelecomManager telecomManager = getSystemService(TelecomManager.class);
    PhoneAccountHandle result = null;
    for (PhoneAccountHandle phoneAccountHandle : telecomManager.getCallCapablePhoneAccounts()) {
      PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
      if (phoneAccount == null) {
        continue;
      }
      if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
        LogUtil.i(
            "DialerSettingsActivity.getSoleSimAccount", phoneAccountHandle + " is a SIM account");
        if (result != null) {
          return null;
        }
        result = phoneAccountHandle;
      }
    }
    return result;
  }

  /** Whether "about" should be shown in settings. Override to hide about. */
  public boolean showAbout() {
    return true;
  }

  /**
   * Returns {@code true} or {@code false} based on whether the display options setting should be
   * shown. For languages such as Chinese, Japanese, or Korean, display options aren't useful since
   * contacts are sorted and displayed family name first by default.
   *
   * @return {@code true} if the display options should be shown, {@code false} otherwise.
   */
  private boolean showDisplayOptions() {
    return getResources().getBoolean(R.bool.config_display_order_user_changeable)
        && getResources().getBoolean(R.bool.config_sort_order_user_changeable);
  }

  @Override
  public void onHeaderClick(Header header, int position) {
    if (header.id == R.id.settings_header_sounds_and_vibration) {
      // If we don't have the permission to write to system settings, go to system sound
      // settings instead. Otherwise, perform the super implementation (which launches our
      // own preference fragment.
      if (!Settings.System.canWrite(this)) {
        Toast.makeText(
                this,
                getResources().getString(R.string.toast_cannot_write_system_settings),
                Toast.LENGTH_SHORT)
            .show();
        startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
        return;
      }
    }
    super.onHeaderClick(header, position);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return false;
  }

  @Override
  public void onBackPressed() {
    if (!isSafeToCommitTransactions()) {
      return;
    }
    super.onBackPressed();
  }

  @Override
  protected boolean isValidFragment(String fragmentName) {
    return true;
  }

  /** @return Whether the current user is the primary user. */
  private boolean isPrimaryUser() {
    return getSystemService(UserManager.class).isSystemUser();
  }
}
