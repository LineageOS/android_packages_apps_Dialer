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

package com.android.dialer.main.impl;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.main.Main;
import javax.inject.Inject;

/** The entry point for the main feature. */
final class MainImpl implements Main {

  @Inject
  MainImpl() {}

  @Override
  public boolean isNewUiEnabled(Context context) {
    return ConfigProviderBindings.get(context).getBoolean("is_nui_shortcut_enabled", false);
  }

  @Override
  public void createNewUiLauncherShortcut(Context context) {
    enableComponent(context);
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
            new ComponentName(context, MainActivity.class),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP);
  }

  @Override
  public void disableComponentForTesting(Context context) {
    context
        .getPackageManager()
        .setComponentEnabledSetting(
            new ComponentName(context, MainActivity.class),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP);
  }
}
