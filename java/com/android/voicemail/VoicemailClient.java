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

package com.android.voicemail;

import android.content.Context;
import android.os.PersistableBundle;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import java.util.List;

/** Public interface for the voicemail module */
public interface VoicemailClient {

  /**
   * Broadcast to tell the client to upload local database changes to the server. Since the dialer
   * UI and the client are in the same package, the {@link
   * android.content.Intent#ACTION_PROVIDER_CHANGED} will always be a self-change even if the UI is
   * external to the client.
   */
  String ACTION_UPLOAD = "com.android.voicemail.VoicemailClient.ACTION_UPLOAD";
  /** Common key for passing {@link PhoneAccountHandle} in bundles. */
  String PARAM_PHONE_ACCOUNT_HANDLE = "phone_account_handle";
  /**
   * Broadcast from the client to inform the app to show a legacy voicemail notification. This
   * broadcast is same as {@link TelephonyManager#ACTION_SHOW_VOICEMAIL_NOTIFICATION}.
   */
  String ACTION_SHOW_LEGACY_VOICEMAIL =
      "com.android.voicemail.VoicemailClient.ACTION_SHOW_LEGACY_VOICEMAIL";
  /**
   * Boolean extra send with {@link #ACTION_SHOW_LEGACY_VOICEMAIL}, indicating that the notification
   * is sent by legacy mode and should not be suppressed even when VVM is activated
   */
  String EXTRA_IS_LEGACY_MODE = "is_legacy_mode";
  /**
   * Secret code to launch the voicemail config activity intended for OEMs and Carriers. {@code
   * *#*#VVMCONFIG#*#*}
   */
  String VOICEMAIL_SECRET_CODE = "886266344";

  /**
   * Whether the voicemail module is enabled (OS has support and not disabled by flags, etc.). This
   * does not mean the carrier has support or user has enabled the feature.
   */
  boolean isVoicemailModuleEnabled();

  /**
   * Whether visual voicemail is supported by the carrier for the {@code phoneAccountHandle}. This
   * is purely based on the MCCMNC, and a single account might still be disabled by the carrier.
   */
  boolean hasCarrierSupport(Context context, PhoneAccountHandle phoneAccountHandle);

  /**
   * Whether the visual voicemail service is enabled for the {@code phoneAccountHandle}. "Enable"
   * means the user "wants" to have this service on, and does not mean the service is actually
   * functional(For example, the service is blocked on the carrier side. The service will be
   * "enabled" but all it will do is show the error).
   */
  boolean isVoicemailEnabled(Context context, PhoneAccountHandle phoneAccountHandle);

  /**
   * Enable or disable visual voicemail service for the {@code phoneAccountHandle}. Setting to
   * enabled will initiate provisioning and activation. Setting to disabled will initiate
   * deactivation.
   */
  void setVoicemailEnabled(Context context, PhoneAccountHandle phoneAccountHandle, boolean enabled);

  /**
   * Appends the selection to ignore voicemails from non-active OMTP voicemail package. In OC there
   * can be multiple packages handling OMTP voicemails which represents the same source of truth.
   * These packages should mark their voicemails as {@link Voicemails#IS_OMTP_VOICEMAIL} and only
   * the voicemails from {@link TelephonyManager#getVisualVoicemailPackageName()} should be shown.
   * For example, the user synced voicemails with DialerA, and then switched to DialerB, voicemails
   * from DialerA should be ignored as they are no longer current. Voicemails from {@link
   * #OMTP_VOICEMAIL_BLACKLIST} will also be ignored as they are voicemail source only valid pre-OC.
   */
  void appendOmtpVoicemailSelectionClause(
      Context context, StringBuilder where, List<String> selectionArgs);

  /**
   * Appends the selection to ignore voicemail status from non-active OMTP voicemail package. The
   * {@link android.provider.VoicemailContract.Status#SOURCE_TYPE} is checked against a list of
   * known OMTP types. Voicemails from {@link #OMTP_VOICEMAIL_BLACKLIST} will also be ignored as
   * they are voicemail source only valid pre-OC.
   *
   * @see #appendOmtpVoicemailSelectionClause(Context, StringBuilder, List)
   */
  void appendOmtpVoicemailStatusSelectionClause(
      Context context, StringBuilder where, List<String> selectionArgs);

  boolean isVoicemailArchiveEnabled(Context context, PhoneAccountHandle phoneAccountHandle);

  /**
   * @return if the voicemail archive feature is available on the current device. This depends on
   *     whether the server side flag is turned on for the feature, and if the OS meets the
   *     requirement for this feature.
   */
  boolean isVoicemailArchiveAvailable(Context context);

  void setVoicemailArchiveEnabled(
      Context context, PhoneAccountHandle phoneAccountHandle, boolean value);

  /**
   * @return if the voicemail transcription feature is available on the current device. This depends
   *     on whether the server side flag is turned on for the feature, visual voicemail is activated
   *     and enabled and if the OS meets the requirement for this feature.
   */
  boolean isVoicemailTranscriptionAvailable(Context context, PhoneAccountHandle account);

  /** @return if the voicemail transcription setting has been enabled by the user. */
  boolean isVoicemailTranscriptionEnabled(Context context, PhoneAccountHandle account);

  /** @return if the voicemail donation feature is available. */
  boolean isVoicemailDonationAvailable(Context context, PhoneAccountHandle account);

  /** @return if the voicemail donation setting has been enabled by the user. */
  boolean isVoicemailDonationEnabled(Context context, PhoneAccountHandle account);

  void setVoicemailTranscriptionEnabled(
      Context context, PhoneAccountHandle phoneAccountHandle, boolean enabled);

  void setVoicemailDonationEnabled(
      Context context, PhoneAccountHandle phoneAccountHandle, boolean enabled);

  /**
   * Whether the client is activated and handling visual voicemail for the {@code
   * phoneAccountHandle}. "Enable" is the intention to use VVM. For example VVM can be enabled but
   * prevented from working because the carrier blocked it, or a connection problem is blocking the
   * provisioning. Being "activated" means all setup are completed, and VVM is expected to work.
   */
  boolean isActivated(Context context, PhoneAccountHandle phoneAccountHandle);

  /**
   * Called when {@link #VOICEMAIL_SECRET_CODE} is dialed. {@code context} will be a broadcast
   * receiver context.
   */
  @MainThread
  void showConfigUi(@NonNull Context context);

  @NonNull
  PersistableBundle getConfig(
      @NonNull Context context, @Nullable PhoneAccountHandle phoneAccountHandle);

  @MainThread
  void onBoot(@NonNull Context context);

  @MainThread
  void onShutdown(@NonNull Context context);

  @MainThread
  void addActivationStateListener(ActivationStateListener listener);

  @MainThread
  void removeActivationStateListener(ActivationStateListener listener);

  /** Provides interface to change the PIN used to access the mailbox by calling. */
  PinChanger createPinChanger(Context context, PhoneAccountHandle phoneAccountHandle);

  void onTosAccepted(Context context, PhoneAccountHandle phoneAccountHandle);

  boolean hasAcceptedTos(Context context, PhoneAccountHandle phoneAccountHandle);

  /**
   * @return arbitrary carrier configuration String value associate with the indicated key. See
   *     {@code CarrierConfigKeys.java}
   */
  @Nullable
  String getCarrierConfigString(Context context, PhoneAccountHandle phoneAccountHandle, String key);

  /** Listener for changes in {@link #isActivated(Context, PhoneAccountHandle)} */
  interface ActivationStateListener {
    @MainThread
    void onActivationStateChanged(PhoneAccountHandle phoneAccountHandle, boolean isActivated);
  }
}
