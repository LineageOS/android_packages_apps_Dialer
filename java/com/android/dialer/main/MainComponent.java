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

package com.android.dialer.main;

import android.content.Context;
import com.android.dialer.inject.HasRootComponent;
import com.android.dialer.main.impl.MainModule;
import dagger.Subcomponent;

/** Subcomponent that can be used to access the main implementation. */
@Subcomponent(modules = MainModule.class)
public abstract class MainComponent {

  public abstract Main getMain();

  public static MainComponent get(Context context) {
    return ((HasComponent) ((HasRootComponent) context.getApplicationContext()).component())
        .mainComponent();
  }

  /** Used to refer to the root application component. */
  public interface HasComponent {
    MainComponent mainComponent();
  }
}
