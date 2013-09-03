/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.dialer.list;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import com.android.contacts.common.list.DirectoryPartition;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.dialer.service.CachedNumberLookupService.CachedContactInfo;

/**
 * List adapter to display regular search results.
 */
public class RegularSearchListAdapter extends PhoneNumberListAdapter {

    public RegularSearchListAdapter(Context context) {
        super(context);
    }

    public CachedContactInfo getContactInfo(int position) {
        CachedContactInfo info = new CachedContactInfo();
        final Cursor item = (Cursor) getItem(position);
        if (item != null) {
            info.name = item.getString(PhoneQuery.DISPLAY_NAME);
            info.type = item.getInt(PhoneQuery.PHONE_TYPE);
            info.label = item.getString(PhoneQuery.PHONE_LABEL);
            info.number = item.getString(PhoneQuery.PHONE_NUMBER);
            final String photoUriStr = item.getString(PhoneQuery.PHOTO_URI);
            info.photoUri = photoUriStr == null ? null : Uri.parse(photoUriStr);
            info.lookupKey = item.getString(PhoneQuery.LOOKUP_KEY);

            final int partitionIndex = getPartitionForPosition(position);
            final DirectoryPartition partition =
                (DirectoryPartition) getPartition(partitionIndex);
            final long directoryId = partition.getDirectoryId();
            if (isExtendedDirectory(directoryId)) {
               info.sourceType = CachedContactInfo.SOURCE_TYPE_EXTENDED;
                // TODO source_id for extended directory?
            } else {
                info.sourceType = CachedContactInfo.SOURCE_TYPE_DIRECTORY;
                info.sourceId = (int) directoryId;
            }
        }
        return info;
    }
}
