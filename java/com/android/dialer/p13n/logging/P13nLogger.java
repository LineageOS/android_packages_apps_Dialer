/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.dialer.p13n.logging;

import com.android.contacts.common.list.PhoneNumberListAdapter;

/** Allows logging of data for personalization. */
public interface P13nLogger {

  /**
   * Logs a search query (text or digits) entered by user.
   *
   * @param query search text (or digits) entered by user
   * @param adapter list adapter providing access to contacts matching search query
   */
  void onSearchQuery(String query, PhoneNumberListAdapter adapter);

  /**
   * Resets logging session (clears searches, re-initializes app entry timestamp, etc.) Should be
   * called when Dialer app is resumed.
   */
  void reset();
}
