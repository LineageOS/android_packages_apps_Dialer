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

package com.android.voicemail.impl;

import android.content.Context;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Status;
import com.android.voicemail.impl.OmtpEvents.Type;

public class DefaultOmtpEventHandler {

  private static final String TAG = "DefErrorCodeHandler";

  public static void handleEvent(
      Context context,
      OmtpVvmCarrierConfigHelper config,
      VoicemailStatus.Editor status,
      OmtpEvents event) {
    switch (event.getType()) {
      case Type.CONFIGURATION:
        handleConfigurationEvent(context, status, event);
        break;
      case Type.DATA_CHANNEL:
        handleDataChannelEvent(context, status, event);
        break;
      case Type.NOTIFICATION_CHANNEL:
        handleNotificationChannelEvent(context, config, status, event);
        break;
      case Type.OTHER:
        handleOtherEvent(context, status, event);
        break;
      default:
        VvmLog.wtf(TAG, "invalid event type " + event.getType() + " for " + event);
    }
  }

  private static void handleConfigurationEvent(
      Context context, VoicemailStatus.Editor status, OmtpEvents event) {
    switch (event) {
      case CONFIG_DEFAULT_PIN_REPLACED:
      case CONFIG_REQUEST_STATUS_SUCCESS:
      case CONFIG_PIN_SET:
        status
            .setConfigurationState(VoicemailContract.Status.CONFIGURATION_STATE_OK)
            .setNotificationChannelState(Status.NOTIFICATION_CHANNEL_STATE_OK)
            .apply();
        break;
      case CONFIG_ACTIVATING:
        // Wipe all errors from the last activation. All errors shown should be new errors
        // for this activation.
        status
            .setConfigurationState(Status.CONFIGURATION_STATE_CONFIGURING)
            .setNotificationChannelState(Status.NOTIFICATION_CHANNEL_STATE_OK)
            .setDataChannelState(Status.DATA_CHANNEL_STATE_OK)
            .apply();
        break;
      case CONFIG_ACTIVATING_SUBSEQUENT:
        status
            .setConfigurationState(Status.CONFIGURATION_STATE_OK)
            .setNotificationChannelState(Status.NOTIFICATION_CHANNEL_STATE_OK)
            .setDataChannelState(Status.DATA_CHANNEL_STATE_OK)
            .apply();
        break;
      case CONFIG_SERVICE_NOT_AVAILABLE:
        status.setConfigurationState(Status.CONFIGURATION_STATE_FAILED).apply();
        break;
      case CONFIG_STATUS_SMS_TIME_OUT:
        status.setConfigurationState(Status.CONFIGURATION_STATE_FAILED).apply();
        break;
      default:
        VvmLog.wtf(TAG, "invalid configuration event " + event);
    }
  }

  private static void handleDataChannelEvent(
      Context context, VoicemailStatus.Editor status, OmtpEvents event) {
    switch (event) {
      case DATA_IMAP_OPERATION_STARTED:
      case DATA_IMAP_OPERATION_COMPLETED:
        status.setDataChannelState(Status.DATA_CHANNEL_STATE_OK).apply();
        break;

      case DATA_NO_CONNECTION:
        status.setDataChannelState(Status.DATA_CHANNEL_STATE_NO_CONNECTION).apply();
        break;

      case DATA_NO_CONNECTION_CELLULAR_REQUIRED:
        status
            .setDataChannelState(Status.DATA_CHANNEL_STATE_NO_CONNECTION_CELLULAR_REQUIRED)
            .apply();
        break;
      case DATA_INVALID_PORT:
        status
            .setDataChannelState(VoicemailContract.Status.DATA_CHANNEL_STATE_BAD_CONFIGURATION)
            .apply();
        break;
      case DATA_CANNOT_RESOLVE_HOST_ON_NETWORK:
        status
            .setDataChannelState(
                VoicemailContract.Status.DATA_CHANNEL_STATE_SERVER_CONNECTION_ERROR)
            .apply();
        break;
      case DATA_SSL_INVALID_HOST_NAME:
      case DATA_CANNOT_ESTABLISH_SSL_SESSION:
      case DATA_IOE_ON_OPEN:
      case DATA_GENERIC_IMAP_IOE:
        status
            .setDataChannelState(VoicemailContract.Status.DATA_CHANNEL_STATE_COMMUNICATION_ERROR)
            .apply();
        break;
      case DATA_BAD_IMAP_CREDENTIAL:
      case DATA_AUTH_UNKNOWN_USER:
      case DATA_AUTH_UNKNOWN_DEVICE:
      case DATA_AUTH_INVALID_PASSWORD:
      case DATA_AUTH_MAILBOX_NOT_INITIALIZED:
      case DATA_AUTH_SERVICE_NOT_PROVISIONED:
      case DATA_AUTH_SERVICE_NOT_ACTIVATED:
      case DATA_AUTH_USER_IS_BLOCKED:
        status
            .setDataChannelState(VoicemailContract.Status.DATA_CHANNEL_STATE_BAD_CONFIGURATION)
            .apply();
        break;

      case DATA_REJECTED_SERVER_RESPONSE:
      case DATA_INVALID_INITIAL_SERVER_RESPONSE:
      case DATA_MAILBOX_OPEN_FAILED:
      case DATA_SSL_EXCEPTION:
      case DATA_ALL_SOCKET_CONNECTION_FAILED:
        status
            .setDataChannelState(VoicemailContract.Status.DATA_CHANNEL_STATE_SERVER_ERROR)
            .apply();
        break;

      default:
        VvmLog.wtf(TAG, "invalid data channel event " + event);
    }
  }

  private static void handleNotificationChannelEvent(
      Context context,
      OmtpVvmCarrierConfigHelper config,
      VoicemailStatus.Editor status,
      OmtpEvents event) {
    switch (event) {
      case NOTIFICATION_IN_SERVICE:
        status
            .setNotificationChannelState(Status.NOTIFICATION_CHANNEL_STATE_OK)
            // Clear the error state. A sync should follow signal return so any error
            // will be reposted.
            .setDataChannelState(Status.DATA_CHANNEL_STATE_OK)
            .apply();
        break;
      case NOTIFICATION_SERVICE_LOST:
        status.setNotificationChannelState(Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION);
        if (config.isCellularDataRequired()) {
          status.setDataChannelState(Status.DATA_CHANNEL_STATE_NO_CONNECTION_CELLULAR_REQUIRED);
        }
        status.apply();
        break;
      default:
        VvmLog.wtf(TAG, "invalid notification channel event " + event);
    }
  }

  private static void handleOtherEvent(
      Context context, VoicemailStatus.Editor status, OmtpEvents event) {
    switch (event) {
      case OTHER_SOURCE_REMOVED:
        status
            .setConfigurationState(Status.CONFIGURATION_STATE_NOT_CONFIGURED)
            .setNotificationChannelState(Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION)
            .setDataChannelState(Status.DATA_CHANNEL_STATE_NO_CONNECTION)
            .apply();
        break;
      default:
        VvmLog.wtf(TAG, "invalid other event " + event);
    }
  }
}
