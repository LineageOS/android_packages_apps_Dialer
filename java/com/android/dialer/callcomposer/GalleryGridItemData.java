/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.callcomposer;

import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore.Images.Media;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import java.io.File;
import java.util.Objects;

/** Provides data for GalleryGridItemView */
public final class GalleryGridItemData implements Parcelable {
  public static final String[] IMAGE_PROJECTION =
      new String[] {Media._ID, Media.DATA, Media.MIME_TYPE, Media.DATE_MODIFIED};

  private static final int INDEX_DATA_PATH = 1;
  private static final int INDEX_MIME_TYPE = 2;
  private static final int INDEX_DATE_MODIFIED = 3;

  private String filePath;
  private String mimeType;
  private long dateModifiedSeconds;

  public GalleryGridItemData() {}

  public GalleryGridItemData(GalleryGridItemData copyData) {
    filePath = Assert.isNotNull(copyData.getFilePath());
    mimeType = Assert.isNotNull(copyData.getMimeType());
    dateModifiedSeconds = Assert.isNotNull(copyData.getDateModifiedSeconds());
  }

  public GalleryGridItemData(Cursor cursor) {
    bind(cursor);
  }

  public void bind(Cursor cursor) {
    mimeType = Assert.isNotNull(cursor.getString(INDEX_MIME_TYPE));
    String dateModified = Assert.isNotNull(cursor.getString(INDEX_DATE_MODIFIED));
    dateModifiedSeconds = !TextUtils.isEmpty(dateModified) ? Long.parseLong(dateModified) : -1;
    filePath = Assert.isNotNull(cursor.getString(INDEX_DATA_PATH));
  }

  @Nullable
  public String getFilePath() {
    return filePath;
  }

  @Nullable
  public Uri getFileUri() {
    return TextUtils.isEmpty(filePath) ? null : Uri.fromFile(new File(filePath));
  }

  /** @return The date in seconds. This can be negative if we could not retrieve date info */
  public long getDateModifiedSeconds() {
    return dateModifiedSeconds;
  }

  public String getMimeType() {
    return mimeType;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof GalleryGridItemData
        && Objects.equals(mimeType, ((GalleryGridItemData) obj).mimeType)
        && Objects.equals(filePath, ((GalleryGridItemData) obj).filePath)
        && ((GalleryGridItemData) obj).dateModifiedSeconds == dateModifiedSeconds;
  }

  @Override
  public int hashCode() {
    return Objects.hash(filePath, mimeType, dateModifiedSeconds);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(filePath);
    dest.writeString(mimeType);
    dest.writeLong(dateModifiedSeconds);
  }

  public static final Creator<GalleryGridItemData> CREATOR =
      new Creator<GalleryGridItemData>() {
        @Override
        public GalleryGridItemData createFromParcel(Parcel in) {
          return new GalleryGridItemData(in);
        }

        @Override
        public GalleryGridItemData[] newArray(int size) {
          return new GalleryGridItemData[size];
        }
      };

  private GalleryGridItemData(Parcel in) {
    filePath = in.readString();
    mimeType = in.readString();
    dateModifiedSeconds = in.readLong();
  }
}
