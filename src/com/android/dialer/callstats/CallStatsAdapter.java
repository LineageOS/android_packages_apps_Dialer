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

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ClipboardUtils;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.contactinfo.ContactInfoCache;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.PhoneNumberUtil;
import com.cyanogen.lookup.phonenumber.contract.LookupProvider;
import com.cyanogen.lookup.phonenumber.provider.LookupProviderImpl;

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
    private final LookupProvider mLookupProvider;

    private ArrayList<CallStatsDetails> mAllItems;
    private ArrayList<CallStatsDetails> mShownItems;
    private CallStatsDetails mTotalItem;
    private Map<ContactInfo, CallStatsDetails> mInfoLookup;

    private int mType = CallLogQueryHandler.CALL_TYPE_ALL;
    private long mFilterFrom;
    private long mFilterTo;
    private boolean mSortByDuration;

    /**
     * Listener that is triggered to populate the context menu with actions to perform on the call's
     * number, when the call log entry is long pressed.
     */
    private final View.OnCreateContextMenuListener mOnCreateContextMenuListener =
            new View.OnCreateContextMenuListener() {
                @Override
                public void onCreateContextMenu(ContextMenu menu, View v,
                        ContextMenu.ContextMenuInfo menuInfo) {
                    final CallStatsListItemViewHolder vh =
                            (CallStatsListItemViewHolder) v.getTag();
                    if (TextUtils.isEmpty(vh.details.number)) {
                        return;
                    }

                    menu.setHeaderTitle(vh.details.number);

                    final MenuItem copyItem = menu.add(
                            ContextMenu.NONE,
                            R.id.context_menu_copy_to_clipboard,
                            ContextMenu.NONE,
                            R.string.copy_text);

                    copyItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            ClipboardUtils.copyText(CallStatsAdapter.this.mContext, null,
                                    vh.details.number, true);
                            return true;
                        }
                    });

                    // The edit number before call does not show up if any of the conditions apply:
                    // 1) Number cannot be called
                    // 2) Number is the voicemail number
                    // 3) Number is a SIP address

                    boolean canPlaceCallsTo = PhoneNumberUtil.canPlaceCallsTo(vh.details.number,
                        vh.details.numberPresentation);
                    if (!canPlaceCallsTo || PhoneNumberUtil.isSipNumber(vh.details.number)) {
                        return;
                    }

                    final MenuItem editItem = menu.add(
                            ContextMenu.NONE,
                            R.id.context_menu_edit_before_call,
                            ContextMenu.NONE,
                            R.string.recentCalls_editNumberBeforeCall);

                    editItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            final Intent intent = new Intent(Intent.ACTION_DIAL,
                                    CallUtil.getCallUri(vh.details.number));
                            intent.setClass(mContext, DialtactsActivity.class);
                            DialerUtils.startActivityWithErrorToast(mContext, intent);
                            return true;
                        }
                    });
                }
            };

    protected final ContactInfoCache.OnContactInfoChangedListener mOnContactInfoChangedListener =
            new ContactInfoCache.OnContactInfoChangedListener() {
                @Override
                public void onContactInfoChanged() {
                    notifyDataSetChanged();
                }
            };

    private final Comparator<CallStatsDetails> mDurationComparator =
            new Comparator<CallStatsDetails>() {
        @Override
        public int compare(CallStatsDetails o1, CallStatsDetails o2) {
            Long duration1 = o1.getRequestedDuration(mType);
            Long duration2 = o2.getRequestedDuration(mType);
            // sort descending
            return duration2.compareTo(duration1);
        }
    };
    private final Comparator<CallStatsDetails> mCountComparator =
            new Comparator<CallStatsDetails>() {
        @Override
        public int compare(CallStatsDetails o1, CallStatsDetails o2) {
            Integer count1 = o1.getRequestedCount(mType);
            Integer count2 = o2.getRequestedCount(mType);
            // sort descending
            return count2.compareTo(count1);
        }
    };

    CallStatsAdapter(Context context) {
        mContext = context;

        final String currentCountryIso = GeoUtil.getCurrentCountryIso(mContext);
        mLookupProvider = LookupProviderImpl.INSTANCE.get(mContext);
        mContactInfoHelper = new ContactInfoHelper(mContext, currentCountryIso, mLookupProvider);

        mAllItems = new ArrayList<CallStatsDetails>();
        mShownItems = new ArrayList<CallStatsDetails>();
        mTotalItem = new CallStatsDetails(null, 0, null, null, null, null, 0);
        mInfoLookup = new ConcurrentHashMap<ContactInfo, CallStatsDetails>();

        mContactInfoCache = new ContactInfoCache(
                mContactInfoHelper, mOnContactInfoChangedListener);
        if (!PermissionsUtil.hasContactsPermissions(context)) {
            mContactInfoCache.disableRequestProcessing();
        }
    }

    public void updateData(Map<ContactInfo, CallStatsDetails> calls, long from, long to) {
        mInfoLookup.clear();
        mInfoLookup.putAll(calls);
        mFilterFrom = from;
        mFilterTo = to;

        mAllItems.clear();
        mTotalItem.reset();

        for (Map.Entry<ContactInfo, CallStatsDetails> entry : calls.entrySet()) {
            final CallStatsDetails call = entry.getValue();
            mAllItems.add(call);
            mTotalItem.mergeWith(call);
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

        viewHolder.mPrimaryActionView.setOnCreateContextMenuListener(mOnCreateContextMenuListener);
        viewHolder.mPrimaryActionView.setTag(viewHolder);

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        CallStatsDetails details = mShownItems.get(position);
        CallStatsDetails first = mShownItems.get(0);
        CallStatsListItemViewHolder views = (CallStatsListItemViewHolder) viewHolder;

        views.setDetails(details, first, mTotalItem, mType, mSortByDuration);
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

    public void destroy() {
        LookupProviderImpl.INSTANCE.release();
    }
}
