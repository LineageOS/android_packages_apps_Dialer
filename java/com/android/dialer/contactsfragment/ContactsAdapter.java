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

package com.android.dialer.contactsfragment;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.support.v4.util.ArrayMap;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.contacts.common.ContactPhotoManager;
import com.android.dialer.common.Assert;

/** List adapter for the union of all contacts associated with every account on the device. */
final class ContactsAdapter extends RecyclerView.Adapter<ContactViewHolder> {

  private final ArrayMap<ContactViewHolder, Integer> holderMap = new ArrayMap<>();
  private final Context context;
  private final Cursor cursor;

  // List of contact sublist headers
  private final String[] headers;

  // Number of contacts that correspond to each header in {@code headers}.
  private final int[] counts;

  public ContactsAdapter(Context context, Cursor cursor) {
    this.context = context;
    this.cursor = cursor;
    headers = cursor.getExtras().getStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
    counts = cursor.getExtras().getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
  }

  @Override
  public ContactViewHolder onCreateViewHolder(ViewGroup parent, int position) {
    return new ContactViewHolder(
        LayoutInflater.from(context).inflate(R.layout.contact_row, parent, false));
  }

  @Override
  public void onBindViewHolder(ContactViewHolder contactViewHolder, int position) {
    holderMap.put(contactViewHolder, position);
    cursor.moveToPosition(position);

    String name = getDisplayName(cursor);
    String header = getHeaderString(position);
    Uri contactUri = getContactUri(cursor);

    ContactPhotoManager.getInstance(context)
        .loadDialerThumbnailOrPhoto(
            contactViewHolder.getPhoto(),
            contactUri,
            getPhotoId(cursor),
            getPhotoUri(cursor),
            name,
            0);

    String photoDescription =
        context.getString(com.android.contacts.common.R.string.description_quick_contact_for, name);
    contactViewHolder.getPhoto().setContentDescription(photoDescription);

    // Always show the view holder's header if it's the first item in the list. Otherwise, compare
    // it to the previous element and only show the anchored header if the row elements fall into
    // the same sublists.
    if (position == 0) {
      contactViewHolder.bind(header, name, contactUri, true);
    } else {
      boolean showHeader = !header.equals(getHeaderString(position - 1));
      contactViewHolder.bind(header, name, contactUri, showHeader);
    }
  }

  public void refreshHeaders() {
    for (ContactViewHolder holder : holderMap.keySet()) {
      onBindViewHolder(holder, holderMap.get(holder));
    }
  }

  @Override
  public int getItemCount() {
    return cursor == null ? 0 : cursor.getCount();
  }

  public String getHeader(int position) {
    return getHolderAt(position).getHeader();
  }

  public TextView getHeaderView(int position) {
    return getHolderAt(position).getHeaderView();
  }

  public void setHeaderVisibility(int position, int visibility) {
    getHolderAt(position).getHeaderView().setVisibility(visibility);
  }

  private ContactViewHolder getHolderAt(int position) {
    for (ContactViewHolder holder : holderMap.keySet()) {
      if (holderMap.get(holder) == position) {
        return holder;
      }
    }
    throw Assert.createIllegalStateFailException("No holder for position: " + position);
  }

  private static String getDisplayName(Cursor cursor) {
    return cursor.getString(ContactsCursorLoader.CONTACT_DISPLAY_NAME);
  }

  private static long getPhotoId(Cursor cursor) {
    return cursor.getLong(ContactsCursorLoader.CONTACT_PHOTO_ID);
  }

  private static Uri getPhotoUri(Cursor cursor) {
    String photoUri = cursor.getString(ContactsCursorLoader.CONTACT_PHOTO_URI);
    return photoUri == null ? null : Uri.parse(photoUri);
  }

  private static Uri getContactUri(Cursor cursor) {
    long contactId = cursor.getLong(ContactsCursorLoader.CONTACT_ID);
    String lookupKey = cursor.getString(ContactsCursorLoader.CONTACT_LOOKUP_KEY);
    return Contacts.getLookupUri(contactId, lookupKey);
  }

  private String getHeaderString(int position) {
    int index = -1;
    int sum = 0;
    while (sum <= position) {
      sum += counts[++index];
    }
    return headers[index];
  }
}
