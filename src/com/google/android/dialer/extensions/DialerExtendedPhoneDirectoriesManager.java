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

package com.google.android.dialer.extensions;

import com.android.dialer.R;

import com.android.contacts.common.extensions.ExtendedPhoneDirectoriesManager;
import com.android.contacts.common.list.DirectoryPartition;
import com.google.android.dialer.provider.DialerProvider;
import com.google.android.gsf.Gservices;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class DialerExtendedPhoneDirectoriesManager
        implements ExtendedPhoneDirectoriesManager {
    @Override
    public List<DirectoryPartition> getExtendedDirectories(Context context) {
        ArrayList<DirectoryPartition> list = new ArrayList<DirectoryPartition>();

        boolean enableNearby = Gservices.getBoolean(context.getContentResolver(),
                "dialer_enable_nearby_places_directory", true);
        boolean enableSearch = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("local_search", true);

        if (enableNearby && enableSearch) {
            DirectoryPartition dp = new DirectoryPartition(false, true);
            dp.setContentUri(DialerProvider.NEARBY_PLACES_URI.toString());
            dp.setLabel(context.getString(R.string.local_search_directory_label));
            dp.setPriorityDirectory(false);
            dp.setPhotoSupported(true);
            dp.setDisplayNumber(false);
            dp.setResultLimit(3);
            list.add(dp);
        } else {
            Log.i("DialerProvider", "Nearby places is disabled");
        }

        return list;
    }
}
