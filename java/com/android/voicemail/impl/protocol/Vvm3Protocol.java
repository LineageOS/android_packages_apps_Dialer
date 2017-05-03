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

package com.android.voicemail.impl.protocol;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.net.Network;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import com.android.dialer.logging.DialerImpression;
import com.android.voicemail.impl.ActivationTask;
import com.android.voicemail.impl.OmtpConstants;
import com.android.voicemail.impl.OmtpEvents;
import com.android.voicemail.impl.OmtpVvmCarrierConfigHelper;
import com.android.voicemail.impl.VisualVoicemailPreferences;
import com.android.voicemail.impl.VoicemailStatus;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.imap.ImapHelper;
import com.android.voicemail.impl.imap.ImapHelper.InitializingException;
import com.android.voicemail.impl.mail.MessagingException;
import com.android.voicemail.impl.settings.VisualVoicemailSettingsUtil;
import com.android.voicemail.impl.settings.VoicemailChangePinActivity;
import com.android.voicemail.impl.sms.OmtpMessageSender;
import com.android.voicemail.impl.sms.StatusMessage;
import com.android.voicemail.impl.sms.Vvm3MessageSender;
import com.android.voicemail.impl.sync.VvmNetworkRequest;
import com.android.voicemail.impl.sync.VvmNetworkRequest.NetworkWrapper;
import com.android.voicemail.impl.sync.VvmNetworkRequest.RequestFailedException;
import com.android.voicemail.impl.utils.LoggerUtils;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * A flavor of OMTP protocol with a different provisioning process
 *
 * <p>Used by carriers such as Verizon Wireless
 */
@TargetApi(VERSION_CODES.O)
public class Vvm3Protocol extends VisualVoicemailProtocol {

  private static final String TAG = "Vvm3Protocol";

  private static final String SMS_EVENT_UNRECOGNIZED = "UNRECOGNIZED";
  private static final String SMS_EVENT_UNRECOGNIZED_CMD = "cmd";
  private static final String SMS_EVENT_UNRECOGNIZED_STATUS = "STATUS";
  private static final String DEFAULT_VMG_URL_KEY = "default_vmg_url";

  private static final String IMAP_CHANGE_TUI_PWD_FORMAT = "CHANGE_TUI_PWD PWD=%1$s OLD_PWD=%2$s";
  private static final String IMAP_CHANGE_VM_LANG_FORMAT = "CHANGE_VM_LANG Lang=%1$s";
  private static final String IMAP_CLOSE_NUT = "CLOSE_NUT";

  private static final String ISO639_Spanish = "es";

  /**
   * For VVM3, if the STATUS SMS returns {@link StatusMessage#getProvisioningStatus()} of {@link
   * OmtpConstants#SUBSCRIBER_UNKNOWN} and {@link StatusMessage#getReturnCode()} of this value, the
   * user can self-provision visual voicemail service. For other response codes, the user must
   * contact customer support to resolve the issue.
   */
  private static final String VVM3_UNKNOWN_SUBSCRIBER_CAN_SUBSCRIBE_RESPONSE_CODE = "2";

  // Default prompt level when using the telephone user interface.
  // Standard prompt when the user call into the voicemail, and no prompts when someone else is
  // leaving a voicemail.
  private static final String VVM3_VM_LANGUAGE_ENGLISH_STANDARD_NO_GUEST_PROMPTS = "5";
  private static final String VVM3_VM_LANGUAGE_SPANISH_STANDARD_NO_GUEST_PROMPTS = "6";

  private static final int DEFAULT_PIN_LENGTH = 6;

  @Override
  public void startActivation(
      OmtpVvmCarrierConfigHelper config, @Nullable PendingIntent sentIntent) {
    // VVM3 does not support activation SMS.
    // Send a status request which will start the provisioning process if the user is not
    // provisioned.
    VvmLog.i(TAG, "Activating");
    config.requestStatus(sentIntent);
  }

  @Override
  public void startDeactivation(OmtpVvmCarrierConfigHelper config) {
    // VVM3 does not support deactivation.
    // do nothing.
  }

  @Override
  public boolean supportsProvisioning() {
    return true;
  }

  @Override
  public void startProvisioning(
      ActivationTask task,
      PhoneAccountHandle phoneAccountHandle,
      OmtpVvmCarrierConfigHelper config,
      VoicemailStatus.Editor status,
      StatusMessage message,
      Bundle data) {
    VvmLog.i(TAG, "start vvm3 provisioning");
    LoggerUtils.logImpressionOnMainThread(
        config.getContext(), DialerImpression.Type.VVM_PROVISIONING_STARTED);
    if (OmtpConstants.SUBSCRIBER_UNKNOWN.equals(message.getProvisioningStatus())) {
      VvmLog.i(TAG, "Provisioning status: Unknown");
      if (VVM3_UNKNOWN_SUBSCRIBER_CAN_SUBSCRIBE_RESPONSE_CODE.equals(message.getReturnCode())) {
        VvmLog.i(TAG, "Self provisioning available, subscribing");
        new Vvm3Subscriber(task, phoneAccountHandle, config, status, data).subscribe();
      } else {
        config.handleEvent(status, OmtpEvents.VVM3_SUBSCRIBER_UNKNOWN);
      }
    } else if (OmtpConstants.SUBSCRIBER_NEW.equals(message.getProvisioningStatus())) {
      VvmLog.i(TAG, "setting up new user");
      // Save the IMAP credentials in preferences so they are persistent and can be retrieved.
      VisualVoicemailPreferences prefs =
          new VisualVoicemailPreferences(config.getContext(), phoneAccountHandle);
      message.putStatus(prefs.edit()).apply();

      startProvisionNewUser(task, phoneAccountHandle, config, status, message);
    } else if (OmtpConstants.SUBSCRIBER_PROVISIONED.equals(message.getProvisioningStatus())) {
      VvmLog.i(TAG, "User provisioned but not activated, disabling VVM");
      VisualVoicemailSettingsUtil.setEnabled(config.getContext(), phoneAccountHandle, false);
    } else if (OmtpConstants.SUBSCRIBER_BLOCKED.equals(message.getProvisioningStatus())) {
      VvmLog.i(TAG, "User blocked");
      config.handleEvent(status, OmtpEvents.VVM3_SUBSCRIBER_BLOCKED);
    }
  }

  @Override
  public OmtpMessageSender createMessageSender(
      Context context,
      PhoneAccountHandle phoneAccountHandle,
      short applicationPort,
      String destinationNumber) {
    return new Vvm3MessageSender(context, phoneAccountHandle, applicationPort, destinationNumber);
  }

  @Override
  public void handleEvent(
      Context context,
      OmtpVvmCarrierConfigHelper config,
      VoicemailStatus.Editor status,
      OmtpEvents event) {
    Vvm3EventHandler.handleEvent(context, config, status, event);
  }

  @Override
  public String getCommand(String command) {
    switch (command) {
      case OmtpConstants.IMAP_CHANGE_TUI_PWD_FORMAT:
        return IMAP_CHANGE_TUI_PWD_FORMAT;
      case OmtpConstants.IMAP_CLOSE_NUT:
        return IMAP_CLOSE_NUT;
      case OmtpConstants.IMAP_CHANGE_VM_LANG_FORMAT:
        return IMAP_CHANGE_VM_LANG_FORMAT;
      default:
        return super.getCommand(command);
    }
  }

  @Override
  public Bundle translateStatusSmsBundle(
      OmtpVvmCarrierConfigHelper config, String event, Bundle data) {
    // UNRECOGNIZED?cmd=STATUS is the response of a STATUS request when the user is provisioned
    // with iPhone visual voicemail without VoLTE. Translate it into an unprovisioned status
    // so provisioning can be done.
    if (!SMS_EVENT_UNRECOGNIZED.equals(event)) {
      return null;
    }
    if (!SMS_EVENT_UNRECOGNIZED_STATUS.equals(data.getString(SMS_EVENT_UNRECOGNIZED_CMD))) {
      return null;
    }
    Bundle bundle = new Bundle();
    bundle.putString(OmtpConstants.PROVISIONING_STATUS, OmtpConstants.SUBSCRIBER_UNKNOWN);
    bundle.putString(
        OmtpConstants.RETURN_CODE, VVM3_UNKNOWN_SUBSCRIBER_CAN_SUBSCRIBE_RESPONSE_CODE);
    String vmgUrl = config.getString(DEFAULT_VMG_URL_KEY);
    if (TextUtils.isEmpty(vmgUrl)) {
      VvmLog.e(TAG, "Unable to translate STATUS SMS: VMG URL is not set in config");
      return null;
    }
    bundle.putString(Vvm3Subscriber.VMG_URL_KEY, vmgUrl);
    VvmLog.i(TAG, "UNRECOGNIZED?cmd=STATUS translated into unprovisioned STATUS SMS");
    return bundle;
  }

  private void startProvisionNewUser(
      ActivationTask task,
      PhoneAccountHandle phoneAccountHandle,
      OmtpVvmCarrierConfigHelper config,
      VoicemailStatus.Editor status,
      StatusMessage message) {
    try (NetworkWrapper wrapper =
        VvmNetworkRequest.getNetwork(config, phoneAccountHandle, status)) {
      Network network = wrapper.get();

      VvmLog.i(TAG, "new user: network available");
      try (ImapHelper helper =
          new ImapHelper(config.getContext(), phoneAccountHandle, network, status)) {
        // VVM3 has inconsistent error language code to OMTP. Just issue a raw command
        // here.
        // TODO(b/29082671): use LocaleList
        if (Locale.getDefault().getLanguage().equals(new Locale(ISO639_Spanish).getLanguage())) {
          // Spanish
          helper.changeVoicemailTuiLanguage(VVM3_VM_LANGUAGE_SPANISH_STANDARD_NO_GUEST_PROMPTS);
        } else {
          // English
          helper.changeVoicemailTuiLanguage(VVM3_VM_LANGUAGE_ENGLISH_STANDARD_NO_GUEST_PROMPTS);
        }
        VvmLog.i(TAG, "new user: language set");

        if (setPin(config.getContext(), phoneAccountHandle, helper, message)) {
          // Only close new user tutorial if the PIN has been changed.
          helper.closeNewUserTutorial();
          VvmLog.i(TAG, "new user: NUT closed");
          LoggerUtils.logImpressionOnMainThread(
              config.getContext(), DialerImpression.Type.VVM_PROVISIONING_COMPLETED);
          config.requestStatus(null);
        }
      } catch (InitializingException | MessagingException | IOException e) {
        config.handleEvent(status, OmtpEvents.VVM3_NEW_USER_SETUP_FAILED);
        task.fail();
        VvmLog.e(TAG, e.toString());
      }
    } catch (RequestFailedException e) {
      config.handleEvent(status, OmtpEvents.DATA_NO_CONNECTION_CELLULAR_REQUIRED);
      task.fail();
    }
  }

  private static boolean setPin(
      Context context,
      PhoneAccountHandle phoneAccountHandle,
      ImapHelper helper,
      StatusMessage message)
      throws IOException, MessagingException {
    String defaultPin = getDefaultPin(message);
    if (defaultPin == null) {
      VvmLog.i(TAG, "cannot generate default PIN");
      return false;
    }

    if (VoicemailChangePinActivity.isDefaultOldPinSet(context, phoneAccountHandle)) {
      // The pin was already set
      VvmLog.i(TAG, "PIN already set");
      return true;
    }
    String newPin = generatePin(getMinimumPinLength(context, phoneAccountHandle));
    if (helper.changePin(defaultPin, newPin) == OmtpConstants.CHANGE_PIN_SUCCESS) {
      VoicemailChangePinActivity.setDefaultOldPIN(context, phoneAccountHandle, newPin);
      helper.handleEvent(OmtpEvents.CONFIG_DEFAULT_PIN_REPLACED);
    }
    VvmLog.i(TAG, "new user: PIN set");
    return true;
  }

  @Nullable
  private static String getDefaultPin(StatusMessage message) {
    // The IMAP username is [phone number]@example.com
    String username = message.getImapUserName();
    try {
      String number = username.substring(0, username.indexOf('@'));
      if (number.length() < 4) {
        VvmLog.e(TAG, "unable to extract number from IMAP username");
        return null;
      }
      return "1" + number.substring(number.length() - 4);
    } catch (StringIndexOutOfBoundsException e) {
      VvmLog.e(TAG, "unable to extract number from IMAP username");
      return null;
    }
  }

  private static int getMinimumPinLength(Context context, PhoneAccountHandle phoneAccountHandle) {
    VisualVoicemailPreferences preferences =
        new VisualVoicemailPreferences(context, phoneAccountHandle);
    // The OMTP pin length format is {min}-{max}
    String[] lengths = preferences.getString(OmtpConstants.TUI_PASSWORD_LENGTH, "").split("-");
    if (lengths.length == 2) {
      try {
        return Integer.parseInt(lengths[0]);
      } catch (NumberFormatException e) {
        return DEFAULT_PIN_LENGTH;
      }
    }
    return DEFAULT_PIN_LENGTH;
  }

  private static String generatePin(int length) {
    SecureRandom random = new SecureRandom();
    return String.format(Locale.US, "%010d", Math.abs(random.nextLong())).substring(0, length);
  }
}
