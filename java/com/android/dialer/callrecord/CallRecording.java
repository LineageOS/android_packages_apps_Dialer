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

package com.android.dialer.callrecord;

import android.content.ContentValues;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import java.io.File;

public final class CallRecording implements Parcelable {
  public String phoneNumber;
  public long creationTime;
  public String fileName;
  public long startRecordingTime;
  public long mediaId;

  public static final Parcelable.Creator<CallRecording> CREATOR =
      new Parcelable.Creator<CallRecording>() {
    @Override
    public CallRecording createFromParcel(Parcel in) {
      return new CallRecording(in);
    }

    @Override
    public CallRecording[] newArray(int size) {
      return new CallRecording[size];
    }
  };

  public CallRecording(String phoneNumber, long creationTime,
      String fileName, long startRecordingTime, long mediaId) {
    this.phoneNumber = phoneNumber;
    this.creationTime = creationTime;
    this.fileName = fileName;
    this.startRecordingTime = startRecordingTime;
    this.mediaId = mediaId;
  }

  public CallRecording(Parcel in) {
    phoneNumber = in.readString();
    creationTime = in.readLong();
    fileName = in.readString();
    startRecordingTime = in.readLong();
    mediaId = in.readLong();
  }

  public static ContentValues generateMediaInsertValues(String fileName, long creationTime) {
    final ContentValues cv = new ContentValues(5);

    cv.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Call Recordings");
    cv.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
    cv.put(MediaStore.Audio.Media.DATE_TAKEN, creationTime);
    cv.put(MediaStore.Audio.Media.IS_PENDING, 1);

    final String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
    final String mime = !TextUtils.isEmpty(extension)
        ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) : "audio/*";
    cv.put(MediaStore.Audio.Media.MIME_TYPE, mime);

    return cv;
  }

  public static ContentValues generateCompletedValues() {
    final ContentValues cv = new ContentValues(1);
    cv.put(MediaStore.Audio.Media.IS_PENDING, 0);
    return cv;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeString(phoneNumber);
    out.writeLong(creationTime);
    out.writeString(fileName);
    out.writeLong(startRecordingTime);
    out.writeLong(mediaId);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public String toString() {
    return "phoneNumber=" + phoneNumber + ", creationTime=" + creationTime +
        ", fileName=" + fileName + ", startRecordingTime=" + startRecordingTime;
  }
}
