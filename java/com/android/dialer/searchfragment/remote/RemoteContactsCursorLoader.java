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
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.searchfragment.common.Projections;
import com.android.dialer.searchfragment.remote.RemoteDirectoriesCursorLoader.Directory;

/** Cursor loader to load extended contacts on device. */
final class RemoteContactsCursorLoader extends CursorLoader {

  private static final Uri ENTERPRISE_CONTENT_FILTER_URI =
      Uri.withAppendedPath(Phone.CONTENT_URI, "filter_enterprise");

  private static final String IGNORE_NUMBER_TOO_LONG_CLAUSE = "length(" + Phone.NUMBER + ") < 1000";
  private static final String MAX_RESULTS = "20";

  private final Directory directory;

  RemoteContactsCursorLoader(Context context, String query, Directory directory) {
    super(
        context,
        getContentFilterUri(query, directory.id),
        Projections.PHONE_PROJECTION,
        IGNORE_NUMBER_TOO_LONG_CLAUSE,
        null,
        Phone.SORT_KEY_PRIMARY);
    this.directory = directory;
  }

  @VisibleForTesting
  static Uri getContentFilterUri(String query, int directoryId) {
    Uri baseUri = Phone.CONTENT_FILTER_URI;
    if (VERSION.SDK_INT >= VERSION_CODES.N) {
      baseUri = ENTERPRISE_CONTENT_FILTER_URI;
    }

    return baseUri
        .buildUpon()
        .appendPath(query)
        .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId))
        .appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true")
        .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, MAX_RESULTS)
        .build();
  }

  public Directory getDirectory() {
    return directory;
  }
}
