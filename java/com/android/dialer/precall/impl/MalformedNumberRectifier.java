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
 * limitations under the License
 */

package com.android.dialer.precall.impl;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.MainThread;
import android.telecom.PhoneAccount;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.precall.PreCallAction;
import com.android.dialer.precall.PreCallCoordinator;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Fix common malformed number before it is dialed. Rewrite the number to the first handler that can
 * handle it
 */
public class MalformedNumberRectifier implements PreCallAction {

  /** Handler for individual rules. */
  public interface MalformedNumberHandler {

    /** @return the number to be corrected to. */
    @MainThread
    Optional<String> handle(Context context, String number);
  }

  private final ImmutableList<MalformedNumberHandler> handlers;

  MalformedNumberRectifier(ImmutableList<MalformedNumberHandler> handlers) {
    this.handlers = handlers;
  }

  @Override
  public boolean requiresUi(Context context, CallIntentBuilder builder) {
    return false;
  }

  @Override
  public void runWithoutUi(Context context, CallIntentBuilder builder) {
    if (!PhoneAccount.SCHEME_TEL.equals(builder.getUri().getScheme())) {
      return;
    }
    String number = builder.getUri().getSchemeSpecificPart();

    for (MalformedNumberHandler handler : handlers) {
      Optional<String> result = handler.handle(context, number);
      if (result.isPresent()) {
        builder.setUri(Uri.fromParts(PhoneAccount.SCHEME_TEL, result.get(), null));
        return;
      }
    }
  }

  @Override
  public void runWithUi(PreCallCoordinator coordinator) {
    runWithoutUi(coordinator.getActivity(), coordinator.getBuilder());
  }

  @Override
  public void onDiscard() {}
}
