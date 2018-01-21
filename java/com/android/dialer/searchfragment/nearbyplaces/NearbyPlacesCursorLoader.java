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
 * limitations under the License.
 */

package com.android.dialer.searchfragment.nearbyplaces;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import com.android.contacts.common.extensions.PhoneDirectoryExtenderAccessor;
import com.android.dialer.common.LogUtil;
import com.android.dialer.searchfragment.common.Projections;
import java.util.List;

/** Cursor loader for nearby places search results. */
public final class NearbyPlacesCursorLoader extends CursorLoader {

  private static final String MAX_RESULTS = "3";
  private static final long INVALID_DIRECTORY_ID = Long.MAX_VALUE;
  private final long directoryId;

  /**
   * @param directoryIds List of directoryIds associated with all directories on device. Required in
   *     order to find a directory ID for the nearby places cursor that doesn't collide with
   *     existing directories.
   */
  public NearbyPlacesCursorLoader(Context context, String query, @NonNull List<Long> directoryIds) {
    super(context, getContentUri(context, query), Projections.DATA_PROJECTION, null, null, null);
    this.directoryId = getDirectoryId(directoryIds);
  }

  @Override
  public Cursor loadInBackground() {
    if (directoryId == INVALID_DIRECTORY_ID) {
      LogUtil.i("NearbyPlacesCursorLoader.loadInBackground", "directory id not set.");
      return null;
    }
    return NearbyPlacesCursor.newInstance(getContext(), super.loadInBackground(), directoryId);
  }

  private static Uri getContentUri(Context context, String query) {
    return PhoneDirectoryExtenderAccessor.get(context)
        .getContentUri()
        .buildUpon()
        .appendPath(query)
        .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, MAX_RESULTS)
        .build();
  }

  private static long getDirectoryId(List<Long> directoryIds) {
    if (directoryIds.isEmpty()) {
      return INVALID_DIRECTORY_ID;
    }

    // The Directory.LOCAL_INVISIBLE might not be a directory we use, but we can't reuse it's
    // "special" ID.
    long maxId = ContactsContract.Directory.LOCAL_INVISIBLE;
    for (int i = 0, n = directoryIds.size(); i < n; i++) {
      long id = directoryIds.get(i);
      if (id > maxId) {
        maxId = id;
      }
    }
    // Add one so that the nearby places ID doesn't collide with extended directory IDs.
    return maxId + 1;
  }
}
