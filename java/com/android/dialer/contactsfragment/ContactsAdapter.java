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
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.contactsfragment.ContactsFragment.Header;
import com.android.dialer.contactsfragment.ContactsFragment.OnContactSelectedListener;
import com.android.dialer.lettertile.LetterTileDrawable;
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
  private final @Header int header;
  private final OnContactSelectedListener onContactSelectedListener;

  // List of contact sublist headers
  private String[] headers = new String[0];
  // Number of contacts that correspond to each header in {@code headers}.
  private int[] counts = new int[0];
  // Cursor with list of contacts
  private Cursor cursor;

  ContactsAdapter(
      Context context, @Header int header, OnContactSelectedListener onContactSelectedListener) {
    this.context = context;
    this.header = header;
    this.onContactSelectedListener = Assert.isNotNull(onContactSelectedListener);
  }

  void updateCursor(Cursor cursor) {
    this.cursor = cursor;
    headers = cursor.getExtras().getStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
    counts = cursor.getExtras().getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
    if (counts != null) {
      int sum = 0;
      for (int count : counts) {
        sum += count;
      }

      if (sum != cursor.getCount()) {
        LogUtil.e(
            "ContactsAdapter", "Count sum (%d) != cursor count (%d).", sum, cursor.getCount());
      }
    }
    notifyDataSetChanged();
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
            LayoutInflater.from(context).inflate(R.layout.contact_row, parent, false),
            onContactSelectedListener);
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
    cursor.moveToPosition(position);
    if (header != Header.NONE) {
      cursor.moveToPrevious();
    }

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
    contactViewHolder.bind(header, name, contactUri, getContactId(cursor), showHeader);
  }

  /**
   * Returns {@link #ADD_CONTACT_VIEW_TYPE} if the adapter was initialized with {@link
   * Header#ADD_CONTACT} and the position is 0. Otherwise, {@link #CONTACT_VIEW_TYPE}.
   */
  @Override
  public @ContactsViewType int getItemViewType(int position) {
    if (header != Header.NONE && position == 0) {
      return ADD_CONTACT_VIEW_TYPE;
    }
    return CONTACT_VIEW_TYPE;
  }

  @Override
  public void onViewRecycled(RecyclerView.ViewHolder contactViewHolder) {
    super.onViewRecycled(contactViewHolder);
    if (contactViewHolder instanceof ContactViewHolder) {
      holderMap.remove(contactViewHolder);
    }
  }

  void refreshHeaders() {
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
    int count = cursor == null || cursor.isClosed() ? 0 : cursor.getCount();
    // Manually insert the header if one exists.
    if (header != Header.NONE) {
      count++;
    }
    return count;
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
    long contactId = getContactId(cursor);
    String lookupKey = cursor.getString(ContactsCursorLoader.CONTACT_LOOKUP_KEY);
    return Contacts.getLookupUri(contactId, lookupKey);
  }

  private static long getContactId(Cursor cursor) {
    return cursor.getLong(ContactsCursorLoader.CONTACT_ID);
  }

  String getHeaderString(int position) {
    if (header != Header.NONE) {
      if (position == 0) {
        return "+";
      }
      position--;
    }

    int index = -1;
    int sum = 0;
    while (sum <= position) {
      sum += counts[++index];
    }
    return headers[index];
  }
}
