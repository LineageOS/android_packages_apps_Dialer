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
package com.android.voicemail.impl.transcribe;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.WorkerThread;
import android.support.v4.os.BuildCompat;
import android.util.Pair;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;

/** Helper class for reading and writing transcription data in the database */
@TargetApi(VERSION_CODES.O)
public class TranscriptionDbHelper {
  private static final String[] PROJECTION =
      new String[] {
        Voicemails.TRANSCRIPTION, // 0
        VoicemailCompat.TRANSCRIPTION_STATE // 1
      };

  public static final int TRANSCRIPTION = 0;
  public static final int TRANSCRIPTION_STATE = 1;

  private final ContentResolver contentResolver;
  private final Uri uri;

  TranscriptionDbHelper(Context context, Uri uri) {
    Assert.isNotNull(uri);
    this.contentResolver = context.getContentResolver();
    this.uri = uri;
  }

  @WorkerThread
  @TargetApi(VERSION_CODES.M) // used for try with resources
  Pair<String, Integer> getTranscriptionAndState() {
    Assert.checkArgument(BuildCompat.isAtLeastO());
    Assert.isWorkerThread();
    try (Cursor cursor = contentResolver.query(uri, PROJECTION, null, null, null)) {
      if (cursor == null) {
        LogUtil.e("TranscriptionDbHelper.getTranscriptionAndState", "query failed.");
        return null;
      }

      if (cursor.moveToFirst()) {
        String transcription = cursor.getString(TRANSCRIPTION);
        int transcriptionState = cursor.getInt(TRANSCRIPTION_STATE);
        return new Pair<>(transcription, transcriptionState);
      }
    }
    LogUtil.i("TranscriptionDbHelper.getTranscriptionAndState", "query returned no results");
    return null;
  }

  @WorkerThread
  void setTranscriptionState(int transcriptionState) {
    Assert.isWorkerThread();
    LogUtil.i(
        "TranscriptionDbHelper.setTranscriptionState",
        "uri: " + uri + ", state: " + transcriptionState);
    ContentValues values = new ContentValues();
    values.put(VoicemailCompat.TRANSCRIPTION_STATE, transcriptionState);
    updateDatabase(values);
  }

  @WorkerThread
  void setTranscriptionAndState(String transcription, int transcriptionState) {
    Assert.isWorkerThread();
    LogUtil.i(
        "TranscriptionDbHelper.setTranscriptionAndState",
        "uri: " + uri + ", state: " + transcriptionState);
    ContentValues values = new ContentValues();
    values.put(Voicemails.TRANSCRIPTION, transcription);
    values.put(VoicemailCompat.TRANSCRIPTION_STATE, transcriptionState);
    updateDatabase(values);
  }

  private void updateDatabase(ContentValues values) {
    int updatedCount = contentResolver.update(uri, values, null, null);
    if (updatedCount != 1) {
      LogUtil.e(
          "TranscriptionDbHelper.updateDatabase",
          "Wrong row count, should have updated 1 row, was: " + updatedCount);
    }
  }
}
