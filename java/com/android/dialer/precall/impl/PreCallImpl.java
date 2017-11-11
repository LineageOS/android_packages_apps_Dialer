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
import android.content.Intent;
import android.support.annotation.NonNull;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.precall.PreCall;
import com.android.dialer.precall.PreCallAction;
import com.google.common.collect.ImmutableList;
import javax.inject.Inject;

/** Implementation of {@link PreCall} */
public class PreCallImpl implements PreCall {

  @Inject
  PreCallImpl() {}

  @Override
  public ImmutableList<PreCallAction> getActions() {
    return ImmutableList.of(new CallingAccountSelector(), new AssistedDialAction());
  }

  @NonNull
  @Override
  public Intent buildIntent(Context context, CallIntentBuilder builder) {
    Intent intent = new Intent(context, PreCallActivity.class);
    intent.putExtra(PreCallCoordinatorImpl.EXTRA_CALL_INTENT_BUILDER, builder);
    return intent;
  }
}
