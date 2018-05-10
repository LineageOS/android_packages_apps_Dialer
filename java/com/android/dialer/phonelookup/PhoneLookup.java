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

package com.android.dialer.phonelookup;

import android.content.Context;
import android.support.annotation.MainThread;
import android.telecom.Call;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.phonenumberproto.DialerPhoneNumberUtil;
import com.android.dialer.telecom.TelecomCallUtil;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Provides operations related to retrieving information about phone numbers.
 *
 * <p>Some operations defined by this interface are generally targeted towards specific use cases;
 * for example {@link #isDirty(ImmutableSet)}, {@link #getMostRecentInfo(ImmutableMap)}, and {@link
 * #onSuccessfulBulkUpdate()} are generally intended to be used by the call log.
 */
public interface PhoneLookup<T> {

  /**
   * Returns a future containing a new info for the number associated with the provided call.
   *
   * <p>The returned message should contain populated data for the sub-message corresponding to this
   * {@link PhoneLookup}. For example, the CP2 implementation returns a {@link
   * PhoneLookupInfo.Cp2Info} sub-message.
   *
   * <p>The default implementation is for all {@link PhoneLookup} implementations that don't need
   * info in the given call, i.e., it simply extracts the phone number from the call and delegates
   * to {@link #lookup(DialerPhoneNumber)}.
   *
   * <p>However, for {@link PhoneLookup} implementations that need info in the call (such as one for
   * CNAP), they should override this method.
   */
  default ListenableFuture<T> lookup(Context appContext, Call call) {
    ListeningExecutorService backgroundExecutor =
        DialerExecutorComponent.get(appContext).backgroundExecutor();

    ListenableFuture<DialerPhoneNumber> numberFuture =
        backgroundExecutor.submit(
            () -> {
              DialerPhoneNumberUtil dialerPhoneNumberUtil = new DialerPhoneNumberUtil();
              return dialerPhoneNumberUtil.parse(
                  TelecomCallUtil.getNumber(call), GeoUtil.getCurrentCountryIso(appContext));
            });

    return Futures.transformAsync(numberFuture, this::lookup, MoreExecutors.directExecutor());
  }

  /**
   * Returns a future containing a new info for the provided number.
   *
   * <p>The returned message should contain populated data for the sub-message corresponding to this
   * {@link PhoneLookup}. For example, the CP2 implementation returns a {@link
   * PhoneLookupInfo.Cp2Info} sub-message.
   *
   * <p>If the lookup can't be done without info in a {@link Call} (e.g., CNAP), this method is
   * expected to return existing info saved during the most recent lookup for a call to/from the
   * provided number ({@link #lookup(Context, Call)}).
   */
  ListenableFuture<T> lookup(DialerPhoneNumber dialerPhoneNumber);

  /**
   * Returns a future which returns true if the information for any of the provided phone numbers
   * has changed, usually since {@link #onSuccessfulBulkUpdate()} was last invoked.
   */
  ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers);

  /**
   * Get the most recent phone lookup information for this {@link PhoneLookup}. The returned map
   * must contain the exact same keys as the provided map. Most implementations will rely on last
   * modified timestamps to efficiently only update the data which needs to be updated.
   *
   * <p>If there are no changes required, it is valid for this method to simply return the provided
   * {@code existingInfoMap}.
   *
   * <p>If there is no longer information associated with a number (for example, a local contact was
   * deleted) the returned map should contain an empty info for that number.
   */
  ListenableFuture<ImmutableMap<DialerPhoneNumber, T>> getMostRecentInfo(
      ImmutableMap<DialerPhoneNumber, T> existingInfoMap);

  /**
   * Populates the sub-message that this {@link PhoneLookup} is responsible for by copying {@code
   * subMessage} into the provided {@code phoneLookupInfo} builder.
   */
  void setSubMessage(PhoneLookupInfo.Builder phoneLookupInfo, T subMessage);

  /**
   * Gets the sub-message that this {@link PhoneLookup} is responsible for from the provided {@code
   * phoneLookupInfo}.
   */
  T getSubMessage(PhoneLookupInfo phoneLookupInfo);

  /**
   * Called when the results of the {@link #getMostRecentInfo(ImmutableMap)} have been applied by
   * the caller.
   *
   * <p>Typically implementations will use this to store a "last processed" timestamp so that future
   * invocations of {@link #isDirty(ImmutableSet)} and {@link #getMostRecentInfo(ImmutableMap)} can
   * be efficiently implemented.
   */
  ListenableFuture<Void> onSuccessfulBulkUpdate();

  @MainThread
  void registerContentObservers();

  @MainThread
  void unregisterContentObservers();

  /**
   * Clear any data written by this lookup. This is called when the new call log framework has been
   * disabled (because for example there was a problem with it).
   */
  ListenableFuture<Void> clearData();

  /**
   * The name of this lookup for logging purposes. This is generally the same as the class name (but
   * should not use methods from {@link Class} because the class names are generally obfuscated by
   * Proguard.
   */
  String getLoggingName();
}
