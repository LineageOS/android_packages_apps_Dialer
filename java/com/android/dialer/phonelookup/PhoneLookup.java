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

import android.support.annotation.NonNull;
import android.telecom.Call;
import com.android.dialer.DialerPhoneNumber;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Provides operations related to retrieving information about phone numbers.
 *
 * <p>Some operations defined by this interface are generally targeted towards specific use cases;
 * for example {@link #isDirty(ImmutableSet, long)} and {@link #bulkUpdate(ImmutableMap, long)} are
 * generally intended to be used by the call log.
 */
public interface PhoneLookup {

  /**
   * Returns a future containing a new {@link PhoneLookupInfo} for the provided call.
   *
   * <p>The returned message should contain populated data for the sub-message corresponding to this
   * {@link PhoneLookup}. For example, the CP2 implementation returns a {@link PhoneLookupInfo} with
   * the {@link PhoneLookupInfo.Cp2Info} sub-message populated.
   */
  ListenableFuture<PhoneLookupInfo> lookup(@NonNull Call call);

  /**
   * Returns a future which returns true if the information for any of the provided phone numbers
   * has changed since {@code lastModified} according to this {@link PhoneLookup}.
   */
  ListenableFuture<Boolean> isDirty(
      ImmutableSet<DialerPhoneNumber> phoneNumbers, long lastModified);

  /**
   * Given a set of existing information and a timestamp, returns a set of information with any
   * changes made since the timestamp according to this {@link PhoneLookup}.
   *
   * <p>If there are no changes required, it is valid for this method to simply return the provided
   * {@code existingInfoMap}.
   *
   * <p>If there is no longer information associated with a number (for example, a local contact was
   * deleted) the returned map should contain an empty {@link PhoneLookupInfo} for that number.
   */
  ListenableFuture<ImmutableMap<DialerPhoneNumber, PhoneLookupInfo>> bulkUpdate(
      ImmutableMap<DialerPhoneNumber, PhoneLookupInfo> existingInfoMap, long lastModified);
}
