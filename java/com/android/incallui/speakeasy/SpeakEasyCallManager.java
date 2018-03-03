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

package com.android.incallui.speakeasy;

import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import com.android.incallui.call.DialerCall;
import com.google.common.base.Optional;

/** Provides operations necessary to SpeakEasy. */
public interface SpeakEasyCallManager {

  /**
   * Returns the Fragment used to display data.
   *
   * <p>An absent optional indicates the feature is unavailable.
   */
  Optional<Fragment> getSpeakEasyFragment(@NonNull DialerCall call);

  /**
   * Indicates a call has been removed.
   *
   * @param call The call which has been removed.
   */
  void onCallRemoved(@NonNull DialerCall call);
}
