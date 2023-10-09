/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * limitations under the License.
 */
package com.android.dialer.app.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.BlockedNumberContract;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.android.dialer.R;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.lookup.LookupSettingsFragment;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.voicemail.settings.VoicemailSettingsFragment;
import com.android.voicemail.VoicemailClient;

/** Activity for dialer settings. */
public class DialerSettingsActivity extends AppCompatActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

  protected SharedPreferences preferences;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    LogUtil.enterBlock("DialerSettingsActivity.onCreate");
    super.onCreate(savedInstanceState);
    preferences = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());

    setContentView(R.layout.activity_settings);

    Intent intent = getIntent();
    Uri data = intent.getData();

    String initialFragment = null;
    if (data != null) {
      String headerToOpen = data.getSchemeSpecificPart();
      if (headerToOpen != null) {
        initialFragment = headerToOpen;
      }
    }

    // If savedInstanceState is non-null, then the activity is being
    // recreated and super.onCreate() has already recreated the fragment.
    if (savedInstanceState == null) {
      if (initialFragment == null) {
        initialFragment = PrefsFragment.class.getName();
      }
      Fragment fragment;
      try {
        fragment = getSupportFragmentManager()
                .getFragmentFactory()
                .instantiate(getClassLoader(), initialFragment);
      } catch (Exception ignored) {
        fragment = new PrefsFragment();
      }

      getSupportFragmentManager().beginTransaction()
              .replace(R.id.content_frame, fragment)
              .commit();
    }

    getSupportFragmentManager().addOnBackStackChangedListener(() -> {
      if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
        setTitle(R.string.dialer_settings_label);
      }
    });
  }

  @Override
  public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller,
                                           @NonNull Preference pref) {
    Fragment fragment = getSupportFragmentManager()
            .getFragmentFactory()
            .instantiate(getClassLoader(), pref.getFragment());
    fragment.setArguments(pref.getExtras());
    getSupportFragmentManager().beginTransaction()
            .replace(R.id.content_frame, fragment, "")
            .addToBackStack(null)
            .commit();
    setTitle(pref.getTitle());
    return true;
  }

  public static class PrefsFragment extends PreferenceFragmentCompat implements
          Preference.OnPreferenceClickListener {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
      setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));

      if (showDisplayOptions()) {
        Preference displayOptions = new Preference(getContext());
        displayOptions.setTitle(R.string.display_options_title);
        displayOptions.setFragment(DisplayOptionsSettingsFragment.class.getName());
        displayOptions.setIconSpaceReserved(false);
        getPreferenceScreen().addPreference(displayOptions);
      }

      Preference soundSettings = new Preference(getContext());
      soundSettings.setTitle(R.string.sounds_and_vibration_title);
      soundSettings.setFragment(SoundSettingsFragment.class.getName());
      soundSettings.setViewId(R.id.settings_header_sounds_and_vibration);
      soundSettings.setIconSpaceReserved(false);
      getPreferenceScreen().addPreference(soundSettings);

      Preference quickResponseSettings = new Preference(getContext());
      Intent quickResponseSettingsIntent =
              new Intent(TelecomManager.ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS);
      quickResponseSettings.setTitle(R.string.respond_via_sms_setting_title);
      quickResponseSettings.setIntent(quickResponseSettingsIntent);
      quickResponseSettings.setIconSpaceReserved(false);
      getPreferenceScreen().addPreference(quickResponseSettings);

      final Preference lookupSettings = new Preference(getContext());
      lookupSettings.setTitle(R.string.lookup_settings_label);
      lookupSettings.setFragment(LookupSettingsFragment.class.getName());
      lookupSettings.setIconSpaceReserved(false);
      getPreferenceScreen().addPreference(lookupSettings);

      TelephonyManager telephonyManager = getContext().getSystemService(TelephonyManager.class);
      TelecomManager telecomManager = (TelecomManager) getContext().getSystemService(
              Context.TELECOM_SERVICE);

      // "Call Settings" (full settings) is shown if the current user is primary user and there
      // is only one SIM. Otherwise, "Calling accounts" is shown.
      boolean isPrimaryUser = isPrimaryUser();
      if (isPrimaryUser && TelephonyManagerCompat.getPhoneCount(telephonyManager) <= 1) {
        Preference callSettings = new Preference(getContext());
        Intent callSettingsIntent = new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS);
        callSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        callSettings.setTitle(R.string.call_settings_label);
        callSettings.setIntent(callSettingsIntent);
        callSettings.setIconSpaceReserved(false);
        getPreferenceScreen().addPreference(callSettings);
      } else {
        Preference phoneAccountSettings = new Preference(getContext());
        Intent phoneAccountSettingsIntent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
        phoneAccountSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        phoneAccountSettings.setTitle(R.string.phone_account_settings_label);
        phoneAccountSettings.setIntent(phoneAccountSettingsIntent);
        phoneAccountSettings.setIconSpaceReserved(false);
        getPreferenceScreen().addPreference(phoneAccountSettings);
      }
      if (BlockedNumberContract.canCurrentUserBlockNumbers(getContext())) {
        Preference blockedCalls = new Preference(getContext());
        blockedCalls.setTitle(R.string.manage_blocked_numbers_label);
        blockedCalls.setIntent(getContext().getSystemService(TelecomManager.class)
                .createManageBlockedNumbersIntent());
        blockedCalls.setIconSpaceReserved(false);
        getPreferenceScreen().addPreference(blockedCalls);
      }

      addVoicemailSettings(isPrimaryUser);

      if (isPrimaryUser
              && (TelephonyManagerCompat.isTtyModeSupported(telecomManager)
              || TelephonyManagerCompat.isHearingAidCompatibilitySupported(telephonyManager))) {
        Preference accessibilitySettings = new Preference(getContext());
        Intent accessibilitySettingsIntent =
                new Intent(TelecomManager.ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS);
        accessibilitySettings.setTitle(R.string.accessibility_settings_title);
        accessibilitySettings.setIntent(accessibilitySettingsIntent);
        accessibilitySettings.setIconSpaceReserved(false);
        getPreferenceScreen().addPreference(accessibilitySettings);
      }
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
      if (preference.getTitle().equals(getString(R.string.sounds_and_vibration_title))) {
        // If we don't have the permission to write to system settings, go to system sound
        // settings instead. Otherwise, perform the super implementation (which launches our
        // own preference fragment.
        if (!Settings.System.canWrite(getContext())) {
          Toast.makeText(getContext(),
                          getResources().getString(R.string.toast_cannot_write_system_settings),
                          Toast.LENGTH_SHORT)
                  .show();
          startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
          return true;
        }
      }
      super.onPreferenceTreeClick(preference);
      return false;
    }

    private void addVoicemailSettings(boolean isPrimaryUser) {
      if (!isPrimaryUser) {
        LogUtil.i("DialerSettingsActivity.addVoicemailSettings", "user not primary user");
        return;
      }

      if (!PermissionsUtil.hasReadPhoneStatePermissions(getContext())) {
        LogUtil.i("DialerSettingsActivity.addVoicemailSettings", "Missing READ_PHONE_STATE");
        return;
      }

      LogUtil.i("DialerSettingsActivity.addVoicemailSettings", "adding voicemail settings");
      Preference voicemailSettings = new Preference(getContext());
      voicemailSettings.setTitle(R.string.voicemail_settings_label);
      Bundle bundle = new Bundle();
      PhoneAccountHandle soleAccount = getSoleSimAccount();
      if (soleAccount == null) {
        LogUtil.i("DialerSettingsActivity.addVoicemailSettings",
                "showing multi-SIM voicemail settings");
        voicemailSettings.setFragment(PhoneAccountSelectionFragment.class.getName());
        bundle.putString(
                PhoneAccountSelectionFragment.PARAM_TARGET_FRAGMENT,
                VoicemailSettingsFragment.class.getName());
        bundle.putString(
                PhoneAccountSelectionFragment.PARAM_PHONE_ACCOUNT_HANDLE_KEY,
                VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE);
        bundle.putBundle(PhoneAccountSelectionFragment.PARAM_ARGUMENTS, new Bundle());
        bundle.putInt(PhoneAccountSelectionFragment.PARAM_TARGET_TITLE_RES,
                R.string.voicemail_settings_label);
      } else {
        LogUtil.i(
                "DialerSettingsActivity.addVoicemailSettings", "showing single-SIM voicemail settings");
        voicemailSettings.setFragment(VoicemailSettingsFragment.class.getName());
        bundle.putParcelable(VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE, soleAccount);
      }
      voicemailSettings.getExtras().putAll(bundle);
      voicemailSettings.setIconSpaceReserved(false);
      getPreferenceScreen().addPreference(voicemailSettings);
    }

    /**
     * @return the only SIM phone account, or {@code null} if there are none or more than one. Note:
     * having a empty SIM slot still count as a PhoneAccountHandle that is "invalid", and
     * voicemail settings should still be available for it.
     */
    @SuppressLint("MissingPermission")
    @Nullable
    private PhoneAccountHandle getSoleSimAccount() {
      TelecomManager telecomManager = requireContext().getSystemService(TelecomManager.class);
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

    /**
     * @return Whether the current user is the primary user.
     */
    private boolean isPrimaryUser() {
      return requireContext().getSystemService(UserManager.class).isSystemUser();
    }
  }
}
