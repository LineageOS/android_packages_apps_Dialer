/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Main activity intents.
 *
 * <p>TODO(calderwoodra): Move this elsewhere.
 */
public class MainComponent {

  public static final String EXTRA_CLEAR_NEW_VOICEMAILS = "EXTRA_CLEAR_NEW_VOICEMAILS";

  /**
   * @param context Context of the application package implementing MainActivity class.
   * @return intent for MainActivity.class
   */
  public static Intent getIntent(Context context) {
    Intent intent = new Intent();
    intent.setComponent(new ComponentName(context, getComponentName()));
    intent.setAction(Intent.ACTION_VIEW);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    return intent;
  }

  public static Intent getShowCallLogIntent(Context context) {
    Intent intent = new Intent();
    intent.setComponent(new ComponentName(context, getComponentName()));
    intent.setAction("ACTION_SHOW_TAB");
    intent.putExtra("EXTRA_SHOW_TAB", 1);
    return intent;
  }

  public static Intent getShowVoicemailIntent(Context context) {
    Intent intent = new Intent();
    intent.setComponent(new ComponentName(context, getComponentName()));
    intent.setAction("ACTION_SHOW_TAB");
    intent.putExtra("EXTRA_SHOW_TAB", 3);
    intent.putExtra(EXTRA_CLEAR_NEW_VOICEMAILS, true);
    return intent;
  }

  private static String getComponentName() {
    return "com.android.dialer.app.DialtactsActivity";
  }
}
