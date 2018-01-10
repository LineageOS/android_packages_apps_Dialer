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

package com.android.dialer.preferredsim.suggestion.stub;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.preferredsim.suggestion.SuggestionProvider;
import com.google.common.base.Optional;
import javax.inject.Inject;

/** {@link SuggestionProvider} that does nothing. */
public class StubSuggestionProvider implements SuggestionProvider {

  @Inject
  public StubSuggestionProvider() {}

  @WorkerThread
  @Override
  public Optional<Suggestion> getSuggestion(Context context, String number) {
    return Optional.absent();
  }

  @Override
  public void reportUserSelection(
      @NonNull Context context,
      @NonNull String number,
      @NonNull PhoneAccountHandle phoneAccountHandle) {}

  @Override
  public void reportIncorrectSuggestion(@NonNull Context context, @NonNull String number) {}
}
