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
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.ArraySet;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.calllog.database.AnnotatedCallLogDatabaseHelper;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.CallLogMutations;
import com.android.dialer.calllog.observer.MarkDirtyObserver;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.compat.android.provider.VoicemailCompat;
import com.android.dialer.duo.Duo;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonenumberproto.DialerPhoneNumberUtil;
import com.android.dialer.storage.Unencrypted;
import com.android.dialer.util.PermissionsUtil;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Responsible for defining the rows in the annotated call log and maintaining the columns in it
 * which are derived from the system call log.
 */
@Singleton
@SuppressWarnings("MissingPermission")
public class SystemCallLogDataSource implements CallLogDataSource {

  @VisibleForTesting
  static final String PREF_LAST_TIMESTAMP_PROCESSED = "systemCallLogLastTimestampProcessed";

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutorService;
  private final MarkDirtyObserver markDirtyObserver;
  private final SharedPreferences sharedPreferences;
  private final AnnotatedCallLogDatabaseHelper annotatedCallLogDatabaseHelper;
  private final Duo duo;

  @Nullable private Long lastTimestampProcessed;
  private boolean isCallLogContentObserverRegistered = false;

  @Inject
  SystemCallLogDataSource(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService,
      MarkDirtyObserver markDirtyObserver,
      @Unencrypted SharedPreferences sharedPreferences,
      AnnotatedCallLogDatabaseHelper annotatedCallLogDatabaseHelper,
      Duo duo) {
    this.appContext = appContext;
    this.backgroundExecutorService = backgroundExecutorService;
    this.markDirtyObserver = markDirtyObserver;
    this.sharedPreferences = sharedPreferences;
    this.annotatedCallLogDatabaseHelper = annotatedCallLogDatabaseHelper;
    this.duo = duo;
  }

  @Override
  public void registerContentObservers() {
    LogUtil.enterBlock("SystemCallLogDataSource.registerContentObservers");

    if (!PermissionsUtil.hasCallLogReadPermissions(appContext)) {
      LogUtil.i("SystemCallLogDataSource.registerContentObservers", "no call log permissions");
      return;
    }

    // The system call log has a last updated timestamp, but deletes are physical (the "deleted"
    // column is unused). This means that we can't detect deletes without scanning the entire table,
    // which would be too slow. So, we just rely on content observers to trigger rebuilds when any
    // change is made to the system call log.
    appContext
        .getContentResolver()
        .registerContentObserver(CallLog.Calls.CONTENT_URI_WITH_VOICEMAIL, true, markDirtyObserver);
    isCallLogContentObserverRegistered = true;

    if (!PermissionsUtil.hasAddVoicemailPermissions(appContext)) {
      LogUtil.i("SystemCallLogDataSource.registerContentObservers", "no add voicemail permissions");
      return;
    }
    // TODO(uabdullah): Need to somehow register observers if user enables permission after launch?
    appContext
        .getContentResolver()
        .registerContentObserver(VoicemailContract.Status.CONTENT_URI, true, markDirtyObserver);
  }

  @Override
  public void unregisterContentObservers() {
    appContext.getContentResolver().unregisterContentObserver(markDirtyObserver);
    isCallLogContentObserverRegistered = false;
  }

  @Override
  public ListenableFuture<Void> clearData() {
    ListenableFuture<Void> deleteSharedPref =
        backgroundExecutorService.submit(
            () -> {
              sharedPreferences.edit().remove(PREF_LAST_TIMESTAMP_PROCESSED).apply();
              return null;
            });

    return Futures.transform(
        Futures.allAsList(deleteSharedPref, annotatedCallLogDatabaseHelper.delete()),
        unused -> null,
        MoreExecutors.directExecutor());
  }

  @Override
  public String getLoggingName() {
    return "SystemCallLogDataSource";
  }

  @Override
  public ListenableFuture<Boolean> isDirty() {
    // This can happen if the call log permission is enabled after the application is started.
    if (!isCallLogContentObserverRegistered
        && PermissionsUtil.hasCallLogReadPermissions(appContext)) {
      registerContentObservers();
      // Consider the data source dirty because calls could have been missed while the content
      // observer wasn't registered.
      return Futures.immediateFuture(true);
    }
    return backgroundExecutorService.submit(this::isDirtyInternal);
  }

  @Override
  public ListenableFuture<Void> fill(CallLogMutations mutations) {
    return backgroundExecutorService.submit(() -> fillInternal(mutations));
  }

  @Override
  public ListenableFuture<Void> onSuccessfulFill() {
    return backgroundExecutorService.submit(this::onSuccessfulFillInternal);
  }

  @WorkerThread
  private boolean isDirtyInternal() {
    Assert.isWorkerThread();

    /*
     * The system call log has a last updated timestamp, but deletes are physical (the "deleted"
     * column is unused). This means that we can't detect deletes without scanning the entire table,
     * which would be too slow. So, we just rely on content observers to trigger rebuilds when any
     * change is made to the system call log.
     *
     * Just return false unless the table has never been written to.
     */
    return !sharedPreferences.contains(PREF_LAST_TIMESTAMP_PROCESSED);
  }

  @WorkerThread
  private Void fillInternal(CallLogMutations mutations) {
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
  private Void onSuccessfulFillInternal() {
    // If a fill operation was a no-op, lastTimestampProcessed could still be null.
    if (lastTimestampProcessed != null) {
      sharedPreferences
          .edit()
          .putLong(PREF_LAST_TIMESTAMP_PROCESSED, lastTimestampProcessed)
          .apply();
    }
    return null;
  }

  private void handleInsertsAndUpdates(
      Context appContext, CallLogMutations mutations, Set<Long> existingAnnotatedCallLogIds) {
    long previousTimestampProcessed = sharedPreferences.getLong(PREF_LAST_TIMESTAMP_PROCESSED, 0L);

    DialerPhoneNumberUtil dialerPhoneNumberUtil = new DialerPhoneNumberUtil();

    // TODO(zachh): Really should be getting last 1000 by timestamp, not by last modified.
    try (Cursor cursor =
        appContext
            .getContentResolver()
            .query(
                Calls.CONTENT_URI_WITH_VOICEMAIL,
                getProjection(),
                Calls.LAST_MODIFIED + " > ? AND " + Voicemails.DELETED + " = 0",
                new String[] {String.valueOf(previousTimestampProcessed)},
                Calls.LAST_MODIFIED + " DESC LIMIT 1000")) {

      if (cursor == null) {
        LogUtil.e("SystemCallLogDataSource.handleInsertsAndUpdates", "null cursor");
        return;
      }

      if (!cursor.moveToFirst()) {
        LogUtil.i("SystemCallLogDataSource.handleInsertsAndUpdates", "no entries to insert/update");
        return;
      }

      LogUtil.i(
          "SystemCallLogDataSource.handleInsertsAndUpdates",
          "found %d entries to insert/update",
          cursor.getCount());

      int idColumn = cursor.getColumnIndexOrThrow(Calls._ID);
      int dateColumn = cursor.getColumnIndexOrThrow(Calls.DATE);
      int lastModifiedColumn = cursor.getColumnIndexOrThrow(Calls.LAST_MODIFIED);
      int numberColumn = cursor.getColumnIndexOrThrow(Calls.NUMBER);
      int presentationColumn = cursor.getColumnIndexOrThrow(Calls.NUMBER_PRESENTATION);
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
      int postDialDigitsColumn = cursor.getColumnIndexOrThrow(Calls.POST_DIAL_DIGITS);

      // The cursor orders by LAST_MODIFIED DESC, so the first result is the most recent timestamp
      // processed.
      lastTimestampProcessed = cursor.getLong(lastModifiedColumn);
      do {
        long id = cursor.getLong(idColumn);
        long date = cursor.getLong(dateColumn);
        String numberAsStr = cursor.getString(numberColumn);
        int type;
        if (cursor.isNull(typeColumn) || (type = cursor.getInt(typeColumn)) == 0) {
          // CallLog.Calls#TYPE lists the allowed values, which are non-null and non-zero.
          throw new IllegalStateException("call type is missing");
        }
        int presentation;
        if (cursor.isNull(presentationColumn)
            || (presentation = cursor.getInt(presentationColumn)) == 0) {
          // CallLog.Calls#NUMBER_PRESENTATION lists the allowed values, which are non-null and
          // non-zero.
          throw new IllegalStateException("presentation is missing");
        }
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
        String postDialDigits = cursor.getString(postDialDigitsColumn);

        // Exclude Duo audio calls.
        if (isDuoAudioCall(phoneAccountComponentName, features)) {
          continue;
        }

        ContentValues contentValues = new ContentValues();
        contentValues.put(AnnotatedCallLog.TIMESTAMP, date);

        if (!TextUtils.isEmpty(numberAsStr)) {
          String numberWithPostDialDigits =
              postDialDigits == null ? numberAsStr : numberAsStr + postDialDigits;
          DialerPhoneNumber dialerPhoneNumber =
              dialerPhoneNumberUtil.parse(numberWithPostDialDigits, countryIso);

          contentValues.put(AnnotatedCallLog.NUMBER, dialerPhoneNumber.toByteArray());
          String formattedNumber =
              PhoneNumberUtils.formatNumber(numberWithPostDialDigits, countryIso);
          if (formattedNumber == null) {
            formattedNumber = numberWithPostDialDigits;
          }
          contentValues.put(AnnotatedCallLog.FORMATTED_NUMBER, formattedNumber);
        } else {
          contentValues.put(
              AnnotatedCallLog.NUMBER, DialerPhoneNumber.getDefaultInstance().toByteArray());
        }
        contentValues.put(AnnotatedCallLog.NUMBER_PRESENTATION, presentation);
        contentValues.put(AnnotatedCallLog.CALL_TYPE, type);
        contentValues.put(AnnotatedCallLog.IS_READ, isRead);
        contentValues.put(AnnotatedCallLog.NEW, isNew);
        contentValues.put(AnnotatedCallLog.GEOCODED_LOCATION, geocodedLocation);
        contentValues.put(AnnotatedCallLog.PHONE_ACCOUNT_COMPONENT_NAME, phoneAccountComponentName);
        contentValues.put(AnnotatedCallLog.PHONE_ACCOUNT_ID, phoneAccountId);
        contentValues.put(AnnotatedCallLog.FEATURES, features);
        contentValues.put(AnnotatedCallLog.DURATION, duration);
        contentValues.put(AnnotatedCallLog.DATA_USAGE, dataUsage);
        contentValues.put(AnnotatedCallLog.TRANSCRIPTION, transcription);
        contentValues.put(AnnotatedCallLog.VOICEMAIL_URI, voicemailUri);

        contentValues.put(AnnotatedCallLog.CALL_MAPPING_ID, String.valueOf(date));

        setTranscriptionState(cursor, contentValues);

        if (existingAnnotatedCallLogIds.contains(id)) {
          mutations.update(id, contentValues);
        } else {
          mutations.insert(id, contentValues);
        }
      } while (cursor.moveToNext());
    }
  }

  /**
   * Returns true if the phone account component name and the features belong to a Duo audio call.
   *
   * <p>Characteristics of a Duo audio call are as follows.
   *
   * <ul>
   *   <li>The phone account is {@link Duo#isDuoAccount(String)}; and
   *   <li>The features don't include {@link Calls#FEATURES_VIDEO}.
   * </ul>
   *
   * <p>It is the caller's responsibility to ensure the phone account component name and the
   * features come from the same call log entry.
   */
  private boolean isDuoAudioCall(@Nullable String phoneAccountComponentName, int features) {
    return duo.isDuoAccount(phoneAccountComponentName)
        && ((features & Calls.FEATURES_VIDEO) != Calls.FEATURES_VIDEO);
  }

  private void setTranscriptionState(Cursor cursor, ContentValues contentValues) {
    if (VERSION.SDK_INT >= VERSION_CODES.O) {
      int transcriptionStateColumn =
          cursor.getColumnIndexOrThrow(VoicemailCompat.TRANSCRIPTION_STATE);
      int transcriptionState = cursor.getInt(transcriptionStateColumn);
      contentValues.put(VoicemailCompat.TRANSCRIPTION_STATE, transcriptionState);
    }
  }

  private static final String[] PROJECTION_PRE_O =
      new String[] {
        Calls._ID,
        Calls.DATE,
        Calls.LAST_MODIFIED,
        Calls.NUMBER,
        Calls.NUMBER_PRESENTATION,
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
        Calls.FEATURES,
        Calls.POST_DIAL_DIGITS
      };

  @RequiresApi(VERSION_CODES.O)
  private static final String[] PROJECTION_O_AND_LATER;

  static {
    List<String> projectionList = new ArrayList<>(Arrays.asList(PROJECTION_PRE_O));
    projectionList.add(VoicemailCompat.TRANSCRIPTION_STATE);
    PROJECTION_O_AND_LATER = projectionList.toArray(new String[projectionList.size()]);
  }

  private String[] getProjection() {
    if (VERSION.SDK_INT >= VERSION_CODES.O) {
      return PROJECTION_O_AND_LATER;
    }
    return PROJECTION_PRE_O;
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

  private static Set<Long> getIdsFromSystemCallLogThatMatch(
      Context appContext, Set<Long> matchingIds) {
    ArraySet<Long> ids = new ArraySet<>();

    // Batch the select statements into chunks of 999, the maximum size for SQLite selection args.
    Iterable<List<Long>> batches = Iterables.partition(matchingIds, 999);
    for (List<Long> idsInBatch : batches) {
      String[] questionMarks = new String[idsInBatch.size()];
      Arrays.fill(questionMarks, "?");

      String whereClause = (Calls._ID + " in (") + TextUtils.join(",", questionMarks) + ")";
      String[] whereArgs = new String[idsInBatch.size()];
      int i = 0;
      for (long id : idsInBatch) {
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
      }
    }
    return ids;
  }
}
