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

package com.android.services.callrecorder.common;

import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

public final class CallRecording implements Parcelable {
    public String phoneNumber;
    public long creationTime;
    public String fileName;
    public long startRecordingTime;

    private static final String PUBLIC_DIRECTORY_NAME = "CallRecordings";

    public static final Parcelable.Creator<CallRecording> CREATOR = new
            Parcelable.Creator<CallRecording>() {
                public CallRecording createFromParcel(Parcel in) {
                    return new CallRecording(in);
                }

                public CallRecording[] newArray(int size) {
                    return new CallRecording[size];
                }
            };

    public CallRecording(String phoneNumber, long creationTime,
            String fileName, long startRecordingTime) {
        this.phoneNumber = phoneNumber;
        this.creationTime = creationTime;
        this.fileName = fileName;
        this.startRecordingTime = startRecordingTime;
    }

    public CallRecording(Parcel in) {
        phoneNumber = in.readString();
        creationTime = in.readLong();
        fileName = in.readString();
        startRecordingTime = in.readLong();
    }

    public File getFile() {
        File dir = Environment.getExternalStoragePublicDirectory(PUBLIC_DIRECTORY_NAME);
        return new File(dir, fileName);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(phoneNumber);
        out.writeLong(creationTime);
        out.writeString(fileName);
        out.writeLong(startRecordingTime);
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
