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

package com.android.dialer.calllog;

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.NumberAttributes;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.calllog.datasources.CallLogMutations;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.protos.ProtoParsers;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Update {@link Calls#CACHED_NAME} and other cached columns after the annotated call log has been
 * updated. Dialer does not read these columns but other apps relies on it.
 */
public final class CallLogCacheUpdater {

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutor;
  private final CallLogState callLogState;

  /**
   * Maximum numbers of operations the updater can do. Each transaction to the system call log will
   * trigger a call log refresh, so the updater can only do a single batch. If there are more
   * operations it will be truncated. Under normal circumstances there will only be 1 operation
   */
  @VisibleForTesting static final int CACHE_UPDATE_LIMIT = 100;

  @Inject
  CallLogCacheUpdater(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor,
      CallLogState callLogState) {
    this.appContext = appContext;
    this.backgroundExecutor = backgroundExecutor;
    this.callLogState = callLogState;
  }

  /**
   * Extracts inserts and updates from {@code mutations} to update the 'cached' columns in the
   * system call log.
   *
   * <p>If the cached columns are non-empty, it will only be updated if {@link Calls#CACHED_NAME}
   * has changed
   */
  public ListenableFuture<Void> updateCache(CallLogMutations mutations) {
    return Futures.transform(
        callLogState.isBuilt(),
        isBuilt -> {
          if (!isBuilt) {
            // Initial build might need to update 1000 caches, which may overflow the batch
            // operation limit. The initial data was already built with the cache, there's no need
            // to update it.
            LogUtil.i("CallLogCacheUpdater.updateCache", "not updating cache for initial build");
            return null;
          }
          updateCacheInternal(mutations);
          return null;
        },
        backgroundExecutor);
  }

  private void updateCacheInternal(CallLogMutations mutations) {
    ArrayList<ContentProviderOperation> operations = new ArrayList<>();
    Stream.concat(
            mutations.getInserts().entrySet().stream(), mutations.getUpdates().entrySet().stream())
        .limit(CACHE_UPDATE_LIMIT)
        .forEach(
            entry -> {
              ContentValues values = entry.getValue();
              if (!values.containsKey(AnnotatedCallLog.NUMBER_ATTRIBUTES)
                  || !values.containsKey(AnnotatedCallLog.NUMBER)) {
                return;
              }
              DialerPhoneNumber dialerPhoneNumber =
                  ProtoParsers.getTrusted(
                      values, AnnotatedCallLog.NUMBER, DialerPhoneNumber.getDefaultInstance());
              NumberAttributes numberAttributes =
                  ProtoParsers.getTrusted(
                      values,
                      AnnotatedCallLog.NUMBER_ATTRIBUTES,
                      NumberAttributes.getDefaultInstance());
              operations.add(
                  ContentProviderOperation.newUpdate(
                          ContentUris.withAppendedId(Calls.CONTENT_URI, entry.getKey()))
                      .withValue(
                          Calls.CACHED_FORMATTED_NUMBER,
                          values.getAsString(AnnotatedCallLog.FORMATTED_NUMBER))
                      .withValue(Calls.CACHED_LOOKUP_URI, numberAttributes.getLookupUri())
                      // Calls.CACHED_MATCHED_NUMBER is not available.
                      .withValue(Calls.CACHED_NAME, numberAttributes.getName())
                      .withValue(
                          Calls.CACHED_NORMALIZED_NUMBER, dialerPhoneNumber.getNormalizedNumber())
                      .withValue(Calls.CACHED_NUMBER_LABEL, numberAttributes.getNumberTypeLabel())
                      // NUMBER_TYPE is lost in NumberAttributes when it is converted to a string
                      // label, Use TYPE_CUSTOM so the label will be displayed.
                      .withValue(Calls.CACHED_NUMBER_TYPE, Phone.TYPE_CUSTOM)
                      .withValue(Calls.CACHED_PHOTO_ID, numberAttributes.getPhotoId())
                      .withValue(Calls.CACHED_PHOTO_URI, numberAttributes.getPhotoUri())
                      // Avoid writing to the call log for insignificant changes to avoid triggering
                      // other content observers such as the voicemail client.
                      .withSelection(
                          Calls.CACHED_NAME + " IS NOT ?",
                          new String[] {numberAttributes.getName()})
                      .build());
            });
    try {
      int count =
          Arrays.stream(appContext.getContentResolver().applyBatch(CallLog.AUTHORITY, operations))
              .mapToInt(result -> result.count)
              .sum();
      LogUtil.i("CallLogCacheUpdater.updateCache", "updated %d rows", count);
    } catch (OperationApplicationException | RemoteException e) {
      throw new IllegalStateException(e);
    }
  }
}
