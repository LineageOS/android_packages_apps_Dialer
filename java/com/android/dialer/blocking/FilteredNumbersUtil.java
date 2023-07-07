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
package com.android.dialer.blocking;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.BlockedNumberContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.UserManagerCompat;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.dialer.R;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.InteractionEvent;
import com.android.dialer.logging.Logger;
import com.android.dialer.storage.StorageComponent;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.PermissionsUtil;
import java.util.concurrent.TimeUnit;

/** Utility to help with tasks related to filtered numbers. */
@Deprecated
public class FilteredNumbersUtil {

  public static final String CALL_BLOCKING_NOTIFICATION_TAG = "call_blocking";
  public static final int CALL_BLOCKING_DISABLED_BY_EMERGENCY_CALL_NOTIFICATION_ID = 10;
  // Pref key for storing the time of end of the last emergency call in milliseconds after epoch.\
  @VisibleForTesting
  public static final String LAST_EMERGENCY_CALL_MS_PREF_KEY = "last_emergency_call_ms";
  // Pref key for storing whether a notification has been dispatched to notify the user that call
  // blocking has been disabled because of a recent emergency call.
  protected static final String NOTIFIED_CALL_BLOCKING_DISABLED_BY_EMERGENCY_CALL_PREF_KEY =
      "notified_call_blocking_disabled_by_emergency_call";
  // Disable incoming call blocking if there was a call within the past 2 days.
  static final long RECENT_EMERGENCY_CALL_THRESHOLD_MS = TimeUnit.DAYS.toMillis(2);

  /**
   * Used for testing to specify the custom threshold value, in milliseconds for whether an
   * emergency call is "recent". The default value will be used if this custom threshold is less
   * than zero. For example, to set this threshold to 60 seconds:
   *
   * <p>adb shell settings put system dialer_emergency_call_threshold_ms 60000
   */
  private static final String RECENT_EMERGENCY_CALL_THRESHOLD_SETTINGS_KEY =
      "dialer_emergency_call_threshold_ms";

  /** Checks if there exists a contact with {@code Contacts.SEND_TO_VOICEMAIL} set to true. */
  public static void checkForSendToVoicemailContact(
      final Context context, final CheckForSendToVoicemailContactListener listener) {
    final AsyncTask task =
        new AsyncTask<Object, Void, Boolean>() {
          @Override
          public Boolean doInBackground(Object... params) {
            if (context == null || !PermissionsUtil.hasContactsReadPermissions(context)) {
              return false;
            }

            final Cursor cursor =
                context
                    .getContentResolver()
                    .query(
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
    Logger.get(context).logInteraction(InteractionEvent.Type.IMPORT_SEND_TO_VOICEMAIL);
    final FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler =
        new FilteredNumberAsyncQueryHandler(context);

    final AsyncTask<Object, Void, Boolean> task =
        new AsyncTask<Object, Void, Boolean>() {
          @Override
          public Boolean doInBackground(Object... params) {
            if (context == null) {
              return false;
            }

            // Get the phone number of contacts marked as SEND_TO_VOICEMAIL.
            final Cursor phoneCursor =
                context
                    .getContentResolver()
                    .query(
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
                final String normalizedNumber =
                    phoneCursor.getString(PhoneQuery.NORMALIZED_NUMBER_COLUMN_INDEX);
                final String number = phoneCursor.getString(PhoneQuery.NUMBER_COLUMN_INDEX);
                if (normalizedNumber != null) {
                  // Block the phone number of the contact.
                  mFilteredNumberAsyncQueryHandler.blockNumber(null, number);
                }
              }
            } finally {
              phoneCursor.close();
            }

            // Clear SEND_TO_VOICEMAIL on all contacts. The setting has been imported to Dialer.
            ContentValues newValues = new ContentValues();
            newValues.put(Contacts.SEND_TO_VOICEMAIL, 0);
            context
                .getContentResolver()
                .update(
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

  public static long getLastEmergencyCallTimeMillis(Context context) {
    return StorageComponent.get(context)
        .unencryptedSharedPrefs()
        .getLong(LAST_EMERGENCY_CALL_MS_PREF_KEY, 0);
  }

  public static boolean hasRecentEmergencyCall(Context context) {
    if (context == null) {
      return false;
    }

    Long lastEmergencyCallTime = getLastEmergencyCallTimeMillis(context);
    if (lastEmergencyCallTime == 0) {
      return false;
    }

    return (System.currentTimeMillis() - lastEmergencyCallTime)
        < getRecentEmergencyCallThresholdMs(context);
  }

  public static void recordLastEmergencyCallTime(Context context) {
    if (context == null) {
      return;
    }

    StorageComponent.get(context)
        .unencryptedSharedPrefs()
        .edit()
        .putLong(LAST_EMERGENCY_CALL_MS_PREF_KEY, System.currentTimeMillis())
        .putBoolean(NOTIFIED_CALL_BLOCKING_DISABLED_BY_EMERGENCY_CALL_PREF_KEY, false)
        .apply();

    if (UserManagerCompat.isUserUnlocked(context)) {
      maybeNotifyCallBlockingDisabled(context);
    }
  }

  public static void maybeNotifyCallBlockingDisabled(final Context context) {
    return;
  }

  /**
   * @param e164Number The e164 formatted version of the number, or {@code null} if such a format
   *     doesn't exist.
   * @param number The number to attempt blocking.
   * @return {@code true} if the number can be blocked, {@code false} otherwise.
   */
  public static boolean canBlockNumber(Context context, String e164Number, String number) {
    String blockableNumber = getBlockableNumber(context, e164Number, number);
    return !TextUtils.isEmpty(blockableNumber)
        && !PhoneNumberUtils.isEmergencyNumber(blockableNumber);
  }

  /**
   * @param e164Number The e164 formatted version of the number, or {@code null} if such a format
   *     doesn't exist..
   * @param number The number to attempt blocking.
   * @return The version of the given number that can be blocked with the current blocking solution.
   */
  @Nullable
  public static String getBlockableNumber(
      Context context, @Nullable String e164Number, String number) {
    return TextUtils.isEmpty(e164Number) ? number : e164Number;
  }

  private static long getRecentEmergencyCallThresholdMs(Context context) {
    if (LogUtil.isVerboseEnabled()) {
      long thresholdMs =
          Settings.System.getLong(
              context.getContentResolver(), RECENT_EMERGENCY_CALL_THRESHOLD_SETTINGS_KEY, 0);
      return thresholdMs > 0 ? thresholdMs : RECENT_EMERGENCY_CALL_THRESHOLD_MS;
    } else {
      return RECENT_EMERGENCY_CALL_THRESHOLD_MS;
    }
  }

  /**
   * Method used to determine if block operations are possible.
   *
   * @param context The {@link Context}.
   * @return {@code true} if the app and user can block numbers, {@code false} otherwise.
   */
  public static boolean canAttemptBlockOperations(Context context) {
    // Great Wall blocking, must be primary user and the default or system dialer
    // TODO(maxwelb): check that we're the system Dialer
    return TelecomUtil.isDefaultDialer(context)
            && safeBlockedNumbersContractCanCurrentUserBlockNumbers(context);
  }

  /**
   * Used to determine if the call blocking settings can be opened.
   *
   * @param context The {@link Context}.
   * @return {@code true} if the current user can open the call blocking settings, {@code false}
   *     otherwise.
   */
  public static boolean canCurrentUserOpenBlockSettings(Context context) {
    // BlockedNumberContract blocking, verify through Contract API
    return TelecomUtil.isDefaultDialer(context)
            && safeBlockedNumbersContractCanCurrentUserBlockNumbers(context);
  }

  /**
   * Calls {@link BlockedNumberContract#canCurrentUserBlockNumbers(Context)} in such a way that it
   * never throws an exception. While on the CryptKeeper screen, the BlockedNumberContract isn't
   * available, using this method ensures that the Dialer doesn't crash when on that screen.
   *
   * @param context The {@link Context}.
   * @return the result of BlockedNumberContract#canCurrentUserBlockNumbers, or {@code false} if an
   *     exception was thrown.
   */
  private static boolean safeBlockedNumbersContractCanCurrentUserBlockNumbers(Context context) {
    try {
      return BlockedNumberContract.canCurrentUserBlockNumbers(context);
    } catch (Exception e) {
      LogUtil.e(
              "FilteredNumberCompat.safeBlockedNumbersContractCanCurrentUserBlockNumbers",
              "Exception while querying BlockedNumberContract",
              e);
      return false;
    }
  }

  public interface CheckForSendToVoicemailContactListener {

    void onComplete(boolean hasSendToVoicemailContact);
  }

  public interface ImportSendToVoicemailContactsListener {

    void onImportComplete();
  }

  private static class ContactsQuery {

    static final String[] PROJECTION = {Contacts._ID};

    static final String SELECT_SEND_TO_VOICEMAIL_TRUE = Contacts.SEND_TO_VOICEMAIL + "=1";

    static final int ID_COLUMN_INDEX = 0;
  }

  public static class PhoneQuery {

    public static final String[] PROJECTION = {Contacts._ID, Phone.NORMALIZED_NUMBER, Phone.NUMBER};

    public static final int ID_COLUMN_INDEX = 0;
    public static final int NORMALIZED_NUMBER_COLUMN_INDEX = 1;
    public static final int NUMBER_COLUMN_INDEX = 2;

    public static final String SELECT_SEND_TO_VOICEMAIL_TRUE = Contacts.SEND_TO_VOICEMAIL + "=1";
  }
}
