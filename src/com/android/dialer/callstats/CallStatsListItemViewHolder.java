/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.content.res.Resources;
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.widget.LinearColorBar;

/**
 * This is an object containing references to views contained by the call log list item. This
 * improves performance by reducing the frequency with which we need to find views by IDs.
 *
 * This object also contains UI logic pertaining to the view, to isolate it from the CallLogAdapter.
 */
public final class CallStatsListItemViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener {

    public CallStatsDetails details;
    public Intent clickIntent;

    public final View mRootView;
    public final QuickContactBadge mQuickContactView;
    public final View mPrimaryActionView;
    public final TextView mNameView;
    public final TextView mNumberView;
    public final TextView mLabelView;
    public final TextView mPercentView;
    public final LinearColorBar mBarView;

    private Context mContext;
    private ContactInfoHelper mContactInfoHelper;
    private final int mPhotoSize;

    private CallStatsListItemViewHolder(View rootView,
            QuickContactBadge quickContactView,
            View primaryActionView,
            TextView nameView,
            TextView numberView,
            TextView labelView,
            TextView percentView,
            LinearColorBar barView,
            ContactInfoHelper contactInfoHelper) {
        super(rootView);

        mRootView = rootView;
        mQuickContactView = quickContactView;
        mPrimaryActionView = primaryActionView;
        mNameView = nameView;
        mNumberView = numberView;
        mLabelView = labelView;
        mPercentView = percentView;
        mBarView = barView;

        mPrimaryActionView.setOnClickListener(this);

        quickContactView.setPrioritizedMimeType(Phone.CONTENT_ITEM_TYPE);

        mContext = rootView.getContext();
        mPhotoSize = mContext.getResources().getDimensionPixelSize(R.dimen.contact_photo_size);
        mContactInfoHelper = contactInfoHelper;
    }

    public static CallStatsListItemViewHolder create(View view,
            ContactInfoHelper contactInfoHelper) {
        return new CallStatsListItemViewHolder(view,
                (QuickContactBadge) view.findViewById(R.id.quick_contact_photo),
                view.findViewById(R.id.primary_action_view),
                (TextView) view.findViewById(R.id.name),
                (TextView) view.findViewById(R.id.number),
                (TextView) view.findViewById(R.id.label),
                (TextView) view.findViewById(R.id.percent),
                (LinearColorBar) view.findViewById(R.id.percent_bar),
                contactInfoHelper);
    }

    @Override
    public void onClick(View v) {
        if (clickIntent != null) {
            DialerUtils.startActivityWithErrorToast(mContext, clickIntent);
        }
    }

    public void setDetails(CallStatsDetails details, CallStatsDetails first,
            CallStatsDetails total, int type, boolean byDuration) {
        this.details = details;
        details.updateDisplayPropertiesIfNeeded(mContext);

        CharSequence numberFormattedLabel = null;
        // Only show a label if the number is shown and it is not a SIP address.
        if (!TextUtils.isEmpty(details.number)
                && !PhoneNumberHelper.isUriNumber(details.number.toString())) {
            numberFormattedLabel = Phone.getTypeLabel(mContext.getResources(),
                    details.numberType, details.numberLabel);
        }

        final CharSequence nameText;
        final CharSequence numberText;
        final CharSequence labelText;

        if (TextUtils.isEmpty(details.name)) {
            nameText = details.displayNumber;
            if (TextUtils.isEmpty(details.geocode) || details.isVoicemailNumber) {
                numberText = null;
            } else {
                numberText = details.geocode;
            }
            labelText = null;
        } else {
            nameText = details.name;
            numberText = details.displayNumber;
            labelText = numberFormattedLabel;
        }

        float in = 0, out = 0, missed = 0, blacklist = 0;
        float ratio = getDetailValue(details, type, byDuration) /
                      getDetailValue(first, type, byDuration);

        if (type == CallLogQueryHandler.CALL_TYPE_ALL) {
            float full = getDetailValue(details, type, byDuration);
            in = getDetailValue(details, Calls.INCOMING_TYPE, byDuration) * ratio / full;
            out = getDetailValue(details, Calls.OUTGOING_TYPE, byDuration) * ratio / full;
            if (!byDuration) {
                missed = getDetailValue(details, Calls.MISSED_TYPE, byDuration) * ratio / full;
                blacklist = getDetailValue(details, Calls.BLACKLIST_TYPE, byDuration) * ratio / full;
            }
        } else if (type == Calls.INCOMING_TYPE) {
            in = ratio;
        } else if (type == Calls.OUTGOING_TYPE) {
            out = ratio;
        } else if (type == Calls.MISSED_TYPE) {
            missed = ratio;
        } else if (type == Calls.BLACKLIST_TYPE) {
            blacklist = ratio;
        }

        mBarView.setRatios(in, out, missed, blacklist);
        mNameView.setText(nameText);
        mNumberView.setText(numberText);
        mLabelView.setText(labelText);
        mLabelView.setVisibility(TextUtils.isEmpty(labelText) ? View.GONE : View.VISIBLE);

        if (byDuration && type == Calls.MISSED_TYPE) {
            mPercentView.setText(getCallCountString(mContext, details.missedCount));
        } else if (byDuration && type == Calls.BLACKLIST_TYPE) {
            mPercentView.setText(getCallCountString(mContext, details.blacklistCount));
        } else {
            float percent = getDetailValue(details, type, byDuration) * 100F /
                            getDetailValue(total, type, byDuration);
            mPercentView.setText(String.format("%.1f%%", percent));
        }

        final String nameForDefaultImage = TextUtils.isEmpty(details.name)
                ? details.displayNumber : details.name;
        setPhoto(details.photoId, details.photoUri, details.contactUri, nameForDefaultImage,
                details.isVoicemailNumber, mContactInfoHelper.isBusiness(details.sourceType));
    }

    private float getDetailValue(CallStatsDetails details, int type, boolean byDuration) {
        if (byDuration) {
            return (float) details.getRequestedDuration(type);
        } else {
            return (float) details.getRequestedCount(type);
        }
    }

    public static String getCallCountString(Context context, long count) {
        return context.getResources().getQuantityString(R.plurals.call, (int) count, (int) count);
    }

    public static String getDurationString(Context context, long duration, boolean includeSeconds) {
        int hours, minutes, seconds;

        hours = (int) (duration / 3600);
        duration -= (long) hours * 3600;
        minutes = (int) (duration / 60);
        duration -= (long) minutes * 60;
        seconds = (int) duration;

        if (!includeSeconds) {
            if (seconds >= 30) {
                minutes++;
            }
            if (minutes >= 60) {
                hours++;
            }
        }

        boolean dispHours = hours > 0;
        boolean dispMinutes = minutes > 0 || (!includeSeconds && hours == 0);
        boolean dispSeconds = includeSeconds && (seconds > 0 || (hours == 0 && minutes == 0));

        final Resources res = context.getResources();
        final String hourString = dispHours ?
            res.getQuantityString(R.plurals.hour, hours, hours) : null;
        final String minuteString = dispMinutes ?
            res.getQuantityString(R.plurals.minute, minutes, minutes) : null;
        final String secondString = dispSeconds ?
            res.getQuantityString(R.plurals.second, seconds, seconds) : null;

        int index = ((dispHours ? 4 : 0) | (dispMinutes ? 2 : 0) | (dispSeconds ? 1 : 0)) - 1;
        String[] formats = res.getStringArray(R.array.call_stats_duration);
        return String.format(formats[index], hourString, minuteString, secondString);
    }

    private void setPhoto(long photoId, Uri photoUri, Uri contactUri, String displayName,
            boolean isVoicemail, boolean isBusiness) {
        mQuickContactView.assignContactUri(contactUri);
        mQuickContactView.setOverlay(null);

        int contactType = ContactPhotoManager.TYPE_DEFAULT;
        if (isVoicemail) {
            contactType = ContactPhotoManager.TYPE_VOICEMAIL;
        } else if (isBusiness) {
            contactType = ContactPhotoManager.TYPE_BUSINESS;
        }

        String lookupKey = null;
        if (contactUri != null) {
            lookupKey = UriUtils.getLookupKeyFromUri(contactUri);
        }

        ContactPhotoManager.DefaultImageRequest request =
                new ContactPhotoManager.DefaultImageRequest(displayName, lookupKey,
                        contactType, true /* isCircular */);

        if (photoId == 0 && photoUri != null) {
            ContactPhotoManager.getInstance(mContext).loadPhoto(mQuickContactView, photoUri,
                    mPhotoSize, false /* darkTheme */, true /* isCircular */, request);
        } else {
            ContactPhotoManager.getInstance(mContext).loadThumbnail(mQuickContactView, photoId,
                    false /* darkTheme */, true /* isCircular */, request);
        }
    }
}
