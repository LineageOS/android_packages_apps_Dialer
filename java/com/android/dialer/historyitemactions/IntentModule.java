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
 * limitations under the License.
 */

package com.android.dialer.historyitemactions;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.precall.PreCall;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;
import com.google.common.collect.ImmutableList;

/**
 * {@link HistoryItemActionModule} useful for making easy to build modules based on starting an
 * intent.
 */
public class IntentModule implements HistoryItemActionModule {

  private final Context context;
  private final Intent intent;
  private final @StringRes int text;
  private final @DrawableRes int image;
  private final ImmutableList<DialerImpression.Type> impressions;

  /**
   * @deprecated use {@link IntentModule#IntentModule(Context, Intent, int, int, ImmutableList)}
   *     instead.
   */
  @Deprecated
  public IntentModule(Context context, Intent intent, @StringRes int text, @DrawableRes int image) {
    this(context, intent, text, image, /* impressions = */ ImmutableList.of());
  }

  IntentModule(
      Context context,
      Intent intent,
      @StringRes int text,
      @DrawableRes int image,
      ImmutableList<DialerImpression.Type> impressions) {
    this.context = context;
    this.intent = intent;
    this.text = text;
    this.image = image;
    this.impressions = impressions;
  }

  @Override
  public int getStringId() {
    return text;
  }

  @Override
  public int getDrawableId() {
    return image;
  }

  @Override
  public boolean onClick() {
    DialerUtils.startActivityWithErrorToast(context, intent);
    impressions.forEach(Logger.get(context)::logImpression);
    return true;
  }

  /** @deprecated Use {@link #newCallModule(Context, CallIntentBuilder, ImmutableList)} instead. */
  @Deprecated
  public static IntentModule newCallModule(Context context, CallIntentBuilder callIntentBuilder) {
    return newCallModule(context, callIntentBuilder, /* impressions = */ ImmutableList.of());
  }

  /** Creates a module for starting an outgoing call with a {@link CallIntentBuilder}. */
  static IntentModule newCallModule(
      Context context,
      CallIntentBuilder callIntentBuilder,
      ImmutableList<DialerImpression.Type> impressions) {
    @StringRes int text;
    @DrawableRes int image;

    if (callIntentBuilder.isVideoCall()) {
      text = R.string.video_call;
      image = R.drawable.quantum_ic_videocam_vd_white_24;
    } else {
      text = R.string.voice_call;
      image = R.drawable.quantum_ic_call_white_24;
    }

    return new IntentModule(
        context, PreCall.getIntent(context, callIntentBuilder), text, image, impressions);
  }

  /**
   * @deprecated Use {@link #newModuleForSendingTextMessage(Context, String, ImmutableList)}
   *     instead.
   */
  @Deprecated
  public static IntentModule newModuleForSendingTextMessage(Context context, String number) {
    return newModuleForSendingTextMessage(context, number, /* impressions = */ ImmutableList.of());
  }

  /** Creates a module for sending a text message to the given number. */
  static IntentModule newModuleForSendingTextMessage(
      Context context, String number, ImmutableList<DialerImpression.Type> impressions) {
    return new IntentModule(
        context,
        IntentUtil.getSendSmsIntent(number),
        R.string.send_a_message,
        R.drawable.quantum_ic_message_vd_theme_24,
        impressions);
  }
}
