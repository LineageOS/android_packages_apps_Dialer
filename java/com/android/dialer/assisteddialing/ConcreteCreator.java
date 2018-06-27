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
 * limitations under the License.
 */

package com.android.dialer.assisteddialing;

import android.content.Context;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.os.UserManagerCompat;
import android.telephony.TelephonyManager;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProvider;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.dialer.strictmode.StrictModeUtils;

/**
 * A Creator for AssistedDialingMediators.
 *
 * <p>This helps keep the dependencies required by AssistedDialingMediator for assisted dialing
 * explicit.
 */
public final class ConcreteCreator {

  // Ceiling set at P (version code 28) because this feature will ship as part of the framework in
  // Q.
  public static final int BUILD_CODE_CEILING = 28;

  /**
   * Creates a new AssistedDialingMediator
   *
   * @param telephonyManager The telephony manager used to determine user location.
   * @param context The context used to determine whether or not a provided number is an emergency
   *     number.
   * @return An AssistedDialingMediator
   */
  public static AssistedDialingMediator createNewAssistedDialingMediator(
      @NonNull TelephonyManager telephonyManager, @NonNull Context context) {

    ConfigProvider configProvider = ConfigProviderComponent.get(context).getConfigProvider();

    if (telephonyManager == null) {
      LogUtil.i(
          "ConcreteCreator.createNewAssistedDialingMediator", "provided TelephonyManager was null");
      throw new NullPointerException("Provided TelephonyManager was null");
    }
    if (context == null) {
      LogUtil.i("ConcreteCreator.createNewAssistedDialingMediator", "provided context was null");
      throw new NullPointerException("Provided context was null");
    }

    if (!UserManagerCompat.isUserUnlocked(context)) {
      // To avoid any issues reading preferences, we disable the feature when the user is in a
      // locked state.
      LogUtil.i("ConcreteCreator.createNewAssistedDialingMediator", "user is locked");
      return new AssistedDialingMediatorStub();
    }

    if (!isAssistedDialingEnabled(configProvider)) {
      LogUtil.i("ConcreteCreator.createNewAssistedDialingMediator", "feature not enabled");
      return new AssistedDialingMediatorStub();
    }

    if (!PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(context.getString(R.string.assisted_dialing_setting_toggle_key), true)) {
      LogUtil.i("ConcreteCreator.createNewAssistedDialingMediator", "disabled by local setting");

      return new AssistedDialingMediatorStub();
    }

    Constraints constraints = new Constraints(context, getCountryCodeProvider(configProvider));
    return new AssistedDialingMediatorImpl(
        new LocationDetector(
            telephonyManager,
            StrictModeUtils.bypass(
                () ->
                    PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(
                            context.getString(R.string.assisted_dialing_setting_cc_key), null))),
        new NumberTransformer(constraints));
  }

  /** Returns a boolean indicating whether or not the assisted dialing feature is enabled. */
  public static boolean isAssistedDialingEnabled(@NonNull ConfigProvider configProvider) {
    if (configProvider == null) {
      LogUtil.i("ConcreteCreator.isAssistedDialingEnabled", "provided configProvider was null");
      throw new NullPointerException("Provided configProvider was null");
    }

    return Build.VERSION.SDK_INT <= BUILD_CODE_CEILING
        && configProvider.getBoolean("assisted_dialing_enabled", false);
  }

  /**
   * Returns a CountryCodeProvider responsible for providing countries eligible for assisted Dialing
   */
  public static CountryCodeProvider getCountryCodeProvider(ConfigProvider configProvider) {
    if (configProvider == null) {
      LogUtil.i("ConcreteCreator.getCountryCodeProvider", "provided configProvider was null");
      throw new NullPointerException("Provided configProvider was null");
    }

    return new CountryCodeProvider(configProvider);
  }
}
