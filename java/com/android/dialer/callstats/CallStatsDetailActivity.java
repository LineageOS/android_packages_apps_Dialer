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

import android.app.DialogFragment;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.common.preference.ContactsPreferences;
import com.android.dialer.R;
import com.android.dialer.app.AccountSelectionActivity;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.calllogutils.CallTypeIconsView;
import com.android.dialer.clipboard.ClipboardUtils;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.phonenumbercache.ContactInfoHelper;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.util.CallUtil;
import com.android.dialer.widget.LinearColorBar;

/**
 * Activity to display detailed information about a callstat item
 */
public class CallStatsDetailActivity extends AppCompatActivity implements
    View.OnClickListener, View.OnLongClickListener {
  private static final String TAG = "CallStatsDetailActivity";

  public static final String EXTRA_DETAILS = "details";
  public static final String EXTRA_TOTAL = "total";
  public static final String EXTRA_FROM = "from";
  public static final String EXTRA_TO = "to";

  private ContactInfoHelper mContactInfoHelper;
  private ContactsPreferences mContactsPreferences;
  private Resources mResources;

  private QuickContactBadge mQuickContactBadge;
  private TextView mCallerName;
  private TextView mCallerNumber;
  private View mCallButton;
  private View mSeparator;
  private View mCopyButton;
  private View mEditNumberButton;

  private TextView mTotalDuration, mTotalCount;
  private TextView mTotalTotalDuration, mTotalTotalCount;

  private DetailLine mInDuration, mOutDuration;
  private DetailLine mInCount, mOutCount;
  private DetailLine mMissedCount, mBlockedCount;
  private DetailLine mInAverage, mOutAverage;

  private LinearColorBar mDurationBar, mCountBar;
  private LinearColorBar mTotalDurationBar, mTotalCountBar;

  private CallStatsDetails mData;
  private CallStatsDetails mTotalData;
  private String mNumber = null;

  private class UpdateContactTask extends AsyncTask<String, Void, ContactInfo> {
    @Override
    protected ContactInfo doInBackground(String... strings) {
      return mContactInfoHelper.lookupNumber(strings[0], strings[1]);
    }

    @Override
    protected void onPostExecute(ContactInfo info) {
      if (info != null) {
        mData.updateFromInfo(info);
        updateData();
      }
    }
  }

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    setContentView(R.layout.call_stats_detail);

    Toolbar toolbar = findViewById(R.id.toolbar);
    toolbar.setNavigationOnClickListener(v -> finish());
    toolbar.setTitle(R.string.call_stats_detail_title);

    mResources = getResources();
    mContactInfoHelper = new ContactInfoHelper(this, GeoUtil.getCurrentCountryIso(this));
    mContactsPreferences = new ContactsPreferences(this);

    mQuickContactBadge = (QuickContactBadge) findViewById(R.id.quick_contact_photo);
    mQuickContactBadge.setOverlay(null);
    mQuickContactBadge.setPrioritizedMimeType(Phone.CONTENT_ITEM_TYPE);
    mCallerName = (TextView) findViewById(R.id.caller_name);
    mCallerNumber = (TextView) findViewById(R.id.caller_number);

    mCallButton = findViewById(R.id.call_back_button);
    mCallButton.setOnClickListener(this);
    mCallButton.setOnLongClickListener(this);

    mSeparator = findViewById(R.id.separator);
    mEditNumberButton = findViewById(R.id.call_detail_action_edit_before_call);
    mEditNumberButton.setOnClickListener(this);
    mCopyButton = findViewById(R.id.call_detail_action_copy);
    mCopyButton.setOnClickListener(this);

    mDurationBar = (LinearColorBar) findViewById(R.id.duration_number_percent_bar);
    mTotalDurationBar = (LinearColorBar) findViewById(R.id.duration_total_percent_bar);
    mTotalDuration = (TextView) findViewById(R.id.total_duration_number);
    mTotalTotalDuration = (TextView) findViewById(R.id.total_duration_total);
    mInDuration = new DetailLine(R.id.in_duration,
        R.string.call_stats_incoming, Calls.INCOMING_TYPE);
    mOutDuration = new DetailLine(R.id.out_duration,
        R.string.call_stats_outgoing, Calls.OUTGOING_TYPE);

    mCountBar = (LinearColorBar) findViewById(R.id.count_number_percent_bar);
    mTotalCountBar = (LinearColorBar) findViewById(R.id.count_total_percent_bar);
    mTotalCount = (TextView) findViewById(R.id.total_count_number);
    mTotalTotalCount = (TextView) findViewById(R.id.total_count_total);
    mInCount = new DetailLine(R.id.in_count, R.string.call_stats_incoming, Calls.INCOMING_TYPE);
    mOutCount = new DetailLine(R.id.out_count, R.string.call_stats_outgoing, Calls.OUTGOING_TYPE);
    mMissedCount = new DetailLine(R.id.missed_count,
        R.string.call_stats_missed, Calls.MISSED_TYPE);
    mBlockedCount = new DetailLine(R.id.blocked_count,
        R.string.call_stats_blocked, Calls.BLOCKED_TYPE);

    mInAverage = new DetailLine(R.id.in_average,
        R.string.call_stats_incoming, Calls.INCOMING_TYPE);
    mOutAverage = new DetailLine(R.id.out_average,
        R.string.call_stats_outgoing, Calls.OUTGOING_TYPE);

    Intent launchIntent = getIntent();
    mData = (CallStatsDetails) launchIntent.getParcelableExtra(EXTRA_DETAILS);
    mTotalData = (CallStatsDetails) launchIntent.getParcelableExtra(EXTRA_TOTAL);
    updateData();

    TextView dateFilterView = (TextView) findViewById(R.id.date_filter);
    long filterFrom = launchIntent.getLongExtra(EXTRA_FROM, -1);
    if (filterFrom == -1) {
      dateFilterView.setVisibility(View.GONE);
    } else {
      long filterTo = launchIntent.getLongExtra(EXTRA_TO, -1);
      dateFilterView.setText(DateUtils.formatDateRange(this, filterFrom, filterTo, 0));
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    mContactsPreferences.refreshValue(ContactsPreferences.DISPLAY_ORDER_KEY);
    new UpdateContactTask().execute(mData.number.toString(), mData.countryIso);
  }

  private void updateData() {
    mNumber = mData.number.toString();

    // Cache the details about the phone number.
    boolean canPlaceCallsTo = PhoneNumberHelper.canPlaceCallsTo(mNumber, mData.numberPresentation);
    final CharSequence callLocationOrType = !TextUtils.isEmpty(mData.displayName)
        ? Phone.getTypeLabel(mResources, mData.numberType, mData.numberLabel)
        : mData.geocode;

    mData.updateDisplayProperties(this, mContactsPreferences.getDisplayOrder());

    final boolean isSipNumber = PhoneNumberHelper.isSipNumber(mNumber);
    boolean hasEditNumberBeforeCallOption =
        canPlaceCallsTo && !isSipNumber && !mData.isVoicemailNumber;

    if (!TextUtils.isEmpty(mData.displayName)) {
      mCallerName.setText(mData.displayName);
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
    mCopyButton.setVisibility(canPlaceCallsTo ? View.VISIBLE : View.GONE);
    mEditNumberButton.setVisibility(hasEditNumberBeforeCallOption ? View.VISIBLE : View.GONE);
    mSeparator.setVisibility(canPlaceCallsTo || hasEditNumberBeforeCallOption
        ? View.VISIBLE : View.GONE);

    final boolean isBusiness = mContactInfoHelper.isBusiness(mData.sourceType);
    final int contactType =
        mData.isVoicemailNumber ? LetterTileDrawable.TYPE_VOICEMAIL :
        isBusiness ? LetterTileDrawable.TYPE_BUSINESS :
        LetterTileDrawable.TYPE_DEFAULT;
    final String nameForDefaultImage = TextUtils.isEmpty(mData.name)
        ? mData.displayNumber : mData.name;

    ContactPhotoManager.getInstance(this).loadDialerThumbnailOrPhoto(mQuickContactBadge,
        mData.contactUri, mData.photoId, mData.photoUri, nameForDefaultImage, contactType);

    invalidateOptionsMenu();

    long totalDuration = mData.getFullDuration();
    mInDuration.updateFromDurations(mData.inDuration, totalDuration);
    mOutDuration.updateFromDurations(mData.outDuration, totalDuration);
    if (totalDuration != 0) {
      mTotalDuration.setText(CallStatsListItemViewHolder.getDurationString(this,
          totalDuration, true));
      mTotalTotalDuration.setText(CallStatsListItemViewHolder.getDurationString(this,
          mTotalData.getFullDuration(), true));
      updateBar(mDurationBar, mData.inDuration, mData.outDuration, 0, 0, totalDuration);
      updateBar(mTotalDurationBar, mData.inDuration, mData.outDuration,
          0, 0, mTotalData.getFullDuration());
      findViewById(R.id.duration_container).setVisibility(View.VISIBLE);
    } else {
      findViewById(R.id.duration_container).setVisibility(View.GONE);
    }

    mInAverage.updateAsAverage(mData.inDuration, mData.incomingCount);
    mOutAverage.updateAsAverage(mData.outDuration, mData.outgoingCount);

    int totalCount = mData.getTotalCount();
    mTotalCount.setText(CallStatsListItemViewHolder.getCallCountString(this, totalCount));
    mTotalTotalCount.setText(
        CallStatsListItemViewHolder.getCallCountString(this, mTotalData.getTotalCount()));
    mInCount.updateFromCounts(mData.incomingCount, totalCount);
    mOutCount.updateFromCounts(mData.outgoingCount, totalCount);
    mMissedCount.updateFromCounts(mData.missedCount, totalCount);
    mBlockedCount.updateFromCounts(mData.blockedCount, totalCount);
    updateBar(mCountBar, mData.incomingCount, mData.outgoingCount,
        mData.missedCount, mData.blockedCount, totalCount);
    updateBar(mTotalCountBar, mData.incomingCount, mData.outgoingCount,
        mData.missedCount, mData.blockedCount, mTotalData.getTotalCount());
  }

  private void updateBar(LinearColorBar bar,
      long value1, long value2, long value3, long value4, long total) {
    bar.setRatios((float) value1 / total, (float) value2 / total,
        (float) value3 / total, (float) value4 / total);
  }

  @Override
  public void onClick(View view) {
    if (view == mCallButton) {
      Intent intent = new CallIntentBuilder(mNumber, CallInitiationType.Type.CALL_LOG).build();
      startActivity(intent);
    } else if (view == mCopyButton) {
      ClipboardUtils.copyText(this, null, mNumber, true);
    } else if (view == mEditNumberButton) {
      startActivity(new Intent(Intent.ACTION_DIAL, CallUtil.getCallUri(mNumber)));
    }
  }

  @Override
  public boolean onLongClick(View view) {
    if (view == mCallButton) {
      Intent intent = AccountSelectionActivity.createIntent(
          CallStatsDetailActivity.this, mNumber, CallInitiationType.Type.CALL_LOG);
      if (intent != null) {
        startActivity(intent);
        return true;
      }
    }
    return false;
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
      String value = CallStatsListItemViewHolder.getCallCountString(mTextView.getContext(), count);
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

    public void updateAsAverage(long duration, int count) {
      if (count == 0) {
        mRootView.setVisibility(View.GONE);
        return;
      }

      mRootView.setVisibility(View.VISIBLE);
      mPercentView.setVisibility(View.GONE);

      long averageDuration = (long) Math.round((float) duration / (float) count);
      String value = CallStatsListItemViewHolder.getDurationString(
          mTextView.getContext(), averageDuration, true);
      mTextView.setText(getString(mValueTemplateResId, value));
    }

    private void updatePercent(long value, long total) {
      int percent = (int) Math.round(100F * value / total);
      mPercentView.setText(getString(R.string.call_stats_percent, percent));
    }
  }
}
