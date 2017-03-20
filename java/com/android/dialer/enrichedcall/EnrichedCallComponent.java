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

package com.android.dialer.enrichedcall;

import android.content.Context;
import android.support.annotation.NonNull;
import dagger.Subcomponent;
import com.android.dialer.enrichedcall.stub.EnrichedCallManagerStub;

/** Subcomponent that can be used to access the enriched call implementation. */
public class EnrichedCallComponent {
  private static EnrichedCallComponent instance;
  private EnrichedCallManager enrichedCallManager;

  @NonNull
  public EnrichedCallManager getEnrichedCallManager() {
    if (enrichedCallManager == null) {
        enrichedCallManager = new EnrichedCallManagerStub();
    }
    return enrichedCallManager;
  }

  public static EnrichedCallComponent get(Context context) {
    if (instance == null) {
        instance = new EnrichedCallComponent();
    }
    return instance;
  }

  /** Used to refer to the root application component. */
  public interface HasComponent {
    EnrichedCallComponent enrichedCallComponent();
  }
}
