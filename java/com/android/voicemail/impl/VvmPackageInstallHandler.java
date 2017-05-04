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

package com.android.voicemail.impl;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ChangedPackages;
import android.os.Build.VERSION_CODES;
import android.preference.PreferenceManager;
import android.provider.Settings.Global;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.ArraySet;
import com.android.dialer.common.PackageUtils;
import com.android.voicemail.impl.settings.VisualVoicemailSettingsUtil;
import java.util.Set;

/**
 * When a new package is installed, check if it matches any of the vvm carrier apps of the currently
 * enabled dialer VVM sources. The dialer VVM client will be disabled upon carrier VVM app
 * installation, unless it was explicitly enabled by the user.
 *
 * <p>The ACTION_PACKAGE_ADDED broadcast can no longer be received. (see
 * https://developer.android.com/preview/features/background.html#broadcasts) New apps are scanned
 * when a VVM SMS is received instead, as it can be a result of the carrier VVM app trying to run
 * activation.
 */
@SuppressLint("AndroidApiChecker") // forEach
@TargetApi(VERSION_CODES.O)
public final class VvmPackageInstallHandler {

  private static final String LAST_BOOT_COUNT =
      "com.android.voicemail.impl.VvmPackageInstallHandler.LAST_BOOT_COUNT";

  private static final String CHANGED_PACKAGES_SEQUENCE_NUMBER =
      "com.android.voicemail.impl.VvmPackageInstallHandler.CHANGED_PACKAGES_SEQUENCE_NUMBER";

  private static final String INSTALLED_CARRIER_PACKAGES =
      "com.android.voicemail.impl.VvmPackageInstallHandler.INSTALLED_CARRIER_PACKAGES";

  /**
   * Perform a scan of all changed apps since the last invocation to see if the carrier VVM app is
   * installed.
   */
  public static void scanNewPackages(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    int sequenceNumber = sharedPreferences.getInt(CHANGED_PACKAGES_SEQUENCE_NUMBER, 0);
    int lastBootCount = sharedPreferences.getInt(LAST_BOOT_COUNT, 0);
    int bootCount = Global.getInt(context.getContentResolver(), Global.BOOT_COUNT, 0);
    if (lastBootCount != bootCount) {
      VvmLog.i(
          "VvmPackageInstallHandler.scanNewPackages", "reboot detected, resetting sequence number");
      sequenceNumber = 0;
      sharedPreferences.edit().putInt(LAST_BOOT_COUNT, bootCount).apply();
    }

    ChangedPackages changedPackages =
        context.getPackageManager().getChangedPackages(sequenceNumber);
    if (changedPackages == null) {
      VvmLog.i("VvmPackageInstallHandler.scanNewPackages", "no package has changed");
      return;
    }
    sharedPreferences
        .edit()
        .putInt(CHANGED_PACKAGES_SEQUENCE_NUMBER, changedPackages.getSequenceNumber())
        .apply();

    Set<String> installedPackages =
        sharedPreferences.getStringSet(INSTALLED_CARRIER_PACKAGES, new ArraySet<>());

    Set<String> monitoredPackage = getMonitoredPackages(context);
    installedPackages.removeIf((packageName) -> !monitoredPackage.contains(packageName));

    for (String packageName : changedPackages.getPackageNames()) {
      if (!monitoredPackage.contains(packageName)) {
        continue;
      }
      if (PackageUtils.isPackageEnabled(packageName, context)) {
        if (!installedPackages.contains(packageName)) {
          VvmLog.i("VvmPackageInstallHandler.scanNewPackages", "new package found: " + packageName);
          installedPackages.add(packageName);
          handlePackageInstalled(context, packageName);
        }
      } else {
        installedPackages.remove(packageName);
      }
    }
    sharedPreferences.edit().putStringSet(INSTALLED_CARRIER_PACKAGES, installedPackages).apply();
  }

  private static Set<String> getMonitoredPackages(Context context) {
    Set<String> result = new ArraySet<>();
    context
        .getSystemService(TelecomManager.class)
        .getCallCapablePhoneAccounts()
        .forEach(
            (phoneAccountHandle -> {
              OmtpVvmCarrierConfigHelper carrierConfigHelper =
                  new OmtpVvmCarrierConfigHelper(context, phoneAccountHandle);
              if (!carrierConfigHelper.isValid()) {
                return;
              }
              if (carrierConfigHelper.getCarrierVvmPackageNames() == null) {
                return;
              }
              result.addAll(carrierConfigHelper.getCarrierVvmPackageNames());
            }));

    return result;
  };

  /**
   * Iterates through all phone account and disable VVM on a account if {@code packageName} is
   * listed as a carrier VVM package.
   */
  private static void handlePackageInstalled(Context context, String packageName) {
    // This get called every time an app is installed and will be noisy. Don't log until the app
    // is identified as a carrier VVM app.
    for (PhoneAccountHandle phoneAccount :
        context.getSystemService(TelecomManager.class).getCallCapablePhoneAccounts()) {
      OmtpVvmCarrierConfigHelper carrierConfigHelper =
          new OmtpVvmCarrierConfigHelper(context, phoneAccount);
      if (!carrierConfigHelper.isValid()) {
        continue;
      }
      if (carrierConfigHelper.getCarrierVvmPackageNames() == null) {
        continue;
      }
      if (!carrierConfigHelper.getCarrierVvmPackageNames().contains(packageName)) {
        continue;
      }

      VvmLog.i("VvmPackageInstallHandler.handlePackageInstalled", "Carrier app installed");
      if (VisualVoicemailSettingsUtil.isEnabledUserSet(context, phoneAccount)) {
        // Skip the check if this voicemail source's setting is overridden by the user.
        VvmLog.i(
            "VvmPackageInstallHandler.handlePackageInstalled",
            "VVM enabled by user, not disabling");
        continue;
      }

      // Force deactivate the client. The user can re-enable it in the settings.
      // There is no need to update the settings for deactivation. At this point, if the
      // default value is used it should be false because a carrier package is present.
      VvmLog.i(
          "VvmPackageInstallHandler.handlePackageInstalled",
          "Carrier VVM package installed, disabling system VVM client");
      VisualVoicemailSettingsUtil.setEnabled(context, phoneAccount, false);
    }
  }
}
