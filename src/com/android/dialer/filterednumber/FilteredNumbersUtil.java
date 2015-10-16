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
package com.android.dialer.filterednumber;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.List;

import com.android.dialer.R;
import com.android.dialer.database.FilteredNumberAsyncQueryHandler;
import com.android.dialer.database.FilteredNumberContract.FilteredNumber;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;

/**
 * Utility to help with tasks related to filtered numbers.
 */
public class FilteredNumbersUtil {

    private static final String HIDE_BLOCKED_CALLS_PREF_KEY = "hide_blocked_calls";

    public interface CheckForSendToVoicemailContactListener {
        public void onComplete(boolean hasSendToVoicemailContact);
    }

    public interface ImportSendToVoicemailContactsListener {
        public void onImportComplete();
    }

    private static class ContactsQuery {
        static final String[] PROJECTION = {
            Contacts._ID
        };

        static final String SELECT_SEND_TO_VOICEMAIL_TRUE = Contacts.SEND_TO_VOICEMAIL + "=1";

        static final int ID_COLUMN_INDEX = 0;
    }

    public static class PhoneQuery {
        static final String[] PROJECTION = {
            Contacts._ID,
            Phone.NORMALIZED_NUMBER,
            Phone.NUMBER
        };

        static final int ID_COLUMN_INDEX = 0;
        static final int NORMALIZED_NUMBER_COLUMN_INDEX = 1;
        static final int NUMBER_COLUMN_INDEX = 2;

        static final String SELECT_SEND_TO_VOICEMAIL_TRUE = Contacts.SEND_TO_VOICEMAIL + "=1";
    }

    /**
     * Checks if there exists a contact with {@code Contacts.SEND_TO_VOICEMAIL} set to true.
     */
    public static void checkForSendToVoicemailContact(
            final Context context, final CheckForSendToVoicemailContactListener listener) {
        final AsyncTask task = new AsyncTask<Object, Void, Boolean>() {
            @Override
            public Boolean doInBackground(Object[]  params) {
                if (context == null) {
                    return false;
                }

                final Cursor cursor = context.getContentResolver().query(
                        Contacts.CONTENT_URI,
                        ContactsQuery.PROJECTION,
                        ContactsQuery.SELECT_SEND_TO_VOICEMAIL_TRUE,
                        null,
                        null);

                boolean hasSendToVoicemailContacts = false;
                if (cursor != null) {
                    try {
                        hasSendToVoicemailContacts = cursor.getCount() > 0;
                    } finally {
                        cursor.close();
                    }
                }

                return hasSendToVoicemailContacts;
            }

            @Override
            public void onPostExecute(Boolean hasSendToVoicemailContact) {
                if (listener != null) {
                    listener.onComplete(hasSendToVoicemailContact);
                }
            }
        };
        task.execute();
    }

    /**
     * Blocks all the phone numbers of any contacts marked as SEND_TO_VOICEMAIL, then clears the
     * SEND_TO_VOICEMAIL flag on those contacts.
     */
    public static void importSendToVoicemailContacts(
            final Context context, final ImportSendToVoicemailContactsListener listener) {
        final FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler =
                new FilteredNumberAsyncQueryHandler(context.getContentResolver());

        final AsyncTask task = new AsyncTask<Object, Void, Boolean>() {
            @Override
            public Boolean doInBackground(Object[] params) {
                if (context == null) {
                    return false;
                }

                // Get the phone number of contacts marked as SEND_TO_VOICEMAIL.
                final Cursor phoneCursor = context.getContentResolver().query(
                        Phone.CONTENT_URI,
                        PhoneQuery.PROJECTION,
                        PhoneQuery.SELECT_SEND_TO_VOICEMAIL_TRUE,
                        null,
                        null);

                if (phoneCursor == null) {
                    return false;
                }

                try {
                    while (phoneCursor.moveToNext()) {
                        final String normalizedNumber = phoneCursor.getString(
                                PhoneQuery.NORMALIZED_NUMBER_COLUMN_INDEX);
                        final String number = phoneCursor.getString(
                                PhoneQuery.NUMBER_COLUMN_INDEX);
                        if (normalizedNumber != null) {
                            // Block the phone number of the contact.
                            mFilteredNumberAsyncQueryHandler.blockNumber(
                                    null, normalizedNumber, number, null);
                        }
                    }
                } finally {
                    phoneCursor.close();
                }

                // Clear SEND_TO_VOICEMAIL on all contacts. The setting has been imported to Dialer.
                ContentValues newValues = new ContentValues();
                newValues.put(Contacts.SEND_TO_VOICEMAIL, 0);
                context.getContentResolver().update(
                        Contacts.CONTENT_URI,
                        newValues,
                        ContactsQuery.SELECT_SEND_TO_VOICEMAIL_TRUE,
                        null);

                return true;
            }

            @Override
            public void onPostExecute(Boolean success) {
                if (success) {
                    if (listener != null) {
                        listener.onImportComplete();
                    }
                } else if (context != null) {
                    String toastStr = context.getString(R.string.send_to_voicemail_import_failed);
                    Toast.makeText(context, toastStr, Toast.LENGTH_SHORT).show();
                }
            }
        };
        task.execute();
    }

     /**
     * WARNING: This method should NOT be executed on the UI thread.
     * Use {@code FilteredNumberAsyncQueryHandler} to asynchronously check if a number is blocked.
     */
    public static boolean shouldBlockVoicemail(
            Context context, String number, String countryIso, long dateMs) {
        final String normalizedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);
        if (TextUtils.isEmpty(normalizedNumber)) {
            return false;
        }

        final Cursor cursor = context.getContentResolver().query(
                FilteredNumber.CONTENT_URI,
                new String[] {
                    FilteredNumberColumns.CREATION_TIME
                },
                FilteredNumberColumns.NORMALIZED_NUMBER + "=?",
                new String[] { normalizedNumber },
                null);

        boolean shouldBlock = false;
        if (cursor != null) {
            try {
                cursor.moveToFirst();

                // Block if number is found and it was added before this voicemail was received.
                shouldBlock = cursor.getCount() > 0 && dateMs > cursor.getLong(0);
            } finally {
                cursor.close();
            }
        }

        return shouldBlock;
    }

    public static boolean shouldHideBlockedCalls(Context context) {
        if (context == null) {
            return false;
        }
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(FilteredNumbersUtil.HIDE_BLOCKED_CALLS_PREF_KEY, false);
    }

    public static void setShouldHideBlockedCalls(Context context, boolean shouldHide) {
        if (context == null) {
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(FilteredNumbersUtil.HIDE_BLOCKED_CALLS_PREF_KEY, shouldHide)
                .apply();
    }

    public static boolean canBlockNumber(Context context, String number) {
        if (PhoneNumberUtils.isEmergencyNumber(number)) {
            return false;
        }

        TelecomManager telecomManager =
                (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        List<PhoneAccountHandle> phoneAccountHandles = telecomManager.getCallCapablePhoneAccounts();
        for (PhoneAccountHandle phoneAccountHandle : phoneAccountHandles) {
            if (telecomManager.isVoiceMailNumber(phoneAccountHandle, number)) {
                return false;
            }
        }

        return true;
    }
}
