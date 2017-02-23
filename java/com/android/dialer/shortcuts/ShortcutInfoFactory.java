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
 * limitations under the License.
 */

package com.android.dialer.shortcuts;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import com.android.dialer.common.Assert;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Creates {@link ShortcutInfo} objects (which are required by shortcut manager system service) from
 * {@link DialerShortcut} objects (which are package-private convenience data structures).
 *
 * <p>The main work this factory does is create shortcut intents. It also delegates to the {@link
 * IconFactory} to create icons.
 */
@TargetApi(VERSION_CODES.N_MR1) // Shortcuts introduced in N MR1
final class ShortcutInfoFactory {

  /** Key for the contact ID extra (a long) stored as part of the shortcut intent. */
  static final String EXTRA_CONTACT_ID = "contactId";

  private final Context context;
  private final IconFactory iconFactory;

  ShortcutInfoFactory(@NonNull Context context, IconFactory iconFactory) {
    this.context = context;
    this.iconFactory = iconFactory;
  }

  /**
   * Builds a list {@link ShortcutInfo} objects from the provided collection of {@link
   * DialerShortcut} objects. This primarily means setting the intent and adding the icon, which
   * {@link DialerShortcut} objects do not hold.
   */
  @WorkerThread
  @NonNull
  List<ShortcutInfo> buildShortcutInfos(@NonNull Map<String, DialerShortcut> shortcutsById) {
    Assert.isWorkerThread();
    List<ShortcutInfo> shortcuts = new ArrayList<>(shortcutsById.size());
    for (DialerShortcut shortcut : shortcutsById.values()) {
      Intent intent = new Intent();
      intent.setClassName(context, "com.android.dialer.shortcuts.CallContactActivity");
      intent.setData(shortcut.getLookupUri());
      intent.setAction("com.android.dialer.shortcuts.CALL_CONTACT");
      intent.putExtra(EXTRA_CONTACT_ID, shortcut.getContactId());

      ShortcutInfo.Builder shortcutInfo =
          new ShortcutInfo.Builder(context, shortcut.getShortcutId())
              .setIntent(intent)
              .setShortLabel(shortcut.getShortLabel())
              .setLongLabel(shortcut.getLongLabel())
              .setIcon(iconFactory.create(shortcut));

      if (shortcut.getRank() != DialerShortcut.NO_RANK) {
        shortcutInfo.setRank(shortcut.getRank());
      }
      shortcuts.add(shortcutInfo.build());
    }
    return shortcuts;
  }

  /**
   * Creates a copy of the provided {@link ShortcutInfo} but with an updated icon fetched from
   * contacts provider.
   */
  @WorkerThread
  @NonNull
  ShortcutInfo withUpdatedIcon(ShortcutInfo info) {
    Assert.isWorkerThread();
    return new ShortcutInfo.Builder(context, info.getId())
        .setIntent(info.getIntent())
        .setShortLabel(info.getShortLabel())
        .setLongLabel(info.getLongLabel())
        .setRank(info.getRank())
        .setIcon(iconFactory.create(info))
        .build();
  }
}
