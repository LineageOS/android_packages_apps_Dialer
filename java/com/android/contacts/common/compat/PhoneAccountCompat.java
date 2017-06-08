/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.contacts.common.compat;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccount;

/** Compatiblity class for {@link android.telecom.PhoneAccount} */
public class PhoneAccountCompat {

  /**
   * Builds and returns an icon {@code Drawable} to represent this {@code PhoneAccount} in a user
   * interface.
   *
   * @param phoneAccount the PhoneAccount from which to build the icon.
   * @param context A {@code Context} to use for loading Drawables.
   * @return An icon for this PhoneAccount, or null
   */
  @Nullable
  public static Drawable createIconDrawable(
      @Nullable PhoneAccount phoneAccount, @Nullable Context context) {
    if (phoneAccount == null || context == null) {
      return null;
    }
    return createIconDrawableMarshmallow(phoneAccount, context);
  }

  @Nullable
  private static Drawable createIconDrawableMarshmallow(
      PhoneAccount phoneAccount, Context context) {
    Icon accountIcon = phoneAccount.getIcon();
    if (accountIcon == null) {
      return null;
    }
    return accountIcon.loadDrawable(context);
  }
}
