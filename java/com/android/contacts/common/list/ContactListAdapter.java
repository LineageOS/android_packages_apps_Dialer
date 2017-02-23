/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.common.list;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.SearchSnippets;
import android.view.ViewGroup;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.R;
import com.android.contacts.common.preference.ContactsPreferences;

/**
 * A cursor adapter for the {@link ContactsContract.Contacts#CONTENT_TYPE} content type. Also
 * includes support for including the {@link ContactsContract.Profile} record in the list.
 */
public abstract class ContactListAdapter extends ContactEntryListAdapter {

  private CharSequence mUnknownNameText;

  public ContactListAdapter(Context context) {
    super(context);

    mUnknownNameText = context.getText(R.string.missing_name);
  }

  protected static Uri buildSectionIndexerUri(Uri uri) {
    return uri.buildUpon().appendQueryParameter(Contacts.EXTRA_ADDRESS_BOOK_INDEX, "true").build();
  }

  public Uri getContactUri(int partitionIndex, Cursor cursor) {
    long contactId = cursor.getLong(ContactQuery.CONTACT_ID);
    String lookupKey = cursor.getString(ContactQuery.CONTACT_LOOKUP_KEY);
    Uri uri = Contacts.getLookupUri(contactId, lookupKey);
    long directoryId = ((DirectoryPartition) getPartition(partitionIndex)).getDirectoryId();
    if (uri != null && directoryId != Directory.DEFAULT) {
      uri =
          uri.buildUpon()
              .appendQueryParameter(
                  ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId))
              .build();
    }
    return uri;
  }

  @Override
  protected ContactListItemView newView(
      Context context, int partition, Cursor cursor, int position, ViewGroup parent) {
    ContactListItemView view = super.newView(context, partition, cursor, position, parent);
    view.setUnknownNameText(mUnknownNameText);
    view.setQuickContactEnabled(isQuickContactEnabled());
    view.setAdjustSelectionBoundsEnabled(isAdjustSelectionBoundsEnabled());
    view.setActivatedStateSupported(isSelectionVisible());
    return view;
  }

  protected void bindSectionHeaderAndDivider(
      ContactListItemView view, int position, Cursor cursor) {
    view.setIsSectionHeaderEnabled(isSectionHeaderDisplayEnabled());
    if (isSectionHeaderDisplayEnabled()) {
      Placement placement = getItemPlacementInSection(position);
      view.setSectionHeader(placement.sectionHeader);
    } else {
      view.setSectionHeader(null);
    }
  }

  protected void bindPhoto(final ContactListItemView view, int partitionIndex, Cursor cursor) {
    if (!isPhotoSupported(partitionIndex)) {
      view.removePhotoView();
      return;
    }

    // Set the photo, if available
    long photoId = 0;
    if (!cursor.isNull(ContactQuery.CONTACT_PHOTO_ID)) {
      photoId = cursor.getLong(ContactQuery.CONTACT_PHOTO_ID);
    }

    if (photoId != 0) {
      getPhotoLoader()
          .loadThumbnail(view.getPhotoView(), photoId, false, getCircularPhotos(), null);
    } else {
      final String photoUriString = cursor.getString(ContactQuery.CONTACT_PHOTO_URI);
      final Uri photoUri = photoUriString == null ? null : Uri.parse(photoUriString);
      DefaultImageRequest request = null;
      if (photoUri == null) {
        request =
            getDefaultImageRequestFromCursor(
                cursor, ContactQuery.CONTACT_DISPLAY_NAME, ContactQuery.CONTACT_LOOKUP_KEY);
      }
      getPhotoLoader()
          .loadDirectoryPhoto(view.getPhotoView(), photoUri, false, getCircularPhotos(), request);
    }
  }

  protected void bindNameAndViewId(final ContactListItemView view, Cursor cursor) {
    view.showDisplayName(cursor, ContactQuery.CONTACT_DISPLAY_NAME);
    // Note: we don't show phonetic any more (See issue 5265330)

    bindViewId(view, cursor, ContactQuery.CONTACT_ID);
  }

  protected void bindPresenceAndStatusMessage(final ContactListItemView view, Cursor cursor) {
    view.showPresenceAndStatusMessage(
        cursor, ContactQuery.CONTACT_PRESENCE_STATUS, ContactQuery.CONTACT_CONTACT_STATUS);
  }

  protected void bindSearchSnippet(final ContactListItemView view, Cursor cursor) {
    view.showSnippet(cursor, ContactQuery.CONTACT_SNIPPET);
  }

  @Override
  public void changeCursor(int partitionIndex, Cursor cursor) {
    super.changeCursor(partitionIndex, cursor);

    if (cursor == null || !cursor.moveToFirst()) {
      return;
    }

    // hasProfile tells whether the first row is a profile
    final boolean hasProfile = cursor.getInt(ContactQuery.CONTACT_IS_USER_PROFILE) == 1;

    // Add ME profile on top of favorites
    cursor.moveToFirst();
    setProfileExists(hasProfile);
  }

  /** @return Projection useful for children. */
  protected final String[] getProjection(boolean forSearch) {
    final int sortOrder = getContactNameDisplayOrder();
    if (forSearch) {
      if (sortOrder == ContactsPreferences.DISPLAY_ORDER_PRIMARY) {
        return ContactQuery.FILTER_PROJECTION_PRIMARY;
      } else {
        return ContactQuery.FILTER_PROJECTION_ALTERNATIVE;
      }
    } else {
      if (sortOrder == ContactsPreferences.DISPLAY_ORDER_PRIMARY) {
        return ContactQuery.CONTACT_PROJECTION_PRIMARY;
      } else {
        return ContactQuery.CONTACT_PROJECTION_ALTERNATIVE;
      }
    }
  }

  protected static class ContactQuery {

    public static final int CONTACT_ID = 0;
    public static final int CONTACT_DISPLAY_NAME = 1;
    public static final int CONTACT_PRESENCE_STATUS = 2;
    public static final int CONTACT_CONTACT_STATUS = 3;
    public static final int CONTACT_PHOTO_ID = 4;
    public static final int CONTACT_PHOTO_URI = 5;
    public static final int CONTACT_LOOKUP_KEY = 6;
    public static final int CONTACT_IS_USER_PROFILE = 7;
    public static final int CONTACT_PHONETIC_NAME = 8;
    public static final int CONTACT_STARRED = 9;
    public static final int CONTACT_SNIPPET = 10;
    private static final String[] CONTACT_PROJECTION_PRIMARY =
        new String[] {
          Contacts._ID, // 0
          Contacts.DISPLAY_NAME_PRIMARY, // 1
          Contacts.CONTACT_PRESENCE, // 2
          Contacts.CONTACT_STATUS, // 3
          Contacts.PHOTO_ID, // 4
          Contacts.PHOTO_THUMBNAIL_URI, // 5
          Contacts.LOOKUP_KEY, // 6
          Contacts.IS_USER_PROFILE, // 7
          Contacts.PHONETIC_NAME, // 8
          Contacts.STARRED, // 9
        };
    private static final String[] CONTACT_PROJECTION_ALTERNATIVE =
        new String[] {
          Contacts._ID, // 0
          Contacts.DISPLAY_NAME_ALTERNATIVE, // 1
          Contacts.CONTACT_PRESENCE, // 2
          Contacts.CONTACT_STATUS, // 3
          Contacts.PHOTO_ID, // 4
          Contacts.PHOTO_THUMBNAIL_URI, // 5
          Contacts.LOOKUP_KEY, // 6
          Contacts.IS_USER_PROFILE, // 7
          Contacts.PHONETIC_NAME, // 8
          Contacts.STARRED, // 9
        };
    private static final String[] FILTER_PROJECTION_PRIMARY =
        new String[] {
          Contacts._ID, // 0
          Contacts.DISPLAY_NAME_PRIMARY, // 1
          Contacts.CONTACT_PRESENCE, // 2
          Contacts.CONTACT_STATUS, // 3
          Contacts.PHOTO_ID, // 4
          Contacts.PHOTO_THUMBNAIL_URI, // 5
          Contacts.LOOKUP_KEY, // 6
          Contacts.IS_USER_PROFILE, // 7
          Contacts.PHONETIC_NAME, // 8
          Contacts.STARRED, // 9
          SearchSnippets.SNIPPET, // 10
        };
    private static final String[] FILTER_PROJECTION_ALTERNATIVE =
        new String[] {
          Contacts._ID, // 0
          Contacts.DISPLAY_NAME_ALTERNATIVE, // 1
          Contacts.CONTACT_PRESENCE, // 2
          Contacts.CONTACT_STATUS, // 3
          Contacts.PHOTO_ID, // 4
          Contacts.PHOTO_THUMBNAIL_URI, // 5
          Contacts.LOOKUP_KEY, // 6
          Contacts.IS_USER_PROFILE, // 7
          Contacts.PHONETIC_NAME, // 8
          Contacts.STARRED, // 9
          SearchSnippets.SNIPPET, // 10
        };
  }
}
