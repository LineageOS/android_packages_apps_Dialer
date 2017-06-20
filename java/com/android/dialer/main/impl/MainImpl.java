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

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build.VERSION_CODES;
import android.support.v4.content.pm.ShortcutInfoCompat;
import android.support.v4.content.pm.ShortcutManagerCompat;
import android.support.v4.graphics.drawable.IconCompat;
import android.support.v4.os.BuildCompat;
import com.android.dialer.buildtype.BuildType;
import com.android.dialer.common.LogUtil;
import com.android.dialer.main.Main;
import javax.inject.Inject;

/** The entry point for the main feature. */
final class MainImpl implements Main {
  private static final String SHORTCUT_KEY = "nui_launcher_shortcut";

  @Inject
  public MainImpl() {}

  @Override
  public boolean isNewUiEnabled(Context context) {
    return BuildType.get() == BuildType.BUGFOOD || LogUtil.isDebugEnabled();
  }

  @Override
  public void createNewUiLauncherShortcut(Context context) {
    enableComponent(context);
    if (BuildCompat.isAtLeastO()) {
      createLauncherShortcutO(context);
    } else {
      createLauncherShortcutPreO(context);
    }
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

  @TargetApi(VERSION_CODES.O)
  private static void createLauncherShortcutO(Context context) {
    ShortcutInfoCompat shortcutInfo =
        new ShortcutInfoCompat.Builder(context, SHORTCUT_KEY)
            .setIcon(IconCompat.createWithResource(context, R.drawable.nui_launcher_icon))
            .setIntent(MainActivity.getIntent(context))
            .setShortLabel(context.getString(R.string.nui_shortcut_name))
            .build();
    ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null);
  }

  private static void createLauncherShortcutPreO(Context context) {
    Intent intent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
    intent.putExtra(
        Intent.EXTRA_SHORTCUT_ICON,
        Intent.ShortcutIconResource.fromContext(context, R.drawable.nui_launcher_icon));
    intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, context.getString(R.string.nui_shortcut_name));
    intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, MainActivity.getIntent(context));
    context.sendBroadcast(intent);
  }
}
