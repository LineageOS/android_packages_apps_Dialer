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
 * limitations under the License.
 */

package com.android.dialer.duo;

import android.content.ComponentName;
import android.telecom.PhoneAccountHandle;

/** Constants to reference the Duo application. */
public final class DuoConstants {
  public static final String PACKAGE_NAME = "com.google.android.apps.tachyon";

  public static final String CONNECTION_SERVICE =
      "com.google.android.apps.tachyon.telecom.TachyonTelecomConnectionService";

  public static final String PHONE_ACCOUNT_ID = "0";

  public static final ComponentName PHONE_ACCOUNT_COMPONENT_NAME =
      new ComponentName(PACKAGE_NAME, CONNECTION_SERVICE);

  public static final PhoneAccountHandle PHONE_ACCOUNT_HANDLE =
      new PhoneAccountHandle(PHONE_ACCOUNT_COMPONENT_NAME, PHONE_ACCOUNT_ID);

  public static final String DUO_ACTIVATE_ACTION =
      "com.google.android.apps.tachyon.action.REGISTER";

  public static final String DUO_INVITE_ACTION = "com.google.android.apps.tachyon.action.INVITE";

  public static final String DUO_CALL_ACTION = "com.google.android.apps.tachyon.action.CALL";

  private DuoConstants() {}
}
