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

package com.android.dialer.activecalls;

import android.support.annotation.MainThread;
import com.google.common.collect.ImmutableList;

/** Exposes information about current active calls to the whole dialer. */
public interface ActiveCalls {

  /**
   * Return a list of current active calls. Any call that is not disconnected is regarded as active.
   * Ordering of elements are not guaranteed.
   */
  ImmutableList<ActiveCallInfo> getActiveCalls();

  /** Should only be called by in call UI. */
  @MainThread
  void setActiveCalls(ImmutableList<ActiveCallInfo> activeCalls);
}
