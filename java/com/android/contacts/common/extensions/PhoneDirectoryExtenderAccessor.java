/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.contacts.common.extensions;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.common.Assert;

/** Accessor for the phone directory extender singleton. */
public final class PhoneDirectoryExtenderAccessor {

  private static PhoneDirectoryExtender instance;

  private PhoneDirectoryExtenderAccessor() {}

  @VisibleForTesting
  public static void setForTesting(PhoneDirectoryExtender extender) {
    instance = extender;
  }

  @NonNull
  public static PhoneDirectoryExtender get(@NonNull Context context) {
    Assert.isNotNull(context);
    if (instance != null) {
      return instance;
    }

    Context application = context.getApplicationContext();
    if (application instanceof PhoneDirectoryExtenderFactory) {
      instance = ((PhoneDirectoryExtenderFactory) application).newPhoneDirectoryExtender();
    }

    if (instance == null) {
      instance = new PhoneDirectoryExtenderStub();
    }
    return instance;
  }
}
