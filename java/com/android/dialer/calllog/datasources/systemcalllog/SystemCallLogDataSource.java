/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.calllog.datasources.systemcalllog;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.ColorInt;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.ArraySet;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.CallLogMutations;
import com.android.dialer.calllog.datasources.util.RowCombiner;
import com.android.dialer.calllogutils.PhoneAccountUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.phonenumberproto.DialerPhoneNumberUtil;
import com.android.dialer.storage.StorageComponent;
import com.android.dialer.theme.R;
import com.android.dialer.util.PermissionsUtil;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/**
 * Responsible for defining the rows in the annotated call log and maintaining the columns in it
 * which are derived from the system call log.
 */
@SuppressWarnings("MissingPermission")
public class SystemCallLogDataSource implements CallLogDataSource {

  @VisibleForTesting
  static final String PREF_LAST_TIMESTAMP_PROCESSED = "systemCallLogLastTimestampProcessed";

  private final ListeningExecutorService backgroundExecutorService;

  @Nullable private Long lastTimestampProcessed;

  @Inject
  SystemCallLogDataSource(@BackgroundExecutor ListeningExecutorService backgroundExecutorService) {
    this.backgroundExecutorService = backgroundExecutorService;
  }

  @MainThread
  @Override
  public void registerContentObservers(
      Context appContext, ContentObserverCallbacks contentObserverCallbacks) {
    Assert.isMainThread();

    LogUtil.enterBlock("SystemCallLogDataSource.registerContentObservers");

    if (!PermissionsUtil.hasCallLogReadPermissions(appContext)) {
      LogUtil.i("SystemCallLogDataSource.registerContentObservers", "no call log permissions");
      return;
    }
    // TODO(zachh): Need to somehow register observers if user enables permission after launch?

    CallLogObserver callLogObserver =
        new CallLogObserver(ThreadUtil.getUiThreadHandler(), appContext, contentObserverCallbacks);

    appContext
        .getContentResolver()
        .registerContentObserver(CallLog.Calls.CONTENT_URI_WITH_VOICEMAIL, true, callLogObserver);

    if (!PermissionsUtil.hasAddVoicemailPermissions(appContext)) {
      LogUtil.i("SystemCallLogDataSource.registerContentObservers", "no add voicemail permissions");
      return;
    }
    // TODO(uabdullah): Need to somehow register observers if user enables permission after launch?
    appContext
        .getContentResolver()
        .registerContentObserver(VoicemailContract.Status.CONTENT_URI, true, callLogObserver);
  }

  @Override
  public ListenableFuture<Boolean> isDirty(Context appContext) {
    return backgroundExecutorService.submit(() -> isDirtyInternal(appContext));
  }

  @Override
  public ListenableFuture<Void> fill(Context appContext, CallLogMutations mutations) {
    return backgroundExecutorService.submit(() -> fillInternal(appContext, mutations));
  }

  @Override
  public ListenableFuture<Void> onSuccessfulFill(Context appContext) {
    return backgroundExecutorService.submit(() -> onSuccessfulFillInternal(appContext));
  }

  @WorkerThread
  private boolean isDirtyInternal(Context appContext) {
    Assert.isWorkerThread();

    /*
     * The system call log has a last updated timestamp, but deletes are physical (the "deleted"
     * column is unused). This means that we can't detect deletes without scanning the entire table,
     * which would be too slow. So, we just rely on content observers to trigger rebuilds when any
     * change is made to the system call log.
     *
     * Just return false unless the table has never been written to.
     */
    return !StorageComponent.get(appContext)
        .unencryptedSharedPrefs()
        .contains(PREF_LAST_TIMESTAMP_PROCESSED);
  }

  @WorkerThread
  private Void fillInternal(Context appContext, CallLogMutations mutations) {
    Assert.isWorkerThread();

    lastTimestampProcessed = null;

    if (!PermissionsUtil.hasPermission(appContext, permission.READ_CALL_LOG)) {
      LogUtil.i("SystemCallLogDataSource.fill", "no call log permissions");
      return null;
    }

    // This data source should always run first so the mutations should always be empty.
    Assert.checkArgument(mutations.isEmpty());

    Set<Long> annotatedCallLogIds = getAnnotatedCallLogIds(appContext);

    LogUtil.i(
        "SystemCallLogDataSource.fill",
        "found %d existing annotated call log ids",
        annotatedCallLogIds.size());

    handleInsertsAndUpdates(appContext, mutations, annotatedCallLogIds);
    handleDeletes(appContext, annotatedCallLogIds, mutations);
    return null;
  }

  @WorkerThread
  private Void onSuccessfulFillInternal(Context appContext) {
    // If a fill operation was a no-op, lastTimestampProcessed could still be null.
    if (lastTimestampProcessed != null) {
      StorageComponent.get(appContext)
          .unencryptedSharedPrefs()
          .edit()
          .putLong(PREF_LAST_TIMESTAMP_PROCESSED, lastTimestampProcessed)
          .apply();
    }
    return null;
  }

  @Override
  public ContentValues coalesce(List<ContentValues> individualRowsSortedByTimestampDesc) {
    assertNoVoicemailsInRows(individualRowsSortedByTimestampDesc);

    return new RowCombiner(individualRowsSortedByTimestampDesc)
        .useMostRecentLong(AnnotatedCallLog.TIMESTAMP)
        .useMostRecentLong(AnnotatedCallLog.NEW)
        // Two different DialerPhoneNumbers could be combined if they are different but considered
        // to be an "exact match" by libphonenumber; in this case we arbitrarily select the most
        // recent one.
        .useMostRecentBlob(AnnotatedCallLog.NUMBER)
        .useMostRecentString(AnnotatedCallLog.FORMATTED_NUMBER)
        .useMostRecentString(AnnotatedCallLog.GEOCODED_LOCATION)
        .useSingleValueString(AnnotatedCallLog.PHONE_ACCOUNT_COMPONENT_NAME)
        .useSingleValueString(AnnotatedCallLog.PHONE_ACCOUNT_ID)
        .useSingleValueString(AnnotatedCallLog.PHONE_ACCOUNT_LABEL)
        .useSingleValueLong(AnnotatedCallLog.PHONE_ACCOUNT_COLOR)
        .useMostRecentLong(AnnotatedCallLog.CALL_TYPE)
        // If any call in a group includes a feature (like Wifi/HD), consider the group to have the
        // feature.
        .bitwiseOr(AnnotatedCallLog.FEATURES)
        .combine();
  }

  private void assertNoVoicemailsInRows(List<ContentValues> individualRowsSortedByTimestampDesc) {
    for (ContentValues contentValue : individualRowsSortedByTimestampDesc) {
      if (contentValue.getAsLong(AnnotatedCallLog.CALL_TYPE) != null) {
        Assert.checkArgument(
            contentValue.getAsLong(AnnotatedCallLog.CALL_TYPE) != Calls.VOICEMAIL_TYPE);
      }
    }
  }

  @TargetApi(Build.VERSION_CODES.M) // Uses try-with-resources
  private void handleInsertsAndUpdates(
      Context appContext, CallLogMutations mutations, Set<Long> existingAnnotatedCallLogIds) {
    long previousTimestampProcessed =
        StorageComponent.get(appContext)
            .unencryptedSharedPrefs()
            .getLong(PREF_LAST_TIMESTAMP_PROCESSED, 0L);

    DialerPhoneNumberUtil dialerPhoneNumberUtil =
        new DialerPhoneNumberUtil(PhoneNumberUtil.getInstance());

    // TODO(zachh): Really should be getting last 1000 by timestamp, not by last modified.
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                Calls.CONTENT_URI_WITH_VOICEMAIL,
                new String[] {
                  Calls._ID,
                  Calls.DATE,
                  Calls.LAST_MODIFIED,
                  Calls.NUMBER,
                  Calls.TYPE,
                  Calls.COUNTRY_ISO,
                  Calls.DURATION,
                  Calls.DATA_USAGE,
                  Calls.TRANSCRIPTION,
                  Calls.VOICEMAIL_URI,
                  Calls.IS_READ,
                  Calls.NEW,
                  Calls.GEOCODED_LOCATION,
                  Calls.PHONE_ACCOUNT_COMPONENT_NAME,
                  Calls.PHONE_ACCOUNT_ID,
                  Calls.FEATURES
                },
                Calls.LAST_MODIFIED + " > ? AND " + Voicemails.DELETED + " = 0",
                new String[] {String.valueOf(previousTimestampProcessed)},
                Calls.LAST_MODIFIED + " DESC LIMIT 1000")) {

      if (cursor == null) {
        LogUtil.e("SystemCallLogDataSource.handleInsertsAndUpdates", "null cursor");
        return;
      }

      LogUtil.i(
          "SystemCallLogDataSource.handleInsertsAndUpdates",
          "found %d entries to insert/update",
          cursor.getCount());

      if (cursor.moveToFirst()) {
        int idColumn = cursor.getColumnIndexOrThrow(Calls._ID);
        int dateColumn = cursor.getColumnIndexOrThrow(Calls.DATE);
        int lastModifiedColumn = cursor.getColumnIndexOrThrow(Calls.LAST_MODIFIED);
        int numberColumn = cursor.getColumnIndexOrThrow(Calls.NUMBER);
        int typeColumn = cursor.getColumnIndexOrThrow(Calls.TYPE);
        int countryIsoColumn = cursor.getColumnIndexOrThrow(Calls.COUNTRY_ISO);
        int durationsColumn = cursor.getColumnIndexOrThrow(Calls.DURATION);
        int dataUsageColumn = cursor.getColumnIndexOrThrow(Calls.DATA_USAGE);
        int transcriptionColumn = cursor.getColumnIndexOrThrow(Calls.TRANSCRIPTION);
        int voicemailUriColumn = cursor.getColumnIndexOrThrow(Calls.VOICEMAIL_URI);
        int isReadColumn = cursor.getColumnIndexOrThrow(Calls.IS_READ);
        int newColumn = cursor.getColumnIndexOrThrow(Calls.NEW);
        int geocodedLocationColumn = cursor.getColumnIndexOrThrow(Calls.GEOCODED_LOCATION);
        int phoneAccountComponentColumn =
            cursor.getColumnIndexOrThrow(Calls.PHONE_ACCOUNT_COMPONENT_NAME);
        int phoneAccountIdColumn = cursor.getColumnIndexOrThrow(Calls.PHONE_ACCOUNT_ID);
        int featuresColumn = cursor.getColumnIndexOrThrow(Calls.FEATURES);

        // The cursor orders by LAST_MODIFIED DESC, so the first result is the most recent timestamp
        // processed.
        lastTimestampProcessed = cursor.getLong(lastModifiedColumn);
        do {
          long id = cursor.getLong(idColumn);
          long date = cursor.getLong(dateColumn);
          String numberAsStr = cursor.getString(numberColumn);
          long type = cursor.getInt(typeColumn);
          String countryIso = cursor.getString(countryIsoColumn);
          int duration = cursor.getInt(durationsColumn);
          int dataUsage = cursor.getInt(dataUsageColumn);
          String transcription = cursor.getString(transcriptionColumn);
          String voicemailUri = cursor.getString(voicemailUriColumn);
          int isRead = cursor.getInt(isReadColumn);
          int isNew = cursor.getInt(newColumn);
          String geocodedLocation = cursor.getString(geocodedLocationColumn);
          String phoneAccountComponentName = cursor.getString(phoneAccountComponentColumn);
          String phoneAccountId = cursor.getString(phoneAccountIdColumn);
          int features = cursor.getInt(featuresColumn);

          ContentValues contentValues = new ContentValues();
          contentValues.put(AnnotatedCallLog.TIMESTAMP, date);

          if (!TextUtils.isEmpty(numberAsStr)) {
            DialerPhoneNumber dialerPhoneNumber =
                dialerPhoneNumberUtil.parse(numberAsStr, countryIso);

            contentValues.put(AnnotatedCallLog.NUMBER, dialerPhoneNumber.toByteArray());
            contentValues.put(
                AnnotatedCallLog.FORMATTED_NUMBER,
                PhoneNumberUtils.formatNumber(numberAsStr, countryIso));
            // TODO(zachh): Need to handle post-dial digits; different on N and M.
          } else {
            contentValues.put(
                AnnotatedCallLog.NUMBER, DialerPhoneNumber.getDefaultInstance().toByteArray());
          }
          contentValues.put(AnnotatedCallLog.CALL_TYPE, type);
          contentValues.put(AnnotatedCallLog.IS_READ, isRead);
          contentValues.put(AnnotatedCallLog.NEW, isNew);
          contentValues.put(AnnotatedCallLog.GEOCODED_LOCATION, geocodedLocation);
          contentValues.put(
              AnnotatedCallLog.PHONE_ACCOUNT_COMPONENT_NAME, phoneAccountComponentName);
          contentValues.put(AnnotatedCallLog.PHONE_ACCOUNT_ID, phoneAccountId);
          populatePhoneAccountLabelAndColor(
              appContext, contentValues, phoneAccountComponentName, phoneAccountId);
          contentValues.put(AnnotatedCallLog.FEATURES, features);
          contentValues.put(AnnotatedCallLog.DURATION, duration);
          contentValues.put(AnnotatedCallLog.DATA_USAGE, dataUsage);
          contentValues.put(AnnotatedCallLog.TRANSCRIPTION, transcription);
          contentValues.put(AnnotatedCallLog.VOICEMAIL_URI, voicemailUri);

          if (existingAnnotatedCallLogIds.contains(id)) {
            mutations.update(id, contentValues);
          } else {
            mutations.insert(id, contentValues);
          }
        } while (cursor.moveToNext());
      } // else no new results, do nothing.
    }
  }

  private void populatePhoneAccountLabelAndColor(
      Context appContext,
      ContentValues contentValues,
      String phoneAccountComponentName,
      String phoneAccountId) {
    PhoneAccountHandle phoneAccountHandle =
        PhoneAccountUtils.getAccount(phoneAccountComponentName, phoneAccountId);
    if (phoneAccountHandle == null) {
      return;
    }
    String label = PhoneAccountUtils.getAccountLabel(appContext, phoneAccountHandle);
    if (TextUtils.isEmpty(label)) {
      return;
    }
    contentValues.put(AnnotatedCallLog.PHONE_ACCOUNT_LABEL, label);

    @ColorInt int color = PhoneAccountUtils.getAccountColor(appContext, phoneAccountHandle);
    if (color == PhoneAccount.NO_HIGHLIGHT_COLOR) {
      color =
          appContext
              .getResources()
              .getColor(R.color.dialer_secondary_text_color, appContext.getTheme());
    }
    contentValues.put(AnnotatedCallLog.PHONE_ACCOUNT_COLOR, color);
  }

  private static void handleDeletes(
      Context appContext, Set<Long> existingAnnotatedCallLogIds, CallLogMutations mutations) {
    Set<Long> systemCallLogIds =
        getIdsFromSystemCallLogThatMatch(appContext, existingAnnotatedCallLogIds);
    LogUtil.i(
        "SystemCallLogDataSource.handleDeletes",
        "found %d matching entries in system call log",
        systemCallLogIds.size());
    Set<Long> idsInAnnotatedCallLogNoLongerInSystemCallLog = new ArraySet<>();
    idsInAnnotatedCallLogNoLongerInSystemCallLog.addAll(existingAnnotatedCallLogIds);
    idsInAnnotatedCallLogNoLongerInSystemCallLog.removeAll(systemCallLogIds);

    LogUtil.i(
        "SystemCallLogDataSource.handleDeletes",
        "found %d call log entries to remove",
        idsInAnnotatedCallLogNoLongerInSystemCallLog.size());

    for (long id : idsInAnnotatedCallLogNoLongerInSystemCallLog) {
      mutations.delete(id);
    }
  }

  @TargetApi(Build.VERSION_CODES.M) // Uses try-with-resources
  private static Set<Long> getAnnotatedCallLogIds(Context appContext) {
    ArraySet<Long> ids = new ArraySet<>();

    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                AnnotatedCallLog.CONTENT_URI,
                new String[] {AnnotatedCallLog._ID},
                null,
                null,
                null)) {

      if (cursor == null) {
        LogUtil.e("SystemCallLogDataSource.getAnnotatedCallLogIds", "null cursor");
        return ids;
      }

      if (cursor.moveToFirst()) {
        int idColumn = cursor.getColumnIndexOrThrow(AnnotatedCallLog._ID);
        do {
          ids.add(cursor.getLong(idColumn));
        } while (cursor.moveToNext());
      }
    }
    return ids;
  }

  @TargetApi(Build.VERSION_CODES.M) // Uses try-with-resources
  private static Set<Long> getIdsFromSystemCallLogThatMatch(
      Context appContext, Set<Long> matchingIds) {
    ArraySet<Long> ids = new ArraySet<>();

    String[] questionMarks = new String[matchingIds.size()];
    Arrays.fill(questionMarks, "?");
    String whereClause = (Calls._ID + " in (") + TextUtils.join(",", questionMarks) + ")";
    String[] whereArgs = new String[matchingIds.size()];
    int i = 0;
    for (long id : matchingIds) {
      whereArgs[i++] = String.valueOf(id);
    }

    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                Calls.CONTENT_URI_WITH_VOICEMAIL,
                new String[] {Calls._ID},
                whereClause,
                whereArgs,
                null)) {

      if (cursor == null) {
        LogUtil.e("SystemCallLogDataSource.getIdsFromSystemCallLog", "null cursor");
        return ids;
      }

      if (cursor.moveToFirst()) {
        int idColumn = cursor.getColumnIndexOrThrow(Calls._ID);
        do {
          ids.add(cursor.getLong(idColumn));
        } while (cursor.moveToNext());
      }
      return ids;
    }
  }

  private static class CallLogObserver extends ContentObserver {
    private final Context appContext;
    private final ContentObserverCallbacks contentObserverCallbacks;

    CallLogObserver(
        Handler handler, Context appContext, ContentObserverCallbacks contentObserverCallbacks) {
      super(handler);
      this.appContext = appContext;
      this.contentObserverCallbacks = contentObserverCallbacks;
    }

    @MainThread
    @Override
    public void onChange(boolean selfChange, Uri uri) {
      Assert.isMainThread();
      LogUtil.i(
          "SystemCallLogDataSource.CallLogObserver.onChange",
          "Uri:%s, SelfChange:%b",
          String.valueOf(uri),
          selfChange);
      super.onChange(selfChange, uri);

      /*
       * The system call log has a last updated timestamp, but deletes are physical (the "deleted"
       * column is unused). This means that we can't detect deletes without scanning the entire
       * table, which would be too slow. So, we just rely on content observers to trigger rebuilds
       * when any change is made to the system call log.
       */
      contentObserverCallbacks.markDirtyAndNotify(appContext);
    }
  }
}
