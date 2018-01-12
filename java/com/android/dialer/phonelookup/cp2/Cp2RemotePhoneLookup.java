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
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Directory;
import android.support.annotation.VisibleForTesting;
import android.telecom.Call;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.Annotations.LightweightExecutor;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.Cp2Info;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.telecom.TelecomCallUtil;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/** PhoneLookup implementation for remote contacts. */
public final class Cp2RemotePhoneLookup implements PhoneLookup<Cp2Info> {

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutorService;
  private final ListeningExecutorService lightweightExecutorService;

  @Inject
  Cp2RemotePhoneLookup(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService,
      @LightweightExecutor ListeningExecutorService lightweightExecutorService) {
    this.appContext = appContext;
    this.backgroundExecutorService = backgroundExecutorService;
    this.lightweightExecutorService = lightweightExecutorService;
  }

  @Override
  public ListenableFuture<Cp2Info> lookup(Call call) {
    String number = TelecomCallUtil.getNumber(call);
    if (number == null) {
      return Futures.immediateFuture(Cp2Info.getDefaultInstance());
    }

    return Futures.transformAsync(
        queryCp2ForRemoteDirectoryIds(),
        remoteDirectoryIds -> queryCp2ForRemoteContact(number, remoteDirectoryIds),
        lightweightExecutorService);
  }

  private ListenableFuture<List<Long>> queryCp2ForRemoteDirectoryIds() {
    return backgroundExecutorService.submit(
        () -> {
          List<Long> remoteDirectoryIds = new ArrayList<>();
          try (Cursor cursor =
              appContext
                  .getContentResolver()
                  .query(
                      getContentUriForDirectoryIds(),
                      /* projection = */ new String[] {ContactsContract.Directory._ID},
                      /* selection = */ null,
                      /* selectionArgs = */ null,
                      /* sortOrder = */ ContactsContract.Directory._ID)) {
            if (cursor == null) {
              LogUtil.e("Cp2RemotePhoneLookup.queryCp2ForDirectoryIds", "null cursor");
              return remoteDirectoryIds;
            }

            if (!cursor.moveToFirst()) {
              LogUtil.i("Cp2RemotePhoneLookup.queryCp2ForDirectoryIds", "empty cursor");
              return remoteDirectoryIds;
            }

            int idColumnIndex = cursor.getColumnIndexOrThrow(ContactsContract.Directory._ID);
            do {
              long directoryId = cursor.getLong(idColumnIndex);

              // Note that IDs of non-remote directories will be included in the result, such as
              // android.provider.ContactsContract.Directory.DEFAULT (the default directory that
              // represents locally stored contacts).
              if (isRemoteDirectory(directoryId)) {
                remoteDirectoryIds.add(cursor.getLong(idColumnIndex));
              }
            } while (cursor.moveToNext());
            return remoteDirectoryIds;
          }
        });
  }

  private ListenableFuture<Cp2Info> queryCp2ForRemoteContact(
      String number, List<Long> remoteDirectoryIds) {
    if (remoteDirectoryIds.isEmpty()) {
      return Futures.immediateFuture(Cp2Info.getDefaultInstance());
    }

    List<ListenableFuture<Cp2Info>> cp2InfoFutures = new ArrayList<>();
    for (long remoteDirectoryId : remoteDirectoryIds) {
      cp2InfoFutures.add(queryCp2ForRemoteContact(number, remoteDirectoryId));
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

  private ListenableFuture<Cp2Info> queryCp2ForRemoteContact(
      String number, long remoteDirectoryId) {
    return backgroundExecutorService.submit(
        () -> {
          Cp2Info.Builder cp2InfoBuilder = Cp2Info.newBuilder();
          try (Cursor cursor =
              appContext
                  .getContentResolver()
                  .query(
                      getContentUriForContacts(number, remoteDirectoryId),
                      Cp2Projections.getProjectionForPhoneLookupTable(),
                      /* selection = */ null,
                      /* selectionArgs = */ null,
                      /* sortOrder = */ null)) {
            if (cursor == null) {
              LogUtil.e(
                  "Cp2RemotePhoneLookup.queryCp2ForRemoteContact",
                  "null cursor returned when querying directory %d",
                  remoteDirectoryId);
              return cp2InfoBuilder.build();
            }

            if (!cursor.moveToFirst()) {
              LogUtil.i(
                  "Cp2RemotePhoneLookup.queryCp2ForRemoteContact",
                  "empty cursor returned when querying directory %d",
                  remoteDirectoryId);
              return cp2InfoBuilder.build();
            }

            do {
              cp2InfoBuilder.addCp2ContactInfo(
                  Cp2Projections.buildCp2ContactInfoFromCursor(appContext, cursor));
            } while (cursor.moveToNext());
          }

          return cp2InfoBuilder.build();
        });
  }

  @VisibleForTesting
  static Uri getContentUriForDirectoryIds() {
    return VERSION.SDK_INT >= VERSION_CODES.N
        ? ContactsContract.Directory.ENTERPRISE_CONTENT_URI
        : ContactsContract.Directory.CONTENT_URI;
  }

  @VisibleForTesting
  static Uri getContentUriForContacts(String number, long directoryId) {
    Uri baseUri =
        VERSION.SDK_INT >= VERSION_CODES.N
            ? ContactsContract.PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI
            : ContactsContract.PhoneLookup.CONTENT_FILTER_URI;

    Uri.Builder builder =
        baseUri
            .buildUpon()
            .appendPath(number)
            .appendQueryParameter(
                ContactsContract.PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS,
                String.valueOf(PhoneNumberHelper.isUriNumber(number)))
            .appendQueryParameter(
                ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId));

    return builder.build();
  }

  private static boolean isRemoteDirectory(long directoryId) {
    return VERSION.SDK_INT >= VERSION_CODES.N
        ? Directory.isRemoteDirectoryId(directoryId)
        : (directoryId != Directory.DEFAULT
            && directoryId != Directory.LOCAL_INVISIBLE
            // Directory.ENTERPRISE_DEFAULT is the default work profile directory for locally stored
            // contacts
            && directoryId != Directory.ENTERPRISE_DEFAULT
            && directoryId != Directory.ENTERPRISE_LOCAL_INVISIBLE);
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    return Futures.immediateFuture(false);
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, Cp2Info>> getMostRecentInfo(
      ImmutableMap<DialerPhoneNumber, Cp2Info> existingInfoMap) {
    return Futures.immediateFuture(existingInfoMap);
  }

  @Override
  public void setSubMessage(PhoneLookupInfo.Builder destination, Cp2Info subMessage) {
    destination.setCp2RemoteInfo(subMessage);
  }

  @Override
  public Cp2Info getSubMessage(PhoneLookupInfo phoneLookupInfo) {
    return phoneLookupInfo.getCp2RemoteInfo();
  }

  @Override
  public ListenableFuture<Void> onSuccessfulBulkUpdate() {
    return Futures.immediateFuture(null);
  }

  @Override
  public void registerContentObservers(
      Context appContext, ContentObserverCallbacks contentObserverCallbacks) {
    // No content observer needed for remote contacts
  }
}
