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

package com.android.incallui.speakeasy;

import javax.inject.Qualifier;

/** Annotations for Speakeasy providers. */
public final class Annotations {

  /** A Speakeasy settings fragment */
  @Qualifier
  public @interface SpeakEasySettingsActivity {}

  /** A Speakeasy settings object */
  @Qualifier
  public @interface SpeakEasySettingsObject {}

  /** A Speakeasy chip */
  @Qualifier
  public @interface SpeakEasyChipResourceId {}

  /** A Speakeasy text resource */
  @Qualifier
  public @interface SpeakEasyTextResourceId {}
}
