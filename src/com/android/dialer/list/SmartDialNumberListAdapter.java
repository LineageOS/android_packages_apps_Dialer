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

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Callable;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.dialer.dialpad.SmartDialCursorLoader;
import com.android.dialer.dialpad.SmartDialCursorLoader.SmartDialPhoneQuery;
import com.android.dialer.dialpad.SmartDialNameMatcher;
import com.android.dialer.dialpad.SmartDialPrefix;
import com.android.dialer.dialpad.SmartDialMatchPosition;

import java.util.ArrayList;

/**
 * List adapter to display the SmartDial search results.
 */
public class SmartDialNumberListAdapter extends PhoneNumberListAdapter{

    private static final String TAG = SmartDialNumberListAdapter.class.getSimpleName();
    private static final boolean DEBUG = false;

    private SmartDialNameMatcher mNameMatcher;

    public SmartDialNumberListAdapter(Context context) {
        super(context);
        if (DEBUG) {
            Log.v(TAG, "Constructing List Adapter");
        }
    }

    /**
     * Sets query for the SmartDialCursorLoader.
     */
    public void configureLoader(SmartDialCursorLoader loader) {
        if (DEBUG) {
            Log.v(TAG, "Congifugure Loader with query" + getQueryString());
        }

        if (getQueryString() == null) {
            mNameMatcher = new SmartDialNameMatcher("", SmartDialPrefix.getMap());
            loader.configureQuery("");
        } else {
            loader.configureQuery(getQueryString());
            mNameMatcher = new SmartDialNameMatcher(PhoneNumberUtils.normalizeNumber(
                    getQueryString()), SmartDialPrefix.getMap());
        }
    }

    /**
     * Sets highlight options for a List item in the SmartDial search results.
     * @param view ContactListItemView where the result will be displayed.
     * @param cursor Object containing information of the associated List item.
     */
    @Override
    protected void setHighlight(ContactListItemView view, Cursor cursor) {
        view.clearHighlightSequences();

        if (mNameMatcher.matches(cursor.getString(SmartDialPhoneQuery.SMARTDIAL_DISPLAY_NAME))) {
            final ArrayList<SmartDialMatchPosition> nameMatches = mNameMatcher.getMatchPositions();
            for (SmartDialMatchPosition match:nameMatches) {
                view.addNameHighlightSequence(match.start, match.end);
                if (DEBUG) {
                    Log.v(TAG, cursor.getString(SmartDialPhoneQuery.SMARTDIAL_DISPLAY_NAME) + " " +
                            mNameMatcher.getQuery() + " " + String.valueOf(match.start));
                }
            }
        }

        final SmartDialMatchPosition numberMatch = mNameMatcher.matchesNumber(cursor.getString(
                SmartDialPhoneQuery.SMARTDIAL_NUMBER));
        if (numberMatch != null) {
            view.addNumberHighlightSequence(numberMatch.start, numberMatch.end);
        }
    }

    /**
     * Gets Uri for the list item at the given position.
     * @param position Location of the data of interest.
     * @return Data Uri of the entry.
     */
    public Uri getDataUri(int position) {
        Cursor cursor = ((Cursor)getItem(position));
        if (cursor != null) {
            long id = cursor.getLong(SmartDialPhoneQuery.SMARTDIAL_ID);
            return ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id);
        } else {
            Log.w(TAG, "Cursor was null in getDataUri() call. Returning null instead.");
            return null;
        }
    }
}
