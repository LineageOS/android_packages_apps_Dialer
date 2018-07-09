/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.dialer.callrecord.impl;

import android.app.Service;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.provider.Settings;
import android.util.Log;

import com.android.dialer.callrecord.CallRecording;
import com.android.dialer.callrecord.ICallRecorderService;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.android.dialer.R;

public class CallRecorderService extends Service {
  private static final String TAG = "CallRecorderService";
  private static final boolean DBG = false;

  private MediaRecorder mMediaRecorder = null;
  private CallRecording mCurrentRecording = null;

  private SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyMMdd_HHmmssSSS");

  private final ICallRecorderService.Stub mBinder = new ICallRecorderService.Stub() {
    @Override
    public CallRecording stopRecording() {
      return stopRecordingInternal();
    }

    @Override
    public boolean startRecording(String phoneNumber, long creationTime) throws RemoteException {
      return startRecordingInternal(phoneNumber, creationTime);
    }

    @Override
    public boolean isRecording() throws RemoteException {
      return mMediaRecorder != null;
    }

    @Override
    public CallRecording getActiveRecording() throws RemoteException {
      return mCurrentRecording;
    }
  };

  @Override
  public void onCreate() {
    if (DBG) Log.d(TAG, "Creating CallRecorderService");
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }

  private int getAudioSource() {
    return getResources().getInteger(R.integer.call_recording_audio_source);
  }

  private int getAudioFormatChoice() {
    // This replicates PreferenceManager.getDefaultSharedPreferences, except
    // that we need multi process preferences, as the pref is written in a separate
    // process (com.android.dialer vs. com.android.incallui)
    final String prefName = getPackageName() + "_preferences";
    final SharedPreferences prefs = getSharedPreferences(prefName, MODE_MULTI_PROCESS);

    try {
      String value = prefs.getString(getString(R.string.call_recording_format_key), null);
      if (value != null) {
        return Integer.parseInt(value);
      }
    } catch (NumberFormatException e) {
      // ignore and fall through
    }
    return 0;
  }

  private synchronized boolean startRecordingInternal(String phoneNumber, long creationTime) {
    if (mMediaRecorder != null) {
      if (DBG) {
        Log.d(TAG, "Start called with recording in progress, stopping  current recording");
      }
      stopRecordingInternal();
    }

    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "Record audio permission not granted, can't record call");
      return false;
    }

    if (DBG) Log.d(TAG, "Starting recording");

    mMediaRecorder = new MediaRecorder();
    try {
      int audioSource = getAudioSource();
      int formatChoice = getAudioFormatChoice();
      if (DBG) Log.d(TAG, "Creating media recorder with audio source " + audioSource);
      mMediaRecorder.setAudioSource(audioSource);
      mMediaRecorder.setOutputFormat(formatChoice == 0
          ? MediaRecorder.OutputFormat.AMR_WB : MediaRecorder.OutputFormat.MPEG_4);
      mMediaRecorder.setAudioEncoder(formatChoice == 0
          ? MediaRecorder.AudioEncoder.AMR_WB : MediaRecorder.AudioEncoder.AAC);
    } catch (IllegalStateException e) {
      Log.w(TAG, "Error initializing media recorder", e);
      mMediaRecorder.reset();
      mMediaRecorder.release();
      mMediaRecorder = null;
      return false;
    }

    String fileName = generateFilename(phoneNumber);
    Uri uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            CallRecording.generateMediaInsertValues(fileName, creationTime));

    try {
      ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w");
      if (pfd == null) {
        throw new IOException("Opening file for URI " + uri + " failed");
      }
      mMediaRecorder.setOutputFile(pfd.getFileDescriptor());
      mMediaRecorder.prepare();
      mMediaRecorder.start();

      long mediaId = Long.parseLong(uri.getLastPathSegment());
      mCurrentRecording = new CallRecording(phoneNumber, creationTime,
              fileName, System.currentTimeMillis(), mediaId);
      return true;
    } catch (IOException | IllegalStateException e) {
      Log.w(TAG, "Could not start recording", e);
      getContentResolver().delete(uri, null, null);
    } catch (RuntimeException e) {
      getContentResolver().delete(uri, null, null);
      // only catch exceptions thrown by the MediaRecorder JNI code
      if (e.getMessage().indexOf("start failed") >= 0) {
        Log.w(TAG, "Could not start recording", e);
      } else {
        throw e;
      }
    }

    mMediaRecorder.reset();
    mMediaRecorder.release();
    mMediaRecorder = null;

    return false;
  }

  private synchronized CallRecording stopRecordingInternal() {
    CallRecording recording = mCurrentRecording;
    if (DBG) Log.d(TAG, "Stopping current recording");
    if (mMediaRecorder != null) {
      try {
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        mMediaRecorder.release();
      } catch (IllegalStateException e) {
        Log.e(TAG, "Exception closing media recorder", e);
      }

      Uri uri = ContentUris.withAppendedId(
          MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mCurrentRecording.mediaId);
      getContentResolver().update(uri, CallRecording.generateCompletedValues(), null, null);

      mMediaRecorder = null;
      mCurrentRecording = null;
    }
    return recording;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (DBG) Log.d(TAG, "Destroying CallRecorderService");
  }

  private String generateFilename(String number) {
    String timestamp = DATE_FORMAT.format(new Date());

    if (TextUtils.isEmpty(number)) {
      number = "unknown";
    }

    int formatChoice = getAudioFormatChoice();
    String extension = formatChoice == 0 ? ".amr" : ".m4a";
    return number + "_" + timestamp + extension;
  }

  public static boolean isEnabled(Context context) {
    return context.getResources().getBoolean(R.bool.call_recording_enabled);
  }
}
