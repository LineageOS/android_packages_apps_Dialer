/*
  * Copyright (C) 2013 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
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

/*
 * This is a reverse-engineered implementation of com.google.android.Dialer.
 * There is no guarantee that this implementation will work correctly or even
 * work at all. Use at your own risk.
 */

package com.google.android.dialer.phonenumbercache;

import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.service.CachedNumberLookupService;
import com.google.android.dialer.GoogleDialerDatabaseHelper;
import com.google.android.dialer.reverselookup.ReverseLookupSettingUtil;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

import java.io.IOException;
import java.io.OutputStream;

import libcore.io.IoUtils;

public class CachedNumberLookupServiceImpl implements CachedNumberLookupService {
    private Uri getContactUri(Cursor cursor) {
        int type = cursor.getInt(8);
        String directory = cursor.getString(9);
        String displayName = cursor.getString(7);
        String encoded = cursor.getString(10);

        if (!TextUtils.isEmpty(encoded) && !TextUtils.isEmpty(directory)) {
            if (type == 1) {
                return ContactsContract.Contacts.getLookupUri(0, encoded)
                        .buildUpon().appendQueryParameter("directory", directory).build();
            }
            if (type == 2 || type == 3 || type == 4) {
                Uri.Builder encodedFragment =
                        ContactsContract.Contacts.CONTENT_LOOKUP_URI.buildUpon()
                        .appendPath("encoded").encodedFragment(encoded);

                if (!TextUtils.isEmpty(displayName)) {
                    encodedFragment.appendQueryParameter("displayName", displayName);
                }

                return encodedFragment.appendQueryParameter("directory", directory).build();
            }
        }
        return null;
    }

    private Uri getPhotoUri(Cursor cursor, String uri) {
        // TODO: What are the variables?

        int i1 = cursor.getInt(1);
        int i2 = cursor.getInt(2);
        if (i1 != 0) {
            return PhoneNumberCacheContract.getPhotoLookupUri(uri);
        }
        if (i2 != 0) {
            return PhoneNumberCacheContract.getThumbnailLookupUri(uri);
        }

        String s = cursor.getString(3);
        if (s != null) {
            return Uri.parse(s);
        } else {
            return null;
        }
    }

    public static void purgePeopleApiCacheEntries(Context context) {
        GoogleDialerDatabaseHelper instance =
                GoogleDialerDatabaseHelper.getInstance(context);

        // TODO: What are the constants?
        instance.purgeSource(3);
        instance.purgeSource(4);
    }

    @Override
    public void addContact(Context context,
            CachedNumberLookupService.CachedContactInfo info) {
        if (info instanceof CachedContactInfoImpl) {
            CachedContactInfoImpl cachedInfo = (CachedContactInfoImpl)info;
            Uri uri = PhoneNumberCacheContract.CONTACT_URI;
            ContentValues contentValues = new ContentValues();
            ContactInfo contactInfo = cachedInfo.getContactInfo();
            if (contactInfo != null && contactInfo != ContactInfo.EMPTY) {
                String number;
                if (contactInfo.number != null) {
                    number = contactInfo.number;
                } else {
                    number = contactInfo.normalizedNumber;
                }
                if (!TextUtils.isEmpty(number)) {
                    contentValues.put("number", number);
                    contentValues.put("phone_type", contactInfo.type);
                    contentValues.put("phone_label", contactInfo.label);
                    contentValues.put("display_name", contactInfo.name);
                    String photoUri;
                    if (contactInfo.photoUri != null) {
                        photoUri = contactInfo.photoUri.toString();
                    } else {
                        photoUri = null;
                    }
                    contentValues.put("photo_uri", photoUri);
                    contentValues.put("source_name", cachedInfo.sourceName);
                    contentValues.put("source_type", cachedInfo.sourceType);
                    contentValues.put("source_id", cachedInfo.sourceId);
                    contentValues.put("lookup_key", cachedInfo.lookupKey);
                    context.getContentResolver().insert(uri, contentValues);
                }
            }
        }
    }

    @Override
    public boolean addPhoto(Context context, String number, byte[] photo) {
        Uri uri = PhoneNumberCacheContract.getPhotoLookupUri(number);
        OutputStream stream = null;
        try {
            stream = context.getContentResolver().openOutputStream(uri);
            stream.write(photo);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            IoUtils.closeQuietly(stream);
        }
    }

    @Override
    public CachedContactInfoImpl buildCachedContactInfo(ContactInfo info) {
        return new CachedContactInfoImpl(info);
    }

    @Override
    public void clearAllCacheEntries(Context context) {
        GoogleDialerDatabaseHelper.getInstance(context).purgeAll();
    }

    @Override
    public boolean isCacheUri(String uri) {
        return uri.startsWith(PhoneNumberCacheContract.AUTHORITY_URI.toString());
    }

    @Override
    public CachedContactInfoImpl lookupCachedContactFromNumber(Context context, String number) {
        Cursor query = context.getContentResolver().query(
                PhoneNumberCacheContract.getContactLookupUri(number),
                CachedNumberQuery.PROJECTION, null, null, null);

        if (query == null) {
            return null;
        }

        try {
            if (!query.moveToFirst()) {
                return buildCachedContactInfo(ContactInfo.EMPTY);
            }

            // TODO: What are the constants
            int type = query.getInt(8);

            if (CachedContactInfoImpl.isPeopleApiSource(type)
                    && !ReverseLookupSettingUtil.isEnabled(context)) {
                purgePeopleApiCacheEntries(context);
                return buildCachedContactInfo(ContactInfo.EMPTY);
            }

            ContactInfo info = new ContactInfo();
            info.lookupUri = getContactUri(query);
            info.name = query.getString(0);
            info.type = query.getInt(5);
            info.label = query.getString(6);
            if (info.type == 0 && info.label == null) {
                info.label = ContactInfo.GEOCODE_AS_LABEL;
            }
            info.number = query.getString(4);
            info.normalizedNumber = number;
            info.formattedNumber = null;
            info.photoId = 0L;
            info.photoUri = getPhotoUri(query, number);

            CachedContactInfoImpl cachedContactInfo = buildCachedContactInfo(info);
            cachedContactInfo.setSource(type, query.getString(7), query.getLong(9));
            return cachedContactInfo;
        }
        finally {
            query.close();
        }
    }

    public static class CachedContactInfoImpl
            implements CachedNumberLookupService.CachedContactInfo {
        private final ContactInfo mInfo;

        public String lookupKey;

        public long sourceId;
        public String sourceName;
        public int sourceType;

        public CachedContactInfoImpl(ContactInfo empty) {
            if (empty == null) {
                empty = ContactInfo.EMPTY;
            }
            mInfo = empty;
        }

        public static boolean isBusiness(int type) {
            return type == 3 || type == 2;
        }

        public static boolean isPeopleApiSource(int type) {
            // TODO: What are the constants?
            return type == 3 || type == 4;
        }

        @Override
        public ContactInfo getContactInfo() {
            return mInfo;
        }

        public int getSourceType() {
            return sourceType;
        }

        @Override
        public void setDirectorySource(String name, long directoryId) {
            // TODO: What is the constant?
            setSource(1, name, directoryId);
        }

        @Override
        public void setExtendedSource(String name, long directoryId) {
            // TODO: What is the constant?
            setSource(2, name, directoryId);
        }

        @Override
        public void setLookupKey(String key) {
            lookupKey = key;
        }

        public void setPeopleAPISource(boolean b) {
            // TODO: What are the constants?

            int type;
            if (b) {
                type = 3;
            } else {
                type = 4;
            }
            setSource(type, "Google Caller ID", 2147483647L);
        }

        protected void setSource(int type, String name, long id) {
            sourceType = type;
            sourceName = name;
            sourceId = id;
            mInfo.sourceType = type;
        }
    }

    public interface CachedNumberQuery {
        public static final String[] PROJECTION = {
                "display_name",
                "has_photo",
                "has_thumbnail",
                "photo_uri",
                "number",
                "phone_type",
                "phone_label",
                "source_name",
                "source_type",
                "source_id",
                "lookup_key" };
    }
}
