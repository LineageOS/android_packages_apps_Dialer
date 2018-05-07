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

package com.android.dialer.precall.impl;

import android.content.Context;
import android.content.Intent;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.Ui;
import com.android.dialer.duo.Duo.ReachabilityData;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.precall.PreCallAction;
import com.android.dialer.precall.PreCallCoordinator;
import com.android.dialer.precall.PreCallCoordinator.PendingAction;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import javax.inject.Inject;

/**
 * Checks if a duo call is actually callable, and request an activity for {@link
 * android.app.Activity#startActivityForResult(Intent, int)}
 */
public class DuoAction implements PreCallAction {

  private final ListeningExecutorService uiExecutor;

  @Inject
  DuoAction(@Ui ListeningExecutorService uiExecutor) {
    this.uiExecutor = uiExecutor;
  }

  @Override
  public boolean requiresUi(Context context, CallIntentBuilder builder) {
    // Duo call must be started with startActivityForResult() which needs a activity.
    return builder.isDuoCall();
  }

  @Override
  public void runWithoutUi(Context context, CallIntentBuilder builder) {}

  @Override
  public void runWithUi(PreCallCoordinator coordinator) {
    if (!requiresUi(coordinator.getActivity(), coordinator.getBuilder())) {
      return;
    }
    String number = coordinator.getBuilder().getUri().getSchemeSpecificPart();
    ListenableFuture<ImmutableMap<String, ReachabilityData>> reachabilities =
        DuoComponent.get(coordinator.getActivity())
            .getDuo()
            .updateReachability(coordinator.getActivity(), ImmutableList.of(number));
    PendingAction pendingAction = coordinator.startPendingAction();

    Futures.addCallback(
        reachabilities,
        new FutureCallback<ImmutableMap<String, ReachabilityData>>() {
          @Override
          public void onSuccess(ImmutableMap<String, ReachabilityData> result) {
            if (!result.containsKey(number) || !result.get(number).videoCallable()) {
              LogUtil.w(
                  "DuoAction.runWithUi",
                  number + " number no longer duo reachable, falling back to carrier video call");
              coordinator.getBuilder().setIsDuoCall(false);
            }
            pendingAction.finish();
          }

          @Override
          public void onFailure(Throwable throwable) {
            LogUtil.e("DuoAction.runWithUi", "reachability query failed", throwable);
            coordinator.getBuilder().setIsDuoCall(false);
            pendingAction.finish();
          }
        },
        uiExecutor);
  }

  @Override
  public void onDiscard() {}
}
