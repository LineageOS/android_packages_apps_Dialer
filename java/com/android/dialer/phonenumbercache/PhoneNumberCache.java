/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.phonenumbercache;

import android.content.Context;
import java.util.Objects;

/** Accessor for the phone number cache bindings. */
public class PhoneNumberCache {

  private static PhoneNumberCacheBindings phoneNumberCacheBindings;

  private PhoneNumberCache() {}

  public static PhoneNumberCacheBindings get(Context context) {
    Objects.requireNonNull(context);
    if (phoneNumberCacheBindings != null) {
      return phoneNumberCacheBindings;
    }

    Context application = context.getApplicationContext();
    if (application instanceof PhoneNumberCacheBindingsFactory) {
      phoneNumberCacheBindings =
          ((PhoneNumberCacheBindingsFactory) application).newPhoneNumberCacheBindings();
    }

    if (phoneNumberCacheBindings == null) {
      phoneNumberCacheBindings = new PhoneNumberCacheBindingsStub();
    }
    return phoneNumberCacheBindings;
  }

  public static void setForTesting(PhoneNumberCacheBindings phoneNumberCacheBindings) {
    PhoneNumberCache.phoneNumberCacheBindings = phoneNumberCacheBindings;
  }
}
