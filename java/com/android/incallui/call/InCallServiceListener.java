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

package com.android.incallui.call;

import android.telecom.InCallService;

/**
 * Interface implemented by In-Call components that maintain a reference to the Telecom API {@code
 * InCallService} object. Clarifies the expectations associated with the relevant method calls.
 */
public interface InCallServiceListener {

  /**
   * Called once at {@code InCallService} startup time with a valid instance. At that time, there
   * will be no existing {@code DialerCall}s.
   *
   * @param inCallService The {@code InCallService} object.
   */
  void setInCallService(InCallService inCallService);

  /**
   * Called once at {@code InCallService} shutdown time. At that time, any {@code DialerCall}s will
   * have transitioned through the disconnected state and will no longer exist.
   */
  void clearInCallService();
}
