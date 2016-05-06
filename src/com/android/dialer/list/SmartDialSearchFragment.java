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

import android.content.ComponentName;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.dialer.dialpad.SmartDialCursorLoader;
import com.android.dialer.R;
import com.android.phone.common.incall.CallMethodInfo;
import com.android.phone.common.incall.DialerDataSubscription;
import com.android.phone.common.incall.utils.CallMethodFilters;

import java.util.HashMap;

/**
 * Implements a fragment to load and display SmartDial search results.
 */
public class SmartDialSearchFragment extends SearchFragment
        implements DialerPhoneNumberListAdapter.searchMethodClicked {
    private static final String TAG = SmartDialSearchFragment.class.getSimpleName();

    /**
     * Creates a SmartDialListAdapter to display and operate on search results.
     */
    @Override
    protected ContactEntryListAdapter createListAdapter() {
        SmartDialNumberListAdapter adapter = new SmartDialNumberListAdapter(getActivity());
        adapter.setUseCallableUri(super.usesCallableUri());
        adapter.setQuickContactEnabled(true);
        // Set adapter's query string to restore previous instance state.
        adapter.setQueryString(getQueryString());
        adapter.setSearchListner(this);
        adapter.setAvailableCallMethods(CallMethodFilters.getAllEnabledCallMethods(
                DialerDataSubscription.get(getActivity())));

        return adapter;
    }

    /**
     * Creates a SmartDialCursorLoader object to load query results.
     */
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Smart dialing does not support Directory Load, falls back to normal search instead.
        if (id == getDirectoryLoaderId()) {
            return super.onCreateLoader(id, args);
        } else {
            return updateData();
        }
    }

    /**
     * Gets the Phone Uri of an entry for calling.
     * @param position Location of the data of interest.
     * @return Phone Uri to establish a phone call.
     */
    @Override
    protected Uri getPhoneUri(int position) {
        final SmartDialNumberListAdapter adapter = (SmartDialNumberListAdapter) getAdapter();
        return adapter.getDataUri(position);
    }

    private Loader<Cursor> updateData() {
        final SmartDialNumberListAdapter adapter = (SmartDialNumberListAdapter) getAdapter();

        SmartDialCursorLoader loader = new SmartDialCursorLoader(super.getContext());

        if (mCurrentCallMethodInfo != null) {
            adapter.configureLoader(loader, mCurrentCallMethodInfo.mMimeType);
        } else {
            adapter.configureLoader(loader, null);
        }

        return loader;
    }

    public void setAvailableProviders(HashMap<ComponentName, CallMethodInfo> callMethods) {
        if (mAvailableProviders != null) {
            mAvailableProviders.clear();
        } else {
            mAvailableProviders = new HashMap<ComponentName, CallMethodInfo>();
        }
        // Note: these should be available (enabled) providers only!
        mAvailableProviders.putAll(callMethods);
        setupEmptyView();
    }

    public boolean isShowingPermissionRequest() {
        return mEmptyView != null && mEmptyView.isShowingContent();
    }
}
