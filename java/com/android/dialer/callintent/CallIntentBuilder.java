/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.callintent;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.text.TextUtils;
import com.android.dialer.assisteddialing.AssistedDialingMediator;
import com.android.dialer.assisteddialing.TransformationInfo;
import com.android.dialer.common.Assert;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.util.CallUtil;
import java.util.Optional;

/** Creates an intent to start a new outgoing call. */
public class CallIntentBuilder {
  private final Uri uri;
  private final CallSpecificAppData callSpecificAppData;
  @Nullable private PhoneAccountHandle phoneAccountHandle;
  private boolean isVideoCall;
  private String callSubject;
  private boolean allowAssistedDial;
  private AssistedDialingMediator assistedDialingMediator;

  private static int lightbringerButtonAppearInExpandedCallLogItemCount = 0;
  private static int lightbringerButtonAppearInCollapsedCallLogItemCount = 0;
  private static int lightbringerButtonAppearInSearchCount = 0;

  public CallIntentBuilder(@NonNull Uri uri, @NonNull CallSpecificAppData callSpecificAppData) {
    this.uri = Assert.isNotNull(uri);
    Assert.isNotNull(callSpecificAppData);
    Assert.checkArgument(
        callSpecificAppData.getCallInitiationType() != CallInitiationType.Type.UNKNOWN_INITIATION);

    CallSpecificAppData.Builder builder =
        CallSpecificAppData.newBuilder(callSpecificAppData)
            .setLightbringerButtonAppearInExpandedCallLogItemCount(
                lightbringerButtonAppearInExpandedCallLogItemCount)
            .setLightbringerButtonAppearInCollapsedCallLogItemCount(
                lightbringerButtonAppearInCollapsedCallLogItemCount)
            .setLightbringerButtonAppearInSearchCount(lightbringerButtonAppearInSearchCount);
    lightbringerButtonAppearInExpandedCallLogItemCount = 0;
    lightbringerButtonAppearInCollapsedCallLogItemCount = 0;
    lightbringerButtonAppearInSearchCount = 0;

    if (PerformanceReport.isRecording()) {
      builder
          .setTimeSinceAppLaunch(PerformanceReport.getTimeSinceAppLaunch())
          .setTimeSinceFirstClick(PerformanceReport.getTimeSinceFirstClick())
          .addAllUiActionsSinceAppLaunch(PerformanceReport.getActions())
          .addAllUiActionTimestampsSinceAppLaunch(PerformanceReport.getActionTimestamps())
          .setStartingTabIndex(PerformanceReport.getStartingTabIndex())
          .build();
      PerformanceReport.stopRecording();
    }

    this.callSpecificAppData = builder.build();
  }

  public CallIntentBuilder(@NonNull Uri uri, CallInitiationType.Type callInitiationType) {
    this(uri, createCallSpecificAppData(callInitiationType));
  }

  public CallIntentBuilder(
      @NonNull String number, @NonNull CallSpecificAppData callSpecificAppData) {
    this(CallUtil.getCallUri(Assert.isNotNull(number)), callSpecificAppData);
  }

  public CallIntentBuilder(@NonNull String number, CallInitiationType.Type callInitiationType) {
    this(CallUtil.getCallUri(Assert.isNotNull(number)), callInitiationType);
  }

  public CallSpecificAppData getCallSpecificAppData() {
    return callSpecificAppData;
  }

  public CallIntentBuilder setPhoneAccountHandle(@Nullable PhoneAccountHandle accountHandle) {
    this.phoneAccountHandle = accountHandle;
    return this;
  }

  public CallIntentBuilder setIsVideoCall(boolean isVideoCall) {
    this.isVideoCall = isVideoCall;
    return this;
  }

  public CallIntentBuilder setAllowAssistedDial(
      boolean allowAssistedDial, @NonNull AssistedDialingMediator assistedDialingMediator) {
    this.assistedDialingMediator = Assert.isNotNull(assistedDialingMediator);
    this.allowAssistedDial = allowAssistedDial;
    return this;
  }

  public CallIntentBuilder setCallSubject(String callSubject) {
    this.callSubject = callSubject;
    return this;
  }

  public Intent build() {
    Intent intent = new Intent(Intent.ACTION_CALL, uri);
    Bundle extras = new Bundle();

    if (allowAssistedDial
        && this.assistedDialingMediator != null
        && this.assistedDialingMediator.isPlatformEligible()) {
      intent = buildAssistedDialingParameters(intent, extras);
    }
    intent.putExtra(
        TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
        isVideoCall ? VideoProfile.STATE_BIDIRECTIONAL : VideoProfile.STATE_AUDIO_ONLY);

    extras.putLong(Constants.EXTRA_CALL_CREATED_TIME_MILLIS, SystemClock.elapsedRealtime());
    CallIntentParser.putCallSpecificAppData(extras, callSpecificAppData);

    intent.putExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);

    if (phoneAccountHandle != null) {
      intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
    }

    if (!TextUtils.isEmpty(callSubject)) {
      intent.putExtra(TelecomManager.EXTRA_CALL_SUBJECT, callSubject);
    }

    return intent;
  }

  @SuppressWarnings("AndroidApiChecker") // Use of optional
  @TargetApi(Build.VERSION_CODES.N)
  private Intent buildAssistedDialingParameters(Intent intent, Bundle extras) {
    extras.putBoolean(TelephonyManagerCompat.ALLOW_ASSISTED_DIAL, true);
    String phoneNumber =
        uri.getScheme().equals(PhoneAccount.SCHEME_TEL) ? uri.getSchemeSpecificPart() : "";
    Optional<TransformationInfo> transformedNumber =
        assistedDialingMediator.attemptAssistedDial(phoneNumber);
    if (transformedNumber.isPresent()) {
      Bundle assistedDialingExtras = transformedNumber.get().toBundle();
      extras.putBoolean(TelephonyManagerCompat.IS_ASSISTED_DIALED, true);
      extras.putBundle(TelephonyManagerCompat.ASSISTED_DIALING_EXTRAS, assistedDialingExtras);
      intent =
          new Intent(
              Intent.ACTION_CALL,
              CallUtil.getCallUri(Assert.isNotNull(transformedNumber.get().transformedNumber())));
    }
    return intent;
  }

  private static @NonNull CallSpecificAppData createCallSpecificAppData(
      CallInitiationType.Type callInitiationType) {
    CallSpecificAppData callSpecificAppData =
        CallSpecificAppData.newBuilder().setCallInitiationType(callInitiationType).build();
    return callSpecificAppData;
  }

  public static void increaseLightbringerCallButtonAppearInExpandedCallLogItemCount() {
    CallIntentBuilder.lightbringerButtonAppearInExpandedCallLogItemCount++;
  }

  public static void increaseLightbringerCallButtonAppearInCollapsedCallLogItemCount() {
    CallIntentBuilder.lightbringerButtonAppearInCollapsedCallLogItemCount++;
  }

  public static void increaseLightbringerCallButtonAppearInSearchCount() {
    CallIntentBuilder.lightbringerButtonAppearInSearchCount++;
  }

  @VisibleForTesting
  public static int getLightbringerButtonAppearInExpandedCallLogItemCount() {
    return lightbringerButtonAppearInExpandedCallLogItemCount;
  }

  @VisibleForTesting
  public static int getLightbringerButtonAppearInCollapsedCallLogItemCount() {
    return lightbringerButtonAppearInCollapsedCallLogItemCount;
  }

  @VisibleForTesting
  public static int getLightbringerButtonAppearInSearchCount() {
    return lightbringerButtonAppearInSearchCount;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public static void clearLightbringerCounts() {
    lightbringerButtonAppearInCollapsedCallLogItemCount = 0;
    lightbringerButtonAppearInExpandedCallLogItemCount = 0;
    lightbringerButtonAppearInSearchCount = 0;
  }
}
