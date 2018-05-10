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

package com.android.dialer.storage;

import android.content.Context;
import android.content.SharedPreferences;
import com.android.dialer.inject.HasRootComponent;
import com.android.dialer.inject.IncludeInDialerRoot;
import dagger.Subcomponent;

/** Dagger component for storage. */
@Subcomponent
public abstract class StorageComponent {

  /**
   * Returns unencrypted default shared preferences. This method should not be used for private
   * data.
   *
   * <p>These shared prefs are available even when the device is in FBE mode and are generally the
   * ones that should be used, because Dialer needs to function while in FBE mode.
   */
  @Unencrypted
  public abstract SharedPreferences unencryptedSharedPrefs();

  public static StorageComponent get(Context context) {
    return ((StorageComponent.HasComponent)
            ((HasRootComponent) context.getApplicationContext()).component())
        .storageComponent();
  }

  /** Used to refer to the root application component. */
  @IncludeInDialerRoot
  public interface HasComponent {
    StorageComponent storageComponent();
  }
}
