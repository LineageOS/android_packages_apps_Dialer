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
import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.R;
import com.android.dialer.calllog.CallTypeIconsView;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.PhoneNumberUtil;
import com.android.dialer.widget.PieChartView;

/**
 * Activity to display detailed information about a callstat item
 */
public class CallStatsDetailActivity extends Activity {
    private static final String TAG = "CallStatsDetailActivity";

    public static final String EXTRA_DETAILS = "details";
    public static final String EXTRA_FROM = "from";
    public static final String EXTRA_TO = "to";
    public static final String EXTRA_BY_DURATION = "by_duration";

    private ContactInfoHelper mContactInfoHelper;
    private Resources mResources;

    private QuickContactBadge mQuickContactBadge;
    private TextView mCallerName;
    private TextView mCallerNumber;
    private View mCallButton;

    private TextView mTotalSummary;
    private TextView mTotalDuration;
    private TextView mInSummary;
    private TextView mInCount;
    private TextView mInDuration;
    private TextView mOutSummary;
    private TextView mOutCount;
    private TextView mOutDuration;
    private TextView mMissedSummary;
    private TextView mMissedCount;
    private TextView mBlacklistSummary;
    private TextView mBlacklistCount;
    private PieChartView mPieChart;

    private CallStatsDetails mData;
    private String mNumber = null;
    private boolean mHasEditNumberBeforeCallOption;

    private class UpdateContactTask extends AsyncTask<String, Void, ContactInfo> {
        @Override
        protected ContactInfo doInBackground(String... strings) {
            ContactInfo info = mContactInfoHelper.lookupNumber(strings[0], strings[1]);
            return info;
        }

        @Override
        protected void onPostExecute(ContactInfo info) {
            mData.updateFromInfo(info);
            updateData();
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.call_stats_detail);

        mResources = getResources();
        mContactInfoHelper = new ContactInfoHelper(this, GeoUtil.getCurrentCountryIso(this));

        mQuickContactBadge = (QuickContactBadge) findViewById(R.id.quick_contact_photo);
        mQuickContactBadge.setOverlay(null);
        mQuickContactBadge.setPrioritizedMimeType(Phone.CONTENT_ITEM_TYPE);
        mCallerName = (TextView) findViewById(R.id.caller_name);
        mCallerNumber = (TextView) findViewById(R.id.caller_number);

        mCallButton = (View) findViewById(R.id.call_back_button);
        mCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(IntentUtil.getCallIntent(mNumber));
            }
        });

        mTotalSummary = (TextView) findViewById(R.id.total_summary);
        mTotalDuration = (TextView) findViewById(R.id.total_duration);
        mInSummary = (TextView) findViewById(R.id.in_summary);
        mInCount = (TextView) findViewById(R.id.in_count);
        mInDuration = (TextView) findViewById(R.id.in_duration);
        mOutSummary = (TextView) findViewById(R.id.out_summary);
        mOutCount = (TextView) findViewById(R.id.out_count);
        mOutDuration = (TextView) findViewById(R.id.out_duration);
        mMissedSummary = (TextView) findViewById(R.id.missed_summary);
        mMissedCount = (TextView) findViewById(R.id.missed_count);
        mBlacklistSummary = (TextView) findViewById(R.id.blacklist_summary);
        mBlacklistCount = (TextView) findViewById(R.id.blacklist_count);
        mPieChart = (PieChartView) findViewById(R.id.pie_chart);

        setCallType(R.id.in_icon, Calls.INCOMING_TYPE);
        setCallType(R.id.out_icon, Calls.OUTGOING_TYPE);
        setCallType(R.id.missed_icon, Calls.MISSED_TYPE);
        setCallType(R.id.blacklist_icon, Calls.BLACKLIST_TYPE);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Intent launchIntent = getIntent();
        mData = (CallStatsDetails) launchIntent.getParcelableExtra(EXTRA_DETAILS);

        TextView dateFilterView = (TextView) findViewById(R.id.date_filter);
        long filterFrom = launchIntent.getLongExtra(EXTRA_FROM, -1);
        if (filterFrom == -1) {
            dateFilterView.setVisibility(View.GONE);
        } else {
            long filterTo = launchIntent.getLongExtra(EXTRA_TO, -1);
            dateFilterView.setText(DateUtils.formatDateRange(
                    this, filterFrom, filterTo, DateUtils.FORMAT_ABBREV_ALL));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        new UpdateContactTask().execute(mData.number.toString(), mData.countryIso);
    }

    private void setCallType(int id, int type) {
        CallTypeIconsView view = (CallTypeIconsView) findViewById(id);
        view.add(type);
    }

    private void updateData() {
        mNumber = mData.number.toString();

        // Cache the details about the phone number.
        final boolean canPlaceCallsTo =
                PhoneNumberUtil.canPlaceCallsTo(mNumber, mData.numberPresentation);
        final CharSequence callLocationOrType = !TextUtils.isEmpty(mData.name)
                ? Phone.getTypeLabel(mResources, mData.numberType, mData.numberLabel)
                : mData.geocode;

        mData.updateDisplayPropertiesIfNeeded(this);

        final boolean isSipNumber = PhoneNumberUtil.isSipNumber(mNumber);
        mHasEditNumberBeforeCallOption =
                canPlaceCallsTo && !isSipNumber && !mData.isVoicemailNumber;

        if (!TextUtils.isEmpty(mData.name)) {
            mCallerName.setText(mData.name);
            mCallerNumber.setText(callLocationOrType + " " + mData.displayNumber);
        } else {
            mCallerName.setText(mData.displayNumber);
            if (!TextUtils.isEmpty(callLocationOrType)) {
                mCallerNumber.setText(callLocationOrType);
                mCallerNumber.setVisibility(View.VISIBLE);
            } else {
                mCallerNumber.setVisibility(View.GONE);
            }
        }

        mCallButton.setVisibility(canPlaceCallsTo ? View.VISIBLE : View.GONE);

        String lookupKey = mData.contactUri == null
                ? null : UriUtils.getLookupKeyFromUri(mData.contactUri);
        final boolean isBusiness = mContactInfoHelper.isBusiness(mData.sourceType);
        final int contactType =
                mData.isVoicemailNumber ? ContactPhotoManager.TYPE_VOICEMAIL :
                isBusiness ? ContactPhotoManager.TYPE_BUSINESS :
                ContactPhotoManager.TYPE_DEFAULT;
        final String nameForDefaultImage = TextUtils.isEmpty(mData.name)
                ? mData.displayNumber : mData.name;

        loadContactPhoto(
                mData.contactUri, mData.photoUri, nameForDefaultImage, lookupKey, contactType);

        invalidateOptionsMenu();

        mPieChart.setOriginAngle(240);
        mPieChart.removeAllSlices();

        boolean byDuration = getIntent().getBooleanExtra(EXTRA_BY_DURATION, true);

        mTotalSummary.setText(getString(R.string.call_stats_header_total_callsonly,
                CallStatsListItemViewHolder.getCallCountString(this, mData.getTotalCount())));
        mTotalDuration.setText(CallStatsListItemViewHolder.getDurationString(
                    this, mData.getFullDuration(), true));

        if (shouldDisplay(Calls.INCOMING_TYPE, byDuration)) {
            int percent = byDuration
                    ? mData.getDurationPercentage(Calls.INCOMING_TYPE)
                    : mData.getCountPercentage(Calls.INCOMING_TYPE);

            mInSummary.setText(getString(R.string.call_stats_incoming, percent));
            mInCount.setText(CallStatsListItemViewHolder.getCallCountString(
                    this, mData.incomingCount));
            mInDuration.setText(CallStatsListItemViewHolder.getDurationString(
                    this, mData.inDuration, true));
            mPieChart.addSlice(byDuration ? mData.inDuration : mData.incomingCount,
                    mResources.getColor(R.color.call_stats_incoming));
        } else {
            findViewById(R.id.in_container).setVisibility(View.GONE);
        }

        if (shouldDisplay(Calls.OUTGOING_TYPE, byDuration)) {
            int percent = byDuration
                    ? mData.getDurationPercentage(Calls.OUTGOING_TYPE)
                    : mData.getCountPercentage(Calls.OUTGOING_TYPE);

            mOutSummary.setText(getString(R.string.call_stats_outgoing, percent));
            mOutCount.setText(CallStatsListItemViewHolder.getCallCountString(
                    this, mData.outgoingCount));
            mOutDuration.setText(CallStatsListItemViewHolder.getDurationString(
                    this, mData.outDuration, true));
            mPieChart.addSlice(byDuration ? mData.outDuration : mData.outgoingCount,
                    mResources.getColor(R.color.call_stats_outgoing));
        } else {
            findViewById(R.id.out_container).setVisibility(View.GONE);
        }

        if (shouldDisplay(Calls.MISSED_TYPE, false)) {
            final String missedCount =
                    CallStatsListItemViewHolder.getCallCountString(this, mData.missedCount);

            if (byDuration) {
                mMissedSummary.setText(R.string.call_stats_missed);
            } else {
                mMissedSummary.setText(getString(R.string.call_stats_missed_percent,
                        mData.getCountPercentage(Calls.MISSED_TYPE)));
                mPieChart.addSlice(mData.missedCount, mResources.getColor(R.color.call_stats_missed));
            }
            mMissedCount.setText(CallStatsListItemViewHolder.getCallCountString(
                    this, mData.missedCount));
        } else {
            findViewById(R.id.missed_container).setVisibility(View.GONE);
        }

        if (shouldDisplay(Calls.BLACKLIST_TYPE, false)) {
            if (byDuration) {
                mBlacklistSummary.setText(R.string.call_stats_blacklist);
            } else {
                mBlacklistSummary.setText(getString(R.string.call_stats_blacklist_percent,
                        mData.getCountPercentage(Calls.BLACKLIST_TYPE)));
                mPieChart.addSlice(mData.blacklistCount,
                        mResources.getColor(R.color.call_stats_blacklist));
            }
            mBlacklistCount.setText(CallStatsListItemViewHolder.getCallCountString(
                    this, mData.blacklistCount));
        } else {
            findViewById(R.id.blacklist_container).setVisibility(View.GONE);
        }

        mPieChart.generatePath();
        findViewById(R.id.call_stats_detail).setVisibility(View.VISIBLE);
    }

    private void loadContactPhoto(Uri contactUri, Uri photoUri, String displayName,
            String lookupKey, int contactType) {

        final ContactPhotoManager.DefaultImageRequest request =
                new ContactPhotoManager.DefaultImageRequest(displayName, lookupKey,
                        contactType, true /* isCircular */);

        mQuickContactBadge.assignContactUri(contactUri);
        mQuickContactBadge.setContentDescription(
                mResources.getString(R.string.description_contact_details, displayName));

        ContactPhotoManager.getInstance(this).loadDirectoryPhoto(mQuickContactBadge, photoUri,
                false /* darkTheme */, true /* isCircular */, request);
    }

    private boolean shouldDisplay(int type, boolean byDuration) {
        if (byDuration) {
            return mData.getRequestedDuration(type) != 0;
        } else {
            return mData.getRequestedCount(type) != 0;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.call_stats_details_options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_edit_number_before_call).setVisible(
                mHasEditNumberBeforeCallOption);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onHomeSelected();
                return true;
            }
            // All the options menu items are handled by onMenu... methods.
            default:
                throw new IllegalArgumentException();
        }
    }

    public void onMenuEditNumberBeforeCall(MenuItem menuItem) {
        startActivity(new Intent(Intent.ACTION_DIAL, CallUtil.getCallUri(mNumber)));
    }

    public void onMenuAddToBlacklist(MenuItem menuItem) {
        mContactInfoHelper.addNumberToBlacklist(mNumber);
    }

    private void onHomeSelected() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Calls.CONTENT_URI);
        // This will open the call log even if the detail view has been opened directly.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
