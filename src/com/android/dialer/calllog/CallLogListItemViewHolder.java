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

import android.content.Context;
import android.content.res.Resources;
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.dialer.PhoneCallDetailsHelper;
import com.android.dialer.PhoneCallDetailsViews;
import com.android.dialer.R;

/**
 * This is an object containing references to views contained by the call log list item. This
 * improves performance by reducing the frequency with which we need to find views by IDs.
 *
 * This object also contains UI logic pertaining to the view, to isolate it from the CallLogAdapter.
 */
public final class CallLogListItemViewHolder extends RecyclerView.ViewHolder {

    /** The root view of the call log list item */
    public final View rootView;
    /** The quick contact badge for the contact. */
    public final QuickContactBadge quickContactView;
    /** The primary action view of the entry. */
    public final View primaryActionView;
    /** The details of the phone call. */
    public final PhoneCallDetailsViews phoneCallDetailsViews;
    /** The text of the header for a day grouping. */
    public final TextView dayGroupHeader;
    /** The view containing the details for the call log row, including the action buttons. */
    public final CardView callLogEntryView;
    /** The actionable view which places a call to the number corresponding to the call log row. */
    public final View callActionView;
    /** The view containing call log item actions.  Null until the ViewStub is inflated. */
    public View actionsView;
    /** The "video call" action button - assigned only when the action section is expanded. */
    public View videoCallButtonView;
    /** The "voicemail" action button - assigned only when the action section is expanded. */
    public View voicemailButtonView;
    /** The "details" action button - assigned only when the action section is expanded. */
    public View detailsButtonView;
    /** The "report" action button. */
    public View reportButtonView;

    /**
     * The row Id for the first call associated with the call log entry.  Used as a key for the
     * map used to track which call log entries have the action button section expanded.
     */
    public long rowId;

    /**
     * The call Ids for the calls represented by the current call log entry.  Used when the user
     * deletes a call log entry.
     */
    public long[] callIds;

    /**
     * The callable phone number for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public String number;

    /**
     * The phone number presentation for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public int numberPresentation;

    /**
     * The type of call for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public int callType;

    /**
     * The account for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public PhoneAccountHandle accountHandle;

    /**
     * If the call has an associated voicemail message, the URI of the voicemail message for
     * playback.  Cached here as the voicemail intent is only set when the actions ViewStub is
     * inflated.
     */
    public String voicemailUri;

    /**
     * The name or number associated with the call.  Cached here for use when setting content
     * descriptions on buttons in the actions ViewStub when it is inflated.
     */
    public CharSequence nameOrNumber;

    /**
     * Whether or not the item has been reported by user as incorrect.
     */
    public boolean reported;

    /**
     * Whether or not the contact info can be marked as invalid from the source where
     * it was obtained.
     */
    public boolean canBeReportedAsInvalid;

    private static final int VOICEMAIL_TRANSCRIPTION_MAX_LINES = 10;

    private final Context mContext;
    private final View.OnClickListener mActionListener;
    private final PhoneNumberUtilsWrapper mPhoneNumberUtilsWrapper;
    private final CallLogListItemHelper mCallLogListItemHelper;

    private final int mPhotoSize;

    private CallLogListItemViewHolder(
            Context context,
            View.OnClickListener actionListener,
            PhoneNumberUtilsWrapper phoneNumberUtilsWrapper,
            CallLogListItemHelper callLogListItemHelper,
            View rootView,
            QuickContactBadge quickContactView,
            View primaryActionView,
            PhoneCallDetailsViews phoneCallDetailsViews,
            CardView callLogEntryView,
            TextView dayGroupHeader,
            View callActionView) {
        super(rootView);

        mContext = context;
        mActionListener = actionListener;
        mPhoneNumberUtilsWrapper = phoneNumberUtilsWrapper;
        mCallLogListItemHelper = callLogListItemHelper;

        this.rootView = rootView;
        this.quickContactView = quickContactView;
        this.primaryActionView = primaryActionView;
        this.phoneCallDetailsViews = phoneCallDetailsViews;
        this.callLogEntryView = callLogEntryView;
        this.dayGroupHeader = dayGroupHeader;
        this.callActionView = callActionView;

        Resources resources = mContext.getResources();
        mPhotoSize = mContext.getResources().getDimensionPixelSize(R.dimen.contact_photo_size);

        // Set text height to false on the TextViews so they don't have extra padding.
        phoneCallDetailsViews.nameView.setElegantTextHeight(false);
        phoneCallDetailsViews.callLocationAndDate.setElegantTextHeight(false);

        if (callActionView != null) {
            callActionView.setOnClickListener(mActionListener);
        }
    }

    public static CallLogListItemViewHolder create(
            View view,
            Context context,
            View.OnClickListener actionListener,
            PhoneNumberUtilsWrapper phoneNumberUtilsWrapper,
            CallLogListItemHelper callLogListItemHelper) {

        return new CallLogListItemViewHolder(
                context,
                actionListener,
                phoneNumberUtilsWrapper,
                callLogListItemHelper,
                view,
                (QuickContactBadge) view.findViewById(R.id.quick_contact_photo),
                view.findViewById(R.id.primary_action_view),
                PhoneCallDetailsViews.fromView(view),
                (CardView) view.findViewById(R.id.call_log_row),
                (TextView) view.findViewById(R.id.call_log_day_group_label),
                view.findViewById(R.id.call_icon));
    }

    /**
     * Configures the action buttons in the expandable actions ViewStub. The ViewStub is not
     * inflated during initial binding, so click handlers, tags and accessibility text must be set
     * here, if necessary.
     *
     * @param callLogItem The call log list item view.
     */
    public void inflateActionViewStub(
            final CallLogAdapter.OnReportButtonClickListener onReportButtonClickListener) {
        ViewStub stub = (ViewStub) rootView.findViewById(R.id.call_log_entry_actions_stub);
        if (stub != null) {
            actionsView = (ViewGroup) stub.inflate();
        }

        if (videoCallButtonView == null) {
            videoCallButtonView = actionsView.findViewById(R.id.video_call_action);
        }

        if (voicemailButtonView == null) {
            voicemailButtonView = actionsView.findViewById(R.id.voicemail_action);
        }

        if (detailsButtonView == null) {
            detailsButtonView = actionsView.findViewById(R.id.details_action);
        }

        if (reportButtonView == null) {
            reportButtonView = actionsView.findViewById(R.id.report_action);
            reportButtonView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onReportButtonClickListener != null) {
                        onReportButtonClickListener.onReportButtonClick(number);
                    }
                }
            });
        }

        bindActionButtons();
    }

    public void updateCallButton() {
        boolean canPlaceCallToNumber =
                PhoneNumberUtilsWrapper.canPlaceCallsTo(number, numberPresentation);

        if (canPlaceCallToNumber) {
            boolean isVoicemailNumber =
                    mPhoneNumberUtilsWrapper.isVoicemailNumber(accountHandle, number);
            if (isVoicemailNumber) {
                // Make a general call to voicemail to ensure that if there are multiple accounts
                // it does not call the voicemail number of a specific phone account.
                callActionView.setTag(IntentProvider.getReturnVoicemailCallIntentProvider());
            } else {
                callActionView.setTag(IntentProvider.getReturnCallIntentProvider(number));
            }

            if (nameOrNumber != null) {
                callActionView.setContentDescription(TextUtils.expandTemplate(
                        mContext.getString(R.string.description_call_action),
                        nameOrNumber));
            } else {
                callActionView.setContentDescription(
                        mContext.getString(R.string.description_call_log_call_action));
            }

            callActionView.setVisibility(View.VISIBLE);
        } else {
            callActionView.setTag(null);
            callActionView.setVisibility(View.GONE);
        }
    }

    /**
     * Binds text titles, click handlers and intents to the voicemail, details and callback action
     * buttons.
     */
    private void bindActionButtons() {
        boolean canPlaceCallToNumber =
                PhoneNumberUtilsWrapper.canPlaceCallsTo(number, numberPresentation);

        // If one of the calls had video capabilities, show the video call button.
        if (CallUtil.isVideoEnabled(mContext) && canPlaceCallToNumber &&
                phoneCallDetailsViews.callTypeIcons.isVideoShown()) {
            videoCallButtonView.setTag(IntentProvider.getReturnVideoCallIntentProvider(number));
            videoCallButtonView.setVisibility(View.VISIBLE);
            videoCallButtonView.setOnClickListener(mActionListener);
        } else {
            videoCallButtonView.setTag(null);
            videoCallButtonView.setVisibility(View.GONE);
        }

        // For voicemail calls, show the "VOICEMAIL" action button; hide otherwise.
        if (callType == Calls.VOICEMAIL_TYPE) {
            voicemailButtonView.setOnClickListener(mActionListener);
            voicemailButtonView.setTag(
                    IntentProvider.getPlayVoicemailIntentProvider(rowId, voicemailUri));
            voicemailButtonView.setVisibility(View.VISIBLE);

            detailsButtonView.setVisibility(View.GONE);
        } else {
            voicemailButtonView.setTag(null);
            voicemailButtonView.setVisibility(View.GONE);

            detailsButtonView.setOnClickListener(mActionListener);
            detailsButtonView.setTag(
                    IntentProvider.getCallDetailIntentProvider(rowId, callIds, null));

            if (canBeReportedAsInvalid && !reported) {
                reportButtonView.setVisibility(View.VISIBLE);
            } else {
                reportButtonView.setVisibility(View.GONE);
            }
        }

        mCallLogListItemHelper.setActionContentDescriptions(this);
    }

    /**
     * Show or hide the action views, such as voicemail, details, and add contact.
     *
     * If the action views have never been shown yet for this view, inflate the view stub.
     */
    public void showActions(boolean show,
            final CallLogAdapter.OnReportButtonClickListener onReportButtonClickListener) {
        expandVoicemailTranscriptionView(show);

        if (show) {
            // Inflate the view stub if necessary, and wire up the event handlers.
            inflateActionViewStub(onReportButtonClickListener);

            actionsView.setVisibility(View.VISIBLE);
            actionsView.setAlpha(1.0f);
        } else {
            // When recycling a view, it is possible the actionsView ViewStub was previously
            // inflated so we should hide it in this case.
            if (actionsView != null) {
                actionsView.setVisibility(View.GONE);
            }
        }
    }

    public void expandVoicemailTranscriptionView(boolean isExpanded) {
        if (callType != Calls.VOICEMAIL_TYPE) {
            return;
        }

        final TextView view = phoneCallDetailsViews.voicemailTranscriptionView;
        if (TextUtils.isEmpty(view.getText())) {
            return;
        }
        view.setMaxLines(isExpanded ? VOICEMAIL_TRANSCRIPTION_MAX_LINES : 1);
        view.setSingleLine(!isExpanded);
    }

    public void setPhoto(long photoId, Uri photoUri, Uri contactUri, String displayName,
            boolean isVoicemail, boolean isBusiness) {
        quickContactView.assignContactUri(contactUri);
        quickContactView.setOverlay(null);

        int contactType = ContactPhotoManager.TYPE_DEFAULT;
        if (isVoicemail) {
            contactType = ContactPhotoManager.TYPE_VOICEMAIL;
        } else if (isBusiness) {
            contactType = ContactPhotoManager.TYPE_BUSINESS;
        }

        String lookupKey = null;
        if (contactUri != null) {
            lookupKey = ContactInfoHelper.getLookupKeyFromUri(contactUri);
        }

        DefaultImageRequest request = new DefaultImageRequest(
                displayName, lookupKey, contactType, true /* isCircular */);

        if (photoId == 0 && photoUri != null) {
            ContactPhotoManager.getInstance(mContext).loadPhoto(quickContactView, photoUri,
                    mPhotoSize, false /* darkTheme */, true /* isCircular */, request);
        } else {
            ContactPhotoManager.getInstance(mContext).loadThumbnail(quickContactView, photoId,
                    false /* darkTheme */, true /* isCircular */, request);
        }
    }

    @NeededForTesting
    public static CallLogListItemViewHolder createForTest(Context context) {
        Resources resources = context.getResources();
        PhoneNumberDisplayHelper phoneNumberHelper =
                new PhoneNumberDisplayHelper(context, resources);
        PhoneNumberUtilsWrapper phoneNumberUtilsWrapper = new PhoneNumberUtilsWrapper(context);
        PhoneCallDetailsHelper phoneCallDetailsHelper = new PhoneCallDetailsHelper(
                context, resources, phoneNumberUtilsWrapper);

        CallLogListItemViewHolder viewHolder = new CallLogListItemViewHolder(
                context,
                null /* actionListener */,
                phoneNumberUtilsWrapper,
                new CallLogListItemHelper(
                        phoneCallDetailsHelper, phoneNumberHelper, resources),
                new View(context),
                new QuickContactBadge(context),
                new View(context),
                PhoneCallDetailsViews.createForTest(context),
                new CardView(context),
                new TextView(context),
                new View(context));
        viewHolder.voicemailButtonView = new TextView(context);
        viewHolder.detailsButtonView = new TextView(context);
        viewHolder.reportButtonView = new TextView(context);
        viewHolder.actionsView = new View(context);

        return viewHolder;
    }
}
