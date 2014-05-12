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
import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.common.test.NeededForTesting;
import com.android.dialer.PhoneCallDetailsViews;
import com.android.dialer.R;

/**
 * Simple value object containing the various views within a call log entry.
 */
public final class CallLogListItemViews {
    /** The quick contact badge for the contact. */
    public final QuickContactBadge quickContactView;
    /** The primary action view of the entry. */
    public final View primaryActionView;
    /** The details of the phone call. */
    public final PhoneCallDetailsViews phoneCallDetailsViews;
    /** The text of the header of a section. */
    public final TextView listHeaderTextView;
    /** The view containing call log item actions.  Null until the ViewStub is inflated. */
    public View actionsView;
    /** The "call back" action button - assigned only when the action section is expanded. */
    public TextView callBackButtonView;
    /** The "delete" action button - assigned only when the action section is expanded. */
    public TextView deleteButtonView;
    /** The "voicemail" action button - assigned only when the action section is expanded. */
    public TextView voicemailButtonView;

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

    private CallLogListItemViews(QuickContactBadge quickContactView, View primaryActionView,
            PhoneCallDetailsViews phoneCallDetailsViews,
            TextView listHeaderTextView) {
        this.quickContactView = quickContactView;
        this.primaryActionView = primaryActionView;
        this.phoneCallDetailsViews = phoneCallDetailsViews;
        this.listHeaderTextView = listHeaderTextView;
    }

    public static CallLogListItemViews fromView(View view) {
        return new CallLogListItemViews(
                (QuickContactBadge) view.findViewById(R.id.quick_contact_photo),
                view.findViewById(R.id.primary_action_view),
                PhoneCallDetailsViews.fromView(view),
                (TextView) view.findViewById(R.id.call_log_header));
    }

    @NeededForTesting
    public static CallLogListItemViews createForTest(Context context) {
        CallLogListItemViews views = new CallLogListItemViews(
                new QuickContactBadge(context),
                new View(context),
                PhoneCallDetailsViews.createForTest(context),
                new TextView(context));
        views.callBackButtonView = new TextView(context);
        views.deleteButtonView = new TextView(context);
        views.voicemailButtonView = new TextView(context);
        views.actionsView = new View(context);
        return views;
    }
}
