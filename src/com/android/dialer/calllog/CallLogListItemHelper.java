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

package com.android.dialer.calllog;

import android.content.res.Resources;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.text.TextUtils;
import android.view.View;

import com.android.dialer.PhoneCallDetails;
import com.android.dialer.PhoneCallDetailsHelper;
import com.android.dialer.R;

/**
 * Helper class to fill in the views of a call log entry.
 */
/* package */class CallLogListItemHelper {
    /** Helper for populating the details of a phone call. */
    private final PhoneCallDetailsHelper mPhoneCallDetailsHelper;
    /** Helper for handling phone numbers. */
    private final PhoneNumberDisplayHelper mPhoneNumberHelper;
    /** Resources to look up strings. */
    private final Resources mResources;

    /**
     * Creates a new helper instance.
     *
     * @param phoneCallDetailsHelper used to set the details of a phone call
     * @param phoneNumberHelper used to process phone number
     */
    public CallLogListItemHelper(PhoneCallDetailsHelper phoneCallDetailsHelper,
            PhoneNumberDisplayHelper phoneNumberHelper, Resources resources) {
        mPhoneCallDetailsHelper = phoneCallDetailsHelper;
        mPhoneNumberHelper = phoneNumberHelper;
        mResources = resources;
    }

    /**
     * Sets the name, label, and number for a contact.
     *
     * @param views the views to populate
     * @param details the details of a phone call needed to fill in the data
     * @param isHighlighted whether to use the highlight text for the call
     * @param showSecondaryActionButton whether to show the secondary action button or not
     */
    public void setPhoneCallDetails(CallLogListItemViews views, PhoneCallDetails details,
            boolean isHighlighted, boolean showSecondaryActionButton) {
        mPhoneCallDetailsHelper.setPhoneCallDetails(views.phoneCallDetailsViews, details,
                isHighlighted);
        boolean canPlay = details.callTypes[0] == Calls.VOICEMAIL_TYPE;

        // Set the accessibility text for the contact badge
        views.quickContactView.setContentDescription(getContactBadgeDescription(details));

        // Set the primary action accessibility description
        views.primaryActionView.setContentDescription(getCallDescription(details));

        // If secondary action is visible, either show voicemail playback icon, or
        // show the "clock" icon corresponding to the call details screen.
        if (showSecondaryActionButton) {
            if (canPlay) {
                // Playback action takes preference.
                configurePlaySecondaryAction(views, isHighlighted);
            } else {
                // Call details is the secondary action.
                configureCallDetailsSecondaryAction(views, details);
            }
        } else {
            // No secondary action is to be shown (ie this is likely a PhoneFavoriteFragment)
            views.secondaryActionView.setVisibility(View.GONE);
        }
    }

    /**
     * Sets the secondary action to invoke call details.
     *
     * @param views   the views to populate
     * @param details the details of a phone call needed to fill in the call details data
     */
    private void configureCallDetailsSecondaryAction(CallLogListItemViews views,
            PhoneCallDetails details) {
        views.secondaryActionView.setVisibility(View.VISIBLE);
        // Use the small dark grey clock icon.
        views.secondaryActionButtonView.setImageResource(R.drawable.ic_menu_history_dk);
        views.secondaryActionButtonView.setContentDescription(
                mResources.getString(R.string.description_call_details));
    }

    /**
     * Returns the accessibility description for the contact badge for a call log entry.
     *
     * @param details Details of call.
     * @return Accessibility description.
     */
    private CharSequence getContactBadgeDescription(PhoneCallDetails details) {
        return mResources.getString(R.string.description_contact_details, getNameOrNumber(details));
    }

    /**
     * Returns the accessibility description of the "return call/call" action for a call log
     * entry.
     * Accessibility text is a combination of:
     * {Voicemail Prefix}. {Number of Calls}. {Caller information}.
     * If most recent call is a voicemail, {Voicemail Prefix} is "New Voicemail.", otherwise "".
     *
     * If more than one call for the caller, {Number of Calls} is:
     * "{number of calls} calls.", otherwise "".
     *
     * The {Caller Information} references the most recent call associated with the caller.
     * For incoming calls:
     * If missed call:  Return missed call from {Name/Number} {Call Type} {Call Time}.
     * If answered call: Return answered call from {Name/Number} {Call Type} {Call Time}.
     *
     * For unknown callers, drop the "Return" part, since the call can't be returned:
     * If answered unknown: Answered call from {Name/Number} {Call Time}.
     * If missed unknown: Missed call from {Name/Number} {Call Time}.
     *
     * For outgoing calls:
     * If outgoing:  Call {Name/Number] {Call Type}.  {Last} called {Call Time}.
     * Where {Last} is dropped if the number of calls for the caller is 1.
     *
     * Where:
     * {Name/Number} is the name or number of the caller (as shown in call log).
     * {Call type} is the contact phone number type (eg mobile) or location.
     * {Call Time} is the time since the last call for the contact occurred.
     *
     * Examples:
     * 3 calls.  New Voicemail.  Return missed call from Joe Smith mobile 2 hours ago.
     * 2 calls.  Call John Doe mobile.  Last called 1 hour ago.
     * @param details Details of call.
     * @return Return call action description.
     */
    public CharSequence getCallDescription(PhoneCallDetails details) {
        int lastCallType = getLastCallType(details.callTypes);
        boolean isVoiceMail = lastCallType == Calls.VOICEMAIL_TYPE;

        // Get the name or number of the caller.
        final CharSequence nameOrNumber = getNameOrNumber(details);

        // Get the call type or location of the caller; null if not applicable
        final CharSequence typeOrLocation = mPhoneCallDetailsHelper.getCallTypeOrLocation(details);

        // Get the time/date of the call
        final CharSequence timeOfCall = mPhoneCallDetailsHelper.getCallDate(details);

        StringBuilder callDescription = new StringBuilder();

        // Prepend the voicemail indication.
        if (isVoiceMail) {
            callDescription.append(mResources.getString(R.string.description_new_voicemail));
        }

        // Add number of calls if more than one.
        if (details.callTypes.length > 1) {
            callDescription.append(mResources.getString(R.string.description_num_calls,
                    details.callTypes.length));
        }

        int stringID = getCallDescriptionStringID(details);

        // Use chosen string resource to build up the message.
        callDescription.append(mResources.getString(stringID,
                nameOrNumber,
                // If no type or location can be determined, sub in empty string.
                typeOrLocation == null ? "" : typeOrLocation,
                timeOfCall));

        return callDescription;
    }

    /**
     * Determine the appropriate string ID to describe a call for accessibility purposes.
     *
     * @param details Call details.
     * @return String resource ID to use.
     */
    public int getCallDescriptionStringID(PhoneCallDetails details) {
        int lastCallType = getLastCallType(details.callTypes);
        boolean isNumberCallable = PhoneNumberUtilsWrapper.canPlaceCallsTo(details.number,
                details.numberPresentation);

        // Default string to use is "call XYZ..." just in case we manage to fall through.
        int stringID = R.string.description_call_last_multiple;

        if (!isNumberCallable) {
            // Number isn't callable; this is an incoming call from an unknown caller.
            // An uncallable outgoing call wouldn't be in the call log.

            // Voicemail and missed calls are both considered missed.
            if (lastCallType == Calls.VOICEMAIL_TYPE ||
                    lastCallType == Calls.MISSED_TYPE) {
                stringID = R.string.description_unknown_missed_call;
            } else if (lastCallType == Calls.INCOMING_TYPE) {
                stringID = R.string.description_unknown_answered_call;
            }
        } else {
            // Known caller, so callable.

            // Missed call (ie voicemail or missed)
            if (lastCallType == Calls.VOICEMAIL_TYPE ||
                    lastCallType == Calls.MISSED_TYPE) {
                stringID = R.string.description_return_missed_call;
            } else if (lastCallType == Calls.INCOMING_TYPE) {
                // Incoming answered.
                stringID = R.string.description_return_answered_call;
            } else {
                // Outgoing call.

                // If we have a history of multiple calls
                if (details.callTypes.length > 1) {
                    stringID = R.string.description_call_last_multiple;
                } else {
                    stringID = R.string.description_call_last;
                }
            }
        }
        return stringID;
    }

    /**
     * Determine the call type for the most recent call.
     * @param callTypes Call types to check.
     * @return Call type.
     */
    private int getLastCallType(int[] callTypes) {
        if (callTypes.length > 0) {
            return callTypes[0];
        } else {
            return Calls.MISSED_TYPE;
        }
    }

    /**
     * Return the name or number of the caller specified by the details.
     * @param details Call details
     * @return the name (if known) of the caller, otherwise the formatted number.
     */
    private CharSequence getNameOrNumber(PhoneCallDetails details) {
        final CharSequence recipient;
        if (!TextUtils.isEmpty(details.name)) {
            recipient = details.name;
        } else {
            recipient = mPhoneNumberHelper.getDisplayNumber(
                    details.number, details.numberPresentation, details.formattedNumber);
        }
        return recipient;
    }

    /** Sets the secondary action to correspond to the play button. */
    private void configurePlaySecondaryAction(CallLogListItemViews views, boolean isHighlighted) {
        views.secondaryActionView.setVisibility(View.VISIBLE);
        views.secondaryActionButtonView.setImageResource(
                isHighlighted ? R.drawable.ic_play_active_holo_dark : R.drawable.ic_play_holo_light);
        views.secondaryActionButtonView.setContentDescription(
                mResources.getString(R.string.description_call_log_play_button));
    }
}
