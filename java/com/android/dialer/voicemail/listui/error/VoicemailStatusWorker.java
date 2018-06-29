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
 * limitations under the License.
 */

package com.android.dialer.voicemail.listui.error;

import android.content.Context;
import android.database.Cursor;
import android.provider.VoicemailContract.Status;
import android.support.annotation.Nullable;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.voicemailstatus.VoicemailStatusQuery;
import com.android.voicemail.VoicemailComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * Worker for {@link com.android.dialer.common.concurrent.DialerExecutors} to fetch voicemail status
 */
public class VoicemailStatusWorker implements Worker<Context, List<VoicemailStatus>> {

  @Nullable
  @Override
  public List<VoicemailStatus> doInBackground(@Nullable Context context) throws Throwable {
    List<VoicemailStatus> statuses = new ArrayList<>();
    if (!TelecomUtil.hasReadWriteVoicemailPermissions(context)) {
      return statuses;
    }
    StringBuilder where = new StringBuilder();
    java.util.List<String> selectionArgs = new ArrayList<>();

    VoicemailComponent.get(context)
        .getVoicemailClient()
        .appendOmtpVoicemailStatusSelectionClause(context, where, selectionArgs);

    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                Status.CONTENT_URI,
                VoicemailStatusQuery.getProjection(),
                where.toString(),
                selectionArgs.toArray(new String[selectionArgs.size()]),
                null)) {
      if (cursor == null) {
        return statuses;
      }

      for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
        statuses.add(new VoicemailStatus(context, cursor));
      }
    }

    return statuses;
  }
}
