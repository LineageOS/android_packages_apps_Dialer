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

package com.android.dialer.preferredsim.suggestion;

import android.content.Context;
import android.support.annotation.WorkerThread;
import com.android.dialer.common.Assert;
import com.android.dialer.inject.HasRootComponent;
import dagger.Subcomponent;

/** Dagger component for {@link SuggestionProvider} */
@Subcomponent
public abstract class SimSuggestionComponent {
  public abstract SuggestionProvider getSuggestionProvider();

  @WorkerThread
  public static SimSuggestionComponent get(Context context) {
    Assert.isWorkerThread();
    return ((HasComponent) ((HasRootComponent) context.getApplicationContext()).component())
        .simSuggestionComponent();
  }

  /** Used to refer to the root application component. */
  public interface HasComponent {
    SimSuggestionComponent simSuggestionComponent();
  }
}
