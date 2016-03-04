/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.dialer.calllog;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.support.v7.widget.RecyclerView;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

/**
 * Tests for {@link GroupingListAdapter}.
 *
 * Running all tests:
 *
 *   adb shell am instrument -e class com.android.dialer.calllog.GroupingListAdapterTests \
 *     -w com.android.dialer.tests/android.test.InstrumentationTestRunner
 */
@MediumTest
public class GroupingListAdapterTests extends AndroidTestCase {

    static private final String[] PROJECTION = new String[] {
        "_id",
        "group",
    };

    private static final int GROUPING_COLUMN_INDEX = 1;

    private MatrixCursor mCursor;
    private long mNextId;

    private GroupingListAdapter mAdapter = new GroupingListAdapter(null) {

        @Override
        protected void addGroups(Cursor cursor) {
            int count = cursor.getCount();
            int groupItemCount = 1;
            cursor.moveToFirst();
            String currentValue = cursor.getString(GROUPING_COLUMN_INDEX);
            for (int i = 1; i < count; i++) {
                cursor.moveToNext();
                String value = cursor.getString(GROUPING_COLUMN_INDEX);
                if (TextUtils.equals(value, currentValue)) {
                    groupItemCount++;
                } else {
                    addGroup(i - groupItemCount, groupItemCount);
                    groupItemCount = 1;
                    currentValue = value;
                }
            }
            addGroup(count - groupItemCount, groupItemCount);
        }

        @Override
        protected void addVoicemailGroups(Cursor c) {
            // Do nothing.
        }

        @Override
        public void onContentChanged() {
            // Do nothing.
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int position) {
            return null;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
            // Do nothing.
        }
    };

    private void buildCursor(String... numbers) {
        mCursor = new MatrixCursor(PROJECTION);
        mNextId = 1;
        for (String number : numbers) {
            mCursor.addRow(new Object[]{mNextId, number});
            mNextId++;
        }
    }

    public void testGroupingWithoutGroups() {
        buildCursor("1", "2", "3");
        mAdapter.changeCursor(mCursor);

        assertEquals(3, mAdapter.getItemCount());
        assertMetadata(0, 1, "1");
        assertMetadata(1, 1, "2");
        assertMetadata(2, 1, "3");
    }

    public void testGroupingWithGroupAtTheBeginning() {
        buildCursor("1", "1", "2");
        mAdapter.changeCursor(mCursor);

        assertEquals(2, mAdapter.getItemCount());
        assertMetadata(0, 2, "1");
        assertMetadata(1, 1, "2");
    }

    public void testGroupingWithGroupInTheMiddle() {
        buildCursor("1", "2", "2", "2", "3");
        mAdapter.changeCursor(mCursor);

        assertEquals(3, mAdapter.getItemCount());
        assertMetadata(0, 1, "1");
        assertMetadata(1, 3, "2");
        assertMetadata(2, 1, "3");
    }

    public void testGroupingWithGroupAtTheEnd() {
        buildCursor("1", "2", "3", "3", "3");
        mAdapter.changeCursor(mCursor);

        assertEquals(3, mAdapter.getItemCount());
        assertMetadata(0, 1, "1");
        assertMetadata(1, 1, "2");
        assertMetadata(2, 3, "3");
    }

    public void testGroupingWithMultipleGroups() {
        buildCursor("1", "2", "2", "3", "4", "4", "5", "5", "6");
        mAdapter.changeCursor(mCursor);

        assertEquals(6, mAdapter.getItemCount());
        assertMetadata(0, 1, "1");
        assertMetadata(1, 2, "2");
        assertMetadata(2, 1, "3");
        assertMetadata(3, 2, "4");
        assertMetadata(4, 2, "5");
        assertMetadata(5, 1, "6");
    }

    public void testGroupDescriptorArrayGrowth() {
        String[] numbers = new String[500];
        for (int i = 0; i < numbers.length; i++) {

            // Make groups of 2
            numbers[i] = String.valueOf((i / 2) * 2);
        }

        buildCursor(numbers);
        mAdapter.changeCursor(mCursor);

        assertEquals(250, mAdapter.getItemCount());
    }

    private void assertMetadata(int listPosition, int groupSize, String objectValue) {
        assertEquals(groupSize, mAdapter.getGroupSize(listPosition));
        MatrixCursor cursor = (MatrixCursor) mAdapter.getItem(listPosition);
        assertEquals(objectValue, cursor.getString(GROUPING_COLUMN_INDEX));
    }
}
