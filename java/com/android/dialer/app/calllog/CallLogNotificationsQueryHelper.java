/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.provider.CallLog.Calls;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.os.UserManagerCompat;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.dialer.app.R;
import com.android.dialer.calllogutils.PhoneNumberDisplayUtil;
import com.android.dialer.common.LogUtil;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.phonenumbercache.ContactInfoHelper;
import com.android.dialer.util.PermissionsUtil;
import java.util.ArrayList;
import java.util.List;

/** Helper class operating on call log notifications. */
public class CallLogNotificationsQueryHelper {

  private final Context mContext;
  private final NewCallsQuery mNewCallsQuery;
  private final ContactInfoHelper mContactInfoHelper;
  private final String mCurrentCountryIso;

  CallLogNotificationsQueryHelper(
      Context context,
      NewCallsQuery newCallsQuery,
      ContactInfoHelper contactInfoHelper,
      String countryIso) {
    mContext = context;
    mNewCallsQuery = newCallsQuery;
    mContactInfoHelper = contactInfoHelper;
    mCurrentCountryIso = countryIso;
  }

  /** Returns an instance of {@link CallLogNotificationsQueryHelper}. */
  public static CallLogNotificationsQueryHelper getInstance(Context context) {
    ContentResolver contentResolver = context.getContentResolver();
    String countryIso = GeoUtil.getCurrentCountryIso(context);
    return new CallLogNotificationsQueryHelper(
        context,
        createNewCallsQuery(context, contentResolver),
        new ContactInfoHelper(context, countryIso),
        countryIso);
  }

  public static void markAllMissedCallsInCallLogAsRead(@NonNull Context context) {
    markMissedCallsInCallLogAsRead(context, null);
  }

  public static void markSingleMissedCallInCallLogAsRead(
      @NonNull Context context, @Nullable Uri callUri) {
    if (callUri == null) {
      LogUtil.e(
          "CallLogNotificationsQueryHelper.markSingleMissedCallInCallLogAsRead",
          "call URI is null, unable to mark call as read");
    } else {
      markMissedCallsInCallLogAsRead(context, callUri);
    }
  }

  /**
   * If callUri is null then calls with a matching callUri are marked as read, otherwise all calls
   * are marked as read.
   */
  @WorkerThread
  private static void markMissedCallsInCallLogAsRead(Context context, @Nullable Uri callUri) {
    if (!UserManagerCompat.isUserUnlocked(context)) {
      LogUtil.e("CallLogNotificationsQueryHelper.markMissedCallsInCallLogAsRead", "locked");
      return;
    }
    if (!PermissionsUtil.hasPhonePermissions(context)) {
      LogUtil.e("CallLogNotificationsQueryHelper.markMissedCallsInCallLogAsRead", "no permission");
      return;
    }

    ContentValues values = new ContentValues();
    values.put(Calls.NEW, 0);
    values.put(Calls.IS_READ, 1);
    StringBuilder where = new StringBuilder();
    where.append(Calls.NEW);
    where.append(" = 1 AND ");
    where.append(Calls.TYPE);
    where.append(" = ?");
    try {
      context
          .getContentResolver()
          .update(
              callUri == null ? Calls.CONTENT_URI : callUri,
              values,
              where.toString(),
              new String[] {Integer.toString(Calls.MISSED_TYPE)});
    } catch (IllegalArgumentException e) {
      LogUtil.e(
          "CallLogNotificationsQueryHelper.markMissedCallsInCallLogAsRead",
          "contacts provider update command failed",
          e);
    }
  }

  /** Create a new instance of {@link NewCallsQuery}. */
  public static NewCallsQuery createNewCallsQuery(
      Context context, ContentResolver contentResolver) {

    return new DefaultNewCallsQuery(context.getApplicationContext(), contentResolver);
  }

  /**
   * Get all voicemails with the "new" flag set to 1.
   *
   * @return A list of NewCall objects where each object represents a new voicemail.
   */
  @Nullable
  public List<NewCall> getNewVoicemails() {
    return mNewCallsQuery.query(Calls.VOICEMAIL_TYPE);
  }

  /**
   * Get all missed calls with the "new" flag set to 1.
   *
   * @return A list of NewCall objects where each object represents a new missed call.
   */
  @Nullable
  public List<NewCall> getNewMissedCalls() {
    return mNewCallsQuery.query(Calls.MISSED_TYPE);
  }

  /**
   * Given a number and number information (presentation and country ISO), get the best name for
   * display. If the name is empty but we have a special presentation, display that. Otherwise
   * attempt to look it up in the database or the cache. If that fails, fall back to displaying the
   * number.
   */
  public String getName(
      @Nullable String number, int numberPresentation, @Nullable String countryIso) {
    return getContactInfo(number, numberPresentation, countryIso).name;
  }

  /**
   * Given a number and number information (presentation and country ISO), get {@link ContactInfo}.
   * If the name is empty but we have a special presentation, display that. Otherwise attempt to
   * look it up in the cache. If that fails, fall back to displaying the number.
   */
  public ContactInfo getContactInfo(
      @Nullable String number, int numberPresentation, @Nullable String countryIso) {
    if (countryIso == null) {
      countryIso = mCurrentCountryIso;
    }

    number = (number == null) ? "" : number;
    ContactInfo contactInfo = new ContactInfo();
    contactInfo.number = number;
    contactInfo.formattedNumber = PhoneNumberUtils.formatNumber(number, countryIso);
    // contactInfo.normalizedNumber is not PhoneNumberUtils.normalizeNumber. Read ContactInfo.
    contactInfo.normalizedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);

    // 1. Special number representation.
    contactInfo.name =
        PhoneNumberDisplayUtil.getDisplayName(mContext, number, numberPresentation, false)
            .toString();
    if (!TextUtils.isEmpty(contactInfo.name)) {
      return contactInfo;
    }

    // 2. Look it up in the cache.
    ContactInfo cachedContactInfo = mContactInfoHelper.lookupNumber(number, countryIso);

    if (cachedContactInfo != null && !TextUtils.isEmpty(cachedContactInfo.name)) {
      return cachedContactInfo;
    }

    if (!TextUtils.isEmpty(contactInfo.formattedNumber)) {
      // 3. If we cannot lookup the contact, use the formatted number instead.
      contactInfo.name = contactInfo.formattedNumber;
    } else if (!TextUtils.isEmpty(number)) {
      // 4. If number can't be formatted, use number.
      contactInfo.name = number;
    } else {
      // 5. Otherwise, it's unknown number.
      contactInfo.name = mContext.getResources().getString(R.string.unknown);
    }
    return contactInfo;
  }

  /** Allows determining the new calls for which a notification should be generated. */
  public interface NewCallsQuery {

    /** Returns the new calls of a certain type for which a notification should be generated. */
    @Nullable
    List<NewCall> query(int type);
  }

  /** Information about a new voicemail. */
  public static final class NewCall {

    public final Uri callsUri;
    @Nullable public final Uri voicemailUri;
    public final String number;
    public final int numberPresentation;
    public final String accountComponentName;
    public final String accountId;
    public final String transcription;
    public final String countryIso;
    public final long dateMs;

    public NewCall(
        Uri callsUri,
        @Nullable Uri voicemailUri,
        String number,
        int numberPresentation,
        String accountComponentName,
        String accountId,
        String transcription,
        String countryIso,
        long dateMs) {
      this.callsUri = callsUri;
      this.voicemailUri = voicemailUri;
      this.number = number;
      this.numberPresentation = numberPresentation;
      this.accountComponentName = accountComponentName;
      this.accountId = accountId;
      this.transcription = transcription;
      this.countryIso = countryIso;
      this.dateMs = dateMs;
    }
  }

  /**
   * Default implementation of {@link NewCallsQuery} that looks up the list of new calls to notify
   * about in the call log.
   */
  private static final class DefaultNewCallsQuery implements NewCallsQuery {

    private static final String[] PROJECTION = {
      Calls._ID,
      Calls.NUMBER,
      Calls.VOICEMAIL_URI,
      Calls.NUMBER_PRESENTATION,
      Calls.PHONE_ACCOUNT_COMPONENT_NAME,
      Calls.PHONE_ACCOUNT_ID,
      Calls.TRANSCRIPTION,
      Calls.COUNTRY_ISO,
      Calls.DATE
    };
    private static final int ID_COLUMN_INDEX = 0;
    private static final int NUMBER_COLUMN_INDEX = 1;
    private static final int VOICEMAIL_URI_COLUMN_INDEX = 2;
    private static final int NUMBER_PRESENTATION_COLUMN_INDEX = 3;
    private static final int PHONE_ACCOUNT_COMPONENT_NAME_COLUMN_INDEX = 4;
    private static final int PHONE_ACCOUNT_ID_COLUMN_INDEX = 5;
    private static final int TRANSCRIPTION_COLUMN_INDEX = 6;
    private static final int COUNTRY_ISO_COLUMN_INDEX = 7;
    private static final int DATE_COLUMN_INDEX = 8;

    private final ContentResolver mContentResolver;
    private final Context mContext;

    private DefaultNewCallsQuery(Context context, ContentResolver contentResolver) {
      mContext = context;
      mContentResolver = contentResolver;
    }

    @Override
    @Nullable
    @TargetApi(VERSION_CODES.M)
    public List<NewCall> query(int type) {
      if (!PermissionsUtil.hasPermission(mContext, Manifest.permission.READ_CALL_LOG)) {
        LogUtil.w(
            "CallLogNotificationsQueryHelper.DefaultNewCallsQuery.query",
            "no READ_CALL_LOG permission, returning null for calls lookup.");
        return null;
      }
      final String selection = String.format("%s = 1 AND %s = ?", Calls.NEW, Calls.TYPE);
      final String[] selectionArgs = new String[] {Integer.toString(type)};
      try (Cursor cursor =
          mContentResolver.query(
              Calls.CONTENT_URI_WITH_VOICEMAIL,
              PROJECTION,
              selection,
              selectionArgs,
              Calls.DEFAULT_SORT_ORDER)) {
        if (cursor == null) {
          return null;
        }
        List<NewCall> newCalls = new ArrayList<>();
        while (cursor.moveToNext()) {
          newCalls.add(createNewCallsFromCursor(cursor));
        }
        return newCalls;
      } catch (RuntimeException e) {
        LogUtil.w(
            "CallLogNotificationsQueryHelper.DefaultNewCallsQuery.query",
            "exception when querying Contacts Provider for calls lookup");
        return null;
      }
    }

    /** Returns an instance of {@link NewCall} created by using the values of the cursor. */
    private NewCall createNewCallsFromCursor(Cursor cursor) {
      String voicemailUriString = cursor.getString(VOICEMAIL_URI_COLUMN_INDEX);
      Uri callsUri =
          ContentUris.withAppendedId(
              Calls.CONTENT_URI_WITH_VOICEMAIL, cursor.getLong(ID_COLUMN_INDEX));
      Uri voicemailUri = voicemailUriString == null ? null : Uri.parse(voicemailUriString);
      return new NewCall(
          callsUri,
          voicemailUri,
          cursor.getString(NUMBER_COLUMN_INDEX),
          cursor.getInt(NUMBER_PRESENTATION_COLUMN_INDEX),
          cursor.getString(PHONE_ACCOUNT_COMPONENT_NAME_COLUMN_INDEX),
          cursor.getString(PHONE_ACCOUNT_ID_COLUMN_INDEX),
          cursor.getString(TRANSCRIPTION_COLUMN_INDEX),
          cursor.getString(COUNTRY_ISO_COLUMN_INDEX),
          cursor.getLong(DATE_COLUMN_INDEX));
    }
  }
}
