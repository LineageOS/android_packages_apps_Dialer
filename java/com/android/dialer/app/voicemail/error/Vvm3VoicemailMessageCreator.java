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

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.VisibleForTesting;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.View.OnClickListener;
import com.android.contacts.common.compat.TelephonyManagerCompat;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.app.voicemail.error.VoicemailErrorMessage.Action;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.VoicemailComponent;
import java.util.Locale;

/**
 * Create error message from {@link VoicemailStatus} for VVM3 visual voicemail. VVM3 is used only by
 * Verizon Wireless.
 */
@RequiresApi(VERSION_CODES.N_MR1)
public class Vvm3VoicemailMessageCreator {

  // Copied from com.android.phone.vvm.omtp.protocol.Vvm3EventHandler
  // TODO(b/28380841): unbundle VVM client so we can access these values directly
  public static final int VMS_DNS_FAILURE = -9001;
  public static final int VMG_DNS_FAILURE = -9002;
  public static final int SPG_DNS_FAILURE = -9003;
  public static final int VMS_NO_CELLULAR = -9004;
  public static final int VMG_NO_CELLULAR = -9005;
  public static final int SPG_NO_CELLULAR = -9006;
  public static final int VMS_TIMEOUT = -9007;
  public static final int VMG_TIMEOUT = -9008;
  public static final int STATUS_SMS_TIMEOUT = -9009;

  public static final int SUBSCRIBER_BLOCKED = -9990;
  public static final int UNKNOWN_USER = -9991;
  public static final int UNKNOWN_DEVICE = -9992;
  public static final int INVALID_PASSWORD = -9993;
  public static final int MAILBOX_NOT_INITIALIZED = -9994;
  public static final int SERVICE_NOT_PROVISIONED = -9995;
  public static final int SERVICE_NOT_ACTIVATED = -9996;
  public static final int USER_BLOCKED = -9998;
  public static final int IMAP_GETQUOTA_ERROR = -9997;
  public static final int IMAP_SELECT_ERROR = -9989;
  public static final int IMAP_ERROR = -9999;

  public static final int VMG_INTERNAL_ERROR = -101;
  public static final int VMG_DB_ERROR = -102;
  public static final int VMG_COMMUNICATION_ERROR = -103;
  public static final int SPG_URL_NOT_FOUND = -301;

  // Non VVM3 codes:
  public static final int VMG_UNKNOWN_ERROR = -1;
  public static final int PIN_NOT_SET = -100;
  public static final int SUBSCRIBER_UNKNOWN = -99;

  private static final String ISO639_SPANISH = "es";
  @VisibleForTesting static final String VVM3_TOS_ACCEPTANCE_FLAG_KEY = "vvm3_tos_acceptance_flag";

  @Nullable
  public static VoicemailErrorMessage create(
      final Context context,
      final VoicemailStatus status,
      final VoicemailStatusReader statusReader) {
    VoicemailErrorMessage tosMessage = maybeShowTosMessage(context, status, statusReader);
    if (tosMessage != null) {
      return tosMessage;
    }

    if (VMS_DNS_FAILURE == status.dataChannelState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_vms_dns_failure_title),
          getCustomerSupportString(context, R.string.vvm3_error_vms_dns_failure_message),
          VoicemailErrorMessage.createRetryAction(context, status),
          createCallCustomerSupportAction(context));
    }

    if (VMG_DNS_FAILURE == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_vmg_dns_failure_title),
          getCustomerSupportString(context, R.string.vvm3_error_vmg_dns_failure_message),
          VoicemailErrorMessage.createRetryAction(context, status),
          createCallCustomerSupportAction(context));
    }

    if (SPG_DNS_FAILURE == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_spg_dns_failure_title),
          getCustomerSupportString(context, R.string.vvm3_error_spg_dns_failure_message),
          VoicemailErrorMessage.createRetryAction(context, status),
          createCallCustomerSupportAction(context));
    }

    if (VMS_NO_CELLULAR == status.dataChannelState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_vms_no_cellular_title),
          getCustomerSupportString(context, R.string.vvm3_error_vms_no_cellular_message),
          VoicemailErrorMessage.createRetryAction(context, status),
          createCallCustomerSupportAction(context));
    }

    if (VMG_NO_CELLULAR == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_vmg_no_cellular_title),
          getCustomerSupportString(context, R.string.vvm3_error_vmg_no_cellular_message),
          VoicemailErrorMessage.createRetryAction(context, status),
          createCallCustomerSupportAction(context));
    }

    if (SPG_NO_CELLULAR == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_spg_no_cellular_title),
          getCustomerSupportString(context, R.string.vvm3_error_spg_no_cellular_message),
          VoicemailErrorMessage.createRetryAction(context, status),
          createCallCustomerSupportAction(context));
    }

    if (VMS_TIMEOUT == status.dataChannelState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_vms_timeout_title),
          getCustomerSupportString(context, R.string.vvm3_error_vms_timeout_message),
          VoicemailErrorMessage.createRetryAction(context, status),
          createCallCustomerSupportAction(context));
    }

    if (VMG_TIMEOUT == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_vmg_timeout_title),
          getCustomerSupportString(context, R.string.vvm3_error_vmg_timeout_message),
          VoicemailErrorMessage.createRetryAction(context, status),
          createCallCustomerSupportAction(context));
    }

    if (STATUS_SMS_TIMEOUT == status.notificationChannelState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_status_sms_timeout_title),
          getCustomerSupportString(context, R.string.vvm3_error_status_sms_timeout_message),
          VoicemailErrorMessage.createRetryAction(context, status),
          createCallCustomerSupportAction(context));
    }

    if (SUBSCRIBER_BLOCKED == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_subscriber_blocked_title),
          getCustomerSupportString(context, R.string.vvm3_error_subscriber_blocked_message),
          VoicemailErrorMessage.createRetryAction(context, status),
          createCallCustomerSupportAction(context));
    }

    if (UNKNOWN_USER == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_unknown_user_title),
          getCustomerSupportString(context, R.string.vvm3_error_unknown_user_message),
          VoicemailErrorMessage.createCallVoicemailAction(context),
          createCallCustomerSupportAction(context));
    }

    if (UNKNOWN_DEVICE == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_unknown_device_title),
          getCustomerSupportString(context, R.string.vvm3_error_unknown_device_message),
          VoicemailErrorMessage.createCallVoicemailAction(context),
          createCallCustomerSupportAction(context));
    }

    if (INVALID_PASSWORD == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_invalid_password_title),
          getCustomerSupportString(context, R.string.vvm3_error_invalid_password_message),
          VoicemailErrorMessage.createCallVoicemailAction(context),
          createCallCustomerSupportAction(context));
    }

    if (MAILBOX_NOT_INITIALIZED == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_mailbox_not_initialized_title),
          getCustomerSupportString(context, R.string.vvm3_error_mailbox_not_initialized_message),
          createCallCustomerSupportAction(context));
    }

    if (SERVICE_NOT_PROVISIONED == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_service_not_provisioned_title),
          getCustomerSupportString(context, R.string.vvm3_error_service_not_provisioned_message),
          createCallCustomerSupportAction(context));
    }

    if (SERVICE_NOT_ACTIVATED == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_service_not_activated_title),
          getCustomerSupportString(context, R.string.vvm3_error_service_not_activated_message),
          createCallCustomerSupportAction(context));
    }

    if (USER_BLOCKED == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_user_blocked_title),
          getCustomerSupportString(context, R.string.vvm3_error_user_blocked_message),
          createCallCustomerSupportAction(context));
    }

    if (SUBSCRIBER_UNKNOWN == status.configurationState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_subscriber_unknown_title),
          getCustomerSupportString(context, R.string.vvm3_error_subscriber_unknown_message),
          VoicemailErrorMessage.createCallVoicemailAction(context),
          createCallCustomerSupportAction(context));
    }

    if (IMAP_GETQUOTA_ERROR == status.dataChannelState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_imap_getquota_error_title),
          getCustomerSupportString(context, R.string.vvm3_error_imap_getquota_error_message),
          VoicemailErrorMessage.createCallVoicemailAction(context),
          createCallCustomerSupportAction(context));
    }

    if (IMAP_SELECT_ERROR == status.dataChannelState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_imap_select_error_title),
          getCustomerSupportString(context, R.string.vvm3_error_imap_select_error_message),
          VoicemailErrorMessage.createCallVoicemailAction(context),
          createCallCustomerSupportAction(context));
    }

    if (IMAP_ERROR == status.dataChannelState) {
      return new VoicemailErrorMessage(
          context.getString(R.string.vvm3_error_imap_error_title),
          getCustomerSupportString(context, R.string.vvm3_error_imap_error_message),
          VoicemailErrorMessage.createCallVoicemailAction(context),
          createCallCustomerSupportAction(context));
    }

    if (PIN_NOT_SET == status.configurationState) {
      Logger.get(context).logImpression(DialerImpression.Type.VOICEMAIL_ALERT_SET_PIN_SHOWN);
      return new VoicemailErrorMessage(
          context.getString(R.string.voicemail_error_pin_not_set_title),
          getCustomerSupportString(context, R.string.voicemail_error_pin_not_set_message),
          VoicemailErrorMessage.createSetPinAction(context, status.getPhoneAccountHandle()));
    }

    return OmtpVoicemailMessageCreator.create(context, status, statusReader);
  }

  @NonNull
  private static CharSequence getCustomerSupportString(Context context, int id) {
    // TODO: get number based on the country the user is currently in.
    return ContactDisplayUtils.getTtsSpannedPhoneNumber(
        context.getResources(),
        id,
        context.getString(R.string.verizon_domestic_customer_support_display_number));
  }

  @NonNull
  private static Action createCallCustomerSupportAction(final Context context) {
    return new Action(
        context.getString(R.string.voicemail_action_call_customer_support),
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            Intent intent =
                new Intent(
                    Intent.ACTION_CALL,
                    Uri.parse(
                        "tel:"
                            + context.getString(
                                R.string.verizon_domestic_customer_support_number)));
            context.startActivity(intent);
          }
        });
  }

  @Nullable
  private static VoicemailErrorMessage maybeShowTosMessage(
      final Context context,
      final VoicemailStatus status,
      final VoicemailStatusReader statusReader) {
    final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    if (preferences.getBoolean(VVM3_TOS_ACCEPTANCE_FLAG_KEY, false)) {
      return null;
    }
    Logger.get(context).logImpression(DialerImpression.Type.VOICEMAIL_VVM3_TOS_SHOWN);

    CharSequence termsAndConditions;
    CharSequence acceptText;
    CharSequence declineText;
    // TODO(b/29082671): use LocaleList
    if (Locale.getDefault().getLanguage().equals(new Locale(ISO639_SPANISH).getLanguage())) {
      // Spanish
      termsAndConditions = context.getString(R.string.verizon_terms_and_conditions_1_1_spanish);
      acceptText = context.getString(R.string.verizon_terms_and_conditions_accept_spanish);
      declineText = context.getString(R.string.verizon_terms_and_conditions_decline_spanish);
    } else {
      termsAndConditions = context.getString(R.string.verizon_terms_and_conditions_1_1_english);
      acceptText = context.getString(R.string.verizon_terms_and_conditions_accept_english);
      declineText = context.getString(R.string.verizon_terms_and_conditions_decline_english);
    }

    return new VoicemailTosMessage(
            context.getString(R.string.verizon_terms_and_conditions_title),
            context.getString(R.string.verizon_terms_and_conditions_message, termsAndConditions),
            new Action(
                declineText,
                new OnClickListener() {
                  @Override
                  public void onClick(View v) {
                    LogUtil.i("Vvm3VoicemailMessageCreator.maybeShowTosMessage", "decline clicked");
                    PhoneAccountHandle handle =
                        new PhoneAccountHandle(
                            ComponentName.unflattenFromString(status.phoneAccountComponentName),
                            status.phoneAccountId);
                    Logger.get(context)
                        .logImpression(DialerImpression.Type.VOICEMAIL_VVM3_TOS_DECLINE_CLICKED);
                    showDeclineTosDialog(context, handle, status);
                  }
                }),
            new Action(
                acceptText,
                new OnClickListener() {
                  @Override
                  public void onClick(View v) {
                    LogUtil.i("Vvm3VoicemailMessageCreator.maybeShowTosMessage", "accept clicked");
                    preferences.edit().putBoolean(VVM3_TOS_ACCEPTANCE_FLAG_KEY, true).apply();
                    Logger.get(context)
                        .logImpression(DialerImpression.Type.VOICEMAIL_VVM3_TOS_ACCEPTED);
                    statusReader.refresh();
                  }
                },
                true /* raised */))
        .setModal(true);
  }

  private static void showDeclineTosDialog(
      final Context context, final PhoneAccountHandle handle, VoicemailStatus status) {
    if (PIN_NOT_SET == status.configurationState) {
      LogUtil.i(
          "Vvm3VoicemailMessageCreator.showDeclineTosDialog",
          "PIN_NOT_SET, showing set PIN dialog");
      showSetPinBeforeDeclineDialog(context);
      return;
    }
    LogUtil.i(
        "Vvm3VoicemailMessageCreator.showDeclineTosDialog",
        "showing decline ToS dialog, status=" + status);
    final TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setMessage(R.string.verizon_terms_and_conditions_decline_dialog_message);
    builder.setPositiveButton(
        R.string.verizon_terms_and_conditions_decline_dialog_downgrade,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Logger.get(context).logImpression(DialerImpression.Type.VOICEMAIL_VVM3_TOS_DECLINED);
            VoicemailClient voicemailClient = VoicemailComponent.get(context).getVoicemailClient();
            if (voicemailClient.isVoicemailModuleEnabled()) {
              voicemailClient.setVoicemailEnabled(context, status.getPhoneAccountHandle(), false);
            } else {
              TelephonyManagerCompat.setVisualVoicemailEnabled(telephonyManager, handle, false);
            }
          }
        });

    builder.setNegativeButton(
        android.R.string.cancel,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
          }
        });

    builder.setCancelable(true);
    builder.show();
  }

  private static void showSetPinBeforeDeclineDialog(final Context context) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setMessage(R.string.verizon_terms_and_conditions_decline_set_pin_dialog_message);
    builder.setPositiveButton(
        R.string.verizon_terms_and_conditions_decline_set_pin_dialog_set_pin,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Logger.get(context)
                .logImpression(DialerImpression.Type.VOICEMAIL_VVM3_TOS_DECLINE_CHANGE_PIN_SHOWN);
            Intent intent = new Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL);
            context.startActivity(intent);
          }
        });

    builder.setNegativeButton(
        android.R.string.cancel,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
          }
        });

    builder.setCancelable(true);
    builder.show();
  }
}
