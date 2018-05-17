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
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract.Contacts;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.util.ArrayMap;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles refreshing of dialer pinned shortcuts.
 *
 * <p>Pinned shortcuts are icons that the user has dragged to their home screen from the dialer
 * application launcher shortcut menu, which is accessible by tapping and holding the dialer
 * launcher icon from the app drawer or a home screen.
 *
 * <p>When refreshing pinned shortcuts, we check to make sure that pinned contact information is
 * still up to date (e.g. photo and name). We also check to see if the contact has been deleted from
 * the user's contacts, and if so, we disable the pinned shortcut.
 */
@TargetApi(VERSION_CODES.N_MR1) // Shortcuts introduced in N MR1
final class PinnedShortcuts {

  private static final String[] PROJECTION =
      new String[] {
        Contacts._ID, Contacts.DISPLAY_NAME_PRIMARY, Contacts.CONTACT_LAST_UPDATED_TIMESTAMP,
      };

  private static class Delta {

    final List<String> shortcutIdsToDisable = new ArrayList<>();
    final Map<String, DialerShortcut> shortcutsToUpdateById = new ArrayMap<>();
  }

  private final Context context;
  private final ShortcutInfoFactory shortcutInfoFactory;

  PinnedShortcuts(@NonNull Context context) {
    this.context = context;
    this.shortcutInfoFactory = new ShortcutInfoFactory(context, new IconFactory(context));
  }

  /**
   * Performs a "complete refresh" of pinned shortcuts. This is done by (synchronously) querying for
   * all contacts which currently have pinned shortcuts. The query results are used to compute a
   * delta which contains a list of shortcuts which need to be updated (e.g. because of name/photo
   * changes) or disabled (if contacts were deleted). Note that pinned shortcuts cannot be deleted
   * programmatically and must be deleted by the user.
   *
   * <p>If the delta is non-empty, it is applied by making appropriate calls to the {@link
   * ShortcutManager} system service.
   *
   * <p>This is a slow blocking call which performs file I/O and should not be performed on the main
   * thread.
   */
  @WorkerThread
  public void refresh() {
    Assert.isWorkerThread();
    LogUtil.enterBlock("PinnedShortcuts.refresh");

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
        != PackageManager.PERMISSION_GRANTED) {
      LogUtil.i("PinnedShortcuts.refresh", "no contact permissions");
      return;
    }

    Delta delta = new Delta();
    ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
    for (ShortcutInfo shortcutInfo : shortcutManager.getPinnedShortcuts()) {
      if (shortcutInfo.isDeclaredInManifest()) {
        // We never update/disable the manifest shortcut (the "create new contact" shortcut).
        continue;
      }
      if (shortcutInfo.isDynamic()) {
        // If the shortcut is both pinned and dynamic, let the logic which updates dynamic shortcuts
        // handle the update. It would be problematic to try and apply the update here, because the
        // setRank is nonsensical for pinned shortcuts and therefore could not be calculated.
        continue;
      }
      // Exclude shortcuts not for contacts.
      String action = null;
      if (shortcutInfo.getIntent() != null) {
        action = shortcutInfo.getIntent().getAction();
      }
      if (action == null || !action.equals("com.android.dialer.shortcuts.CALL_CONTACT")) {
        continue;
      }

      String lookupKey = DialerShortcut.getLookupKeyFromShortcutInfo(shortcutInfo);
      Uri lookupUri = DialerShortcut.getLookupUriFromShortcutInfo(shortcutInfo);

      try (Cursor cursor =
          context.getContentResolver().query(lookupUri, PROJECTION, null, null, null)) {

        if (cursor == null || !cursor.moveToNext()) {
          LogUtil.i("PinnedShortcuts.refresh", "contact disabled");
          delta.shortcutIdsToDisable.add(shortcutInfo.getId());
          continue;
        }

        // Note: The lookup key may have changed but we cannot refresh it because that would require
        // changing the shortcut ID, which can only be accomplished with a remove and add; but
        // pinned shortcuts cannot be added or removed.
        DialerShortcut shortcut =
            DialerShortcut.builder()
                .setContactId(cursor.getLong(cursor.getColumnIndexOrThrow(Contacts._ID)))
                .setLookupKey(lookupKey)
                .setDisplayName(
                    cursor.getString(cursor.getColumnIndexOrThrow(Contacts.DISPLAY_NAME_PRIMARY)))
                .build();

        if (shortcut.needsUpdate(shortcutInfo)) {
          LogUtil.i("PinnedShortcuts.refresh", "contact updated");
          delta.shortcutsToUpdateById.put(shortcutInfo.getId(), shortcut);
        }
      }
    }
    applyDelta(delta);
  }

  private void applyDelta(@NonNull Delta delta) {
    ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
    String shortcutDisabledMessage =
        context.getResources().getString(R.string.dialer_shortcut_disabled_message);
    if (!delta.shortcutIdsToDisable.isEmpty()) {
      shortcutManager.disableShortcuts(delta.shortcutIdsToDisable, shortcutDisabledMessage);
    }
    if (!delta.shortcutsToUpdateById.isEmpty()) {
      // Note: This call updates both pinned and dynamic shortcuts, but the delta should contain
      // no dynamic shortcuts.
      if (!shortcutManager.updateShortcuts(
          shortcutInfoFactory.buildShortcutInfos(delta.shortcutsToUpdateById))) {
        LogUtil.i("PinnedShortcuts.applyDelta", "shortcutManager rate limited.");
      }
    }
  }
}
