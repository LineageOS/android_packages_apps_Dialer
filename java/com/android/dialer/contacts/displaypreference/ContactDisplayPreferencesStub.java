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

package com.android.dialer.contacts.displaypreference;

import javax.inject.Inject;

/**
 * Stub implementation of {@link ContactDisplayPreferences} that always returns primary. Used when
 * the device is locked and regular storage is not available.
 */
public final class ContactDisplayPreferencesStub implements ContactDisplayPreferences {

  @Inject
  ContactDisplayPreferencesStub() {}

  @Override
  public DisplayOrder getDisplayOrder() {
    return DisplayOrder.PRIMARY;
  }

  @Override
  public void setDisplayOrder(DisplayOrder displayOrder) {
    // Do nothing
  }

  @Override
  public SortOrder getSortOrder() {
    return SortOrder.BY_PRIMARY;
  }

  @Override
  public void setSortOrder(SortOrder sortOrder) {
    // Do nothing
  }
}
