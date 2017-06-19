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
 * limitations under the License.
 */

package com.android.dialer.contactactions;

import com.android.dialer.common.Assert;

/**
 * A module that inserts a grey line divider into {@link ContactActionModule}. Layout it provided in
 * R.layout.divider_layout.xml
 */
public final class DividerModule implements ContactActionModule {

  @Override
  public int getStringId() {
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public int getDrawableId() {
    throw Assert.createUnsupportedOperationFailException();
  }

  @Override
  public boolean onClick() {
    throw Assert.createUnsupportedOperationFailException();
  }
}
