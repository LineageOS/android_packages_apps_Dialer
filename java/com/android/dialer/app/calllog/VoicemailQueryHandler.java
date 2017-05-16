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

import android.app.NotificationManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import com.android.dialer.app.R;
import com.android.dialer.common.Assert;
import com.android.dialer.notification.GroupedNotificationUtil;

/** Handles asynchronous queries to the call log for voicemail. */
public class VoicemailQueryHandler extends AsyncQueryHandler {

  private static final String TAG = "VoicemailQueryHandler";

  /** The token for the query to mark all new voicemails as old. */
  private static final int UPDATE_MARK_VOICEMAILS_AS_OLD_TOKEN = 50;

  private Context mContext;

  @MainThread
  public VoicemailQueryHandler(Context context, ContentResolver contentResolver) {
    super(contentResolver);
    Assert.isMainThread();
    mContext = context;
  }

  /** Updates all new voicemails to mark them as old. */
  public void markNewVoicemailsAsOld(@Nullable Uri voicemailUri) {
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

    GroupedNotificationUtil.removeNotification(
        mContext.getSystemService(NotificationManager.class),
        voicemailUri != null ? voicemailUri.toString() : null,
        R.id.notification_visual_voicemail,
        DefaultVoicemailNotifier.VISUAL_VOICEMAIL_NOTIFICATION_TAG);
  }
}
