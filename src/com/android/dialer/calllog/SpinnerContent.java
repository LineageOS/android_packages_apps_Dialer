/*
 * Copyright (C) 2014 The CyanogenMod Project
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
package com.android.dialer.calllog;

import android.content.Context;
import android.provider.CallLog;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Spinner;

import com.android.contacts.common.MoreContactUtils;
import com.android.dialer.R;

import java.util.ArrayList;
import java.util.List;

/**
 * To save the spinner content.
 */
public class SpinnerContent {
    private static String TAG = SpinnerContent.class.getSimpleName();

    public final int value;
    public final String label;

    // The index for call type spinner.
    private static final int INDEX_CALL_TYPE_ALL = 0;
    private static final int INDEX_CALL_TYPE_INCOMING = 1;
    private static final int INDEX_CALL_TYPE_OUTGOING = 2;
    private static final int INDEX_CALL_TYPE_MISSED = 3;
    private static final int INDEX_CALL_TYPE_BLOCKED = 4;
    private static final int INDEX_CALL_TYPE_VOICEMAIL = 5;

    public static void setSpinnerContentValue(Spinner spinner, int value) {
        for (int i = 0, count = spinner.getCount(); i < count; i++) {
            SpinnerContent sc = (SpinnerContent) spinner.getItemAtPosition(i);
            if (sc.value == value) {
                spinner.setSelection(i, true);
                Log.i(TAG, "Set selection for spinner(" + sc + ") with the value: " + value);
                return;
            }
        }
    }

    public SpinnerContent(int value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

    /**
     * @return the spinner contents for the different sims (all, sim0, sim1 etc)
     */
    public static List<SpinnerContent> setupSubFilterContent(Context context) {
        List<PhoneAccountHandle> accountHandles =
                PhoneAccountUtils.getSubscriptionPhoneAccounts(context);
        int count = accountHandles.size();
        // Update the filter sub content.
        ArrayList<SpinnerContent> values = new ArrayList<SpinnerContent>(count + 1);
        values.add(new SpinnerContent(CallLogQueryHandler.CALL_SUB_ALL,
                context.getString(R.string.call_log_show_all_slots)));
        for (int i = 0; i < count; i++) {
            String subDisplayName = PhoneAccountUtils.getAccountLabel(context,
                    accountHandles.get(i));
            if (!TextUtils.isEmpty(subDisplayName)) {
                values.add(new SpinnerContent(i, subDisplayName));
            }
        }
        return values;
    }

    /**
     * @param voicemailAvailable true if voicemail should be included in the return values
     * @return the spinner contents for the different call types (incoming, outgoing etc)
     */
    public static List<SpinnerContent> setupStatusFilterContent(Context context,
            boolean voicemailAvailable) {
        // Didn't show the voice mail item if not available.
        int statusCount = voicemailAvailable ? 6 : 5;
        ArrayList<SpinnerContent> values = new ArrayList<SpinnerContent>(statusCount);
        for (int i = 0; i < statusCount; i++) {
            int value = CallLogQueryHandler.CALL_TYPE_ALL;
            String label = null;
            switch (i) {
                case INDEX_CALL_TYPE_ALL:
                    value = CallLogQueryHandler.CALL_TYPE_ALL;
                    label = context.getString(R.string.call_log_all_calls_header);
                    break;
                case INDEX_CALL_TYPE_INCOMING:
                    value = CallLog.Calls.INCOMING_TYPE;
                    label = context.getString(R.string.call_log_incoming_header);
                    break;
                case INDEX_CALL_TYPE_OUTGOING:
                    value = CallLog.Calls.OUTGOING_TYPE;
                    label = context.getString(R.string.call_log_outgoing_header);
                    break;
                case INDEX_CALL_TYPE_MISSED:
                    value = CallLog.Calls.MISSED_TYPE;
                    label = context.getString(R.string.call_log_missed_header);
                    break;
                case INDEX_CALL_TYPE_BLOCKED:
                    value = CallLog.Calls.BLOCKED_TYPE;
                    label = context.getString(R.string.call_log_blacklist_header);
                    break;
                case INDEX_CALL_TYPE_VOICEMAIL:
                    value = CallLog.Calls.VOICEMAIL_TYPE;
                    label = context.getString(R.string.call_log_voicemail_header);
                    break;
            }
            values.add(new SpinnerContent(value, label));
        }
        return values;
    }
}
