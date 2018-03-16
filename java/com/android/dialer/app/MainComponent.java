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
import android.content.pm.PackageManager;
import com.android.dialer.configprovider.ConfigProviderBindings;

/** This class is a copy of dialer.main.impl.MainImpl to get around a dependency issue. */
public class MainComponent {

  public static boolean isNewUiEnabled(Context context) {
    return ConfigProviderBindings.get(context).getBoolean("is_nui_shortcut_enabled", false);
  }

  public static void createNewUiLauncherShortcut(Context context) {
    enableComponent(context);
  }

  public static boolean isNuiComponentEnabled(Context context) {
    if (!isNewUiEnabled(context)) {
      return false;
    }
    return context
            .getPackageManager()
            .getComponentEnabledSetting(new ComponentName(context, getComponentName()))
        == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
  }

  /**
   * Enables the NUI activity component. By default the component is disabled and can't be accessed.
   * Once the component has been enabled the user will get an option to use the new UI to handle
   * DIAL (and other) intents.
   */
  private static void enableComponent(Context context) {
    context
        .getPackageManager()
        .setComponentEnabledSetting(
            new ComponentName(context, getComponentName()),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP);
  }

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
    return intent;
  }

  private static String getComponentName() {
    return "com.android.dialer.main.impl.MainActivity";
  }
}
