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
import android.content.Intent;
import android.provider.Settings;
import android.provider.VoicemailContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.view.View;
import android.view.View.OnClickListener;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.PerAccountSharedPreferences;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.precall.PreCall;
import com.android.dialer.voicemail.settings.VoicemailChangePinActivity;
import com.android.voicemail.VoicemailClient;
import com.android.voicemail.VoicemailComponent;
import java.util.Arrays;
import java.util.List;

/**
 * Represents an error determined from the current {@link
 * android.provider.VoicemailContract.Status}. The message will contain a title, a description, and
 * a list of actions that can be performed.
 */
public class VoicemailErrorMessage {

  private final CharSequence title;
  private final CharSequence description;
  private final List<Action> actions;

  private boolean modal;
  private Integer imageResourceId;

  /** Something the user can click on to resolve an error, such as retrying or calling voicemail */
  public static class Action {

    private final CharSequence text;
    private final View.OnClickListener listener;
    private final boolean raised;

    public Action(CharSequence text, View.OnClickListener listener) {
      this(text, listener, false);
    }

    public Action(CharSequence text, View.OnClickListener listener, boolean raised) {
      this.text = text;
      this.listener = listener;
      this.raised = raised;
    }

    public CharSequence getText() {
      return text;
    }

    public View.OnClickListener getListener() {
      return listener;
    }

    public boolean isRaised() {
      return raised;
    }
  }

  public CharSequence getTitle() {
    return title;
  }

  public CharSequence getDescription() {
    return description;
  }

  @Nullable
  public List<Action> getActions() {
    return actions;
  }

  public boolean isModal() {
    return modal;
  }

  public VoicemailErrorMessage setModal(boolean value) {
    modal = value;
    return this;
  }

  @Nullable
  public Integer getImageResourceId() {
    return imageResourceId;
  }

  public VoicemailErrorMessage setImageResourceId(Integer imageResourceId) {
    this.imageResourceId = imageResourceId;
    return this;
  }

  public VoicemailErrorMessage(CharSequence title, CharSequence description, Action... actions) {
    this(title, description, Arrays.asList(actions));
  }

  public VoicemailErrorMessage(
      CharSequence title, CharSequence description, @Nullable List<Action> actions) {
    this.title = title;
    this.description = description;
    this.actions = actions;
  }

  @NonNull
  public static Action createChangeAirplaneModeAction(final Context context) {
    return new Action(
        context.getString(R.string.voicemail_action_turn_off_airplane_mode),
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            Logger.get(context)
                .logImpression(DialerImpression.Type.VVM_CHANGE_AIRPLANE_MODE_CLICKED);
            Intent intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            context.startActivity(intent);
          }
        });
  }

  @NonNull
  public static Action createSetPinAction(
      final Context context, PhoneAccountHandle phoneAccountHandle) {
    return new Action(
        context.getString(R.string.voicemail_action_set_pin),
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            Logger.get(context)
                .logImpression(DialerImpression.Type.VOICEMAIL_ALERT_SET_PIN_CLICKED);
            Intent intent = new Intent(context, VoicemailChangePinActivity.class);
            intent.putExtra(VoicemailClient.PARAM_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
            context.startActivity(intent);
          }
        });
  }

  @NonNull
  public static Action createCallVoicemailAction(
      final Context context) {
    return new Action(
        context.getString(R.string.voicemail_action_call_voicemail),
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            Logger.get(context).logImpression(DialerImpression.Type.VVM_CALL_VOICEMAIL_CLICKED);
            PreCall.start(
                context,
                CallIntentBuilder.forVoicemail(
                    CallInitiationType.Type.VOICEMAIL_ERROR_MESSAGE));
          }
        });
  }

  @NonNull
  public static Action createSyncAction(final Context context, final VoicemailStatus status) {
    return new Action(
        context.getString(R.string.voicemail_action_sync),
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            Logger.get(context).logImpression(DialerImpression.Type.VVM_USER_SYNC);
            Intent intent = new Intent(VoicemailContract.ACTION_SYNC_VOICEMAIL);
            intent.setPackage(status.sourcePackage);
            context.sendBroadcast(intent);
          }
        });
  }

  @NonNull
  public static Action createRetryAction(final Context context, final VoicemailStatus status) {
    return new Action(
        context.getString(R.string.voicemail_action_retry),
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            Logger.get(context).logImpression(DialerImpression.Type.VVM_USER_RETRY);
            Intent intent = new Intent(VoicemailContract.ACTION_SYNC_VOICEMAIL);
            intent.setPackage(status.sourcePackage);
            context.sendBroadcast(intent);
          }
        });
  }

  @NonNull
  public static Action createTurnArchiveOnAction(
      final Context context,
      DialerImpression.Type impressionToLog,
      final VoicemailStatus status,
      VoicemailStatusReader statusReader,
      VoicemailClient voicemailClient,
      PhoneAccountHandle phoneAccountHandle) {
    return new Action(
        context.getString(R.string.voicemail_action_turn_archive_on),
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            Assert.checkArgument(
                VoicemailComponent.get(context)
                    .getVoicemailClient()
                    .isVoicemailArchiveAvailable(context));
            Logger.get(context).logImpression(impressionToLog);
            voicemailClient.setVoicemailArchiveEnabled(context, phoneAccountHandle, true);
            Intent intent = new Intent(VoicemailContract.ACTION_SYNC_VOICEMAIL);
            intent.setPackage(status.sourcePackage);
            context.sendBroadcast(intent);
            statusReader.refresh();
          }
        });
  }

  @NonNull
  public static Action createDismissTurnArchiveOnAction(
      final Context context,
      DialerImpression.Type impressionToLog,
      VoicemailStatusReader statusReader,
      PerAccountSharedPreferences sharedPreferenceForAccount,
      String preferenceKeyToUpdate) {
    return new Action(
        context.getString(R.string.voicemail_action_dimiss),
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            Assert.checkArgument(
                VoicemailComponent.get(context)
                    .getVoicemailClient()
                    .isVoicemailArchiveAvailable(context));
            Logger.get(context).logImpression(impressionToLog);
            sharedPreferenceForAccount.edit().putBoolean(preferenceKeyToUpdate, true).apply();
            statusReader.refresh();
          }
        });
  }
}
