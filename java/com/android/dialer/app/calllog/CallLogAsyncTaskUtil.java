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

package com.android.dialer.app.calllog;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.CallLog;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.ContextCompat;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import com.android.contacts.common.GeoUtil;
import com.android.dialer.app.PhoneCallDetails;
import com.android.dialer.common.AsyncTaskExecutor;
import com.android.dialer.common.AsyncTaskExecutors;
import com.android.dialer.common.LogUtil;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.phonenumbercache.ContactInfoHelper;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.PermissionsUtil;
import java.util.ArrayList;
import java.util.Arrays;

@TargetApi(VERSION_CODES.M)
public class CallLogAsyncTaskUtil {

  private static final String TAG = "CallLogAsyncTaskUtil";
  private static AsyncTaskExecutor sAsyncTaskExecutor;

  private static void initTaskExecutor() {
    sAsyncTaskExecutor = AsyncTaskExecutors.createThreadPoolExecutor();
  }

  public static void getCallDetails(
      @NonNull final Context context,
      @Nullable final CallLogAsyncTaskListener callLogAsyncTaskListener,
      @NonNull final Uri... callUris) {
    if (sAsyncTaskExecutor == null) {
      initTaskExecutor();
    }

    sAsyncTaskExecutor.submit(
        Tasks.GET_CALL_DETAILS,
        new AsyncTask<Void, Void, PhoneCallDetails[]>() {
          @Override
          public PhoneCallDetails[] doInBackground(Void... params) {
            if (ContextCompat.checkSelfPermission(context, permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
              LogUtil.w("CallLogAsyncTaskUtil.getCallDetails", "missing READ_CALL_LOG permission");
              return null;
            }
            // TODO: All calls correspond to the same person, so make a single lookup.
            final int numCalls = callUris.length;
            PhoneCallDetails[] details = new PhoneCallDetails[numCalls];
            try {
              for (int index = 0; index < numCalls; ++index) {
                details[index] = getPhoneCallDetailsForUri(context, callUris[index]);
              }
              return details;
            } catch (IllegalArgumentException e) {
              // Something went wrong reading in our primary data.
              LogUtil.e(
                  "CallLogAsyncTaskUtil.getCallDetails", "invalid URI starting call details", e);
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

  /** Return the phone call details for a given call log URI. */
  private static PhoneCallDetails getPhoneCallDetailsForUri(
      @NonNull Context context, @NonNull Uri callUri) {
    Cursor cursor =
        context
            .getContentResolver()
            .query(callUri, CallDetailQuery.CALL_LOG_PROJECTION, null, null, null);

    try {
      if (cursor == null || !cursor.moveToFirst()) {
        throw new IllegalArgumentException("Cannot find content: " + callUri);
      }

      // Read call log.
      final String countryIso = cursor.getString(CallDetailQuery.COUNTRY_ISO_COLUMN_INDEX);
      final String number = cursor.getString(CallDetailQuery.NUMBER_COLUMN_INDEX);
      final String postDialDigits =
          (VERSION.SDK_INT >= VERSION_CODES.N)
              ? cursor.getString(CallDetailQuery.POST_DIAL_DIGITS)
              : "";
      final String viaNumber =
          (VERSION.SDK_INT >= VERSION_CODES.N) ? cursor.getString(CallDetailQuery.VIA_NUMBER) : "";
      final int numberPresentation =
          cursor.getInt(CallDetailQuery.NUMBER_PRESENTATION_COLUMN_INDEX);

      final PhoneAccountHandle accountHandle =
          PhoneAccountUtils.getAccount(
              cursor.getString(CallDetailQuery.ACCOUNT_COMPONENT_NAME),
              cursor.getString(CallDetailQuery.ACCOUNT_ID));

      // If this is not a regular number, there is no point in looking it up in the contacts.
      ContactInfoHelper contactInfoHelper =
          new ContactInfoHelper(context, GeoUtil.getCurrentCountryIso(context));
      boolean isVoicemail = PhoneNumberHelper.isVoicemailNumber(context, accountHandle, number);
      boolean shouldLookupNumber =
          PhoneNumberHelper.canPlaceCallsTo(number, numberPresentation) && !isVoicemail;
      ContactInfo info = ContactInfo.EMPTY;

      if (shouldLookupNumber) {
        ContactInfo lookupInfo = contactInfoHelper.lookupNumber(number, countryIso);
        info = lookupInfo != null ? lookupInfo : ContactInfo.EMPTY;
      }

      PhoneCallDetails details = new PhoneCallDetails(number, numberPresentation, postDialDigits);
      details.updateDisplayNumber(context, info.formattedNumber, isVoicemail);

      details.viaNumber = viaNumber;
      details.accountHandle = accountHandle;
      details.contactUri = info.lookupUri;
      details.namePrimary = info.name;
      details.nameAlternative = info.nameAlternative;
      details.numberType = info.type;
      details.numberLabel = info.label;
      details.photoUri = info.photoUri;
      details.sourceType = info.sourceType;
      details.objectId = info.objectId;

      details.callTypes = new int[] {cursor.getInt(CallDetailQuery.CALL_TYPE_COLUMN_INDEX)};
      details.date = cursor.getLong(CallDetailQuery.DATE_COLUMN_INDEX);
      details.duration = cursor.getLong(CallDetailQuery.DURATION_COLUMN_INDEX);
      details.features = cursor.getInt(CallDetailQuery.FEATURES);
      details.geocode = cursor.getString(CallDetailQuery.GEOCODED_LOCATION_COLUMN_INDEX);
      details.transcription = cursor.getString(CallDetailQuery.TRANSCRIPTION_COLUMN_INDEX);

      details.countryIso =
          !TextUtils.isEmpty(countryIso) ? countryIso : GeoUtil.getCurrentCountryIso(context);

      if (!cursor.isNull(CallDetailQuery.DATA_USAGE)) {
        details.dataUsage = cursor.getLong(CallDetailQuery.DATA_USAGE);
      }

      return details;
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
   * @param callLogAsyncTaskListener The listener to invoke after the entries have been deleted.
   */
  public static void deleteCalls(
      @NonNull final Context context,
      final String callIds,
      @Nullable final CallLogAsyncTaskListener callLogAsyncTaskListener) {
    if (sAsyncTaskExecutor == null) {
      initTaskExecutor();
    }

    sAsyncTaskExecutor.submit(
        Tasks.DELETE_CALL,
        new AsyncTask<Void, Void, Void>() {
          @Override
          public Void doInBackground(Void... params) {
            context
                .getContentResolver()
                .delete(
                    TelecomUtil.getCallLogUri(context),
                    CallLog.Calls._ID + " IN (" + callIds + ")",
                    null);
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

  public static void markVoicemailAsRead(
      @NonNull final Context context, @NonNull final Uri voicemailUri) {
    if (sAsyncTaskExecutor == null) {
      initTaskExecutor();
    }

    sAsyncTaskExecutor.submit(
        Tasks.MARK_VOICEMAIL_READ,
        new AsyncTask<Void, Void, Void>() {
          @Override
          public Void doInBackground(Void... params) {
            ContentValues values = new ContentValues();
            values.put(Voicemails.IS_READ, true);
            context
                .getContentResolver()
                .update(voicemailUri, values, Voicemails.IS_READ + " = 0", null);

            Intent intent = new Intent(context, CallLogNotificationsService.class);
            intent.setAction(CallLogNotificationsService.ACTION_MARK_NEW_VOICEMAILS_AS_OLD);
            context.startService(intent);
            return null;
          }
        });
  }

  public static void deleteVoicemail(
      @NonNull final Context context,
      final Uri voicemailUri,
      @Nullable final CallLogAsyncTaskListener callLogAsyncTaskListener) {
    if (sAsyncTaskExecutor == null) {
      initTaskExecutor();
    }

    sAsyncTaskExecutor.submit(
        Tasks.DELETE_VOICEMAIL,
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

  public static void markCallAsRead(@NonNull final Context context, @NonNull final long[] callIds) {
    if (!PermissionsUtil.hasPhonePermissions(context)) {
      return;
    }
    if (sAsyncTaskExecutor == null) {
      initTaskExecutor();
    }

    sAsyncTaskExecutor.submit(
        Tasks.MARK_CALL_READ,
        new AsyncTask<Void, Void, Void>() {
          @Override
          public Void doInBackground(Void... params) {

            StringBuilder where = new StringBuilder();
            where.append(CallLog.Calls.TYPE).append(" = ").append(CallLog.Calls.MISSED_TYPE);
            where.append(" AND ");

            Long[] callIdLongs = new Long[callIds.length];
            for (int i = 0; i < callIds.length; i++) {
              callIdLongs[i] = callIds[i];
            }
            where
                .append(CallLog.Calls._ID)
                .append(" IN (" + TextUtils.join(",", callIdLongs) + ")");

            ContentValues values = new ContentValues(1);
            values.put(CallLog.Calls.IS_READ, "1");
            context
                .getContentResolver()
                .update(CallLog.Calls.CONTENT_URI, values, where.toString(), null);
            return null;
          }
        });
  }

  @VisibleForTesting
  public static void resetForTest() {
    sAsyncTaskExecutor = null;
  }

  /** The enumeration of {@link AsyncTask} objects used in this class. */
  public enum Tasks {
    DELETE_VOICEMAIL,
    DELETE_CALL,
    MARK_VOICEMAIL_READ,
    MARK_CALL_READ,
    GET_CALL_DETAILS,
    UPDATE_DURATION,
  }

  public interface CallLogAsyncTaskListener {

    void onDeleteCall();

    void onDeleteVoicemail();

    void onGetCallDetails(PhoneCallDetails[] details);
  }

  private static final class CallDetailQuery {

    public static final String[] CALL_LOG_PROJECTION;
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
    static final int POST_DIAL_DIGITS = 12;
    static final int VIA_NUMBER = 13;
    private static final String[] CALL_LOG_PROJECTION_INTERNAL =
        new String[] {
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

    static {
      ArrayList<String> projectionList = new ArrayList<>();
      projectionList.addAll(Arrays.asList(CALL_LOG_PROJECTION_INTERNAL));
      if (VERSION.SDK_INT >= VERSION_CODES.N) {
        projectionList.add(CallLog.Calls.POST_DIAL_DIGITS);
        projectionList.add(CallLog.Calls.VIA_NUMBER);
      }
      projectionList.trimToSize();
      CALL_LOG_PROJECTION = projectionList.toArray(new String[projectionList.size()]);
    }
  }
}
