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
import android.provider.CallLog.Calls;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.dialer.PhoneCallDetailsViews;
import com.android.dialer.R;

/**
 * This is an object containing the various views within a call log entry. It contains values
 * pointing to views contained by a call log list item view, so that we can improve performance
 * by reducing the frequency with which we need to find views by IDs.
 *
 * This object also contains methods for inflating action views and binding action behaviors. This
 * is a way of isolating view logic from the CallLogAdapter. We should consider moving that logic
 * if the call log list item is eventually represented as a UI component.
 */
public final class CallLogListItemViews {
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
    public final View callLogEntryView;
    /** The view containing call log item actions.  Null until the ViewStub is inflated. */
    public View actionsView;
    /** The "call back" action button - assigned only when the action section is expanded. */
    public TextView callBackButtonView;
    /** The "video call" action button - assigned only when the action section is expanded. */
    public TextView videoCallButtonView;
    /** The "voicemail" action button - assigned only when the action section is expanded. */
    public TextView voicemailButtonView;
    /** The "details" action button - assigned only when the action section is expanded. */
    public TextView detailsButtonView;
    /** The "report" action button. */
    public TextView reportButtonView;

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

    private Context mContext;

    private int mCallLogBackgroundColor;
    private int mExpandedBackgroundColor;
    private float mExpandedTranslationZ;

    private CallLogListItemViews(
            Context context,
            View rootView,
            QuickContactBadge quickContactView,
            View primaryActionView,
            PhoneCallDetailsViews phoneCallDetailsViews,
            View callLogEntryView,
            TextView dayGroupHeader) {
        mContext = context;

        this.rootView = rootView;
        this.quickContactView = quickContactView;
        this.primaryActionView = primaryActionView;
        this.phoneCallDetailsViews = phoneCallDetailsViews;
        this.callLogEntryView = callLogEntryView;
        this.dayGroupHeader = dayGroupHeader;

        Resources resources = mContext.getResources();
        mCallLogBackgroundColor = resources.getColor(R.color.background_dialer_list_items);
        mExpandedBackgroundColor = resources.getColor(R.color.call_log_expanded_background_color);
        mExpandedTranslationZ = resources.getDimension(R.dimen.call_log_expanded_translation_z);
    }

    /**
     * Configures the action buttons in the expandable actions ViewStub. The ViewStub is not
     * inflated during initial binding, so click handlers, tags and accessibility text must be set
     * here, if necessary.
     *
     * @param callLogItem The call log list item view.
     */
    public void inflateActionViewStub(
            final CallLogAdapter.OnReportButtonClickListener onReportButtonClickListener,
            View.OnClickListener actionListener,
            PhoneNumberUtilsWrapper phoneNumberUtilsWrapper,
            CallLogListItemHelper callLogViewsHelper) {
        ViewStub stub = (ViewStub) rootView.findViewById(R.id.call_log_entry_actions_stub);
        if (stub != null) {
            actionsView = (ViewGroup) stub.inflate();
        }

        if (callBackButtonView == null) {
            callBackButtonView = (TextView) actionsView.findViewById(R.id.call_back_action);
        }

        if (videoCallButtonView == null) {
            videoCallButtonView = (TextView) actionsView.findViewById(R.id.video_call_action);
        }

        if (voicemailButtonView == null) {
            voicemailButtonView = (TextView) actionsView.findViewById(R.id.voicemail_action);
        }

        if (detailsButtonView == null) {
            detailsButtonView = (TextView) actionsView.findViewById(R.id.details_action);
        }

        if (reportButtonView == null) {
            reportButtonView = (TextView) actionsView.findViewById(R.id.report_action);
            reportButtonView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onReportButtonClickListener != null) {
                        onReportButtonClickListener.onReportButtonClick(number);
                    }
                }
            });
        }

        bindActionButtons(actionListener, phoneNumberUtilsWrapper, callLogViewsHelper);
    }

    /**
     * Binds text titles, click handlers and intents to the voicemail, details and callback action
     * buttons.
     */
    private void bindActionButtons(
            View.OnClickListener actionListener,
            PhoneNumberUtilsWrapper phoneNumberUtilsWrapper,
            CallLogListItemHelper callLogViewsHelper) {
        boolean canPlaceCallToNumber =
                PhoneNumberUtilsWrapper.canPlaceCallsTo(number, numberPresentation);

        // Set return call intent, otherwise null.
        if (canPlaceCallToNumber) {
            boolean isVoicemailNumber =
                    phoneNumberUtilsWrapper.isVoicemailNumber(accountHandle, number);
            if (isVoicemailNumber) {
                // Make a general call to voicemail to ensure that if there are multiple accounts
                // it does not call the voicemail number of a specific phone account.
                callBackButtonView.setTag(IntentProvider.getReturnVoicemailCallIntentProvider());
            } else {
                // Sets the primary action to call the number.
                callBackButtonView.setTag(IntentProvider.getReturnCallIntentProvider(number));
            }
            callBackButtonView.setVisibility(View.VISIBLE);
            callBackButtonView.setOnClickListener(actionListener);

            final int titleId;
            if (callType == Calls.VOICEMAIL_TYPE || callType == Calls.OUTGOING_TYPE) {
                titleId = R.string.call_log_action_redial;
            } else {
                titleId = R.string.call_log_action_call_back;
            }
            callBackButtonView.setText(mContext.getString(titleId));
        } else {
            // Number is not callable, so hide button.
            callBackButtonView.setTag(null);
            callBackButtonView.setVisibility(View.GONE);
        }

        // If one of the calls had video capabilities, show the video call button.
        if (CallUtil.isVideoEnabled(mContext) && canPlaceCallToNumber &&
                phoneCallDetailsViews.callTypeIcons.isVideoShown()) {
            videoCallButtonView.setTag(IntentProvider.getReturnVideoCallIntentProvider(number));
            videoCallButtonView.setVisibility(View.VISIBLE);
            videoCallButtonView.setOnClickListener(actionListener);
        } else {
            videoCallButtonView.setTag(null);
            videoCallButtonView.setVisibility(View.GONE);
        }

        // For voicemail calls, show the "VOICEMAIL" action button; hide otherwise.
        if (callType == Calls.VOICEMAIL_TYPE) {
            voicemailButtonView.setOnClickListener(actionListener);
            voicemailButtonView.setTag(
                    IntentProvider.getPlayVoicemailIntentProvider(rowId, voicemailUri));
            voicemailButtonView.setVisibility(View.VISIBLE);

            detailsButtonView.setVisibility(View.GONE);
        } else {
            voicemailButtonView.setTag(null);
            voicemailButtonView.setVisibility(View.GONE);

            detailsButtonView.setOnClickListener(actionListener);
            detailsButtonView.setTag(
                    IntentProvider.getCallDetailIntentProvider(rowId, callIds, null));

            if (canBeReportedAsInvalid && !reported) {
                reportButtonView.setVisibility(View.VISIBLE);
            } else {
                reportButtonView.setVisibility(View.GONE);
            }
        }

        callLogViewsHelper.setActionContentDescriptions(this);
    }

    /**
     * Expands or collapses the view containing the CALLBACK/REDIAL, VOICEMAIL and DETAILS action
     * buttons.
     *
     * TODO: Reduce number of classes which need to be passed in to inflate the action view stub.
     *     1) Instantiate them in this class, and store local references.
     *     2) Set them on the CallLogListItemHelper and use it for inflation.
     *     3) Implement a parent view for a call log list item, and store references in that class.
     */
    public void expandOrCollapseActions(
            boolean isExpanded,
            final CallLogAdapter.OnReportButtonClickListener onReportButtonClickListener,
            View.OnClickListener actionListener,
            PhoneNumberUtilsWrapper phoneNumberUtilsWrapper,
            CallLogListItemHelper callLogViewsHelper) {
        expandVoicemailTranscriptionView(isExpanded);

        if (isExpanded) {
            // Inflate the view stub if necessary, and wire up the event handlers.
            inflateActionViewStub(onReportButtonClickListener, actionListener,
                    phoneNumberUtilsWrapper, callLogViewsHelper);

            actionsView.setVisibility(View.VISIBLE);
            actionsView.setAlpha(1.0f);
            callLogEntryView.setBackgroundColor(mExpandedBackgroundColor);
            callLogEntryView.setTranslationZ(mExpandedTranslationZ);
            rootView.setTranslationZ(mExpandedTranslationZ); // WAR
        } else {
            // When recycling a view, it is possible the actionsView ViewStub was previously
            // inflated so we should hide it in this case.
            if (actionsView != null) {
                actionsView.setVisibility(View.GONE);
            }

            callLogEntryView.setBackgroundColor(mCallLogBackgroundColor);
            callLogEntryView.setTranslationZ(0);
            rootView.setTranslationZ(0); // WAR
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

    public static CallLogListItemViews fromView(Context context, View view) {
        return new CallLogListItemViews(
                context,
                view,
                (QuickContactBadge) view.findViewById(R.id.quick_contact_photo),
                view.findViewById(R.id.primary_action_view),
                PhoneCallDetailsViews.fromView(view),
                view.findViewById(R.id.call_log_row),
                (TextView) view.findViewById(R.id.call_log_day_group_label));
    }

    @NeededForTesting
    public static CallLogListItemViews createForTest(Context context) {
        CallLogListItemViews views = new CallLogListItemViews(
                context,
                new View(context),
                new QuickContactBadge(context),
                new View(context),
                PhoneCallDetailsViews.createForTest(context),
                new View(context),
                new TextView(context));
        views.callBackButtonView = new TextView(context);
        views.voicemailButtonView = new TextView(context);
        views.detailsButtonView = new TextView(context);
        views.reportButtonView = new TextView(context);
        views.actionsView = new View(context);
        return views;
    }
}
