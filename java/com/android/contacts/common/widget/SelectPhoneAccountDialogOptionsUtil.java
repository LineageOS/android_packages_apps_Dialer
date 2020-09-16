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

import android.os.Parcel;
import android.os.UserHandle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.common.Assert;
import com.android.dialer.telecom.TelecomUtil;

import com.google.protobuf.ByteString;

import java.util.Collection;

/** Provides common operation on a {@link SelectPhoneAccountDialogOptions} */
public final class SelectPhoneAccountDialogOptionsUtil {
  private SelectPhoneAccountDialogOptionsUtil() {}

  public static PhoneAccountHandle getPhoneAccountHandle(
      SelectPhoneAccountDialogOptions.Entry entry) {
    UserHandle userHandle;
    Parcel parcel = Parcel.obtain();
    try {
      byte[] marshalledUserHandle = entry.getUserHandle().toByteArray();
      parcel.unmarshall(marshalledUserHandle, 0, marshalledUserHandle.length);
      parcel.setDataPosition(0);
      userHandle = parcel.readParcelable(UserHandle.class.getClassLoader());
    } catch (NullPointerException e) {
      userHandle = null;
    }
    parcel.recycle();
    return Assert.isNotNull(
        TelecomUtil.composePhoneAccountHandle(
            entry.getPhoneAccountHandleComponentName(), entry.getPhoneAccountHandleId(),
                userHandle));
  }

  public static SelectPhoneAccountDialogOptions.Entry.Builder setPhoneAccountHandle(
      SelectPhoneAccountDialogOptions.Entry.Builder entryBuilder,
      PhoneAccountHandle phoneAccountHandle) {
    entryBuilder.setPhoneAccountHandleComponentName(
        phoneAccountHandle.getComponentName().flattenToString());
    entryBuilder.setPhoneAccountHandleId(phoneAccountHandle.getId());
    Parcel parcel = Parcel.obtain();
    parcel.writeParcelable(phoneAccountHandle.getUserHandle(), 0);
    entryBuilder.setUserHandle(ByteString.copyFrom(parcel.marshall()));
    parcel.recycle();
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
