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
 * limitations under the License
 */

package com.android.incallui;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.os.UserManagerCompat;
import com.android.contacts.common.preference.ContactsPreferences;

/** Factory class for {@link ContactsPreferences}. */
public class ContactsPreferencesFactory {

  private static boolean sUseTestInstance;
  private static ContactsPreferences sTestInstance;

  /**
   * Creates a new {@link ContactsPreferences} object if possible.
   *
   * @param context the context to use when creating the ContactsPreferences.
   * @return a new ContactsPreferences object or {@code null} if the user is locked.
   */
  @Nullable
  public static ContactsPreferences newContactsPreferences(Context context) {
    if (sUseTestInstance) {
      return sTestInstance;
    }
    if (UserManagerCompat.isUserUnlocked(context)) {
      return new ContactsPreferences(context);
    }
    return null;
  }

  /**
   * Sets the instance to be returned by all calls to {@link #newContactsPreferences(Context)}.
   *
   * @param testInstance the instance to return.
   */
  static void setTestInstance(ContactsPreferences testInstance) {
    sUseTestInstance = true;
    sTestInstance = testInstance;
  }
}
