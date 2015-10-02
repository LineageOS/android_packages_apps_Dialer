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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

import java.util.List;

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

    public InCallContactInteractions(Context context, boolean isBusiness) {
        mContext = context;
        setIsBusiness(isBusiness);
    }

    public InCallContactInteractionsListAdapter getListAdapter() {
        return mListAdapter;
    }

    /**
     * Resets the "isBusiness" value, also recreates the list adapter with the resource
     * corresponding to the new isBusiness value.
     * @param isBusiness Whether or not the contact is a business.
     */
    public void setIsBusiness(boolean isBusiness) {
        if (mIsBusiness != isBusiness || mListAdapter == null) {
            mIsBusiness = isBusiness;
            mListAdapter = new InCallContactInteractionsListAdapter(mContext,
                    mIsBusiness ? R.layout.business_context_info_list_item
                            : R.layout.person_context_info_list_item);
        }
    }

    /**
     * Set the data for the list adapter.
     * @param data The data to add to the list adapter. This completely replaces any previous data.
     */
    public void setData(List<ContactContextInfo> data) {
        mListAdapter.clear();
        mListAdapter.addAll(data);
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
        Context mContext;

        public ContactContextInfo(Context context) {
            mContext = context;
        }

        public abstract void setListView(View listItem);
    }

    public static class BusinessContextInfo extends ContactContextInfo {
        public BusinessContextInfo(Context context) {
            super(context);
        }

        int iconId;
        String heading;
        String detail;

        @Override
        public void setListView(View listItem) {
            ImageView imageView = (ImageView) listItem.findViewById(R.id.icon);
            TextView headingTextView = (TextView) listItem.findViewById(R.id.heading);
            TextView detailTextView = (TextView) listItem.findViewById(R.id.detail);

            if (this.iconId == 0 || this.heading == null || this.detail == null) {
                return;
            }

            imageView.setImageDrawable(mContext.getDrawable(this.iconId));
            headingTextView.setText(this.heading);
            detailTextView.setText(this.detail);
        }
    }

    public static class PersonContextInfo extends ContactContextInfo {
        boolean isIncoming;
        String message;
        String detail;

        public PersonContextInfo(Context context) {
            super(context);
        }

        @Override
        public void setListView(View listItem) {
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
            LayoutInflater inflater = (LayoutInflater)
                    getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View listItem;
            listItem = inflater.inflate(mResId, null);

            ContactContextInfo item = getItem(position);

            if (item == null) {
                return listItem;
            }

            item.setListView(listItem);

            return listItem;
        }
    }
}