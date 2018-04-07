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

package com.android.dialer.inject;

/** Represents all dialer variants. */
public enum DialerVariant {
  // AOSP Dialer variants
  DIALER_AOSP("DialerAosp"),
  DIALER_AOSP_ESPRESSO("DialerAospEspresso"),
  DIALER_ROBOLECTRIC("DialerRobolectric"),



  // TEST variant will be used in situations where we need create in-test application class which
  // doesn't belong to any variants listed above
  DIALER_TEST("DialerTest"),
  // Just for sample code in inject/demo.
  DIALER_DEMO("DialerDemo");

  private final String variant;

  DialerVariant(String variant) {
    this.variant = variant;
  }

  @Override
  public String toString() {
    return variant;
  }
}
