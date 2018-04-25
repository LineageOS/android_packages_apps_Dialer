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
 * limitations under the License.
 */

package com.android.contacts.common.widget;

import android.telecom.PhoneAccountHandle;
import com.android.dialer.common.Assert;
import com.android.dialer.telecom.TelecomUtil;
import java.util.Collection;

/** Provides common operation on a {@link SelectPhoneAccountDialogOptions} */
public final class SelectPhoneAccountDialogOptionsUtil {
  private SelectPhoneAccountDialogOptionsUtil() {}

  public static PhoneAccountHandle getPhoneAccountHandle(
      SelectPhoneAccountDialogOptions.Entry entry) {
    return Assert.isNotNull(
        TelecomUtil.composePhoneAccountHandle(
            entry.getPhoneAccountHandleComponentName(), entry.getPhoneAccountHandleId()));
  }

  public static SelectPhoneAccountDialogOptions.Entry.Builder setPhoneAccountHandle(
      SelectPhoneAccountDialogOptions.Entry.Builder entryBuilder,
      PhoneAccountHandle phoneAccountHandle) {
    entryBuilder.setPhoneAccountHandleComponentName(
        phoneAccountHandle.getComponentName().flattenToString());
    entryBuilder.setPhoneAccountHandleId(phoneAccountHandle.getId());
    return entryBuilder;
  }

  public static SelectPhoneAccountDialogOptions.Builder builderWithAccounts(
      Collection<PhoneAccountHandle> phoneAccountHandles) {
    SelectPhoneAccountDialogOptions.Builder optionsBuilder =
        SelectPhoneAccountDialogOptions.newBuilder();
    for (PhoneAccountHandle phoneAccountHandle : phoneAccountHandles) {
      optionsBuilder.addEntries(
          SelectPhoneAccountDialogOptionsUtil.setPhoneAccountHandle(
              SelectPhoneAccountDialogOptions.Entry.newBuilder(), phoneAccountHandle));
    }
    return optionsBuilder;
  }
}
