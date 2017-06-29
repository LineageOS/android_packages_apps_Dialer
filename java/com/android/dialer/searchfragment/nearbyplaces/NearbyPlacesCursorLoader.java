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
import android.net.Uri;
import android.provider.ContactsContract;
import com.android.contacts.common.extensions.PhoneDirectoryExtenderAccessor;
import com.android.dialer.searchfragment.common.Projections;

/** Cursor loader for nearby places search results. */
public final class NearbyPlacesCursorLoader extends CursorLoader {

  private static final String MAX_RESULTS = "3";

  public NearbyPlacesCursorLoader(Context context, String query) {
    super(context, getContentUri(context, query), Projections.PHONE_PROJECTION, null, null, null);
  }

  private static Uri getContentUri(Context context, String query) {
    return PhoneDirectoryExtenderAccessor.get(context)
        .getContentUri()
        .buildUpon()
        .appendPath(query)
        .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, MAX_RESULTS)
        .build();
  }
}
