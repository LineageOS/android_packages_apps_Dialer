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

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.util.ArrayMap;
import com.android.contacts.common.list.ContactEntry;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Handles refreshing of dialer dynamic shortcuts.
 *
 * <p>Dynamic shortcuts are the list of shortcuts which is accessible by tapping and holding the
 * dialer launcher icon from the app drawer or a home screen.
 *
 * <p>Dynamic shortcuts are refreshed whenever the dialtacts activity detects changes to favorites
 * tiles. This class compares the newly updated favorites tiles to the existing list of (previously
 * published) dynamic shortcuts to compute a delta, which consists of lists of shortcuts which need
 * to be updated, added, or deleted.
 *
 * <p>Dynamic shortcuts should mirror (in order) the contacts displayed in the "tiled favorites" tab
 * of the dialer application. When selecting a dynamic shortcut, the behavior should be the same as
 * if the user had tapped on the contact from the tiled favorites tab. Specifically, if the user has
 * more than one phone number, a number picker should be displayed, and otherwise the contact should
 * be called directly.
 *
 * <p>Note that an icon change by itself does not trigger a shortcut update, because it is not
 * possible to detect an icon update and we don't want to constantly force update icons, because
 * that is an expensive operation which requires storage I/O.
 *
 * <p>However, the job scheduler uses {@link #updateIcons()} to makes sure icons are forcefully
 * updated periodically (about once a day).
 *
 */
@TargetApi(VERSION_CODES.N_MR1) // Shortcuts introduced in N MR1
final class DynamicShortcuts {

  private static final int MAX_DYNAMIC_SHORTCUTS = 3;

  private static class Delta {

    final Map<String, DialerShortcut> shortcutsToUpdateById = new ArrayMap<>();
    final List<String> shortcutIdsToRemove = new ArrayList<>();
    final Map<String, DialerShortcut> shortcutsToAddById = new ArrayMap<>();
  }

  private final Context context;
  private final ShortcutInfoFactory shortcutInfoFactory;

  DynamicShortcuts(@NonNull Context context, IconFactory iconFactory) {
    this.context = context;
    this.shortcutInfoFactory = new ShortcutInfoFactory(context, iconFactory);
  }

  /**
   * Performs a "complete refresh" of dynamic shortcuts. This is done by comparing the provided
   * contact information with the existing dynamic shortcuts in order to compute a delta which
   * contains shortcuts which should be added, updated, or removed.
   *
   * <p>If the delta is non-empty, it is applied by making appropriate calls to the {@link
   * ShortcutManager} system service.
   *
   * <p>This is a slow blocking call which performs file I/O and should not be performed on the main
   * thread.
   */
  @WorkerThread
  public void refresh(List<ContactEntry> contacts) {
    Assert.isWorkerThread();
    LogUtil.enterBlock("DynamicShortcuts.refresh");

    ShortcutManager shortcutManager = getShortcutManager(context);

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
        != PackageManager.PERMISSION_GRANTED) {
      LogUtil.i("DynamicShortcuts.refresh", "no contact permissions");
      shortcutManager.removeAllDynamicShortcuts();
      return;
    }

    // Fill the available shortcuts with dynamic shortcuts up to a maximum of 3 dynamic shortcuts.
    int numDynamicShortcutsToCreate =
        Math.min(
            MAX_DYNAMIC_SHORTCUTS,
            shortcutManager.getMaxShortcutCountPerActivity()
                - shortcutManager.getManifestShortcuts().size());

    Map<String, DialerShortcut> newDynamicShortcutsById =
        new ArrayMap<>(numDynamicShortcutsToCreate);
    int rank = 0;
    for (ContactEntry entry : contacts) {
      if (newDynamicShortcutsById.size() >= numDynamicShortcutsToCreate) {
        break;
      }

      DialerShortcut shortcut =
          DialerShortcut.builder()
              .setContactId(entry.id)
              .setLookupKey(entry.lookupKey)
              .setDisplayName(entry.getPreferredDisplayName())
              .setRank(rank++)
              .build();
      newDynamicShortcutsById.put(shortcut.getShortcutId(), shortcut);
    }

    List<ShortcutInfo> oldDynamicShortcuts = new ArrayList<>(shortcutManager.getDynamicShortcuts());
    Delta delta = computeDelta(oldDynamicShortcuts, newDynamicShortcutsById);
    applyDelta(delta);
  }

  /**
   * Forces an update of all dynamic shortcut icons. This should only be done from job scheduler as
   * updating icons requires storage I/O.
   */
  @WorkerThread
  void updateIcons() {
    Assert.isWorkerThread();
    LogUtil.enterBlock("DynamicShortcuts.updateIcons");

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
        != PackageManager.PERMISSION_GRANTED) {
      LogUtil.i("DynamicShortcuts.updateIcons", "no contact permissions");
      return;
    }

    ShortcutManager shortcutManager = getShortcutManager(context);

    int maxDynamicShortcutsToCreate =
        shortcutManager.getMaxShortcutCountPerActivity()
            - shortcutManager.getManifestShortcuts().size();
    int count = 0;

    List<ShortcutInfo> newShortcuts = new ArrayList<>();
    for (ShortcutInfo oldInfo : shortcutManager.getDynamicShortcuts()) {
      newShortcuts.add(shortcutInfoFactory.withUpdatedIcon(oldInfo));
      if (++count >= maxDynamicShortcutsToCreate) {
        break;
      }
    }
    LogUtil.i("DynamicShortcuts.updateIcons", "updating %d shortcut icons", newShortcuts.size());
    shortcutManager.setDynamicShortcuts(newShortcuts);
  }

  @NonNull
  private Delta computeDelta(
      @NonNull List<ShortcutInfo> oldDynamicShortcuts,
      @NonNull Map<String, DialerShortcut> newDynamicShortcutsById) {
    Delta delta = new Delta();
    if (oldDynamicShortcuts.isEmpty()) {
      delta.shortcutsToAddById.putAll(newDynamicShortcutsById);
      return delta;
    }

    for (ShortcutInfo oldInfo : oldDynamicShortcuts) {
      // Check to see if the new shortcut list contains the existing shortcut.
      DialerShortcut newShortcut = newDynamicShortcutsById.get(oldInfo.getId());
      if (newShortcut != null) {
        if (newShortcut.needsUpdate(oldInfo)) {
          LogUtil.i("DynamicShortcuts.computeDelta", "contact updated");
          delta.shortcutsToUpdateById.put(oldInfo.getId(), newShortcut);
        } // else the shortcut hasn't changed, nothing to do to it
      } else {
        // The old shortcut is not in the new shortcut list, remove it.
        LogUtil.i("DynamicShortcuts.computeDelta", "contact removed");
        delta.shortcutIdsToRemove.add(oldInfo.getId());
      }
    }

    // Add any new shortcuts that were not in the old shortcuts.
    for (Entry<String, DialerShortcut> entry : newDynamicShortcutsById.entrySet()) {
      String newId = entry.getKey();
      DialerShortcut newShortcut = entry.getValue();
      if (!containsShortcut(oldDynamicShortcuts, newId)) {
        // The new shortcut was not found in the old shortcut list, so add it.
        LogUtil.i("DynamicShortcuts.computeDelta", "contact added");
        delta.shortcutsToAddById.put(newId, newShortcut);
      }
    }
    return delta;
  }

  private void applyDelta(@NonNull Delta delta) {
    ShortcutManager shortcutManager = getShortcutManager(context);
    // Must perform remove before performing add to avoid adding more than supported by system.
    if (!delta.shortcutIdsToRemove.isEmpty()) {
      shortcutManager.removeDynamicShortcuts(delta.shortcutIdsToRemove);
    }
    if (!delta.shortcutsToUpdateById.isEmpty()) {
      // Note: This may update pinned shortcuts as well. Pinned shortcuts which are also dynamic
      // are not updated by the pinned shortcut logic. The reason that they are updated here
      // instead of in the pinned shortcut logic is because setRank is required and only available
      // here.
      shortcutManager.updateShortcuts(
          shortcutInfoFactory.buildShortcutInfos(delta.shortcutsToUpdateById));
    }
    if (!delta.shortcutsToAddById.isEmpty()) {
      shortcutManager.addDynamicShortcuts(
          shortcutInfoFactory.buildShortcutInfos(delta.shortcutsToAddById));
    }
  }

  private boolean containsShortcut(
      @NonNull List<ShortcutInfo> shortcutInfos, @NonNull String shortcutId) {
    for (ShortcutInfo oldInfo : shortcutInfos) {
      if (oldInfo.getId().equals(shortcutId)) {
        return true;
      }
    }
    return false;
  }

  private static ShortcutManager getShortcutManager(Context context) {
    //noinspection WrongConstant
    return (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
  }
}
