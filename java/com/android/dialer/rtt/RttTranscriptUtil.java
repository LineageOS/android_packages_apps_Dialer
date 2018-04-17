/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.rtt;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.support.annotation.WorkerThread;
import com.android.dialer.common.Assert;
import com.android.dialer.rtt.RttTranscriptContract.RttTranscriptColumn;
import com.google.protobuf.InvalidProtocolBufferException;

/** Util class to save and load RTT transcript. */
public final class RttTranscriptUtil {

  private final RttTranscriptDatabaseHelper databaseHelper;

  public RttTranscriptUtil(Context context) {
    databaseHelper = new RttTranscriptDatabaseHelper(context.getApplicationContext());
  }

  @Override
  protected void finalize() throws Throwable {
    databaseHelper.close();
    super.finalize();
  }

  /** @return true if there is RTT transcript available. */
  @WorkerThread
  public boolean checkRttTranscriptAvailability(String transcriptId) {
    Assert.isWorkerThread();
    try (Cursor cursor =
        databaseHelper
            .getReadableDatabase()
            .query(
                RttTranscriptDatabaseHelper.TABLE,
                new String[] {RttTranscriptColumn.TRANSCRIPT_ID},
                RttTranscriptColumn.TRANSCRIPT_ID + " = ?",
                new String[] {transcriptId},
                null,
                null,
                null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return true;
      } else {
        return false;
      }
    }
  }

  @WorkerThread
  public RttTranscript getRttTranscript(String transcriptId) {
    Assert.isWorkerThread();
    try (Cursor cursor =
        databaseHelper
            .getReadableDatabase()
            .query(
                RttTranscriptDatabaseHelper.TABLE,
                new String[] {RttTranscriptColumn.TRANSCRIPT_DATA},
                RttTranscriptColumn.TRANSCRIPT_ID + " = ?",
                new String[] {transcriptId},
                null,
                null,
                null)) {
      if (cursor != null && cursor.moveToFirst()) {
        try {
          return RttTranscript.parseFrom(cursor.getBlob(0));
        } catch (InvalidProtocolBufferException e) {
          throw new RuntimeException("Parse failed for RTT transcript", e);
        }
      } else {
        return null;
      }
    }
  }

  @WorkerThread
  public void saveRttTranscript(RttTranscript rttTranscript) {
    Assert.isWorkerThread();
    ContentValues value = new ContentValues();
    value.put(RttTranscriptColumn.TRANSCRIPT_ID, rttTranscript.getId());
    value.put(RttTranscriptColumn.TRANSCRIPT_DATA, rttTranscript.toByteArray());
    long id =
        databaseHelper.getWritableDatabase().insert(RttTranscriptDatabaseHelper.TABLE, null, value);
    if (id < 0) {
      throw new RuntimeException("Failed to save RTT transcript");
    }
  }
}
