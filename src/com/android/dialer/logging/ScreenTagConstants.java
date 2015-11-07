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
 * limitations under the License.
 */
package com.android.dialer.logging;

import com.google.android.dialer.settings.GoogleDialerSettingsActivity;

import com.android.contacts.common.dialog.ClearFrequentsDialog;
import com.android.contacts.common.interactions.ImportExportDialogFragment;
import com.android.dialer.calllog.CallLogFragment;
import com.android.dialer.dialpad.DialpadFragment;
import com.android.dialer.list.AllContactsFragment;
import com.android.dialer.list.RegularSearchFragment;
import com.android.dialer.list.SmartDialSearchFragment;
import com.android.dialer.list.SpeedDialFragment;
import com.android.incallui.AnswerFragment;
import com.android.incallui.CallCardFragment;
import com.android.incallui.ConferenceManagerFragment;

/**
 * Central repository of all string constants used to identify screens/fragments/dialogs for
 * logging purposes.
 */
public class ScreenTagConstants {
    /**
     * Unique identifiers for each screen that is displayed in the Dialer
     */
    public static final String DIALPAD = DialpadFragment.class.getSimpleName();
    public static final String SPEED_DIAL = SpeedDialFragment.class.getSimpleName();
    public static final String CALL_LOG = CallLogFragment.class.getSimpleName();
    public static final String ALL_CONTACTS = AllContactsFragment.class.getSimpleName();
    public static final String REGULAR_SEARCH = RegularSearchFragment.class.getSimpleName();
    public static final String SMART_DIAL_SEARCH = SmartDialSearchFragment.class.getSimpleName();
    public static final String SETTINGS = GoogleDialerSettingsActivity.class.getSimpleName();
    public static final String IMPORT_EXPORT_CONTACTS =
            ImportExportDialogFragment.class.getSimpleName();
    public static final String CLEAR_FREQUENTS = ClearFrequentsDialog.class.getSimpleName();
    public static final String SEND_FEEDBACK = "SendFeedback";
    public static final String INCALL = CallCardFragment.class.getSimpleName();
    public static final String INCOMING_CALL = AnswerFragment.class.getSimpleName();
    public static final String CONFERENCE_MANAGEMENT =
            ConferenceManagerFragment.class.getSimpleName();

    /**
     * Additional constants that allow disambiguation between similar fragments in different
     * activities.
     */
    // The dialpad in DialtactsActivity
    public static final String DIALPAD_DIALER = "Dialer";
    // The dialpad in InCallActivity
    public static final String DIALPAD_INCALL = "InCall";

    // The HISTORY tab in DialtactsActivity
    public static final String CALL_LOG_HISTORY = "History";
    // The VOICEMAIL tab in DialtactsActivity
    public static final String CALL_LOG_VOICEMAIL = "Voicemail";
    // The ALL tab in CallLogActivity
    public static final String CALL_LOG_ALL = "All";
    // The MISSED tab in CallLogActivity
    public static final String CALL_LOG_MISSED = "Missed";
}
