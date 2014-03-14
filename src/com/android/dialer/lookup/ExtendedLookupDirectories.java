/*
 * Copyright (C) 2014 Xiao-Long Chen <chillermillerlong@hotmail.com>
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

package com.android.dialer.lookup;

import com.android.dialer.R;

import com.android.contacts.common.extensions.ExtendedPhoneDirectoriesManager;
import com.android.contacts.common.list.DirectoryPartition;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class ExtendedLookupDirectories
        implements ExtendedPhoneDirectoriesManager {
    public static final String TAG =
            ExtendedLookupDirectories.class.getSimpleName();

    /**
     * Return a list of extended directories to add. May return null if no directories are to be
     * added.
     */
    @Override
    public List<DirectoryPartition> getExtendedDirectories(Context context) {
        ArrayList<DirectoryPartition> list = new ArrayList<DirectoryPartition>();

        // The directories are shown in reverse order, so insert forward lookup
        // last to make it show up at the top

        if (LookupSettings.isPeopleLookupEnabled(context)) {
            DirectoryPartition dp = new DirectoryPartition(false, true);
            dp.setContentUri(LookupProvider.PEOPLE_LOOKUP_URI.toString());
            dp.setLabel(context.getString(R.string.people));
            dp.setPriorityDirectory(false);
            dp.setPhotoSupported(true);
            dp.setDisplayNumber(false);
            dp.setResultLimit(3);
            list.add(dp);
        } else {
            Log.i(TAG, "Forward lookup (people) is disabled");
        }

        if (LookupSettings.isForwardLookupEnabled(context)) {
            DirectoryPartition dp = new DirectoryPartition(false, true);
            dp.setContentUri(LookupProvider.NEARBY_LOOKUP_URI.toString());
            dp.setLabel(context.getString(R.string.nearby_places));
            dp.setPriorityDirectory(false);
            dp.setPhotoSupported(true);
            dp.setDisplayNumber(false);
            dp.setResultLimit(3);
            list.add(dp);
        } else {
            Log.i(TAG, "Forward lookup (nearby places) is disabled");
        }

        return list;
    }
}
