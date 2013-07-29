/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2013 Android Open Kang Project
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

package com.android.dialer.callstats;

import android.app.ActionBar;
import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.format.DateUtils;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.util.Constants;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.ContactInfo;
import com.android.internal.telephony.CallerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CallStatsActivity extends ListActivity implements
        CallStatsQueryHandler.Listener, ActionBar.OnNavigationListener,
        DoubleDatePickerDialog.OnDateSetListener {
    private static final String TAG = "CallStatsActivity";

    private static final int[] CALL_DIRECTION_RESOURCES = new int[] {
        R.drawable.ic_call_inout_holo_dark,
        R.drawable.ic_call_incoming_holo_dark,
        R.drawable.ic_call_outgoing_holo_dark,
        R.drawable.ic_call_missed_holo_dark
    };

    private String[] mNavItems;

    private int mCallTypeFilter = CallStatsQueryHandler.CALL_TYPE_ALL;
    private long mFilterFrom = -1;
    private long mFilterTo = -1;
    private boolean mSortByDuration = true;
    private boolean mDataLoaded = false;

    private static final String STATE_SELECTED_NAVIGATION_ITEM = "selected_navigation_item";

    private CallStatsAdapter mAdapter;
    private CallStatsQueryHandler mCallStatsQueryHandler;

    private TextView mSumHeaderView;
    private TextView mDateFilterView;

    private boolean mRefreshDataRequired = true;
    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            mRefreshDataRequired = true;
        }
    };

    public class CallStatsNavAdapter extends ArrayAdapter<String> {
        public CallStatsNavAdapter(Context context, int textResourceId, Object[] objects) {
            super(context, textResourceId, mNavItems);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }

        public View getCustomView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.call_stats_nav_item, parent, false);
            }

            TextView label = (TextView) convertView.findViewById(R.id.call_stats_nav_text);
            label.setText(mNavItems[position]);

            ImageView icon = (ImageView) convertView.findViewById(R.id.call_stats_nav_icon);
            icon.setImageResource(CALL_DIRECTION_RESOURCES[position]);

            return convertView;
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        final ContentResolver cr = getContentResolver();
        mCallStatsQueryHandler = new CallStatsQueryHandler(cr, this);
        cr.registerContentObserver(CallLog.CONTENT_URI, true, mObserver);
        cr.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, mObserver);

        mNavItems = getResources().getStringArray(R.array.call_stats_nav_items);
        configureActionBar();

        mAdapter = new CallStatsAdapter(this);
        setListAdapter(mAdapter);

        getListView().setItemsCanFocus(true);
        setContentView(R.layout.call_stats_activity);

        mSumHeaderView = (TextView) findViewById(R.id.sum_header);
        mDateFilterView = (TextView) findViewById(R.id.date_filter);
    }

    private void configureActionBar() {
        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

        CallStatsNavAdapter navAdapter = new CallStatsNavAdapter(
                actionBar.getThemedContext(), android.R.layout.simple_list_item_1, mNavItems);

        // Set up the dropdown list navigation in the action bar.
        actionBar.setListNavigationCallbacks(navAdapter, this);
        actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP
                | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
    }

    /** Invoked when the user presses the home button in the action bar. */
    private void onHomeSelected() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setClass(this, DialtactsActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Restore the previously serialized current dropdown position.
        if (savedInstanceState.containsKey(STATE_SELECTED_NAVIGATION_ITEM)) {
            getActionBar().setSelectedNavigationItem(
                    savedInstanceState.getInt(STATE_SELECTED_NAVIGATION_ITEM));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // Serialize the current dropdown position.
        outState.putInt(STATE_SELECTED_NAVIGATION_ITEM,
                getActionBar().getSelectedNavigationIndex());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.call_stats_options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem resetItem = menu.findItem(R.id.reset_date_filter);
        final MenuItem sortDurationItem = menu.findItem(R.id.sort_by_duration);
        final MenuItem sortCountItem = menu.findItem(R.id.sort_by_count);

        resetItem.setVisible(mFilterFrom != -1);
        sortDurationItem.setVisible(!mSortByDuration);
        sortCountItem.setVisible(mSortByDuration);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        final int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home: {
                onHomeSelected();
                break;
            }
            case R.id.date_filter: {
                final DoubleDatePickerDialog.Fragment fragment =
                        new DoubleDatePickerDialog.Fragment();
                fragment.setArguments(DoubleDatePickerDialog.Fragment.createArguments(
                        mFilterFrom, mFilterTo));
                fragment.show(getFragmentManager(), "filter");
                break;
            }
            case R.id.reset_date_filter: {
                mFilterFrom = -1;
                mFilterTo = -1;
                fetchCalls();
                invalidateOptionsMenu();
                break;
            }
            case R.id.sort_by_duration:
            case R.id.sort_by_count: {
                mSortByDuration = itemId == R.id.sort_by_duration;
                mAdapter.updateDisplayedData(mCallTypeFilter, mSortByDuration);
                invalidateOptionsMenu();
                break;
            }
        }
        return true;
    }

    @Override
    public void onDateSet(long from, long to) {
        mFilterFrom = from;
        mFilterTo = to;
        invalidateOptionsMenu();
        fetchCalls();
    }

    @Override
    public boolean onNavigationItemSelected(int position, long id) {
        mCallTypeFilter = position;
        mAdapter.updateDisplayedData(mCallTypeFilter, mSortByDuration);
        if (mDataLoaded) {
            updateHeader();
        }
        return true;
    }

    /**
     * Called by the CallStatsQueryHandler when the list of calls has been
     * fetched or updated.
     */
    @Override
    public void onCallsFetched(Map<ContactInfo, CallStatsDetails> calls) {
        if (isFinishing()) {
            return;
        }

        mDataLoaded = true;
        mAdapter.updateData(calls, mFilterFrom, mFilterTo);
        mAdapter.updateDisplayedData(mCallTypeFilter, mSortByDuration);
        updateHeader();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Kill the requests thread
        mAdapter.stopRequestProcessing();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mObserver);
    }

    /* package */ boolean isDataLoaded() {
        return mDataLoaded;
    }

    private void fetchCalls() {
        mCallStatsQueryHandler.fetchCalls(mFilterFrom, mFilterTo);
    }

    private void updateHeader() {
        final String callCount = mAdapter.getTotalCallCountString();
        final String duration = mAdapter.getFullDurationString(false);

        if (duration != null) {
            mSumHeaderView.setText(getString(R.string.call_stats_header_total, callCount, duration));
        } else {
            mSumHeaderView.setText(getString(R.string.call_stats_header_total_callsonly, callCount));
        }

        if (mFilterFrom == -1) {
            mDateFilterView.setVisibility(View.GONE);
        } else {
            mDateFilterView.setText(DateUtils.formatDateRange(this, mFilterFrom, mFilterTo, 0));
            mDateFilterView.setVisibility(View.VISIBLE);
        }

        findViewById(R.id.call_stats_header).setVisibility(View.VISIBLE);
    }

    public void callSelectedEntry() {
        int position = getListView().getSelectedItemPosition();
        if (position < 0) {
            // In touch mode you may often not have something selected, so
            // just call the first entry to make sure that [send] calls
            // the most recent entry.
            position = 0;
        }
        final CallStatsDetails item = mAdapter.getItem(position);
        String number = (String) item.number;
        if (TextUtils.isEmpty(number)
                || number.equals(CallerInfo.UNKNOWN_NUMBER)
                || number.equals(CallerInfo.PRIVATE_NUMBER)
                || number.equals(CallerInfo.PAYPHONE_NUMBER)) {
            // This number can't be called, do nothing
            return;
        }

        Uri callUri;
        // If "number" is really a SIP address, construct a sip: URI.
        if (PhoneNumberUtils.isUriNumber(number)) {
            callUri = Uri.fromParts(CallUtil.SCHEME_SIP, number, null);
        } else {
            if (!number.startsWith("+")) {
                // If the caller-id matches a contact with a better qualified
                // number, use it
                number = mAdapter.getBetterNumberFromContacts(number, item.countryIso);
            }
            callUri = Uri.fromParts(CallUtil.SCHEME_TEL, number, null);
        }

        final Intent intent = CallUtil.getCallIntent(callUri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
    }

    /** Requests updates to the data to be shown. */
    private void refreshData() {
        // Prevent unnecessary refresh.
        if (mRefreshDataRequired) {
            // Mark all entries in the contact info cache as out of date, so
            // they will be looked up again once being shown.
            mAdapter.invalidateCache();
            fetchCalls();
            mRefreshDataRequired = false;
        }
    }
}
