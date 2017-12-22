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

package com.android.dialer.precall.impl;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import com.android.dialer.assisteddialing.AssistedDialingMediator;
import com.android.dialer.assisteddialing.ConcreteCreator;
import com.android.dialer.assisteddialing.TransformationInfo;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.configprovider.ConfigProvider;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.precall.PreCallAction;
import com.android.dialer.precall.PreCallCoordinator;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.CallUtil;
import java.util.Optional;

/** Rewrites the call URI with country code. */
public class AssistedDialAction implements PreCallAction {

  @Override
  public boolean requiresUi(Context context, CallIntentBuilder builder) {
    return false;
  }

  @SuppressWarnings("AndroidApiChecker") // Use of optional
  @TargetApi(Build.VERSION_CODES.N)
  @Override
  public void runWithoutUi(Context context, CallIntentBuilder builder) {
    if (!builder.isAssistedDialAllowed()) {
      return;
    }

    AssistedDialingMediator assistedDialingMediator =
        ConcreteCreator.createNewAssistedDialingMediator(
            getAssistedDialingTelephonyManager(context, builder), context);
    if (Build.VERSION.SDK_INT > ConcreteCreator.BUILD_CODE_CEILING) {
      builder.getOutgoingCallExtras().putBoolean(TelephonyManagerCompat.USE_ASSISTED_DIALING, true);
    }
    // Checks the platform is N+ and meets other pre-flight checks.
    if (!assistedDialingMediator.isPlatformEligible()) {
      return;
    }
    String phoneNumber =
        builder.getUri().getScheme().equals(PhoneAccount.SCHEME_TEL)
            ? builder.getUri().getSchemeSpecificPart()
            : "";
    Optional<TransformationInfo> transformedNumber =
        assistedDialingMediator.attemptAssistedDial(phoneNumber);
    if (transformedNumber.isPresent()) {
      builder.getOutgoingCallExtras().putBoolean(TelephonyManagerCompat.USE_ASSISTED_DIALING, true);
      Bundle assistedDialingExtras = transformedNumber.get().toBundle();
      builder
          .getOutgoingCallExtras()
          .putBundle(TelephonyManagerCompat.ASSISTED_DIALING_EXTRAS, assistedDialingExtras);
      builder.setUri(
          CallUtil.getCallUri(Assert.isNotNull(transformedNumber.get().transformedNumber())));
    }
  }

  /**
   * A convenience method to return the proper TelephonyManager in possible multi-sim environments.
   */
  @SuppressWarnings("AndroidApiChecker") // Use of createForSubscriptionId
  @TargetApi(Build.VERSION_CODES.N)
  private TelephonyManager getAssistedDialingTelephonyManager(
      Context context, CallIntentBuilder builder) {

    ConfigProvider configProvider = ConfigProviderBindings.get(context);
    TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
    // None of this will be required in the framework because the PhoneAccountHandle
    // is already mapped to the request in the TelecomConnection.
    if (builder.getPhoneAccountHandle() == null) {
      return telephonyManager;
    }

    if (!configProvider.getBoolean("assisted_dialing_dual_sim_enabled", false)) {
      return telephonyManager;
    }

    com.google.common.base.Optional<SubscriptionInfo> subscriptionInfo =
        TelecomUtil.getSubscriptionInfo(context, builder.getPhoneAccountHandle());
    if (!subscriptionInfo.isPresent()) {
      LogUtil.i(
          "AssistedDialAction.getAssistedDialingTelephonyManager", "subcriptionInfo was absent.");
      return telephonyManager;
    }
    TelephonyManager pinnedtelephonyManager =
        telephonyManager.createForSubscriptionId(subscriptionInfo.get().getSubscriptionId());
    if (pinnedtelephonyManager == null) {
      LogUtil.i(
          "AssistedDialAction.getAssistedDialingTelephonyManager",
          "createForSubscriptionId pinnedtelephonyManager was null.");
      return telephonyManager;
    }
    LogUtil.i(
        "AssistedDialAction.getAssistedDialingTelephonyManager",
        "createForPhoneAccountHandle using pinnedtelephonyManager from subscription id.");
    return pinnedtelephonyManager;
  }

  @Override
  public void runWithUi(PreCallCoordinator coordinator) {
    runWithoutUi(coordinator.getActivity(), coordinator.getBuilder());
  }

  @Override
  public void onDiscard() {}
}
