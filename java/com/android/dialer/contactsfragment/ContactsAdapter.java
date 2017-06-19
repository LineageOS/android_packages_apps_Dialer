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
import android.support.annotation.IntDef;
import android.support.v4.util.ArrayMap;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.lettertiles.LetterTileDrawable;
import com.android.dialer.common.Assert;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** List adapter for the union of all contacts associated with every account on the device. */
final class ContactsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private static final int UNKNOWN_VIEW_TYPE = 0;
  private static final int ADD_CONTACT_VIEW_TYPE = 1;
  private static final int CONTACT_VIEW_TYPE = 2;

  /** An Enum for the different row view types shown by this adapter. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({UNKNOWN_VIEW_TYPE, ADD_CONTACT_VIEW_TYPE, CONTACT_VIEW_TYPE})
  @interface ContactsViewType {}

  private final ArrayMap<ContactViewHolder, Integer> holderMap = new ArrayMap<>();
  private final Context context;
  private final Cursor cursor;

  // List of contact sublist headers
  private final String[] headers;

  // Number of contacts that correspond to each header in {@code headers}.
  private final int[] counts;

  ContactsAdapter(Context context, Cursor cursor) {
    this.context = context;
    this.cursor = cursor;
    headers = cursor.getExtras().getStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
    counts = cursor.getExtras().getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(
      ViewGroup parent, @ContactsViewType int viewType) {
    switch (viewType) {
      case ADD_CONTACT_VIEW_TYPE:
        return new AddContactViewHolder(
            LayoutInflater.from(context).inflate(R.layout.add_contact_row, parent, false));
      case CONTACT_VIEW_TYPE:
        return new ContactViewHolder(
            LayoutInflater.from(context).inflate(R.layout.contact_row, parent, false));
      case UNKNOWN_VIEW_TYPE:
      default:
        throw Assert.createIllegalStateFailException("Invalid view type: " + viewType);
    }
  }

  @Override
  public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
    if (viewHolder instanceof AddContactViewHolder) {
      return;
    }

    ContactViewHolder contactViewHolder = (ContactViewHolder) viewHolder;
    holderMap.put(contactViewHolder, position);
    // Cursor should be offset by 1 because of add contact row
    cursor.moveToPosition(position - 1);

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
            LetterTileDrawable.TYPE_DEFAULT);

    String photoDescription =
        context.getString(com.android.contacts.common.R.string.description_quick_contact_for, name);
    contactViewHolder.getPhoto().setContentDescription(photoDescription);

    // Always show the view holder's header if it's the first item in the list. Otherwise, compare
    // it to the previous element and only show the anchored header if the row elements fall into
    // the same sublists.
    boolean showHeader = position == 0 || !header.equals(getHeaderString(position - 1));
    contactViewHolder.bind(header, name, contactUri, showHeader);
  }

  @Override
  public @ContactsViewType int getItemViewType(int position) {
    return position == 0 ? ADD_CONTACT_VIEW_TYPE : CONTACT_VIEW_TYPE;
  }

  @Override
  public void onViewRecycled(RecyclerView.ViewHolder contactViewHolder) {
    super.onViewRecycled(contactViewHolder);
    if (contactViewHolder instanceof ContactViewHolder) {
      holderMap.remove(contactViewHolder);
    }
  }

  public void refreshHeaders() {
    for (ContactViewHolder holder : holderMap.keySet()) {
      int position = holderMap.get(holder);
      boolean showHeader =
          position == 0 || !getHeaderString(position).equals(getHeaderString(position - 1));
      int visibility = showHeader ? View.VISIBLE : View.INVISIBLE;
      holder.getHeaderView().setVisibility(visibility);
    }
  }

  @Override
  public int getItemCount() {
    return (cursor == null ? 0 : cursor.getCount()) + 1; // add contact
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

  public String getHeaderString(int position) {
    if (position == 0) {
      return "+";
    }
    position--;

    int index = -1;
    int sum = 0;
    while (sum <= position) {
      sum += counts[++index];
    }
    return headers[index];
  }
}
