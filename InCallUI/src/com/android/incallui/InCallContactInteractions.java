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

import com.google.common.annotations.VisibleForTesting;

import android.content.Context;
import android.location.Address;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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

    public View getBusinessListHeaderView() {
        if (mBusinessHeaderView == null) {
            mBusinessHeaderView = mInflater.inflate(
                    R.layout.business_contact_context_list_header, null);
        }
        return mBusinessHeaderView;
    }

    public void setBusinessInfo(Address address, float distance,
            List<Pair<Calendar, Calendar>> openingHours) {
        mListAdapter.clear();
        List<ContactContextInfo> info = new ArrayList<ContactContextInfo>();

        // Hours of operation
        if (openingHours != null) {
            BusinessContextInfo hoursInfo = constructHoursInfo(openingHours);
            if (hoursInfo != null) {
                info.add(hoursInfo);
            }
        }

        // Location information
        if (address != null) {
            BusinessContextInfo locationInfo = constructLocationInfo(address, distance);
            info.add(locationInfo);
        }

        mListAdapter.addAll(info);
    }

    /**
     * Construct a BusinessContextInfo object containing hours of operation information.
     * The format is:
     *      [Open now/Closed now]
     *      [Hours]
     *
     * @param openingHours
     * @return BusinessContextInfo object with the schedule icon, the heading set to whether the
     * business is open or not and the details set to the hours of operation.
     */
    private BusinessContextInfo constructHoursInfo(List<Pair<Calendar, Calendar>> openingHours) {
        return constructHoursInfo(Calendar.getInstance(), openingHours);
    }

    /**
     * Pass in arbitrary current calendar time.
     */
    @VisibleForTesting
    BusinessContextInfo constructHoursInfo(Calendar currentTime,
            List<Pair<Calendar, Calendar>> openingHours) {
        if (currentTime == null || openingHours == null || openingHours.size() == 0) {
            return null;
        }

        BusinessContextInfo hoursInfo = new BusinessContextInfo();
        hoursInfo.iconId = R.drawable.ic_schedule_white_24dp;

        boolean isOpen = false;
        for (Pair<Calendar, Calendar> hours : openingHours) {
            if (hours.first.compareTo(currentTime) <= 0
                    && currentTime.compareTo(hours.second) < 0) {
                // If the current time is on or after the opening time and strictly before the
                // closing time, then this business is open.
                isOpen = true;
            }

            String openTimeSpan = mContext.getString(R.string.open_time_span,
                    DateFormat.getTimeFormat(mContext).format(hours.first.getTime()),
                    DateFormat.getTimeFormat(mContext).format(hours.second.getTime()));

            if (TextUtils.isEmpty(hoursInfo.detail)) {
                hoursInfo.detail = openTimeSpan;
            } else {
                hoursInfo.detail = mContext.getString(R.string.opening_hours, hoursInfo.detail,
                        openTimeSpan);
            }
        }

        hoursInfo.heading = isOpen ? mContext.getString(R.string.open_now)
                : mContext.getString(R.string.closed_now);

        return hoursInfo;
    }

    /**
     * Construct a BusinessContextInfo object with the location information of the business.
     * The format is:
     *      [Straight line distance in miles or kilometers]
     *      [Address without state/country/etc.]
     *
     * @param address An Address object containing address details of the business
     * @param distance The distance to the location in meters
     * @return A BusinessContextInfo object with the location icon, the heading as the distance to
     * the business and the details containing the address.
     */
    private BusinessContextInfo constructLocationInfo(Address address, float distance) {
        return constructLocationInfo(Locale.getDefault(), address, distance);
    }

    @VisibleForTesting
    BusinessContextInfo constructLocationInfo(Locale locale, Address address,
            float distance) {
        if (address == null) {
            return null;
        }

        BusinessContextInfo locationInfo = new BusinessContextInfo();
        locationInfo.iconId = R.drawable.ic_location_on_white_24dp;
        if (distance != DistanceHelper.DISTANCE_NOT_FOUND) {
            //TODO: add a setting to allow the user to select "KM" or "MI" as their distance units.
            if (Locale.US.equals(locale)) {
                locationInfo.heading = mContext.getString(R.string.distance_imperial_away,
                        distance * DistanceHelper.MILES_PER_METER);
            } else {
                locationInfo.heading = mContext.getString(R.string.distance_metric_away,
                        distance * DistanceHelper.KILOMETERS_PER_METER);
            }
        }
        if (address.getLocality() != null) {
            locationInfo.detail = mContext.getString(
                    R.string.display_address,
                    address.getAddressLine(0),
                    address.getLocality());
        } else {
            locationInfo.detail = address.getAddressLine(0);
        }
        return locationInfo;
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