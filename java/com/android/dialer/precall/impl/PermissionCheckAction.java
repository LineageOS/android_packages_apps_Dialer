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

package com.android.dialer.precall.impl;

import android.content.Context;
import android.widget.Toast;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.precall.PreCallAction;
import com.android.dialer.precall.PreCallCoordinator;
import com.android.dialer.telecom.TelecomUtil;

/** Aborts call and show a toast if phone permissions are missing. */
public class PermissionCheckAction implements PreCallAction {

  @Override
  public boolean requiresUi(Context context, CallIntentBuilder builder) {
    return !TelecomUtil.hasCallPhonePermission(context);
  }

  @Override
  public void runWithoutUi(Context context, CallIntentBuilder builder) {}

  @Override
  public void runWithUi(PreCallCoordinator coordinator) {
    if (!requiresUi(coordinator.getActivity(), coordinator.getBuilder())) {
      return;
    }
    Toast.makeText(
            coordinator.getActivity(),
            coordinator
                .getActivity()
                .getString(R.string.pre_call_permission_check_no_phone_permission),
            Toast.LENGTH_LONG)
        .show();
    coordinator.abortCall();
  }

  @Override
  public void onDiscard() {}
}
