/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.dialer.app.calllog;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION_CODES;
import android.provider.CallLog;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.AsyncTaskExecutor;
import com.android.dialer.common.concurrent.AsyncTaskExecutors;
import com.android.dialer.util.PermissionsUtil;
import com.android.voicemail.VoicemailClient;

@TargetApi(VERSION_CODES.M)
public class CallLogAsyncTaskUtil {

  private static final String TAG = "CallLogAsyncTaskUtil";
  private static AsyncTaskExecutor sAsyncTaskExecutor;

  private static void initTaskExecutor() {
    sAsyncTaskExecutor = AsyncTaskExecutors.createThreadPoolExecutor();
  }

  public static void markVoicemailAsRead(
      @NonNull final Context context, @NonNull final Uri voicemailUri) {
    LogUtil.enterBlock("CallLogAsyncTaskUtil.markVoicemailAsRead, voicemailUri: " + voicemailUri);
    if (sAsyncTaskExecutor == null) {
      initTaskExecutor();
    }

    sAsyncTaskExecutor.submit(
        Tasks.MARK_VOICEMAIL_READ,
        new AsyncTask<Void, Void, Void>() {
          @Override
          public Void doInBackground(Void... params) {
            ContentValues values = new ContentValues();
            values.put(Voicemails.IS_READ, true);
            // "External" changes to the database will be automatically marked as dirty, but this
            // voicemail might be from dialer so it need to be marked manually.
            values.put(Voicemails.DIRTY, 1);
            if (context
                    .getContentResolver()
                    .update(voicemailUri, values, Voicemails.IS_READ + " = 0", null)
                > 0) {
              uploadVoicemailLocalChangesToServer(context);
            }

            CallLogNotificationsService.markAllNewVoicemailsAsOld(context);
            return null;
          }
        });
  }

  public static void deleteVoicemail(
      @NonNull final Context context,
      final Uri voicemailUri,
      @Nullable final CallLogAsyncTaskListener callLogAsyncTaskListener) {
    if (sAsyncTaskExecutor == null) {
      initTaskExecutor();
    }

    sAsyncTaskExecutor.submit(
        Tasks.DELETE_VOICEMAIL,
        new AsyncTask<Void, Void, Void>() {
          @Override
          public Void doInBackground(Void... params) {
            deleteVoicemailSynchronous(context, voicemailUri);
            return null;
          }

          @Override
          public void onPostExecute(Void result) {
            if (callLogAsyncTaskListener != null) {
              callLogAsyncTaskListener.onDeleteVoicemail();
            }
          }
        });
  }

  public static void deleteVoicemailSynchronous(Context context, Uri voicemailUri) {
    ContentValues values = new ContentValues();
    values.put(Voicemails.DELETED, "1");
    context.getContentResolver().update(voicemailUri, values, null, null);
    // TODO(b/35440541): check which source package is changed. Don't need
    // to upload changes on foreign voicemails, they will get a PROVIDER_CHANGED
    uploadVoicemailLocalChangesToServer(context);
  }

  public static void markCallAsRead(@NonNull final Context context, @NonNull final long[] callIds) {
    if (!PermissionsUtil.hasPhonePermissions(context)
        || !PermissionsUtil.hasCallLogWritePermissions(context)) {
      return;
    }
    if (sAsyncTaskExecutor == null) {
      initTaskExecutor();
    }

    sAsyncTaskExecutor.submit(
        Tasks.MARK_CALL_READ,
        new AsyncTask<Void, Void, Void>() {
          @Override
          public Void doInBackground(Void... params) {

            StringBuilder where = new StringBuilder();
            where.append(CallLog.Calls.TYPE).append(" = ").append(CallLog.Calls.MISSED_TYPE);
            where.append(" AND ");

            Long[] callIdLongs = new Long[callIds.length];
            for (int i = 0; i < callIds.length; i++) {
              callIdLongs[i] = callIds[i];
            }
            where
                .append(CallLog.Calls._ID)
                .append(" IN (" + TextUtils.join(",", callIdLongs) + ")");

            ContentValues values = new ContentValues(1);
            values.put(CallLog.Calls.IS_READ, "1");
            context
                .getContentResolver()
                .update(CallLog.Calls.CONTENT_URI, values, where.toString(), null);
            return null;
          }
        });
  }

  /** The enumeration of {@link AsyncTask} objects used in this class. */
  public enum Tasks {
    DELETE_VOICEMAIL,
    DELETE_CALL,
    MARK_VOICEMAIL_READ,
    MARK_CALL_READ,
    GET_CALL_DETAILS,
    UPDATE_DURATION,
  }

  public interface CallLogAsyncTaskListener {
    void onDeleteVoicemail();
  }

  private static void uploadVoicemailLocalChangesToServer(Context context) {
    Intent intent = new Intent(VoicemailClient.ACTION_UPLOAD);
    intent.setPackage(context.getPackageName());
    context.sendBroadcast(intent);
  }
}
