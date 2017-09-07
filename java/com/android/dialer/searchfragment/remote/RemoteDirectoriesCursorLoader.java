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

package com.android.dialer.searchfragment.remote;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import com.google.auto.value.AutoValue;

/** CursorLoader to load the list of remote directories on the device. */
public final class RemoteDirectoriesCursorLoader extends CursorLoader {

  /** Positions of columns in {@code PROJECTIONS}. */
  private static final int ID = 0;

  private static final int DISPLAY_NAME = 1;
  private static final int PHOTO_SUPPORT = 2;

  @VisibleForTesting
  static final String[] PROJECTION = {
    ContactsContract.Directory._ID,
    ContactsContract.Directory.DISPLAY_NAME,
    ContactsContract.Directory.PHOTO_SUPPORT,
  };

  public RemoteDirectoriesCursorLoader(Context context) {
    super(context, getContentUri(), PROJECTION, null, null, ContactsContract.Directory._ID);
  }

  /** @return current cursor row represented as a {@link Directory}. */
  public static Directory readDirectory(Cursor cursor) {
    return Directory.create(
        cursor.getInt(ID), cursor.getString(DISPLAY_NAME), cursor.getInt(PHOTO_SUPPORT) != 0);
  }

  private static Uri getContentUri() {
    return VERSION.SDK_INT >= VERSION_CODES.N
        ? ContactsContract.Directory.ENTERPRISE_CONTENT_URI
        : ContactsContract.Directory.CONTENT_URI;
  }

  /** POJO representing the results returned from {@link RemoteDirectoriesCursorLoader}. */
  @AutoValue
  public abstract static class Directory {
    public static Directory create(int id, @Nullable String displayName, boolean supportsPhotos) {
      return new AutoValue_RemoteDirectoriesCursorLoader_Directory(id, displayName, supportsPhotos);
    }

    public abstract int getId();

    /** Returns a user facing display name of the directory. Null if none exists. */
    abstract @Nullable String getDisplayName();

    abstract boolean supportsPhotos();
  }
}
