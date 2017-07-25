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
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import com.android.dialer.common.LogUtil;

/** CursorLoader to load the list of remote directories on the device. */
public final class RemoteDirectoriesCursorLoader extends CursorLoader {

  /** Positions of columns in {@code PROJECTIONS}. */
  private static final int ID = 0;

  private static final int PACKAGE_NAME = 1;
  private static final int TYPE_RESOURCE_ID = 2;
  private static final int DISPLAY_NAME = 3;
  private static final int PHOTO_SUPPORT = 4;

  @VisibleForTesting
  static final String[] PROJECTION = {
    ContactsContract.Directory._ID,
    ContactsContract.Directory.PACKAGE_NAME,
    ContactsContract.Directory.TYPE_RESOURCE_ID,
    ContactsContract.Directory.DISPLAY_NAME,
    ContactsContract.Directory.PHOTO_SUPPORT,
  };

  RemoteDirectoriesCursorLoader(Context context) {
    super(context, getContentUri(), PROJECTION, null, null, ContactsContract.Directory._ID);
  }

  /**
   * Returns the type of directory as a String (e.g. "Corporate Directory"). Null if the directory
   * type cannot be found.
   */
  @Nullable
  private static String getDirectoryType(Context context, Cursor cursor) {
    String packageName = cursor.getString(PACKAGE_NAME);
    int typeResourceId = cursor.getInt(TYPE_RESOURCE_ID);
    if (TextUtils.isEmpty(packageName) || typeResourceId == 0) {
      return null;
    }

    try {
      return context
          .getPackageManager()
          .getResourcesForApplication(packageName)
          .getString(typeResourceId);
    } catch (NameNotFoundException e) {
      LogUtil.e(
          "ContactEntryListAdapter.loadInBackground",
          "cannot obtain directory type from package: %s",
          packageName);
      return null;
    }
  }

  /** @return current cursor row represented as a {@link Directory}. */
  public static Directory readDirectory(Context context, Cursor cursor) {
    return new Directory(
        cursor.getInt(ID),
        cursor.getString(DISPLAY_NAME),
        getDirectoryType(context, cursor),
        cursor.getInt(PHOTO_SUPPORT) != 0);
  }

  private static Uri getContentUri() {
    return VERSION.SDK_INT >= VERSION_CODES.N
        ? ContactsContract.Directory.ENTERPRISE_CONTENT_URI
        : ContactsContract.Directory.CONTENT_URI;
  }

  /** POJO representing the results returned from {@link RemoteDirectoriesCursorLoader}. */
  public static class Directory {

    public final int id;
    // TODO(calderwoodra): investigate which of these fields will be used as the display name and
    // update the fields and javadoc accordingly.
    /** An optional name that can be used in the UI to represent the directory. */
    @Nullable public final String name;

    @Nullable public final String type;
    public final boolean supportsPhotos;

    public Directory(int id, String name, @Nullable String type, boolean supportsPhotos) {
      this.id = id;
      this.name = name;
      this.type = type;
      this.supportsPhotos = supportsPhotos;
    }
  }
}
