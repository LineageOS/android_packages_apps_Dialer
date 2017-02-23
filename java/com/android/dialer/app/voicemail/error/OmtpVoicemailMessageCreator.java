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

package com.android.dialer.app.voicemail.error;

import android.content.Context;
import android.provider.VoicemailContract.Status;
import android.support.annotation.Nullable;
import com.android.dialer.app.voicemail.error.VoicemailErrorMessage.Action;
import com.android.dialer.common.LogUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * Create error message from {@link VoicemailStatus} for OMTP visual voicemail. This is also the
 * default behavior if other message creator does not handle the status.
 */
public class OmtpVoicemailMessageCreator {

  private static final float QUOTA_NEAR_FULL_THRESHOLD = 0.9f;
  private static final float QUOTA_FULL_THRESHOLD = 0.99f;

  @Nullable
  public static VoicemailErrorMessage create(Context context, VoicemailStatus status) {
    if (Status.CONFIGURATION_STATE_OK == status.configurationState
        && Status.DATA_CHANNEL_STATE_OK == status.dataChannelState
        && Status.NOTIFICATION_CHANNEL_STATE_OK == status.notificationChannelState) {

      return checkQuota(context, status);
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

    // This should be an assertion error, but there's a bug in NYC-DR (b/31069259) that will
    // sometimes give status mixed from multiple SIMs. There's no meaningful message to be displayed
    // from it, so just suppress the message.
    LogUtil.e("OmtpVoicemailMessageCreator.create", "Unhandled status: " + status);
    return null;
  }

  @Nullable
  private static VoicemailErrorMessage checkQuota(Context context, VoicemailStatus status) {
    if (status.quotaOccupied != Status.QUOTA_UNAVAILABLE
        && status.quotaTotal != Status.QUOTA_UNAVAILABLE) {
      if ((float) status.quotaOccupied / (float) status.quotaTotal >= QUOTA_FULL_THRESHOLD) {
        return new VoicemailErrorMessage(
            context.getString(R.string.voicemail_error_inbox_full_title),
            context.getString(R.string.voicemail_error_inbox_full_message));
      }

      if ((float) status.quotaOccupied / (float) status.quotaTotal >= QUOTA_NEAR_FULL_THRESHOLD) {
        return new VoicemailErrorMessage(
            context.getString(R.string.voicemail_error_inbox_near_full_title),
            context.getString(R.string.voicemail_error_inbox_near_full_message));
      }
    }
    return null;
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
