/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.dialer.callintent;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.telecom.Call.Details;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.dialer.callintent.CallInitiationType.Type;
import com.android.dialer.common.Assert;
import com.android.dialer.util.CallUtil;
import com.google.protobuf.InvalidProtocolBufferException;

/** Creates an intent to start a new outgoing call. */
public class CallIntentBuilder implements Parcelable {
  private Uri uri;
  private final CallSpecificAppData callSpecificAppData;
  @Nullable
  private PhoneAccountHandle phoneAccountHandle;
  private boolean isVideoCall;
  private String callSubject;
  private boolean allowAssistedDial;

  private final Bundle inCallUiIntentExtras = new Bundle();
  private final Bundle placeCallExtras = new Bundle();

  public CallIntentBuilder(@NonNull Uri uri, @NonNull CallSpecificAppData callSpecificAppData) {
    this.uri = Assert.isNotNull(uri);
    Assert.isNotNull(callSpecificAppData);
    Assert.checkArgument(
        callSpecificAppData.getCallInitiationType() != CallInitiationType.Type.UNKNOWN_INITIATION);

    CallSpecificAppData.Builder builder = CallSpecificAppData.newBuilder(callSpecificAppData);
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

  public CallIntentBuilder(@NonNull Parcel parcel) {
    ClassLoader classLoader = CallIntentBuilder.class.getClassLoader();
    uri = parcel.readParcelable(classLoader, Uri.class);
    CallSpecificAppData data;
    try {
      data = CallSpecificAppData.parseFrom(parcel.createByteArray());
    } catch (InvalidProtocolBufferException e) {
      data = createCallSpecificAppData(Type.UNKNOWN_INITIATION);
    }
    callSpecificAppData = data;
    phoneAccountHandle = parcel.readParcelable(classLoader, PhoneAccountHandle.class);
    isVideoCall = parcel.readInt() != 0;
    callSubject = parcel.readString();
    allowAssistedDial = parcel.readInt() != 0;
    inCallUiIntentExtras.putAll(parcel.readBundle(classLoader));
  }

  public static CallIntentBuilder forVoicemail(
      CallInitiationType.Type callInitiationType) {
    return new CallIntentBuilder(
            Uri.fromParts(PhoneAccount.SCHEME_VOICEMAIL, "", null), callInitiationType)
        .setPhoneAccountHandle(null);
  }

  public void setUri(@NonNull Uri uri) {
    this.uri = Assert.isNotNull(uri);
  }

  public Uri getUri() {
    return uri;
  }

  public CallSpecificAppData getCallSpecificAppData() {
    return callSpecificAppData;
  }

  public CallIntentBuilder setPhoneAccountHandle(@Nullable PhoneAccountHandle accountHandle) {
    this.phoneAccountHandle = accountHandle;
    return this;
  }

  @Nullable
  public PhoneAccountHandle getPhoneAccountHandle() {
    return phoneAccountHandle;
  }

  public CallIntentBuilder setIsVideoCall(boolean isVideoCall) {
    this.isVideoCall = isVideoCall;
    return this;
  }

  public boolean isVideoCall() {
    return isVideoCall;
  }

  /** Default false. Should only be set to true if the number has a lookup URI. */
  public CallIntentBuilder setAllowAssistedDial(boolean allowAssistedDial) {
    this.allowAssistedDial = allowAssistedDial;
    return this;
  }

  public boolean isAssistedDialAllowed() {
    return allowAssistedDial;
  }

  public CallIntentBuilder setCallSubject(String callSubject) {
    this.callSubject = callSubject;
    return this;
  }

  /** Additional data the in call UI can read with {@link Details#getIntentExtras()} */
  public Bundle getInCallUiIntentExtras() {
    return inCallUiIntentExtras;
  }

  /**
   * Other extras that should be used with {@link TelecomManager#placeCall(Uri, Bundle)}. This will
   * override everything set by the CallIntentBuilder
   */
  public Bundle getPlaceCallExtras() {
    return placeCallExtras;
  }

  /**
   * @deprecated Use {@link com.android.dialer.precall.PreCall#getIntent(android.content.Context,
   *     CallIntentBuilder)} instead.
   */
  @Deprecated
  public Intent build() {
    Intent intent = new Intent(Intent.ACTION_CALL, uri);

    intent.putExtra(
        TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
        isVideoCall ? VideoProfile.STATE_BIDIRECTIONAL : VideoProfile.STATE_AUDIO_ONLY);

    inCallUiIntentExtras.putLong(
        Constants.EXTRA_CALL_CREATED_TIME_MILLIS, SystemClock.elapsedRealtime());
    CallIntentParser.putCallSpecificAppData(inCallUiIntentExtras, callSpecificAppData);

    intent.putExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, inCallUiIntentExtras);

    if (phoneAccountHandle != null) {
      intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
    }

    if (!TextUtils.isEmpty(callSubject)) {
      intent.putExtra(TelecomManager.EXTRA_CALL_SUBJECT, callSubject);
    }

    intent.putExtras(placeCallExtras);

    return intent;
  }

  private static @NonNull CallSpecificAppData createCallSpecificAppData(
      CallInitiationType.Type callInitiationType) {
    CallSpecificAppData callSpecificAppData =
        CallSpecificAppData.newBuilder().setCallInitiationType(callInitiationType).build();
    return callSpecificAppData;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(uri, flags);
    dest.writeByteArray(callSpecificAppData.toByteArray());
    dest.writeParcelable(phoneAccountHandle, flags);
    dest.writeInt(isVideoCall ? 1 : 0);
    dest.writeString(callSubject);
    dest.writeInt(allowAssistedDial ? 1 : 0);
    dest.writeBundle(inCallUiIntentExtras);
  }

  public static final Creator<CallIntentBuilder> CREATOR =
      new Creator<CallIntentBuilder>() {
        @Override
        public CallIntentBuilder createFromParcel(Parcel source) {
          return new CallIntentBuilder(source);
        }

        @Override
        public CallIntentBuilder[] newArray(int size) {
          return new CallIntentBuilder[0];
        }
      };
}
