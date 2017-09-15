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

package com.android.dialer.app.voicemail.error;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AlignmentSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.view.View.OnClickListener;
import com.android.dialer.app.voicemail.error.VoicemailErrorMessage.Action;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.voicemail.VisualVoicemailTypeExtensions;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.VoicemailComponent;
import java.util.Locale;

/**
 * Create error message from {@link VoicemailStatus} for voicemail. This is will show different
 * terms of service for Verizon and for other carriers.
 */
public class VoicemailTosMessageCreator {
  // Flag to check which version of the Verizon ToS that the user has accepted.
  public static final String VVM3_TOS_VERSION_ACCEPTED_KEY = "vvm3_tos_version_accepted";

  // Flag to check which version of the Google Dialer ToS that the user has accepted.
  public static final String DIALER_TOS_VERSION_ACCEPTED_KEY = "dialer_tos_version_accepted";

  public static final int CURRENT_VVM3_TOS_VERSION = 2;
  public static final int CURRENT_DIALER_TOS_VERSION = 1;

  private static final String ISO639_SPANISH = "es";

  private final Context context;
  private final VoicemailStatus status;
  private final VoicemailStatusReader statusReader;
  private final SharedPreferences preferences;

  VoicemailTosMessageCreator(
      final Context context,
      final VoicemailStatus status,
      final VoicemailStatusReader statusReader) {
    this.context = context;
    this.status = status;
    this.statusReader = statusReader;
    this.preferences =
        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
  }

  @Nullable
  VoicemailErrorMessage maybeCreateTosMessage() {
    if (hasAcceptedTos()) {
      return null;
    }

    if (!shouldShowTos()) {
      return null;
    }

    logTosCreatedImpression();

    return new VoicemailTosMessage(
            getTosTitle(),
            getTosMessage(),
            new Action(
                getDeclineText(),
                new OnClickListener() {
                  @Override
                  public void onClick(View v) {
                    LogUtil.i("VoicemailTosMessageCreator.maybeShowTosMessage", "decline clicked");
                    PhoneAccountHandle handle =
                        new PhoneAccountHandle(
                            ComponentName.unflattenFromString(status.phoneAccountComponentName),
                            status.phoneAccountId);
                    logTosDeclinedImpression();
                    showDeclineTosDialog(handle);
                  }
                }),
            new Action(
                getAcceptText(),
                new OnClickListener() {
                  @Override
                  public void onClick(View v) {
                    LogUtil.i("VoicemailTosMessageCreator.maybeShowTosMessage", "accept clicked");
                    recordTosAcceptance();
                    logTosAcceptedImpression();
                    statusReader.refresh();
                  }
                },
                true /* raised */))
        .setModal(true)
        .setImageResourceId(R.drawable.voicemail_tos_image);
  }

  private boolean shouldShowTos() {
    if (isVvm3()) {
      LogUtil.i("VoicemailTosMessageCreator.shouldShowTos", "showing TOS for verizon");
      return true;
    }

    if (isVoicemailTranscriptionEnabled()) {
      LogUtil.i(
          "VoicemailTosMessageCreator.shouldShowTos", "showing TOS for Google transcription users");
      return true;
    }

    return false;
  }

  private boolean isVoicemailTranscriptionEnabled() {
    return ConfigProviderBindings.get(context).getBoolean("voicemail_transcription_enabled", false);
  }

  private void showDeclineTosDialog(final PhoneAccountHandle handle) {
    if (isVvm3() && Vvm3VoicemailMessageCreator.PIN_NOT_SET == status.configurationState) {
      LogUtil.i(
          "VoicemailTosMessageCreator.showDeclineTosDialog", "PIN_NOT_SET, showing set PIN dialog");
      showSetPinBeforeDeclineDialog(handle);
      return;
    }
    LogUtil.i(
        "VoicemailTosMessageCreator.showDeclineVerizonTosDialog",
        "showing decline ToS dialog, status=" + status);
    final TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setMessage(getTosDeclinedDialogMessageId());
    builder.setPositiveButton(
        getTosDeclinedDialogDowngradeId(),
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

  private void showSetPinBeforeDeclineDialog(PhoneAccountHandle phoneAccountHandle) {
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
            intent.putExtra(TelephonyManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
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

  private boolean isVvm3() {
    return VisualVoicemailTypeExtensions.VVM_TYPE_VVM3.equals(status.type);
  }

  private boolean useSpanish() {
    return Locale.getDefault().getLanguage().equals(new Locale(ISO639_SPANISH).getLanguage());
  }

  private boolean hasAcceptedTos() {
    if (isVvm3()) {
      return preferences.getInt(VVM3_TOS_VERSION_ACCEPTED_KEY, 0) >= CURRENT_VVM3_TOS_VERSION;
    } else {
      return preferences.getInt(DIALER_TOS_VERSION_ACCEPTED_KEY, 0) >= CURRENT_DIALER_TOS_VERSION;
    }
  }

  private void recordTosAcceptance() {
    if (isVvm3()) {
      preferences.edit().putInt(VVM3_TOS_VERSION_ACCEPTED_KEY, CURRENT_VVM3_TOS_VERSION).apply();
    } else {
      preferences
          .edit()
          .putInt(DIALER_TOS_VERSION_ACCEPTED_KEY, CURRENT_DIALER_TOS_VERSION)
          .apply();
    }
    VoicemailComponent.get(context).getVoicemailClient().onTosAccepted(context);
  }

  private void logTosCreatedImpression() {
    if (isVvm3()) {
      Logger.get(context).logImpression(DialerImpression.Type.VOICEMAIL_VVM3_TOS_V2_CREATED);
    } else {
      Logger.get(context).logImpression(DialerImpression.Type.VOICEMAIL_DIALER_TOS_CREATED);
    }
  }

  private void logTosDeclinedImpression() {
    if (isVvm3()) {
      Logger.get(context)
          .logImpression(DialerImpression.Type.VOICEMAIL_VVM3_TOS_V2_DECLINE_CLICKED);
    } else {
      Logger.get(context).logImpression(DialerImpression.Type.VOICEMAIL_DIALER_TOS_DECLINE_CLICKED);
    }
  }

  private void logTosAcceptedImpression() {
    if (isVvm3()) {
      Logger.get(context).logImpression(DialerImpression.Type.VOICEMAIL_VVM3_TOS_V2_ACCEPTED);
    } else {
      Logger.get(context).logImpression(DialerImpression.Type.VOICEMAIL_DIALER_TOS_ACCEPTED);
    }
  }

  private CharSequence getVvm3Tos() {
    String policyUrl = context.getString(R.string.verizon_terms_and_conditions_policy_url);
    return useSpanish()
        ? context.getString(R.string.verizon_terms_and_conditions_1_1_spanish, policyUrl)
        : context.getString(R.string.verizon_terms_and_conditions_1_1_english, policyUrl);
  }

  private CharSequence getDialerTos() {
    if (!isVoicemailTranscriptionEnabled()) {
      return "";
    }

    String learnMoreText = context.getString(R.string.dialer_terms_and_conditions_learn_more);
    return isVvm3()
        ? context.getString(R.string.dialer_terms_and_conditions_for_verizon_1_0, learnMoreText)
        : context.getString(R.string.dialer_terms_and_conditions_1_0, learnMoreText);
  }

  private CharSequence getAcceptText() {
    if (isVvm3()) {
      return useSpanish()
          ? context.getString(R.string.verizon_terms_and_conditions_accept_spanish)
          : context.getString(R.string.verizon_terms_and_conditions_accept_english);
    } else {
      return useSpanish()
          ? context.getString(R.string.dialer_terms_and_conditions_accept_spanish)
          : context.getString(R.string.dialer_terms_and_conditions_accept_english);
    }
  }

  private CharSequence getDeclineText() {
    if (isVvm3()) {
      return useSpanish()
          ? context.getString(R.string.verizon_terms_and_conditions_decline_spanish)
          : context.getString(R.string.verizon_terms_and_conditions_decline_english);
    } else {
      return useSpanish()
          ? context.getString(R.string.dialer_terms_and_conditions_decline_spanish)
          : context.getString(R.string.dialer_terms_and_conditions_decline_english);
    }
  }

  private CharSequence getTosTitle() {
    return isVvm3()
        ? context.getString(R.string.verizon_terms_and_conditions_title)
        : context.getString(R.string.dialer_terms_and_conditions_title);
  }

  private CharSequence getTosMessage() {
    SpannableString spannableTos;
    if (isVvm3()) {
      // For verizon the TOS consist of three pieces: google dialer TOS, Verizon TOS message and
      // Verizon TOS details.
      CharSequence vvm3Details = getVvm3Tos();
      CharSequence tos =
          context.getString(
              R.string.verizon_terms_and_conditions_message, getDialerTos(), vvm3Details);
      spannableTos = new SpannableString(tos);
      // Set the text style for the details part of the TOS
      int start = spannableTos.length() - vvm3Details.length();
      spannableTos.setSpan(
          new TextAppearanceSpan(context, R.style.TosDetailsTextStyle),
          start,
          start + vvm3Details.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      // Add verizon policy link
      String linkUrl = context.getString(R.string.verizon_terms_and_conditions_policy_url);
      addLink(spannableTos, linkUrl, linkUrl);
    } else {
      // The TOS for everyone else, there are no details, but change to center alignment.
      CharSequence tos =
          context.getString(R.string.dialer_terms_and_conditions_message, getDialerTos());
      spannableTos = new SpannableString(tos);
      spannableTos.setSpan(
          new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
          0,
          tos.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // Add 'Learn more' link for dialer TOS
    String learnMore = context.getString(R.string.dialer_terms_and_conditions_learn_more);
    String linkUrl = context.getString(R.string.dialer_terms_and_conditions_learn_more_url);
    return addLink(spannableTos, learnMore, linkUrl);
  }

  private SpannableString addLink(SpannableString spannable, String linkText, String linkUrl) {
    if (TextUtils.isEmpty(linkUrl) || TextUtils.isEmpty(linkText)) {
      return spannable;
    }

    int start = spannable.toString().indexOf(linkText);
    if (start != -1) {
      int end = start + linkText.length();
      spannable.setSpan(new URLSpan(linkUrl), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      spannable.setSpan(
          new TextAppearanceSpan(context, R.style.TosLinkStyle),
          start,
          end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return spannable;
  }

  private int getTosDeclinedDialogMessageId() {
    return isVvm3()
        ? R.string.verizon_terms_and_conditions_decline_dialog_message
        : R.string.dialer_terms_and_conditions_decline_dialog_message;
  }

  private int getTosDeclinedDialogDowngradeId() {
    return isVvm3()
        ? R.string.verizon_terms_and_conditions_decline_dialog_downgrade
        : R.string.dialer_terms_and_conditions_decline_dialog_downgrade;
  }
}
