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

package com.android.dialer.searchfragment.directories;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import com.android.dialer.common.LogUtil;
import com.android.dialer.util.PermissionsUtil;
import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link CursorLoader} to load information about all directories (local and remote).
 *
 * <p>Information about a directory includes its ID, display name, etc, but doesn't include the
 * contacts in it.
 */
public final class DirectoriesCursorLoader extends CursorLoader {

  public static final String[] PROJECTION = {
    ContactsContract.Directory._ID,
    ContactsContract.Directory.DISPLAY_NAME,
    ContactsContract.Directory.PHOTO_SUPPORT,
  };

  // Indices of columns in PROJECTION
  private static final int ID = 0;
  private static final int DISPLAY_NAME = 1;
  private static final int PHOTO_SUPPORT = 2;

  public DirectoriesCursorLoader(Context context) {
    super(
        context,
        ContactsContract.Directory.ENTERPRISE_CONTENT_URI,
        PROJECTION,
        null,
        null,
        ContactsContract.Directory._ID);
  }

  @Override
  public Cursor loadInBackground() {
    if (!PermissionsUtil.hasContactsReadPermissions(getContext())) {
      LogUtil.i("DirectoriesCursorLoader.loadInBackground", "Contacts permission denied.");
      return null;
    }
    return super.loadInBackground();
  }

  /**
   * Creates a complete list of directories from the data set loaded by this loader.
   *
   * @param cursor A cursor pointing to the data set loaded by this loader. The caller must ensure
   *     the cursor is not null.
   * @return A list of directories.
   */
  public static List<Directory> toDirectories(Cursor cursor) {
    if (cursor == null) {
      LogUtil.i("DirectoriesCursorLoader.toDirectories", "Cursor was null");
      return new ArrayList<>();
    }

    List<Directory> directories = new ArrayList<>();
    cursor.moveToPosition(-1);
    while (cursor.moveToNext()) {
      directories.add(
          Directory.create(
              cursor.getInt(ID),
              cursor.getString(DISPLAY_NAME),
              /* supportsPhotos = */ cursor.getInt(PHOTO_SUPPORT) != 0));
    }
    return directories;
  }

  /** POJO representing the results returned from {@link DirectoriesCursorLoader}. */
  @AutoValue
  public abstract static class Directory {
    public static Directory create(long id, @Nullable String displayName, boolean supportsPhotos) {
      return new AutoValue_DirectoriesCursorLoader_Directory(id, displayName, supportsPhotos);
    }

    public abstract long getId();

    /** Returns a user facing display name of the directory. Null if none exists. */
    public abstract @Nullable String getDisplayName();

    public abstract boolean supportsPhotos();
  }
}
