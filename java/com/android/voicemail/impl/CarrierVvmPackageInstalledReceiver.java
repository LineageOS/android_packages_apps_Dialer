/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.voicemail.impl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives the system API broadcast
 * com.android.internal.telephony.ACTION_CARRIER_VVM_PACKAGE_INSTALLED. This broadcast is only sent
 * to the system dialer. A non-system dialer does not need to respect the carrier VVM app.
 */
public class CarrierVvmPackageInstalledReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
    VvmLog.i("CarrierVvmPackageInstalledReceiver.onReceive", "package installed: " + packageName);
    VvmPackageInstallHandler.handlePackageInstalled(context, packageName);
  }
}
