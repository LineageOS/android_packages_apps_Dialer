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

package com.android.dialer.voicemail.listui.error;

import android.content.Context;
import android.preference.PreferenceManager;
import android.provider.VoicemailContract.Status;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.PerAccountSharedPreferences;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.voicemail.listui.error.VoicemailErrorMessage.Action;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.VoicemailComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * Create error message from {@link VoicemailStatus} for OMTP visual voicemail. This is also the
 * default behavior if other message creator does not handle the status.
 */
public class OmtpVoicemailMessageCreator {

  private static final float QUOTA_NEAR_FULL_THRESHOLD = 0.9f;
  private static final float QUOTA_FULL_THRESHOLD = 0.99f;
  protected static final String VOICEMAIL_PROMO_DISMISSED_KEY =
      "voicemail_archive_promo_was_dismissed";
  protected static final String VOICEMAIL_PROMO_ALMOST_FULL_DISMISSED_KEY =
      "voicemail_archive_almost_full_promo_was_dismissed";

  @Nullable
  public static VoicemailErrorMessage create(
      Context context, VoicemailStatus status, final VoicemailStatusReader statusReader) {
    VoicemailErrorMessage tosMessage =
        new VoicemailTosMessageCreator(context, status, statusReader).maybeCreateTosMessage();
    if (tosMessage != null) {
      return tosMessage;
    }

    if (Status.CONFIGURATION_STATE_OK == status.configurationState
        && Status.DATA_CHANNEL_STATE_OK == status.dataChannelState
        && Status.NOTIFICATION_CHANNEL_STATE_OK == status.notificationChannelState) {
      return checkQuota(context, status, statusReader);
    }
    // Initial state when the source is activating. Other error might be written into data and
    // notification channel during activation.
    if (Status.CONFIGURATION_STATE_CONFIGURING == status.configurationState
        && Status.DATA_CHANNEL_STATE_OK == status.dataChannelState
        && Status.NOTIFICATION_CHANNEL_STATE_OK == status.notificationChannelState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.voicemail_error_activating_title),
          context.getString(R.string.voicemail_error_activating_message),
          VoicemailErrorMessage.createCallVoicemailAction(context));
    }

    if (Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION == status.notificationChannelState) {
      return createNoSignalMessage(context, status);
    }

    if (Status.CONFIGURATION_STATE_FAILED == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.voicemail_error_activation_failed_title),
          context.getString(R.string.voicemail_error_activation_failed_message),
          VoicemailErrorMessage.createCallVoicemailAction(context),
          VoicemailErrorMessage.createRetryAction(context, status));
    }

    if (Status.DATA_CHANNEL_STATE_NO_CONNECTION == status.dataChannelState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.voicemail_error_no_data_title),
          context.getString(R.string.voicemail_error_no_data_message),
          VoicemailErrorMessage.createCallVoicemailAction(context),
          VoicemailErrorMessage.createRetryAction(context, status));
    }

    if (Status.DATA_CHANNEL_STATE_NO_CONNECTION_CELLULAR_REQUIRED == status.dataChannelState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.voicemail_error_no_data_title),
          context.getString(R.string.voicemail_error_no_data_cellular_required_message),
          VoicemailErrorMessage.createCallVoicemailAction(context),
          VoicemailErrorMessage.createRetryAction(context, status));
    }

    if (Status.DATA_CHANNEL_STATE_BAD_CONFIGURATION == status.dataChannelState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.voicemail_error_bad_config_title),
          context.getString(R.string.voicemail_error_bad_config_message),
          VoicemailErrorMessage.createCallVoicemailAction(context),
          VoicemailErrorMessage.createRetryAction(context, status));
    }

    if (Status.DATA_CHANNEL_STATE_COMMUNICATION_ERROR == status.dataChannelState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.voicemail_error_communication_title),
          context.getString(R.string.voicemail_error_communication_message),
          VoicemailErrorMessage.createCallVoicemailAction(context),
          VoicemailErrorMessage.createRetryAction(context, status));
    }

    if (Status.DATA_CHANNEL_STATE_SERVER_ERROR == status.dataChannelState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.voicemail_error_server_title),
          context.getString(R.string.voicemail_error_server_message),
          VoicemailErrorMessage.createCallVoicemailAction(context),
          VoicemailErrorMessage.createRetryAction(context, status));
    }

    if (Status.DATA_CHANNEL_STATE_SERVER_CONNECTION_ERROR == status.dataChannelState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.voicemail_error_server_connection_title),
          context.getString(R.string.voicemail_error_server_connection_message),
          VoicemailErrorMessage.createCallVoicemailAction(context),
          VoicemailErrorMessage.createRetryAction(context, status));
    }

    // This should be an assertion error, but there's a bug in NYC-DR (a bug) that will
    // sometimes give status mixed from multiple SIMs. There's no meaningful message to be displayed
    // from it, so just suppress the message.
    LogUtil.e("OmtpVoicemailMessageCreator.create", "Unhandled status: " + status);
    return null;
  }

  public static boolean isSyncBlockingError(VoicemailStatus status) {
    if (status.notificationChannelState != Status.NOTIFICATION_CHANNEL_STATE_OK) {
      return true;
    }

    if (status.dataChannelState != Status.DATA_CHANNEL_STATE_OK) {
      return true;
    }

    switch (status.configurationState) {
      case Status.CONFIGURATION_STATE_OK:
        // allow activation to be queued again in case it is interrupted
      case Status.CONFIGURATION_STATE_CONFIGURING:
        return false;
      default:
        return true;
    }
  }

  @Nullable
  private static VoicemailErrorMessage checkQuota(
      Context context, VoicemailStatus status, VoicemailStatusReader statusReader) {
    if (status.quotaOccupied != Status.QUOTA_UNAVAILABLE
        && status.quotaTotal != Status.QUOTA_UNAVAILABLE) {
      return createInboxErrorMessage(context, status, statusReader);
    }
    Logger.get(context).logImpression(DialerImpression.Type.VVM_QUOTA_CHECK_UNAVAILABLE);
    return null;
  }

  @Nullable
  private static VoicemailErrorMessage createInboxErrorMessage(
      Context context, VoicemailStatus status, VoicemailStatusReader statusReader) {

    float voicemailOccupiedFraction = (float) status.quotaOccupied / (float) status.quotaTotal;

    if (voicemailOccupiedFraction < QUOTA_NEAR_FULL_THRESHOLD) {
      return null;
    }

    boolean isFull = voicemailOccupiedFraction >= QUOTA_FULL_THRESHOLD;

    PhoneAccountHandle phoneAccountHandle = status.getPhoneAccountHandle();

    PerAccountSharedPreferences sharedPreferenceForAccount =
        new PerAccountSharedPreferences(
            context, phoneAccountHandle, PreferenceManager.getDefaultSharedPreferences(context));

    VoicemailClient voicemailClient = VoicemailComponent.get(context).getVoicemailClient();

    boolean shouldShowPromoForArchive =
        !isPromoForArchiveDismissed(sharedPreferenceForAccount, isFull)
            && !voicemailClient.isVoicemailArchiveEnabled(context, phoneAccountHandle)
            && voicemailClient.isVoicemailArchiveAvailable(context);

    if (!shouldShowPromoForArchive) {
      if (isFull) {
        Logger.get(context)
            .logImpression(DialerImpression.Type.VVM_USER_SHOWN_VM_FULL_ERROR_MESSAGE);
        return new VoicemailErrorMessage(
            context.getString(R.string.voicemail_error_inbox_full_title),
            context.getString(R.string.voicemail_error_inbox_full_message));
      } else {
        Logger.get(context)
            .logImpression(DialerImpression.Type.VVM_USER_SHOWN_VM_ALMOST_FULL_ERROR_MESSAGE);
        return new VoicemailErrorMessage(
            context.getString(R.string.voicemail_error_inbox_near_full_title),
            context.getString(R.string.voicemail_error_inbox_near_full_message));
      }
    }

    String title;
    CharSequence message;
    DialerImpression.Type enabledImpression;
    DialerImpression.Type dismissedImpression;
    String dismissedKey;

    if (isFull) {
      Logger.get(context).logImpression(DialerImpression.Type.VVM_USER_SHOWN_VM_FULL_PROMO);
      title = context.getString(R.string.voicemail_error_inbox_full_turn_archive_on_title);
      message = context.getText(R.string.voicemail_error_inbox_full_turn_archive_on_message);
      enabledImpression = DialerImpression.Type.VVM_USER_ENABLED_ARCHIVE_FROM_VM_FULL_PROMO;
      dismissedImpression = DialerImpression.Type.VVM_USER_DISMISSED_VM_FULL_PROMO;
      dismissedKey = VOICEMAIL_PROMO_DISMISSED_KEY;
    } else {
      Logger.get(context).logImpression(DialerImpression.Type.VVM_USER_SHOWN_VM_ALMOST_FULL_PROMO);
      title = context.getString(R.string.voicemail_error_inbox_almost_full_turn_archive_on_title);
      message = context.getText(R.string.voicemail_error_inbox_almost_full_turn_archive_on_message);
      enabledImpression = DialerImpression.Type.VVM_USER_ENABLED_ARCHIVE_FROM_VM_ALMOST_FULL_PROMO;
      dismissedImpression = DialerImpression.Type.VVM_USER_DISMISSED_VM_ALMOST_FULL_PROMO;
      dismissedKey = VOICEMAIL_PROMO_ALMOST_FULL_DISMISSED_KEY;
    }

    return createVMQuotaPromo(
        context,
        phoneAccountHandle,
        status,
        statusReader,
        voicemailClient,
        sharedPreferenceForAccount,
        title,
        message,
        enabledImpression,
        dismissedImpression,
        dismissedKey);
  }

  private static boolean isPromoForArchiveDismissed(
      PerAccountSharedPreferences sharedPreferenceForAccount, boolean isFull) {
    if (isFull) {
      return sharedPreferenceForAccount.getBoolean(VOICEMAIL_PROMO_DISMISSED_KEY, false);
    } else {
      return sharedPreferenceForAccount.getBoolean(
          VOICEMAIL_PROMO_ALMOST_FULL_DISMISSED_KEY, false);
    }
  }

  private static VoicemailErrorMessage createVMQuotaPromo(
      Context context,
      PhoneAccountHandle phoneAccountHandle,
      VoicemailStatus status,
      VoicemailStatusReader statusReader,
      VoicemailClient voicemailClient,
      PerAccountSharedPreferences sharedPreferenceForAccount,
      String title,
      CharSequence message,
      DialerImpression.Type impressionToLogOnEnable,
      DialerImpression.Type impressionToLogOnDismiss,
      String preferenceKeyToUpdate) {
    return new VoicemailErrorMessage(
        title,
        message,
        VoicemailErrorMessage.createTurnArchiveOnAction(
            context,
            impressionToLogOnEnable,
            status,
            statusReader,
            voicemailClient,
            phoneAccountHandle),
        VoicemailErrorMessage.createDismissTurnArchiveOnAction(
            context,
            impressionToLogOnDismiss,
            statusReader,
            sharedPreferenceForAccount,
            preferenceKeyToUpdate));
  }

  @Nullable
  private static VoicemailErrorMessage createNoSignalMessage(
      Context context, VoicemailStatus status) {
    CharSequence title;
    CharSequence description;
    List<Action> actions = new ArrayList<>();
    if (Status.CONFIGURATION_STATE_OK == status.configurationState) {
      if (Status.DATA_CHANNEL_STATE_NO_CONNECTION_CELLULAR_REQUIRED == status.dataChannelState) {
        title = context.getString(R.string.voicemail_error_no_signal_title);
        description =
            context.getString(R.string.voicemail_error_no_signal_cellular_required_message);
      } else {
        title = context.getString(R.string.voicemail_error_no_signal_title);
        if (status.isAirplaneMode) {
          description = context.getString(R.string.voicemail_error_no_signal_airplane_mode_message);
        } else {
          description = context.getString(R.string.voicemail_error_no_signal_message);
        }
        actions.add(VoicemailErrorMessage.createSyncAction(context, status));
      }
    } else {
      title = context.getString(R.string.voicemail_error_not_activate_no_signal_title);
      if (status.isAirplaneMode) {
        description =
            context.getString(
                R.string.voicemail_error_not_activate_no_signal_airplane_mode_message);
      } else {
        description = context.getString(R.string.voicemail_error_not_activate_no_signal_message);
        actions.add(VoicemailErrorMessage.createRetryAction(context, status));
      }
    }
    if (status.isAirplaneMode) {
      actions.add(VoicemailErrorMessage.createChangeAirplaneModeAction(context));
    }
    return new VoicemailErrorMessage(title, description, actions);
  }
}
