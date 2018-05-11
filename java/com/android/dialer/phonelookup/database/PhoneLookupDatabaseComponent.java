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
package com.android.dialer.phonelookup.database;

import android.content.Context;
import com.android.dialer.inject.HasRootComponent;
import com.android.dialer.inject.IncludeInDialerRoot;
import dagger.Subcomponent;

/** Dagger component for database package. */
@Subcomponent
public abstract class PhoneLookupDatabaseComponent {

  public abstract PhoneLookupHistoryDatabaseHelper phoneLookupHistoryDatabaseHelper();

  public static PhoneLookupDatabaseComponent get(Context context) {
    return ((PhoneLookupDatabaseComponent.HasComponent)
            ((HasRootComponent) context.getApplicationContext()).component())
        .phoneLookupDatabaseComponent();
  }

  /** Used to refer to the root application component. */
  @IncludeInDialerRoot
  public interface HasComponent {
    PhoneLookupDatabaseComponent phoneLookupDatabaseComponent();
  }
}
