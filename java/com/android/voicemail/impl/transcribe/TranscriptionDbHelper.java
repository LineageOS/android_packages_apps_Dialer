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
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.util.Pair;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.android.provider.VoicemailCompat;
import java.util.ArrayList;
import java.util.List;

/** Helper class for reading and writing transcription data in the database */
@TargetApi(Build.VERSION_CODES.O)
public class TranscriptionDbHelper {
  @VisibleForTesting
  static final String[] PROJECTION =
      new String[] {
        Voicemails._ID, // 0
        Voicemails.TRANSCRIPTION, // 1
        VoicemailCompat.TRANSCRIPTION_STATE // 2
      };

  static final int ID = 0;
  static final int TRANSCRIPTION = 1;
  static final int TRANSCRIPTION_STATE = 2;

  private final ContentResolver contentResolver;
  private final Uri uri;

  TranscriptionDbHelper(Context context, Uri uri) {
    Assert.isNotNull(uri);
    this.contentResolver = context.getContentResolver();
    this.uri = uri;
  }

  TranscriptionDbHelper(Context context) {
    this(context, Voicemails.buildSourceUri(context.getPackageName()));
  }

  @WorkerThread
  Pair<String, Integer> getTranscriptionAndState() {
    Assert.checkState(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
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
  List<Uri> getUntranscribedVoicemails() {
    Assert.checkState(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
    Assert.isWorkerThread();
    List<Uri> untranscribed = new ArrayList<>();
    String whereClause =
        "("
            + Voicemails.TRANSCRIPTION
            + " is NULL OR "
            + Voicemails.TRANSCRIPTION
            + " = '')"
            + " AND "
            + VoicemailCompat.TRANSCRIPTION_STATE
            + "=?";
    String[] whereArgs = {String.valueOf(VoicemailCompat.TRANSCRIPTION_NOT_STARTED)};
    try (Cursor cursor = contentResolver.query(uri, PROJECTION, whereClause, whereArgs, null)) {
      if (cursor == null) {
        LogUtil.e("TranscriptionDbHelper.getUntranscribedVoicemails", "query failed.");
      } else {
        while (cursor.moveToNext()) {
          untranscribed.add(ContentUris.withAppendedId(uri, cursor.getLong(ID)));
        }
      }
    }
    return untranscribed;
  }

  @WorkerThread
  List<Uri> getTranscribingVoicemails() {
    Assert.checkState(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
    Assert.isWorkerThread();
    List<Uri> inProgress = new ArrayList<>();
    String whereClause = VoicemailCompat.TRANSCRIPTION_STATE + "=?";
    String[] whereArgs = {String.valueOf(VoicemailCompat.TRANSCRIPTION_IN_PROGRESS)};
    try (Cursor cursor = contentResolver.query(uri, PROJECTION, whereClause, whereArgs, null)) {
      if (cursor == null) {
        LogUtil.e("TranscriptionDbHelper.getTranscribingVoicemails", "query failed.");
      } else {
        while (cursor.moveToNext()) {
          inProgress.add(ContentUris.withAppendedId(uri, cursor.getLong(ID)));
        }
      }
    }
    return inProgress;
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
