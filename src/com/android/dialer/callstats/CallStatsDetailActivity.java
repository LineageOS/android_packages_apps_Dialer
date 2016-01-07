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
import com.android.dialer.widget.LinearColorBar;

/**
 * Activity to display detailed information about a callstat item
 */
public class CallStatsDetailActivity extends Activity {
    private static final String TAG = "CallStatsDetailActivity";

    public static final String EXTRA_DETAILS = "details";
    public static final String EXTRA_FROM = "from";
    public static final String EXTRA_TO = "to";

    private ContactInfoHelper mContactInfoHelper;
    private Resources mResources;

    private QuickContactBadge mQuickContactBadge;
    private TextView mCallerName;
    private TextView mCallerNumber;
    private View mCallButton;

    private TextView mTotalDuration, mTotalCount;

    private DetailLine mInDuration, mOutDuration;
    private DetailLine mInCount, mOutCount;
    private DetailLine mMissedCount, mBlacklistedCount;

    private LinearColorBar mDurationBar, mCountBar;

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

        mDurationBar = (LinearColorBar) findViewById(R.id.duration_percent_bar);
        mTotalDuration = (TextView) findViewById(R.id.total_duration);
        mInDuration = new DetailLine(R.id.in_duration,
                R.string.call_stats_incoming, Calls.INCOMING_TYPE);
        mOutDuration = new DetailLine(R.id.out_duration,
                R.string.call_stats_outgoing, Calls.OUTGOING_TYPE);

        mCountBar = (LinearColorBar) findViewById(R.id.count_percent_bar);
        mTotalCount = (TextView) findViewById(R.id.total_count);
        mInCount = new DetailLine(R.id.in_count,
                R.string.call_stats_incoming, Calls.INCOMING_TYPE);
        mOutCount = new DetailLine(R.id.out_count,
                R.string.call_stats_outgoing, Calls.OUTGOING_TYPE);
        mMissedCount = new DetailLine(R.id.missed_count,
                R.string.call_stats_missed, Calls.MISSED_TYPE);
        mBlacklistedCount = new DetailLine(R.id.blacklisted_count,
                R.string.call_stats_blacklisted, Calls.BLACKLIST_TYPE);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Intent launchIntent = getIntent();
        mData = (CallStatsDetails) launchIntent.getParcelableExtra(EXTRA_DETAILS);
        updateData();

        TextView dateFilterView = (TextView) findViewById(R.id.date_filter);
        long filterFrom = launchIntent.getLongExtra(EXTRA_FROM, -1);
        if (filterFrom == -1) {
            dateFilterView.setVisibility(View.GONE);
        } else {
            long filterTo = launchIntent.getLongExtra(EXTRA_TO, -1);
            dateFilterView.setText(DateUtils.formatDateRange(
                    this, filterFrom, filterTo, 0));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        new UpdateContactTask().execute(mData.number.toString(), mData.countryIso);
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

        long totalDuration = mData.getFullDuration();
        mInDuration.updateFromDurations(mData.inDuration, totalDuration);
        mOutDuration.updateFromDurations(mData.outDuration, totalDuration);
        if (totalDuration != 0) {
            mTotalDuration.setText(CallStatsListItemViewHolder.getDurationString(this,
                    totalDuration, true));
            mTotalDuration.setVisibility(View.VISIBLE);
            updateBar(mDurationBar, mData.inDuration, mData.outDuration, 0, 0);
            mDurationBar.setVisibility(View.VISIBLE);
        } else {
            mTotalDuration.setVisibility(View.GONE);
            mDurationBar.setVisibility(View.GONE);
        }

        int totalCount = mData.getTotalCount();
        mTotalCount.setText(CallStatsListItemViewHolder.getCallCountString(this, totalCount));
        mInCount.updateFromCounts(mData.incomingCount, totalCount);
        mOutCount.updateFromCounts(mData.outgoingCount, totalCount);
        mMissedCount.updateFromCounts(mData.missedCount, totalCount);
        mBlacklistedCount.updateFromCounts(mData.blacklistCount, totalCount);
        updateBar(mCountBar, mData.incomingCount, mData.outgoingCount,
                mData.missedCount, mData.blacklistCount);
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

    private void updateBar(LinearColorBar bar,
            long value1, long value2, long value3, long value4) {
        float total = value1 + value2 + value3 + value4;
        bar.setRatios((float) value1 / total, (float) value2 / total,
                (float) value3 / total, (float) value4 / total);
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

    private class DetailLine {
        private int mValueTemplateResId;
        private View mRootView;
        private TextView mTextView;
        private TextView mPercentView;

        public DetailLine(int rootViewId, int valueTemplateResId, int iconType) {
            mValueTemplateResId = valueTemplateResId;
            mRootView = findViewById(rootViewId);
            mTextView = (TextView) mRootView.findViewById(R.id.value);
            mPercentView = (TextView) mRootView.findViewById(R.id.percent);

            CallTypeIconsView icon = (CallTypeIconsView) mRootView.findViewById(R.id.icon);
            icon.add(iconType);
        }

        public void updateFromCounts(int count, int totalCount) {
            if (count == 0 && totalCount > 0) {
                mRootView.setVisibility(View.GONE);
                return;
            }

            mRootView.setVisibility(View.VISIBLE);
            String value = CallStatsListItemViewHolder.getCallCountString(
                    mTextView.getContext(), count);
            mTextView.setText(getString(mValueTemplateResId, value));
            updatePercent(count, totalCount);
        }

        public void updateFromDurations(long duration, long totalDuration) {
            if (duration == 0 && totalDuration >= 0) {
                mRootView.setVisibility(View.GONE);
                return;
            }

            mRootView.setVisibility(View.VISIBLE);
            String value = CallStatsListItemViewHolder.getDurationString(
                    mTextView.getContext(), duration, true);
            mTextView.setText(getString(mValueTemplateResId, value));
            updatePercent(duration, totalDuration);
        }

        private void updatePercent(long value, long total) {
            int percent = (int) Math.round(100F * value / total);
            mPercentView.setText(getString(R.string.call_stats_percent, percent));
        }
    }
}
