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

package com.android.dialer.contactactions;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import com.android.dialer.callintent.CallInitiationType.Type;
import com.android.dialer.callintent.CallIntentBuilder;

/**
 * {@link ContactActionModule} useful for making easy to build modules based on starting an intent.
 */
public class IntentModule implements ContactActionModule {

  private final Context context;
  private final Intent intent;
  private final @StringRes int text;
  private final @DrawableRes int image;

  public IntentModule(Context context, Intent intent, @StringRes int text, @DrawableRes int image) {
    this.context = context;
    this.intent = intent;
    this.text = text;
    this.image = image;
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
    context.startActivity(intent);
    return true;
  }

  public static IntentModule newCallModule(Context context, String number, Type initiationType) {
    return new IntentModule(
        context,
        new CallIntentBuilder(number, initiationType).build(),
        R.string.call,
        R.drawable.quantum_ic_call_white_24);
  }

  public static IntentModule newVideoCallModule(
      Context context, String number, Type initiationType) {
    return new IntentModule(
        context,
        new CallIntentBuilder(number, initiationType).setIsVideoCall(true).build(),
        R.string.video_call,
        R.drawable.quantum_ic_videocam_white_24);
  }
}
