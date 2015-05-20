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

package com.android.dialer.calllog;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.CallLog;
import android.provider.VoicemailContract.Voicemails;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.GeoUtil;
import com.android.dialer.PhoneCallDetails;
import com.android.dialer.util.AsyncTaskExecutor;
import com.android.dialer.util.AsyncTaskExecutors;
import com.android.dialer.util.TelecomUtil;

public class CallLogAsyncTaskUtil {
    private static String TAG = CallLogAsyncTaskUtil.class.getSimpleName();

   /** The enumeration of {@link AsyncTask} objects used in this class. */
    public enum Tasks {
        DELETE_VOICEMAIL,
        DELETE_CALL,
        MARK_VOICEMAIL_READ,
        GET_CALL_DETAILS,
    }

    private static class CallDetailQuery {
        static final String[] CALL_LOG_PROJECTION = new String[] {
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.COUNTRY_ISO,
            CallLog.Calls.GEOCODED_LOCATION,
            CallLog.Calls.NUMBER_PRESENTATION,
            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            CallLog.Calls.PHONE_ACCOUNT_ID,
            CallLog.Calls.FEATURES,
            CallLog.Calls.DATA_USAGE,
            CallLog.Calls.TRANSCRIPTION
        };

        static final int DATE_COLUMN_INDEX = 0;
        static final int DURATION_COLUMN_INDEX = 1;
        static final int NUMBER_COLUMN_INDEX = 2;
        static final int CALL_TYPE_COLUMN_INDEX = 3;
        static final int COUNTRY_ISO_COLUMN_INDEX = 4;
        static final int GEOCODED_LOCATION_COLUMN_INDEX = 5;
        static final int NUMBER_PRESENTATION_COLUMN_INDEX = 6;
        static final int ACCOUNT_COMPONENT_NAME = 7;
        static final int ACCOUNT_ID = 8;
        static final int FEATURES = 9;
        static final int DATA_USAGE = 10;
        static final int TRANSCRIPTION_COLUMN_INDEX = 11;
    }

    public interface CallLogAsyncTaskListener {
        public void onDeleteCall();
        public void onDeleteVoicemail();
        public void onGetCallDetails(PhoneCallDetails[] details);
    }

    private static AsyncTaskExecutor sAsyncTaskExecutor;

    private static void initTaskExecutor() {
        sAsyncTaskExecutor = AsyncTaskExecutors.createThreadPoolExecutor();
    }

    public static void getCallDetails(
            final Context context,
            final Uri[] callUris,
            final CallLogAsyncTaskListener callLogAsyncTaskListener) {
        if (sAsyncTaskExecutor == null) {
            initTaskExecutor();
        }

        sAsyncTaskExecutor.submit(Tasks.GET_CALL_DETAILS,
                new AsyncTask<Void, Void, PhoneCallDetails[]>() {
                    @Override
                    public PhoneCallDetails[] doInBackground(Void... params) {
                        // TODO: All calls correspond to the same person, so make a single lookup.
                        final int numCalls = callUris.length;
                        PhoneCallDetails[] details = new PhoneCallDetails[numCalls];
                        try {
                            for (int index = 0; index < numCalls; ++index) {
                                details[index] =
                                        getPhoneCallDetailsForUri(context, callUris[index]);
                            }
                            return details;
                        } catch (IllegalArgumentException e) {
                            // Something went wrong reading in our primary data.
                            Log.w(TAG, "Invalid URI starting call details", e);
                            return null;
                        }
                    }

                    @Override
                    public void onPostExecute(PhoneCallDetails[] phoneCallDetails) {
                        if (callLogAsyncTaskListener != null) {
                            callLogAsyncTaskListener.onGetCallDetails(phoneCallDetails);
                        }
                    }
                });
    }

    /**
     * Return the phone call details for a given call log URI.
     */
    private static PhoneCallDetails getPhoneCallDetailsForUri(Context context, Uri callUri) {
        Cursor cursor = context.getContentResolver().query(
                callUri, CallDetailQuery.CALL_LOG_PROJECTION, null, null, null);

        try {
            if (cursor == null || !cursor.moveToFirst()) {
                throw new IllegalArgumentException("Cannot find content: " + callUri);
            }

            // Read call log.
            final String number = cursor.getString(CallDetailQuery.NUMBER_COLUMN_INDEX);
            final int numberPresentation =
                    cursor.getInt(CallDetailQuery.NUMBER_PRESENTATION_COLUMN_INDEX);
            final long date = cursor.getLong(CallDetailQuery.DATE_COLUMN_INDEX);
            final long duration = cursor.getLong(CallDetailQuery.DURATION_COLUMN_INDEX);
            final int callType = cursor.getInt(CallDetailQuery.CALL_TYPE_COLUMN_INDEX);
            final String geocode = cursor.getString(CallDetailQuery.GEOCODED_LOCATION_COLUMN_INDEX);
            final String transcription =
                    cursor.getString(CallDetailQuery.TRANSCRIPTION_COLUMN_INDEX);

            final PhoneAccountHandle accountHandle = PhoneAccountUtils.getAccount(
                    cursor.getString(CallDetailQuery.ACCOUNT_COMPONENT_NAME),
                    cursor.getString(CallDetailQuery.ACCOUNT_ID));

            String countryIso = cursor.getString(CallDetailQuery.COUNTRY_ISO_COLUMN_INDEX);
            if (TextUtils.isEmpty(countryIso)) {
                countryIso = GeoUtil.getCurrentCountryIso(context);
            }

            // Formatted phone number.
            final CharSequence formattedNumber;
            // Read contact specifics.
            final CharSequence nameText;
            final int numberType;
            final CharSequence numberLabel;
            final Uri photoUri;
            final Uri lookupUri;
            int sourceType;

            // If this is not a regular number, there is no point in looking it up in the contacts.
            ContactInfoHelper contactInfoHelper =
                    new ContactInfoHelper(context, GeoUtil.getCurrentCountryIso(context));
            PhoneNumberUtilsWrapper phoneNumberUtilsWrapper =
                    new PhoneNumberUtilsWrapper(context);
            boolean isVoicemail = phoneNumberUtilsWrapper.isVoicemailNumber(accountHandle, number);
            boolean shouldLookupNumber =
                    PhoneNumberUtilsWrapper.canPlaceCallsTo(number, numberPresentation)
                            && !isVoicemail;
            ContactInfo info = shouldLookupNumber
                            ? contactInfoHelper.lookupNumber(number, countryIso) : null;

            if (info == null) {
                formattedNumber = PhoneNumberDisplayUtil.getDisplayNumber(
                        context, accountHandle, number, numberPresentation, null, isVoicemail);
                nameText = "";
                numberType = 0;
                numberLabel = "";
                photoUri = null;
                lookupUri = null;
                sourceType = 0;
            } else {
                formattedNumber = info.formattedNumber;
                nameText = info.name;
                numberType = info.type;
                numberLabel = info.label;
                photoUri = info.photoUri;
                lookupUri = info.lookupUri;
                sourceType = info.sourceType;
            }


            final int features = cursor.getInt(CallDetailQuery.FEATURES);

            Long dataUsage = null;
            if (!cursor.isNull(CallDetailQuery.DATA_USAGE)) {
                dataUsage = cursor.getLong(CallDetailQuery.DATA_USAGE);
            }

            return new PhoneCallDetails(context, number, numberPresentation, formattedNumber,
                    countryIso, geocode, new int[]{ callType }, date, duration, nameText,
                    numberType, numberLabel, lookupUri, photoUri, sourceType, accountHandle,
                    features, dataUsage, transcription, isVoicemail);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }


    /**
     * Delete specified calls from the call log.
     *
     * @param context The context.
     * @param callIds String of the callIds to delete from the call log, delimited by commas (",").
     * @param callLogAsyncTaskListenerg The listener to invoke after the entries have been deleted.
     */
    public static void deleteCalls(
            final Context context,
            final String callIds,
            final CallLogAsyncTaskListener callLogAsyncTaskListener) {
        if (sAsyncTaskExecutor == null) {
            initTaskExecutor();
        }

        sAsyncTaskExecutor.submit(Tasks.DELETE_CALL,
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    public Void doInBackground(Void... params) {
                        context.getContentResolver().delete(
                                TelecomUtil.getCallLogUri(context),
                                CallLog.Calls._ID + " IN (" + callIds + ")", null);
                        return null;
                    }

                    @Override
                    public void onPostExecute(Void result) {
                        if (callLogAsyncTaskListener != null) {
                            callLogAsyncTaskListener.onDeleteCall();
                        }
                    }
                });

    }

    public static void markVoicemailAsRead(final Context context, final Uri voicemailUri) {
        if (sAsyncTaskExecutor == null) {
            initTaskExecutor();
        }

        sAsyncTaskExecutor.submit(Tasks.MARK_VOICEMAIL_READ, new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(Void... params) {
                ContentValues values = new ContentValues();
                values.put(Voicemails.IS_READ, true);
                context.getContentResolver().update(
                        voicemailUri, values, Voicemails.IS_READ + " = 0", null);

                Intent intent = new Intent(context, CallLogNotificationsService.class);
                intent.setAction(CallLogNotificationsService.ACTION_MARK_NEW_VOICEMAILS_AS_OLD);
                context.startService(intent);
                return null;
            }
        });
    }

    public static void deleteVoicemail(
            final Context context,
            final Uri voicemailUri,
            final CallLogAsyncTaskListener callLogAsyncTaskListener) {
        if (sAsyncTaskExecutor == null) {
            initTaskExecutor();
        }

        sAsyncTaskExecutor.submit(Tasks.DELETE_VOICEMAIL,
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    public Void doInBackground(Void... params) {
                        context.getContentResolver().delete(voicemailUri, null, null);
                        return null;
                    }

                    @Override
                    public void onPostExecute(Void result) {
                        if (callLogAsyncTaskListener != null) {
                            callLogAsyncTaskListener.onDeleteVoicemail();
                        }
                    }
                });
    }
}
