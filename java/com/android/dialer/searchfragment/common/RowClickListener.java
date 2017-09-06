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

package com.android.dialer.searchfragment.common;

import com.android.dialer.dialercontact.DialerContact;

/** Interface of possible actions that can be performed by search elements. */
public interface RowClickListener {

  /**
   * Places a traditional voice call.
   *
   * @param ranking position in the list relative to the other elements
   */
  void placeVoiceCall(String phoneNumber, int ranking);

  /**
   * Places an IMS video call.
   *
   * @param ranking position in the list relative to the other elements
   */
  void placeVideoCall(String phoneNumber, int ranking);

  /** Places a Duo video call. */
  void placeDuoCall(String phoneNumber);

  /** Opens the enriched calling/call composer interface. */
  void openCallAndShare(DialerContact dialerContact);
}
