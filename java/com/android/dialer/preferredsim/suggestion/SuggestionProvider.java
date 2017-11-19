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

package com.android.dialer.preferredsim.suggestion;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.common.Assert;
import com.google.common.base.Optional;

/** Provides hints to the user when selecting a SIM to make a call. */
public interface SuggestionProvider {

  /** The reason the suggestion is made. */
  enum Reason {
    UNKNOWN,
    // The SIM has the same carrier as the callee.
    INTRA_CARRIER,
    // The user has selected the SIM for the callee multiple times.
    FREQUENT
  }

  /** The suggestion. */
  class Suggestion {
    @NonNull public final PhoneAccountHandle phoneAccountHandle;
    @NonNull public final Reason reason;

    public Suggestion(@NonNull PhoneAccountHandle phoneAccountHandle, @NonNull Reason reason) {
      this.phoneAccountHandle = Assert.isNotNull(phoneAccountHandle);
      this.reason = Assert.isNotNull(reason);
    }
  }

  @WorkerThread
  @NonNull
  Optional<Suggestion> getSuggestion(@NonNull Context context, @NonNull String number);

  @WorkerThread
  void reportUserSelection(
      @NonNull Context context,
      @NonNull String number,
      @NonNull PhoneAccountHandle phoneAccountHandle);
}
