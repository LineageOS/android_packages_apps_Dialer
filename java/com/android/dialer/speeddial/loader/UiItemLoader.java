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

package com.android.dialer.speeddial.loader;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

/** Provides operation for loading {@link SpeedDialUiItem SpeedDialUiItems} */
public interface UiItemLoader {

  /**
   * Returns a {@link ListenableFuture} for a list of {@link SpeedDialUiItem SpeedDialUiItems}. This
   * list is composed of starred contacts from {@link
   * com.android.dialer.speeddial.database.SpeedDialEntryDatabaseHelper} and suggestions from {@link
   * android.provider.ContactsContract.Contacts#STREQUENT_PHONE_ONLY}.
   */
  ListenableFuture<ImmutableList<SpeedDialUiItem>> loadSpeedDialUiItems();
}
