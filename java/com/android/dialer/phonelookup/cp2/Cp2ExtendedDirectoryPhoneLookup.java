/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.phonelookup.cp2;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Directory;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.Annotations.LightweightExecutor;
import com.android.dialer.common.concurrent.Annotations.NonUiSerial;
import com.android.dialer.configprovider.ConfigProvider;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.Cp2Info;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.util.PermissionsUtil;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import javax.inject.Inject;

/**
 * PhoneLookup implementation for contacts in both local and remote directories other than the
 * default directory.
 *
 * <p>Contacts in these directories are accessible only by specifying a directory ID.
 */
public final class Cp2ExtendedDirectoryPhoneLookup implements PhoneLookup<Cp2Info> {

  /** Config flag for timeout (in ms). */
  @VisibleForTesting
  static final String CP2_EXTENDED_DIRECTORY_PHONE_LOOKUP_TIMEOUT_MILLIS =
      "cp2_extended_directory_phone_lookup_timout_millis";

  private final Context appContext;
  private final ConfigProvider configProvider;
  private final ListeningExecutorService backgroundExecutorService;
  private final ListeningExecutorService lightweightExecutorService;
  private final MissingPermissionsOperations missingPermissionsOperations;
  private final ScheduledExecutorService scheduledExecutorService;

  @Inject
  Cp2ExtendedDirectoryPhoneLookup(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService,
      @LightweightExecutor ListeningExecutorService lightweightExecutorService,
      @NonUiSerial ScheduledExecutorService scheduledExecutorService,
      ConfigProvider configProvider,
      MissingPermissionsOperations missingPermissionsOperations) {
    this.appContext = appContext;
    this.backgroundExecutorService = backgroundExecutorService;
    this.lightweightExecutorService = lightweightExecutorService;
    this.scheduledExecutorService = scheduledExecutorService;
    this.configProvider = configProvider;
    this.missingPermissionsOperations = missingPermissionsOperations;
  }

  @Override
  public ListenableFuture<Cp2Info> lookup(DialerPhoneNumber dialerPhoneNumber) {
    if (!PermissionsUtil.hasContactsReadPermissions(appContext)) {
      return Futures.immediateFuture(Cp2Info.getDefaultInstance());
    }

    ListenableFuture<Cp2Info> cp2InfoFuture =
        Futures.transformAsync(
            queryCp2ForExtendedDirectoryIds(),
            directoryIds -> queryCp2ForDirectoryContact(dialerPhoneNumber, directoryIds),
            lightweightExecutorService);

    long timeoutMillis =
        configProvider.getLong(CP2_EXTENDED_DIRECTORY_PHONE_LOOKUP_TIMEOUT_MILLIS, Long.MAX_VALUE);

    // Do not pass Long.MAX_VALUE to Futures.withTimeout as it will cause the internal
    // ScheduledExecutorService for timing to keep waiting even after "cp2InfoFuture" is done.
    // Do not pass 0 or a negative value to Futures.withTimeout either as it will cause the timeout
    // event to be triggered immediately.
    return timeoutMillis == Long.MAX_VALUE
        ? cp2InfoFuture
        : Futures.catching(
            Futures.withTimeout(
                cp2InfoFuture, timeoutMillis, TimeUnit.MILLISECONDS, scheduledExecutorService),
            TimeoutException.class,
            unused -> {
              LogUtil.w("Cp2ExtendedDirectoryPhoneLookup.lookup", "Time out!");
              Logger.get(appContext)
                  .logImpression(DialerImpression.Type.CP2_EXTENDED_DIRECTORY_PHONE_LOOKUP_TIMEOUT);
              return Cp2Info.getDefaultInstance();
            },
            lightweightExecutorService);
  }

  private ListenableFuture<List<Long>> queryCp2ForExtendedDirectoryIds() {
    return backgroundExecutorService.submit(
        () -> {
          List<Long> directoryIds = new ArrayList<>();
          try (Cursor cursor =
              appContext
                  .getContentResolver()
                  .query(
                      Directory.ENTERPRISE_CONTENT_URI,
                      /* projection = */ new String[] {ContactsContract.Directory._ID},
                      /* selection = */ null,
                      /* selectionArgs = */ null,
                      /* sortOrder = */ ContactsContract.Directory._ID)) {
            if (cursor == null) {
              LogUtil.e(
                  "Cp2ExtendedDirectoryPhoneLookup.queryCp2ForExtendedDirectoryIds", "null cursor");
              return directoryIds;
            }

            if (!cursor.moveToFirst()) {
              LogUtil.i(
                  "Cp2ExtendedDirectoryPhoneLookup.queryCp2ForExtendedDirectoryIds",
                  "empty cursor");
              return directoryIds;
            }

            int idColumnIndex = cursor.getColumnIndexOrThrow(ContactsContract.Directory._ID);
            do {
              long directoryId = cursor.getLong(idColumnIndex);

              if (isExtendedDirectory(directoryId)) {
                directoryIds.add(cursor.getLong(idColumnIndex));
              }
            } while (cursor.moveToNext());
            return directoryIds;
          }
        });
  }

  private ListenableFuture<Cp2Info> queryCp2ForDirectoryContact(
      DialerPhoneNumber dialerPhoneNumber, List<Long> directoryIds) {
    if (directoryIds.isEmpty()) {
      return Futures.immediateFuture(Cp2Info.getDefaultInstance());
    }

    // Note: This loses country info when number is not valid.
    String number = dialerPhoneNumber.getNormalizedNumber();

    List<ListenableFuture<Cp2Info>> cp2InfoFutures = new ArrayList<>();
    for (long directoryId : directoryIds) {
      cp2InfoFutures.add(queryCp2ForDirectoryContact(number, directoryId));
    }

    return Futures.transform(
        Futures.allAsList(cp2InfoFutures),
        cp2InfoList -> {
          Cp2Info.Builder cp2InfoBuilder = Cp2Info.newBuilder();
          for (Cp2Info cp2Info : cp2InfoList) {
            cp2InfoBuilder.addAllCp2ContactInfo(cp2Info.getCp2ContactInfoList());
          }
          return cp2InfoBuilder.build();
        },
        lightweightExecutorService);
  }

  private ListenableFuture<Cp2Info> queryCp2ForDirectoryContact(String number, long directoryId) {
    return backgroundExecutorService.submit(
        () -> {
          Cp2Info.Builder cp2InfoBuilder = Cp2Info.newBuilder();
          try (Cursor cursor =
              appContext
                  .getContentResolver()
                  .query(
                      getContentUriForContacts(number, directoryId),
                      Cp2Projections.getProjectionForPhoneLookupTable(),
                      /* selection = */ null,
                      /* selectionArgs = */ null,
                      /* sortOrder = */ null)) {
            if (cursor == null) {
              LogUtil.e(
                  "Cp2ExtendedDirectoryPhoneLookup.queryCp2ForDirectoryContact",
                  "null cursor returned when querying directory %d",
                  directoryId);
              return cp2InfoBuilder.build();
            }

            if (!cursor.moveToFirst()) {
              LogUtil.i(
                  "Cp2ExtendedDirectoryPhoneLookup.queryCp2ForDirectoryContact",
                  "empty cursor returned when querying directory %d",
                  directoryId);
              return cp2InfoBuilder.build();
            }

            do {
              cp2InfoBuilder.addCp2ContactInfo(
                  Cp2Projections.buildCp2ContactInfoFromCursor(appContext, cursor, directoryId));
            } while (cursor.moveToNext());
          }

          return cp2InfoBuilder.build();
        });
  }

  @VisibleForTesting
  static Uri getContentUriForContacts(String number, long directoryId) {
    Uri.Builder builder =
        ContactsContract.PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(number)
            .appendQueryParameter(
                ContactsContract.PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS,
                String.valueOf(PhoneNumberHelper.isUriNumber(number)))
            .appendQueryParameter(
                ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId));

    return builder.build();
  }

  private static boolean isExtendedDirectory(long directoryId) {
    return Directory.isRemoteDirectoryId(directoryId)
        || Directory.isEnterpriseDirectoryId(directoryId);
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    if (!PermissionsUtil.hasContactsReadPermissions(appContext)) {
      Predicate<PhoneLookupInfo> phoneLookupInfoIsDirtyFn =
          phoneLookupInfo ->
              !phoneLookupInfo.getExtendedCp2Info().equals(Cp2Info.getDefaultInstance());
      return missingPermissionsOperations.isDirtyForMissingPermissions(
          phoneNumbers, phoneLookupInfoIsDirtyFn);
    }
    return Futures.immediateFuture(false);
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, Cp2Info>> getMostRecentInfo(
      ImmutableMap<DialerPhoneNumber, Cp2Info> existingInfoMap) {
    if (!PermissionsUtil.hasContactsReadPermissions(appContext)) {
      LogUtil.w("Cp2ExtendedDirectoryPhoneLookup.getMostRecentInfo", "missing permissions");
      return missingPermissionsOperations.getMostRecentInfoForMissingPermissions(existingInfoMap);
    }
    return Futures.immediateFuture(existingInfoMap);
  }

  @Override
  public void setSubMessage(PhoneLookupInfo.Builder destination, Cp2Info subMessage) {
    destination.setExtendedCp2Info(subMessage);
  }

  @Override
  public Cp2Info getSubMessage(PhoneLookupInfo phoneLookupInfo) {
    return phoneLookupInfo.getExtendedCp2Info();
  }

  @Override
  public ListenableFuture<Void> onSuccessfulBulkUpdate() {
    return Futures.immediateFuture(null);
  }

  @Override
  public void registerContentObservers() {
    // For contacts in remote directories, no content observer can be registered.
    // For contacts in local (but not default) directories (e.g., the local work directory), we
    // don't register a content observer for now.
  }

  @Override
  public void unregisterContentObservers() {}

  @Override
  public ListenableFuture<Void> clearData() {
    return Futures.immediateFuture(null);
  }

  @Override
  public String getLoggingName() {
    return "Cp2ExtendedDirectoryPhoneLookup";
  }
}
