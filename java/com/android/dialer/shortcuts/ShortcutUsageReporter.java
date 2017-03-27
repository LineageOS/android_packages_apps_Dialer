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
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.AsyncTaskExecutor;
import com.android.dialer.common.concurrent.AsyncTaskExecutors;

/**
 * Reports outgoing calls as shortcut usage.
 *
 * <p>Note that all outgoing calls are considered shortcut usage, no matter where they are initiated
 * from (i.e. from anywhere in the dialer app, or even from other apps).
 *
 * <p>This allows launcher applications to provide users with shortcut suggestions, even if the user
 * isn't already using shortcuts.
 */
@TargetApi(VERSION_CODES.N_MR1) // Shortcuts introduced in N_MR1
public class ShortcutUsageReporter {

  private static final AsyncTaskExecutor EXECUTOR = AsyncTaskExecutors.createThreadPoolExecutor();

  /**
   * Called when an outgoing call is added to the call list in order to report outgoing calls as
   * shortcut usage. This should be called exactly once for each outgoing call.
   *
   * <p>Asynchronously queries the contacts database for the contact's lookup key which corresponds
   * to the provided phone number, and uses that to report shortcut usage.
   *
   * @param context used to access ShortcutManager system service
   * @param phoneNumber the phone number being called
   */
  @MainThread
  public static void onOutgoingCallAdded(@NonNull Context context, @Nullable String phoneNumber) {
    Assert.isMainThread();
    Assert.isNotNull(context);

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || TextUtils.isEmpty(phoneNumber)) {
      return;
    }

    EXECUTOR.submit(Task.ID, new Task(context), phoneNumber);
  }

  private static final class Task extends AsyncTask<String, Void, Void> {
    private static final String ID = "ShortcutUsageReporter.Task";

    private final Context context;

    public Task(Context context) {
      this.context = context;
    }

    /** @param phoneNumbers array with exactly one non-empty phone number */
    @Override
    @WorkerThread
    protected Void doInBackground(@NonNull String... phoneNumbers) {
      Assert.isWorkerThread();

      String lookupKey = queryForLookupKey(phoneNumbers[0]);
      if (!TextUtils.isEmpty(lookupKey)) {
        LogUtil.i("ShortcutUsageReporter.backgroundLogUsage", "%s", lookupKey);
        ShortcutManager shortcutManager =
            (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);

        // Note: There may not currently exist a shortcut with the provided key, but it is logged
        // anyway, so that launcher applications at least have the information should the shortcut
        // be created in the future.
        shortcutManager.reportShortcutUsed(lookupKey);
      }
      return null;
    }

    @Nullable
    @WorkerThread
    private String queryForLookupKey(String phoneNumber) {
      Assert.isWorkerThread();

      if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
          != PackageManager.PERMISSION_GRANTED) {
        LogUtil.i("ShortcutUsageReporter.queryForLookupKey", "No contact permissions");
        return null;
      }

      Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
      try (Cursor cursor =
          context
              .getContentResolver()
              .query(uri, new String[] {Contacts.LOOKUP_KEY}, null, null, null)) {

        if (cursor == null || !cursor.moveToNext()) {
          return null; // No contact for dialed number
        }
        // Arbitrarily use first result.
        return cursor.getString(cursor.getColumnIndex(Contacts.LOOKUP_KEY));
      }
    }
  }
}
