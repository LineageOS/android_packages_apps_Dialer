/*
 * Copyright (C) 2026 The LineageOS Project
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

package com.android.dialer.sensitivephone;

import android.os.IBinder;
import android.os.RemoteException;

import com.android.dialer.common.LogUtil;

import org.lineageos.services.telecom.ISensitivePhoneNumbers;
import org.lineageos.services.telecom.Item;

import java.util.ArrayList;
import java.util.List;

public class SensitivePhoneNumbers {
  private static final SensitivePhoneNumbers INSTANCE = new SensitivePhoneNumbers();

  private ISensitivePhoneNumbers service;

  private SensitivePhoneNumbers() {}

  public static SensitivePhoneNumbers getInstance() {
    return INSTANCE;
  }

  public boolean isSensitiveNumber(String number, int subId) {
    ISensitivePhoneNumbers sensitivePhoneNumbers = getService();
    if (sensitivePhoneNumbers == null) {
      return false;
    }
    try {
      return sensitivePhoneNumbers.isSensitiveNumber(number, subId);
    } catch (RemoteException e) {
      LogUtil.e("SensitivePhoneNumbers.isSensitiveNumber", "Failed to check sensitive number", e);
      service = null;
      return false;
    }
  }

  public ArrayList<Item> getSensitivePnInfosForMcc(String mcc) {
    ISensitivePhoneNumbers sensitivePhoneNumbers = getService();
    if (sensitivePhoneNumbers == null) {
      return new ArrayList<>();
    }
    try {
      List<Item> result = sensitivePhoneNumbers.getSensitivePnInfosForMcc(mcc);
      return result == null ? new ArrayList<>() : new ArrayList<>(result);
    } catch (RemoteException e) {
      LogUtil.e("SensitivePhoneNumbers.getSensitivePnInfosForMcc",
          "Failed to load sensitive phone numbers", e);
      service = null;
      return new ArrayList<>();
    }
  }

  private ISensitivePhoneNumbers getService() {
    if (service == null) {
      IBinder binder = getServiceBinder(ISensitivePhoneNumbers.SERVICE_NAME);
      if (binder != null) {
        service = ISensitivePhoneNumbers.Stub.asInterface(binder);
      }
    }
    return service;
  }

  private static IBinder getServiceBinder(String name) {
    try {
      return (IBinder) Class.forName("android.os.ServiceManager")
          .getMethod("getService", String.class)
          .invoke(null, name);
    } catch (Throwable t) {
      return null;
    }
  }
}
