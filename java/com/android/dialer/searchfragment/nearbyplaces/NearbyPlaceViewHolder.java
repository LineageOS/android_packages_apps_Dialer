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

package com.android.dialer.searchfragment.nearbyplaces;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.lettertiles.LetterTileDrawable;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.searchfragment.common.Projections;
import com.android.dialer.searchfragment.common.QueryBoldingUtil;
import com.android.dialer.searchfragment.common.R;
import com.android.dialer.telecom.TelecomUtil;

/** ViewHolder for a nearby place row. */
public final class NearbyPlaceViewHolder extends RecyclerView.ViewHolder
    implements View.OnClickListener {

  private final Context context;
  private final TextView placeName;
  private final TextView placeAddress;
  private final QuickContactBadge photo;

  private String number;

  public NearbyPlaceViewHolder(View view) {
    super(view);
    view.setOnClickListener(this);
    photo = view.findViewById(R.id.photo);
    placeName = view.findViewById(R.id.primary);
    placeAddress = view.findViewById(R.id.secondary);
    context = view.getContext();
  }

  /**
   * Binds the ViewHolder with a cursor from {@link NearbyPlacesCursorLoader} with the data found at
   * the cursors set position.
   */
  public void bind(Cursor cursor, String query) {
    number = cursor.getString(Projections.PHONE_NUMBER);
    String name = cursor.getString(Projections.PHONE_DISPLAY_NAME);
    String address = cursor.getString(Projections.PHONE_LABEL);

    placeName.setText(QueryBoldingUtil.getNameWithQueryBolded(query, name));
    placeAddress.setText(QueryBoldingUtil.getNameWithQueryBolded(query, address));

    String photoUri = cursor.getString(Projections.PHONE_PHOTO_URI);
    ContactPhotoManager.getInstance(context)
        .loadDialerThumbnailOrPhoto(
            photo,
            getContactUri(cursor),
            cursor.getLong(Projections.PHONE_PHOTO_ID),
            photoUri == null ? null : Uri.parse(photoUri),
            name,
            LetterTileDrawable.TYPE_DEFAULT);
  }

  private static Uri getContactUri(Cursor cursor) {
    long contactId = cursor.getLong(Projections.PHONE_ID);
    String lookupKey = cursor.getString(Projections.PHONE_LOOKUP_KEY);
    return ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
  }

  @Override
  public void onClick(View v) {
    TelecomUtil.placeCall(
        context, new CallIntentBuilder(number, CallInitiationType.Type.REGULAR_SEARCH).build());
  }
}
