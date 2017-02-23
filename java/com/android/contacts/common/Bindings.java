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

package com.android.contacts.common;

import android.content.Context;
import com.android.contacts.common.bindings.ContactsCommonBindings;
import com.android.contacts.common.bindings.ContactsCommonBindingsFactory;
import com.android.contacts.common.bindings.ContactsCommonBindingsStub;
import java.util.Objects;

/** Accessor for the contacts common bindings. */
public class Bindings {

  private static ContactsCommonBindings instance;

  private Bindings() {}

  public static ContactsCommonBindings get(Context context) {
    Objects.requireNonNull(context);
    if (instance != null) {
      return instance;
    }

    Context application = context.getApplicationContext();
    if (application instanceof ContactsCommonBindingsFactory) {
      instance = ((ContactsCommonBindingsFactory) application).newContactsCommonBindings();
    }

    if (instance == null) {
      instance = new ContactsCommonBindingsStub();
    }
    return instance;
  }

  public static void setForTesting(ContactsCommonBindings testInstance) {
    instance = testInstance;
  }
}
