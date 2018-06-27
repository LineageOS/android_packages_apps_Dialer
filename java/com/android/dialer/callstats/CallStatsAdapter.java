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

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.common.preference.ContactsPreferences;
import com.android.dialer.R;
import com.android.dialer.app.DialtactsActivity;
import com.android.dialer.app.contactinfo.ContactInfoCache;
import com.android.dialer.app.contactinfo.NumberWithCountryIso;
import com.android.dialer.clipboard.ClipboardUtils;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.phonenumbercache.ContactInfoHelper;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.util.CallUtil;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.ExpirableCache;
import com.android.dialer.util.PermissionsUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter class to hold and handle call stat entries
 */
class CallStatsAdapter extends RecyclerView.Adapter {
  private final Context mContext;
  private final ContactInfoHelper mContactInfoHelper;
  private final ContactInfoCache mContactInfoCache;
  private final ContactsPreferences mContactsPreferences;

  private ArrayList<CallStatsDetails> mAllItems;
  private ArrayList<CallStatsDetails> mShownItems;
  private CallStatsDetails mTotalItem;
  private Map<CallStatsDetails, ContactInfo> mInfoLookup;

  private int mType = -1;
  private long mFilterFrom;
  private long mFilterTo;
  private boolean mSortByDuration;

  /**
   * Listener that is triggered to populate the context menu with actions to perform on the call's
   * number, when the call log entry is long pressed.
   */
  private final View.OnCreateContextMenuListener mContextMenuListener = (menu, v, menuInfo) -> {
    final CallStatsListItemViewHolder vh = (CallStatsListItemViewHolder) v.getTag();
    if (TextUtils.isEmpty(vh.details.number)) {
      return;
    }

    menu.setHeaderTitle(vh.details.number);

    final MenuItem copyItem = menu.add(ContextMenu.NONE, R.id.context_menu_copy_to_clipboard,
        ContextMenu.NONE, R.string.action_copy_number_text);

    copyItem.setOnMenuItemClickListener(item -> {
      ClipboardUtils.copyText(CallStatsAdapter.this.mContext, null, vh.details.number, true);
      return true;
    });

    // The edit number before call does not show up if any of the conditions apply:
    // 1) Number cannot be called
    // 2) Number is the voicemail number
    // 3) Number is a SIP address

    boolean canPlaceCallsTo = PhoneNumberHelper.canPlaceCallsTo(vh.details.number,
        vh.details.numberPresentation);
    if (!canPlaceCallsTo || PhoneNumberHelper.isSipNumber(vh.details.number)) {
      return;
    }

    final MenuItem editItem = menu.add(ContextMenu.NONE, R.id.context_menu_edit_before_call,
        ContextMenu.NONE, R.string.action_edit_number_before_call);

    editItem.setOnMenuItemClickListener(item -> {
      final Intent intent = new Intent(Intent.ACTION_DIAL, CallUtil.getCallUri(vh.details.number));
      DialerUtils.startActivityWithErrorToast(v.getContext(), intent);
      return true;
    });
  };

  private final Comparator<CallStatsDetails> mDurationComparator = (o1, o2) -> {
    Long duration1 = o1.getRequestedDuration(mType);
    Long duration2 = o2.getRequestedDuration(mType);
    // sort descending
    return duration2.compareTo(duration1);
  };
  private final Comparator<CallStatsDetails> mCountComparator = (o1, o2) -> {
    Integer count1 = o1.getRequestedCount(mType);
    Integer count2 = o2.getRequestedCount(mType);
    // sort descending
    return count2.compareTo(count1);
  };

  CallStatsAdapter(Context context, ContactsPreferences prefs,
      ExpirableCache<NumberWithCountryIso,ContactInfo> cache) {
    mContext = context;
    mContactsPreferences = prefs;

    final String currentCountryIso = GeoUtil.getCurrentCountryIso(mContext);
    mContactInfoHelper = new ContactInfoHelper(mContext, currentCountryIso);

    mAllItems = new ArrayList<CallStatsDetails>();
    mShownItems = new ArrayList<CallStatsDetails>();
    mTotalItem = new CallStatsDetails(null, 0, null, null, null, null, null, 0);
    mInfoLookup = new ConcurrentHashMap<>();

    mContactInfoCache = new ContactInfoCache(cache,
        mContactInfoHelper, () -> notifyDataSetChanged());
    if (!PermissionsUtil.hasContactsReadPermissions(context)) {
      mContactInfoCache.disableRequestProcessing();
    }
  }

  public void updateData(Map<ContactInfo, CallStatsDetails> calls, long from, long to) {
    mInfoLookup.clear();
    mFilterFrom = from;
    mFilterTo = to;

    mAllItems.clear();
    mTotalItem.reset();

    for (Map.Entry<ContactInfo, CallStatsDetails> entry : calls.entrySet()) {
      final CallStatsDetails call = entry.getValue();
      mAllItems.add(call);
      mTotalItem.mergeWith(call);
      mInfoLookup.put(call, entry.getKey());
    }
  }

  public void updateDisplayedData(int type, boolean sortByDuration) {
    mType = type;
    mSortByDuration = sortByDuration;

    mShownItems.clear();

    for (CallStatsDetails call : mAllItems) {
      if (sortByDuration && call.getRequestedDuration(type) > 0) {
          mShownItems.add(call);
      } else if (!sortByDuration && call.getRequestedCount(type) > 0) {
          mShownItems.add(call);
      }
    }

    Collections.sort(mShownItems, sortByDuration ? mDurationComparator : mCountComparator);
    notifyDataSetChanged();
  }

  public void invalidateCache() {
    mContactInfoCache.invalidate();
  }

  public void startCache() {
    if (PermissionsUtil.hasPermission(mContext, android.Manifest.permission.READ_CONTACTS)) {
      mContactInfoCache.start();
    }
  }

  public void pauseCache() {
    mContactInfoCache.stop();
  }

  @Override
  public int getItemCount() {
    return mShownItems.size();
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(mContext);
    View view = inflater.inflate(R.layout.call_stats_list_item, parent, false);
    CallStatsListItemViewHolder viewHolder = CallStatsListItemViewHolder.create(view,
        mContactInfoHelper);

    viewHolder.mPrimaryActionView.setOnCreateContextMenuListener(mContextMenuListener);
    viewHolder.mPrimaryActionView.setTag(viewHolder);

    return viewHolder;
  }

  @Override
  public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
    CallStatsDetails details = mShownItems.get(position);
    CallStatsDetails first = mShownItems.get(0);
    CallStatsListItemViewHolder views = (CallStatsListItemViewHolder) viewHolder;

    if (PhoneNumberHelper.canPlaceCallsTo(details.number, details.numberPresentation)
        && !details.isVoicemailNumber) {
      ContactInfo info = mContactInfoCache.getValue(details.number + details.postDialDigits,
          details.countryIso, mInfoLookup.get(details), false);
      if (info != null) {
        details.updateFromInfo(info);
      }
    }
    views.setDetails(details, first, mTotalItem, mType,
        mSortByDuration, mContactsPreferences.getDisplayOrder());
    views.clickIntent = getItemClickIntent(details);
  }

  private Intent getItemClickIntent(CallStatsDetails details) {
    Intent intent = new Intent(mContext, CallStatsDetailActivity.class);
    intent.putExtra(CallStatsDetailActivity.EXTRA_DETAILS, details);
    intent.putExtra(CallStatsDetailActivity.EXTRA_TOTAL, mTotalItem);
    intent.putExtra(CallStatsDetailActivity.EXTRA_FROM, mFilterFrom);
    intent.putExtra(CallStatsDetailActivity.EXTRA_TO, mFilterTo);
    return intent;
  }

  public String getTotalCallCountString() {
    return CallStatsListItemViewHolder.getCallCountString(
        mContext, mTotalItem.getRequestedCount(mType));
  }

  public String getFullDurationString(boolean withSeconds) {
    final long duration = mTotalItem.getRequestedDuration(mType);
    return CallStatsListItemViewHolder.getDurationString(
        mContext, duration, withSeconds);
  }
}
