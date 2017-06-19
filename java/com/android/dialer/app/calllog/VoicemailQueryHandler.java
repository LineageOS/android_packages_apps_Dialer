/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;

/** Handles asynchronous queries to the call log for voicemail. */
public class VoicemailQueryHandler extends AsyncQueryHandler {

  /** The token for the query to mark all new voicemails as old. */
  private static final int UPDATE_MARK_VOICEMAILS_AS_OLD_TOKEN = 50;

  @MainThread
  private VoicemailQueryHandler(ContentResolver contentResolver) {
    super(contentResolver);
    Assert.isMainThread();
  }

  @WorkerThread
  public static void markAllNewVoicemailsAsRead(final @NonNull Context context) {
    ThreadUtil.postOnUiThread(
        () -> {
          new VoicemailQueryHandler(context.getContentResolver()).markNewVoicemailsAsOld(null);
        });
  }

  @WorkerThread
  public static void markSingleNewVoicemailAsRead(
      final @NonNull Context context, final Uri voicemailUri) {
    if (voicemailUri == null) {
      LogUtil.e("VoicemailQueryHandler.markSingleNewVoicemailAsRead", "voicemail URI is null");
      return;
    }
    ThreadUtil.postOnUiThread(
        () -> {
          new VoicemailQueryHandler(context.getContentResolver())
              .markNewVoicemailsAsOld(voicemailUri);
        });
  }

  /** Updates all new voicemails to mark them as old. */
  private void markNewVoicemailsAsOld(@Nullable Uri voicemailUri) {
    // Mark all "new" voicemails as not new anymore.
    StringBuilder where = new StringBuilder();
    where.append(Calls.NEW);
    where.append(" = 1 AND ");
    where.append(Calls.TYPE);
    where.append(" = ?");

    if (voicemailUri != null) {
      where.append(" AND ").append(Calls.VOICEMAIL_URI).append(" = ?");
    }

    ContentValues values = new ContentValues(1);
    values.put(Calls.NEW, "0");

    startUpdate(
        UPDATE_MARK_VOICEMAILS_AS_OLD_TOKEN,
        null,
        Calls.CONTENT_URI_WITH_VOICEMAIL,
        values,
        where.toString(),
        voicemailUri == null
            ? new String[] {Integer.toString(Calls.VOICEMAIL_TYPE)}
            : new String[] {Integer.toString(Calls.VOICEMAIL_TYPE), voicemailUri.toString()});
  }
}
