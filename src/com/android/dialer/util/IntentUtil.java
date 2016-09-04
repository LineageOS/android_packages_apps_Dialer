/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.dialer.util;

import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.content.Context;
import com.android.dialer.R;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;

import com.android.contacts.common.CallUtil;
import java.util.List;

/**
 * Utilities for creation of intents in Dialer, such as {@link Intent#ACTION_CALL}.
 */
public class IntentUtil {

    public static final String CALL_ACTION = Intent.ACTION_CALL;
    private static final String SMS_URI_PREFIX = "sms:";
    private static final int NO_PHONE_TYPE = -1;

    public static final String EXTRA_CALL_INITIATION_TYPE
            = "com.android.dialer.EXTRA_CALL_INITIATION_TYPE";

    public static class CallIntentBuilder {
        private Uri mUri;
        private int mCallInitiationType;
        private PhoneAccountHandle mPhoneAccountHandle;
        private boolean mIsVideoCall = false;

        public CallIntentBuilder(Uri uri) {
            mUri = uri;
        }

        public CallIntentBuilder(String number) {
            this(CallUtil.getCallUri(number));
        }

        public CallIntentBuilder setCallInitiationType(int initiationType) {
            mCallInitiationType = initiationType;
            return this;
        }

        public CallIntentBuilder setPhoneAccountHandle(PhoneAccountHandle accountHandle) {
            mPhoneAccountHandle = accountHandle;
            return this;
        }

        public CallIntentBuilder setIsVideoCall(boolean isVideoCall) {
            mIsVideoCall = isVideoCall;
            return this;
        }

        public Intent build() {
            return getCallIntent(
                    mUri,
                    mPhoneAccountHandle,
                    mIsVideoCall ? VideoProfile.STATE_BIDIRECTIONAL : VideoProfile.STATE_AUDIO_ONLY,
                    mCallInitiationType);
        }
    }

    /**
     * Create a call intent that can be used to place a call.
     *
     * @param uri Address to place the call to.
     * @param accountHandle {@link PhoneAccountHandle} to place the call with.
     * @param videoState Initial video state of the call.
     * @param callIntiationType The UI affordance the call was initiated by.
     * @return Call intent with provided extras and data.
     */
    public static Intent getCallIntent(
            Uri uri, PhoneAccountHandle accountHandle, int videoState, int callIntiationType) {
        final Intent intent = new Intent(CALL_ACTION, uri);
        intent.putExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, videoState);

        final Bundle b = new Bundle();
        b.putInt(EXTRA_CALL_INITIATION_TYPE, callIntiationType);
        intent.putExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, b);

        if (accountHandle != null) {
            intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle);
        }

        return intent;
    }

    public static Intent getSendSmsIntent(CharSequence phoneNumber) {
        return new Intent(Intent.ACTION_SENDTO, Uri.parse(SMS_URI_PREFIX + phoneNumber));
    }

    public static Intent getNewContactIntent() {
        return new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
    }

    public static Intent getNewContactIntent(CharSequence phoneNumber) {
        return getNewContactIntent(
                null /* name */,
                phoneNumber /* phoneNumber */,
                NO_PHONE_TYPE);
    }

    public static Intent getNewContactIntent(
            CharSequence name, CharSequence phoneNumber, int phoneNumberType) {
        Intent intent = getNewContactIntent();
        populateContactIntent(intent, name, phoneNumber, phoneNumberType);
        return intent;
    }

    public static Intent getAddToExistingContactIntent() {
        Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        return intent;
    }

    public static Intent getAddToExistingContactIntent(CharSequence phoneNumber) {
        return getAddToExistingContactIntent(
                null /* name */,
                phoneNumber /* phoneNumber */,
                NO_PHONE_TYPE);
    }

    public static Intent getAddToExistingContactIntent(
            CharSequence name, CharSequence phoneNumber, int phoneNumberType) {
        Intent intent = getAddToExistingContactIntent();
        populateContactIntent(intent, name, phoneNumber, phoneNumberType);
        return intent;
    }

    private static void populateContactIntent(
            Intent intent, CharSequence name, CharSequence phoneNumber, int phoneNumberType) {
        if (phoneNumber != null) {
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber);
        }
        if (name != null) {
            intent.putExtra(ContactsContract.Intents.Insert.NAME, name);
        }
        if (phoneNumberType != NO_PHONE_TYPE) {
            intent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE, phoneNumberType);
        }
    }
    /**
     * if true, conference dialer  is enabled.
     */
    public static boolean isConferDialerEnabled(Context context) {
        boolean isEnabled = false;
        List<SubscriptionInfo> subInfos = SubscriptionManager.from(context)
                .getActiveSubscriptionInfoList();
        if (subInfos != null) {
            for (SubscriptionInfo subInfo : subInfos ) {
                if (SubscriptionManager.isValidSubscriptionId(subInfo.getSubscriptionId())) {
                    Resources subRes = SubscriptionManager.getResourcesForSubId(context,
                            subInfo.getSubscriptionId());
                    if (subRes.getBoolean(R.bool.config_enable_conference_dialer)) {
                        TelephonyManager telephonyMgr = (TelephonyManager) context.
                                getSystemService(Context.TELEPHONY_SERVICE);
                        isEnabled = telephonyMgr.isImsRegistered();
                        if (isEnabled) {
                            break;
                        }
                    }
                }
            }
        }
        return isEnabled;
    }

    /**
     * get intent to start conference dialer
     * with this intent, we can originate an conference call
     */
    public static Intent getConferenceDialerIntent(String number) {
        Intent intent = new Intent("android.intent.action.ADDPARTICIPANT");
        intent.putExtra("confernece_number_key", number);
        return intent;
    }

    /**
     * used to get intent to start conference dialer
     * with this intent, we can add participants to an existing conference call
     */
    public static Intent getAddParticipantsIntent(String number) {
        Intent intent = new Intent("android.intent.action.ADDPARTICIPANT");
        intent.putExtra("add_participant", true);
        intent.putExtra("current_participant_list", number);
        return intent;
    }
}
