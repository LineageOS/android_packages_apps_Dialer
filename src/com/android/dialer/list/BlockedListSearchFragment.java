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
package com.android.dialer.list;

import android.util.Log;

import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;

public class BlockedListSearchFragment extends RegularSearchFragment {
    private static final String TAG = BlockedListSearchFragment.class.getSimpleName();

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        BlockedListSearchAdapter adapter = new BlockedListSearchAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(usesCallableUri());
        return adapter;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        final int shortcutType = adapter.getShortcutTypeFromPosition(position);
        final OnPhoneNumberPickerActionListener listener = getOnPhoneNumberPickerListener();
        final String number;

        if (listener == null) {
            Log.d(TAG, "getOnPhoneNumberPickerListener() returned null in onItemClick.");
            return;
        }

        switch (shortcutType) {
            case DialerPhoneNumberListAdapter.SHORTCUT_INVALID:
                // Handles click on a search result, either contact or nearby places result.
                number = adapter.getPhoneNumber(position);
                listener.onCallNumberDirectly(number, false, getCallInitiationType(false));
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_BLOCK_NUMBER:
                // Handles click on 'Block number' shortcut to add the user query as a number.
                number = adapter.getQueryString();
                listener.onCallNumberDirectly(number, false, getCallInitiationType(false));
                break;
            default:
                Log.w(TAG, "Ignoring unsupported shortcut type: " + shortcutType);
                break;
        }
    }
}
