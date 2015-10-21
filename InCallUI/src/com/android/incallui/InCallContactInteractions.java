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
 * limitations under the License
 */

package com.android.incallui;

import android.content.Context;
import android.location.Address;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wrapper class for objects that are used in generating the context about the contact in the InCall
 * screen.
 *
 * This handles generating the appropriate resource for the ListAdapter based on whether the contact
 * is a business contact or not and logic for the manipulation of data for the call context.
 */
public class InCallContactInteractions {
    private Context mContext;
    private InCallContactInteractionsListAdapter mListAdapter;
    private boolean mIsBusiness;
    private View mBusinessHeaderView;
    private LayoutInflater mInflater;

    public InCallContactInteractions(Context context, boolean isBusiness) {
        mContext = context;
        mInflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        switchContactType(isBusiness);
    }

    public InCallContactInteractionsListAdapter getListAdapter() {
        return mListAdapter;
    }

    /**
     * Switches the "isBusiness" value, if applicable. Recreates the list adapter with the resource
     * corresponding to the new isBusiness value if the "isBusiness" value is switched.
     *
     * @param isBusiness Whether or not the contact is a business.
     *
     * @return {@code true} if a new list adapter was created, {@code} otherwise.
     */
    public boolean switchContactType(boolean isBusiness) {
        if (mIsBusiness != isBusiness || mListAdapter == null) {
            mIsBusiness = isBusiness;
            mListAdapter = new InCallContactInteractionsListAdapter(mContext,
                    mIsBusiness ? R.layout.business_context_info_list_item
                            : R.layout.person_context_info_list_item);
            return true;
        }
        return false;
    }

    public void setBusinessInfo(Address address, float distance) {
        mListAdapter.clear();
        mListAdapter.addAll(constructBusinessContextInfo(address, distance));
    }

    public View getBusinessListHeaderView() {
        if (mBusinessHeaderView == null) {
            mBusinessHeaderView = mInflater.inflate(
                    R.layout.business_contact_context_list_header, null);
        }
        return mBusinessHeaderView;
    }

    private List<ContactContextInfo> constructBusinessContextInfo(Address address, float distance) {
        List<ContactContextInfo> info = new ArrayList<ContactContextInfo>();

        //TODO: hours of operation information

        // Location information
        BusinessContextInfo distanceInfo = new BusinessContextInfo();
        distanceInfo.iconId = R.drawable.ic_location_on_white_24dp;
        if (distance != DistanceHelper.DISTANCE_NOT_FOUND) {
            //TODO: add a setting to allow the user to select "KM" or "MI" as their distance units.
            if (Locale.US.equals(Locale.getDefault())) {
                distanceInfo.heading = mContext.getString(R.string.distance_imperial_away,
                        distance * DistanceHelper.MILES_PER_METER);
            } else {
                distanceInfo.heading = mContext.getString(R.string.distance_metric_away,
                        distance * DistanceHelper.KILOMETERS_PER_METER);
            }
        }
        if (address != null) {
            distanceInfo.detail = address.getAddressLine(0);
        }
        info.add(distanceInfo);

        return info;
    }

    /**
     * Get the appropriate title for the context.
     * @return The "Business info" title for a business contact and the "Recent messages" title for
     *         personal contacts.
     */
    public String getContactContextTitle() {
        return mIsBusiness
                ? mContext.getResources().getString(R.string.business_contact_context_title)
                : mContext.getResources().getString(R.string.person_contact_context_title);
    }

    public static abstract class ContactContextInfo {
        public abstract void bindView(View listItem);
    }

    public static class BusinessContextInfo extends ContactContextInfo {
        int iconId;
        String heading;
        String detail;

        @Override
        public void bindView(View listItem) {
            ImageView imageView = (ImageView) listItem.findViewById(R.id.icon);
            TextView headingTextView = (TextView) listItem.findViewById(R.id.heading);
            TextView detailTextView = (TextView) listItem.findViewById(R.id.detail);

            if (this.iconId == 0 || (this.heading == null && this.detail == null)) {
                return;
            }

            imageView.setImageDrawable(listItem.getContext().getDrawable(this.iconId));

            headingTextView.setText(this.heading);
            headingTextView.setVisibility(TextUtils.isEmpty(this.heading)
                    ? View.GONE : View.VISIBLE);

            detailTextView.setText(this.detail);
            detailTextView.setVisibility(TextUtils.isEmpty(this.detail)
                    ? View.GONE : View.VISIBLE);

        }
    }

    public static class PersonContextInfo extends ContactContextInfo {
        boolean isIncoming;
        String message;
        String detail;

        @Override
        public void bindView(View listItem) {
            TextView messageTextView = (TextView) listItem.findViewById(R.id.message);
            TextView detailTextView = (TextView) listItem.findViewById(R.id.detail);

            if (this.message == null || this.detail == null) {
                return;
            }

            messageTextView.setBackgroundResource(this.isIncoming ?
                    R.drawable.incoming_sms_background : R.drawable.outgoing_sms_background);
            messageTextView.setText(this.message);
            LayoutParams messageLayoutParams = (LayoutParams) messageTextView.getLayoutParams();
            messageLayoutParams.addRule(this.isIncoming?
                    RelativeLayout.ALIGN_PARENT_START : RelativeLayout.ALIGN_PARENT_END);
            messageTextView.setLayoutParams(messageLayoutParams);

            LayoutParams detailLayoutParams = (LayoutParams) detailTextView.getLayoutParams();
            detailLayoutParams.addRule(this.isIncoming ?
                    RelativeLayout.ALIGN_PARENT_START : RelativeLayout.ALIGN_PARENT_END);
            detailTextView.setLayoutParams(detailLayoutParams);
            detailTextView.setText(this.detail);
        }
    }

    /**
     * A list adapter for call context information. We use the same adapter for both business and
     * contact context.
     */
    private class InCallContactInteractionsListAdapter extends ArrayAdapter<ContactContextInfo> {
        // The resource id of the list item layout.
        int mResId;

        public InCallContactInteractionsListAdapter(Context context, int resource) {
            super(context, resource);
            mResId = resource;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View listItem = mInflater.inflate(mResId, null);
            ContactContextInfo item = getItem(position);

            if (item == null) {
                return listItem;
            }

            item.bindView(listItem);
            return listItem;
        }
    }
}