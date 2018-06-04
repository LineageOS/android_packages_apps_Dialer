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

package com.android.dialer.voicemail.listui.error;

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
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.voicemail.listui.error.VoicemailErrorMessage.Action;
import com.android.voicemail.VisualVoicemailTypeExtensions;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.VoicemailComponent;
import com.android.voicemail.VoicemailVersionConstants;
import java.util.Locale;

/**
 * Create error message from {@link VoicemailStatus} for voicemail. This is will show different
 * terms of service for Verizon and for other carriers.
 */
public class VoicemailTosMessageCreator {
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
    if (!canShowTos()) {
      return null;
    } else if (shouldShowTos()) {
      logTosCreatedImpression();
      return getTosMessage();
    } else if (shouldShowPromo()) {
      return getPromoMessage();
    } else {
      return null;
    }
  }

  private VoicemailErrorMessage getTosMessage() {
    return new VoicemailTosMessage(
            getNewUserTosTitle(),
            getNewUserTosMessageText(),
            new Action(
                getDeclineText(),
                new OnClickListener() {
                  @Override
                  public void onClick(View v) {
                    LogUtil.i("VoicemailTosMessageCreator.getTosMessage", "decline clicked");
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
                    LogUtil.i("VoicemailTosMessageCreator.getTosMessage", "accept clicked");
                    if (isVoicemailTranscriptionAvailable()) {
                      VoicemailComponent.get(context)
                          .getVoicemailClient()
                          .setVoicemailTranscriptionEnabled(
                              context, status.getPhoneAccountHandle(), true);
                    }
                    recordTosAcceptance();
                    // Accepting the TOS also acknowledges the latest features
                    recordFeatureAcknowledgement();
                    logTosAcceptedImpression();
                    statusReader.refresh();
                  }
                },
                true /* raised */))
        .setModal(true)
        .setImageResourceId(R.drawable.voicemail_tos_image);
  }

  private VoicemailErrorMessage getPromoMessage() {
    return new VoicemailTosMessage(
            getExistingUserTosTitle(),
            getExistingUserTosMessageText(),
            new Action(
                context.getString(R.string.dialer_terms_and_conditions_existing_user_decline),
                new OnClickListener() {
                  @Override
                  public void onClick(View v) {
                    LogUtil.i(
                        "VoicemailTosMessageCreator.getPromoMessage", "declined transcription");
                    if (isVoicemailTranscriptionAvailable()) {
                      VoicemailClient voicemailClient =
                          VoicemailComponent.get(context).getVoicemailClient();
                      voicemailClient.setVoicemailTranscriptionEnabled(
                          context, status.getPhoneAccountHandle(), false);
                      // Feature acknowledgement also means accepting TOS, otherwise after removing
                      // the feature ToS, we'll end up showing the ToS
                      // TODO(uabdullah): Consider separating the ToS acceptance and feature
                      // acknowledgment.
                      recordTosAcceptance();
                      recordFeatureAcknowledgement();
                      statusReader.refresh();
                    } else {
                      LogUtil.e(
                          "VoicemailTosMessageCreator.getPromoMessage",
                          "voicemail transcription not available");
                    }
                  }
                }),
            new Action(
                context.getString(R.string.dialer_terms_and_conditions_existing_user_ack),
                new OnClickListener() {
                  @Override
                  public void onClick(View v) {
                    LogUtil.i("VoicemailTosMessageCreator.getPromoMessage", "acknowledge clicked");
                    if (isVoicemailTranscriptionAvailable()) {
                      VoicemailComponent.get(context)
                          .getVoicemailClient()
                          .setVoicemailTranscriptionEnabled(
                              context, status.getPhoneAccountHandle(), true);
                    }
                    // Feature acknowledgement also means accepting TOS
                    recordTosAcceptance();
                    recordFeatureAcknowledgement();
                    statusReader.refresh();
                  }
                },
                true /* raised */))
        .setModal(true)
        .setImageResourceId(R.drawable.voicemail_tos_image);
  }

  private boolean canShowTos() {
    if (!isValidVoicemailType(status.type)) {
      LogUtil.i("VoicemailTosMessageCreator.canShowTos", "unsupported type: " + status.type);
      return false;
    }

    if (status.getPhoneAccountHandle() == null
        || status.getPhoneAccountHandle().getComponentName() == null) {
      LogUtil.i("VoicemailTosMessageCreator.canShowTos", "invalid phone account");
      return false;
    }

    return true;
  }

  private boolean shouldShowTos() {
    if (hasAcceptedTos()) {
      LogUtil.i("VoicemailTosMessageCreator.shouldShowTos", "already accepted TOS");
      return false;
    }

    if (isVvm3()) {
      LogUtil.i("VoicemailTosMessageCreator.shouldShowTos", "showing TOS for verizon");
      return true;
    }

    if (isVoicemailTranscriptionAvailable() && !isLegacyVoicemailUser()) {
      LogUtil.i(
          "VoicemailTosMessageCreator.shouldShowTos", "showing TOS for Google transcription users");
      return true;
    }

    return false;
  }

  private boolean shouldShowPromo() {
    if (hasAcknowledgedFeatures()) {
      LogUtil.i(
          "VoicemailTosMessageCreator.shouldShowPromo", "already acknowledeged latest features");
      return false;
    }

    if (isVoicemailTranscriptionAvailable()) {
      LogUtil.i(
          "VoicemailTosMessageCreator.shouldShowPromo",
          "showing promo for Google transcription users");
      return true;
    }

    return false;
  }

  private static boolean isValidVoicemailType(String type) {
    if (type == null) {
      return false;
    }
    switch (type) {
      case TelephonyManager.VVM_TYPE_OMTP:
      case TelephonyManager.VVM_TYPE_CVVM:
      case VisualVoicemailTypeExtensions.VVM_TYPE_VVM3:
        return true;
      default:
        return false;
    }
  }

  private boolean isVoicemailTranscriptionAvailable() {
    return VoicemailComponent.get(context)
        .getVoicemailClient()
        .isVoicemailTranscriptionAvailable(context, status.getPhoneAccountHandle());
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
    builder.setTitle(R.string.terms_and_conditions_decline_dialog_title);
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
      return preferences.getInt(VoicemailVersionConstants.PREF_VVM3_TOS_VERSION_ACCEPTED_KEY, 0)
          >= VoicemailVersionConstants.CURRENT_VVM3_TOS_VERSION;
    } else {
      return preferences.getInt(VoicemailVersionConstants.PREF_DIALER_TOS_VERSION_ACCEPTED_KEY, 0)
          >= VoicemailVersionConstants.CURRENT_DIALER_TOS_VERSION;
    }
  }

  private void recordTosAcceptance() {
    if (isVvm3()) {
      preferences
          .edit()
          .putInt(
              VoicemailVersionConstants.PREF_VVM3_TOS_VERSION_ACCEPTED_KEY,
              VoicemailVersionConstants.CURRENT_VVM3_TOS_VERSION)
          .apply();
    } else {
      preferences
          .edit()
          .putInt(
              VoicemailVersionConstants.PREF_DIALER_TOS_VERSION_ACCEPTED_KEY,
              VoicemailVersionConstants.CURRENT_DIALER_TOS_VERSION)
          .apply();
    }

    PhoneAccountHandle handle =
        new PhoneAccountHandle(
            ComponentName.unflattenFromString(status.phoneAccountComponentName),
            status.phoneAccountId);
    VoicemailComponent.get(context).getVoicemailClient().onTosAccepted(context, handle);
  }

  private boolean hasAcknowledgedFeatures() {
    if (isVvm3()) {
      return true;
    }

    return preferences.getInt(
            VoicemailVersionConstants.PREF_DIALER_FEATURE_VERSION_ACKNOWLEDGED_KEY, 0)
        >= VoicemailVersionConstants.CURRENT_VOICEMAIL_FEATURE_VERSION;
  }

  private void recordFeatureAcknowledgement() {
    preferences
        .edit()
        .putInt(
            VoicemailVersionConstants.PREF_DIALER_FEATURE_VERSION_ACKNOWLEDGED_KEY,
            VoicemailVersionConstants.CURRENT_VOICEMAIL_FEATURE_VERSION)
        .apply();
  }

  private boolean isLegacyVoicemailUser() {
    return preferences.getInt(
            VoicemailVersionConstants.PREF_DIALER_FEATURE_VERSION_ACKNOWLEDGED_KEY, 0)
        == VoicemailVersionConstants.LEGACY_VOICEMAIL_FEATURE_VERSION;
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

  private CharSequence getVvmDialerTos() {
    return context.getString(R.string.dialer_terms_and_conditions_for_verizon_1_0);
  }

  private CharSequence getNewUserDialerTos() {
    if (!isVoicemailTranscriptionAvailable()) {
      return "";
    }

    String learnMoreText = context.getString(R.string.dialer_terms_and_conditions_learn_more);
    return context.getString(R.string.dialer_terms_and_conditions_1_0, learnMoreText);
  }

  private CharSequence getExistingUserDialerTos() {
    if (!isVoicemailTranscriptionAvailable()) {
      return "";
    }

    String learnMoreText = context.getString(R.string.dialer_terms_and_conditions_learn_more);
    return context.getString(R.string.dialer_terms_and_conditions_existing_user, learnMoreText);
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

  private CharSequence getNewUserTosTitle() {
    return isVvm3()
        ? context.getString(R.string.verizon_terms_and_conditions_title)
        : context.getString(R.string.dialer_terms_and_conditions_title);
  }

  private CharSequence getExistingUserTosTitle() {
    return isVvm3()
        ? context.getString(R.string.verizon_terms_and_conditions_title)
        : context.getString(R.string.dialer_terms_and_conditions_existing_user_title);
  }

  private CharSequence getNewUserTosMessageText() {
    SpannableString spannableTos;
    if (isVvm3()) {
      // For verizon the TOS consist of three pieces: google dialer TOS, Verizon TOS message and
      // Verizon TOS details.
      CharSequence vvm3Details = getVvm3Tos();
      CharSequence tos =
          context.getString(
              R.string.verizon_terms_and_conditions_message, getVvmDialerTos(), vvm3Details);
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
      return addLink(spannableTos, linkUrl, linkUrl);
    } else {
      // The TOS for everyone else, there are no details, but change to center alignment.
      CharSequence tos =
          context.getString(R.string.dialer_terms_and_conditions_message, getNewUserDialerTos());
      spannableTos = new SpannableString(tos);
      spannableTos.setSpan(
          new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
          0,
          tos.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

      // Add 'Learn more' link for dialer TOS
      String learnMore = context.getString(R.string.dialer_terms_and_conditions_learn_more);
      return addLink(spannableTos, learnMore, getLearnMoreUrl());
    }
  }

  private CharSequence getExistingUserTosMessageText() {
    SpannableString spannableTos;
    // Change to center alignment.
    CharSequence tos =
        context.getString(R.string.dialer_terms_and_conditions_message, getExistingUserDialerTos());
    spannableTos = new SpannableString(tos);
    spannableTos.setSpan(
        new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
        0,
        tos.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    // Add 'Learn more' link for dialer TOS
    String learnMore = context.getString(R.string.dialer_terms_and_conditions_learn_more);
    return addLink(spannableTos, learnMore, getLearnMoreUrl());
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

  private String getLearnMoreUrl() {
    return ConfigProviderComponent.get(context)
        .getConfigProvider()
        .getString(
            "voicemail_transcription_learn_more_url",
            context.getString(R.string.dialer_terms_and_conditions_learn_more_url));
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
