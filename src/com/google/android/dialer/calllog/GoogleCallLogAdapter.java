/*
  * Copyright (C) 2013 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
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

/*
 * This is a reverse-engineered implementation of com.google.android.Dialer.
 * There is no guarantee that this implementation will work correctly or even
 * work at all. Use at your own risk.
 */

package com.google.android.dialer.calllog;

import com.android.dialer.R;

import com.android.dialer.calllog.CallLogAdapter;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.calllog.PhoneNumberUtilsWrapper;
import com.android.dialer.PhoneCallDetails;
import com.android.incallui.Log;
import com.android.incallui.service.PhoneNumberService;
import com.android.incalluibind.ServiceFactory;
import com.google.android.dialer.phonenumbercache.CachedNumberLookupServiceImpl;
import com.google.android.dialer.reverselookup.ReverseLookupSettingUtil;
import com.google.android.dialer.settings.GoogleDialerSettingsActivity;
import com.google.android.dialer.settings.GoogleCallerIdSettingsFragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class GoogleCallLogAdapter extends CallLogAdapter {
    private static final String TAG = GoogleCallLogAdapter.class.getSimpleName();
    private View mBadgeContainer;
    private View mCallerIdContainer;
    private ImageView mCallerIdImageView;
    private TextView mCallerIdText;
    private ImageView mDismissButton;
    private SharedPreferences mPrefs;

    public GoogleCallLogAdapter(Context context, CallLogAdapter.CallFetcher callFetcher,
            ContactInfoHelper contactInfoHelper, boolean useCallAsPrimaryAction,
            boolean isCallLog) {
        super(context, callFetcher, contactInfoHelper, useCallAsPrimaryAction, isCallLog);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    private void lookupNumber(final String phoneNumber, final String countryIso,
            boolean isIncoming) {
        PhoneNumberService phoneNumberService = ServiceFactory.newPhoneNumberService(mContext);
        if (phoneNumberService != null) {
            phoneNumberService.getPhoneNumberInfo(
                    phoneNumber, new PhoneNumberService.NumberLookupListener() {
                @Override
                public void onPhoneNumberInfoComplete(PhoneNumberService.PhoneNumberInfo info) {
                    if (info != null) {
                        enqueueRequest(phoneNumber, countryIso, null, true);
                    }
                }
            }, new PhoneNumberService.ImageLookupListener() {
                @Override
                public void onImageFetchComplete(Bitmap bitmap) {
                    enqueueRequest(phoneNumber, countryIso, null, true);
                }
            }, isIncoming);
        }
    }

    private boolean shouldShowCallerIdBadge(ContactInfo info, PhoneCallDetails details) {
        if (mPrefs.getBoolean("google_caller_id_show_enabled_msg", true)) {
            boolean isEnabled = ReverseLookupSettingUtil.isEnabled(mContext);
            boolean isNormalNumber = PhoneNumberUtilsWrapper.isUnknownNumberThatCanBeLookedUp(
                    details.number, details.numberPresentation);

            Log.d(TAG, "shouldShowCallerIdBadge() - isEnabled " + isEnabled
                    + ", isNormalNumber: " + isNormalNumber
                    + ", info.sourceType: " + info.sourceType
                    + ", info.name: " + info.name);

            return isEnabled && isNormalNumber
                    && (CachedNumberLookupServiceImpl.CachedContactInfoImpl
                    .isPeopleApiSource(info.sourceType)
                    || TextUtils.isEmpty(info.name));
        }

        return false;
    }

    @Override
    protected void bindBadge(View view, ContactInfo info, PhoneCallDetails details, int callType) {
        super.bindBadge(view, info, details, callType);

        if (shouldShowCallerIdBadge(info, details)) {
            Log.d(TAG, "Showing caller id badge.");

            mCallerIdText.setText(mContext.getResources()
                    .getString(R.string.reverse_lookup_enabled));

            mCallerIdImageView.setImageResource(
                    R.drawable.ic_action_settings_blue);

            mCallerIdContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent i = new Intent(mContext, GoogleDialerSettingsActivity.class);
                    i.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                            GoogleCallerIdSettingsFragment.class.getName());
                    i.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_TITLE,
                            R.string.google_caller_id_setting_title);
                    mContext.startActivity(i);
                }
            });

            mBadgeContainer.setVisibility(View.VISIBLE);

            if (mPrefs.getBoolean("google_caller_id_shown_first_time", true)) {
                mPrefs.edit().putBoolean("google_caller_id_shown_first_time", false).commit();

                // TODO: Is this right?
                if (callType != CallLog.Calls.INCOMING_TYPE &&
                        callType != CallLog.Calls.MISSED_TYPE) {
                    lookupNumber(details.number.toString(), details.countryIso, false);
                } else {
                    lookupNumber(details.number.toString(), details.countryIso, true);
                }
            }
        } else {
            Log.d(TAG, "Hiding caller id badge.");
            mBadgeContainer.setVisibility(View.GONE);
        }
    }

    @Override
    protected View newChildView(Context context, ViewGroup parent) {
        ViewGroup viewGroup = (ViewGroup) super.newChildView(context, parent);

        mBadgeContainer = ((LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE)).inflate(
                R.layout.call_log_list_item_extra, null, false);

        mCallerIdContainer = mBadgeContainer.findViewById(
                R.id.badge_link_container);

        mCallerIdImageView = (ImageView) mBadgeContainer.findViewById(
                R.id.badge_image);

        mCallerIdText = (TextView) mBadgeContainer.findViewById(
                R.id.badge_text);

        mDismissButton = (ImageView) mBadgeContainer.findViewById(
                R.id.dismiss_button);
        mDismissButton.setVisibility(View.VISIBLE);

        mDismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBadgeContainer.setVisibility(View.GONE);
                mPrefs.edit().putBoolean("google_caller_id_show_enabled_msg", false).commit();
                notifyDataSetChanged();
            }
        });

        viewGroup.addView(mBadgeContainer);
        return viewGroup;
    }
}
