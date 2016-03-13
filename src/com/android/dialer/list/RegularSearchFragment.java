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

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.READ_CONTACTS;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.PinnedHeaderListView;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.commonbind.analytics.AnalyticsUtil;
import com.android.dialerbind.ObjectFactory;

import com.android.dialer.R;
import com.android.dialer.lookup.LookupCache;
import com.android.dialer.lookup.LookupSettings;
import com.android.dialer.service.CachedNumberLookupService;
import com.android.dialer.widget.EmptyContentView;
import com.android.dialer.widget.EmptyContentView.OnEmptyViewActionButtonClickedListener;

import com.android.phone.common.incall.CreditBarHelper;
import com.android.phone.common.incall.DialerDataSubscription;
import com.android.phone.common.incall.utils.CallMethodFilters;

public class RegularSearchFragment extends SearchFragment
        implements OnEmptyViewActionButtonClickedListener,
        CreditBarHelper.CreditBarVisibilityListener {

    private static final int READ_CONTACTS_PERMISSION_REQUEST_CODE = 1;
    private static final int ACCESS_FINE_LOCATION_PERMISSION_REQUEST_CODE = 2;

    private static final int SEARCH_DIRECTORY_RESULT_LIMIT = 5;

    private static final CachedNumberLookupService mCachedNumberLookupService =
        ObjectFactory.newCachedNumberLookupService();

    public RegularSearchFragment() {
        configureDirectorySearch();
    }

    @Override
    public void onStart() {
        super.onStart();
        AnalyticsUtil.sendScreenView(this);

        if (LookupSettings.isForwardLookupEnabled(getActivity())
                || LookupSettings.isPeopleLookupEnabled(getActivity())) {
            if (getActivity().checkSelfPermission(ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{ACCESS_FINE_LOCATION},
                        ACCESS_FINE_LOCATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    public void configureDirectorySearch() {
        setDirectorySearchEnabled(true);
        setDirectoryResultLimit(SEARCH_DIRECTORY_RESULT_LIMIT);
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, final ViewGroup container) {
        super.onCreateView(inflater, container);
        ((PinnedHeaderListView) getListView()).setScrollToSectionOnHeaderTouch(true);
    }

    protected ContactEntryListAdapter createListAdapter() {
        RegularSearchListAdapter adapter = new RegularSearchListAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(usesCallableUri());
        adapter.setAvailableCallMethods(CallMethodFilters.getAllEnabledCallMethods(
                DialerDataSubscription.get(getActivity())));
        return adapter;
    }

    @Override
    protected void cacheContactInfo(int position) {
        final RegularSearchListAdapter adapter =
                (RegularSearchListAdapter) getAdapter();
        if (mCachedNumberLookupService != null) {
            mCachedNumberLookupService.addContact(getContext(),
                    adapter.getContactInfo(mCachedNumberLookupService, position));
        }
        LookupCache.cacheContact(getActivity(),
                adapter.getLookupContactInfo(position));
    }

    @Override
    protected void setupEmptyView() {
        if (mEmptyView != null && getActivity() != null) {
            if (!PermissionsUtil.hasPermission(getActivity(), READ_CONTACTS)) {
                mEmptyView.setImage(R.drawable.empty_contacts);
                mEmptyView.setActionLabel(R.string.permission_single_turn_on);
                mEmptyView.setDescription(R.string.permission_no_search);
                mEmptyView.setActionClickedListener(this);
            } else {
                mEmptyView.setImage(EmptyContentView.NO_IMAGE);
                mEmptyView.setActionLabel(EmptyContentView.NO_LABEL);
                mEmptyView.setDescription(EmptyContentView.NO_LABEL);
            }
        }
    }

    @Override
    public void onEmptyViewActionButtonClicked() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        requestPermissions(new String[] {READ_CONTACTS}, READ_CONTACTS_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (requestCode == READ_CONTACTS_PERMISSION_REQUEST_CODE) {
            setupEmptyView();
        }
    }
}
