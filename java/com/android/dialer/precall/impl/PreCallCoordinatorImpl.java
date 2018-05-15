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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.UiListener;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.function.Consumer;
import com.android.dialer.logging.DialerImpression.Type;
import com.android.dialer.logging.Logger;
import com.android.dialer.precall.PreCallAction;
import com.android.dialer.precall.PreCallComponent;
import com.android.dialer.precall.PreCallCoordinator;
import com.android.dialer.telecom.TelecomUtil;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Implements {@link PreCallCoordinator}. Listens to the life cycle of {@link PreCallActivity} to
 * save/restore states.
 */
public class PreCallCoordinatorImpl implements PreCallCoordinator {

  private static final String SAVED_STATE_CURRENT_ACTION = "current_action";

  @NonNull private final Activity activity;

  private CallIntentBuilder builder;
  private ImmutableList<PreCallAction> actions;
  private int currentActionIndex = 0;
  private PreCallAction currentAction;
  private PendingAction pendingAction;
  private boolean aborted = false;

  private UiListener<Object> uiListener;

  PreCallCoordinatorImpl(@NonNull Activity activity) {
    this.activity = Assert.isNotNull(activity);
  }

  void onCreate(Intent intent, @Nullable Bundle savedInstanceState) {
    LogUtil.enterBlock("PreCallCoordinatorImpl.onCreate");
    if (savedInstanceState != null) {
      currentActionIndex = savedInstanceState.getInt(SAVED_STATE_CURRENT_ACTION);
      builder = Assert.isNotNull(savedInstanceState.getParcelable(EXTRA_CALL_INTENT_BUILDER));
    } else {
      builder = Assert.isNotNull(intent.getParcelableExtra(EXTRA_CALL_INTENT_BUILDER));
    }
    uiListener =
        DialerExecutorComponent.get(activity)
            .createUiListener(activity.getFragmentManager(), "PreCallCoordinatorImpl.uiListener");
  }

  void onRestoreInstanceState(Bundle savedInstanceState) {
    currentActionIndex = savedInstanceState.getInt(SAVED_STATE_CURRENT_ACTION);
    builder = savedInstanceState.getParcelable(EXTRA_CALL_INTENT_BUILDER);
  }

  void onResume() {
    actions = PreCallComponent.get(activity).createActions();
    runNextAction();
  }

  void onPause() {
    if (currentAction != null) {
      currentAction.onDiscard();
    }
    currentAction = null;
    pendingAction = null;
  }

  void onSaveInstanceState(Bundle outState) {
    outState.putInt(SAVED_STATE_CURRENT_ACTION, currentActionIndex);
    outState.putParcelable(EXTRA_CALL_INTENT_BUILDER, builder);
  }

  private void runNextAction() {
    LogUtil.enterBlock("PreCallCoordinatorImpl.runNextAction");
    Assert.checkArgument(currentAction == null);
    if (currentActionIndex >= actions.size()) {
      placeCall();
      activity.finish();
      return;
    }
    LogUtil.i("PreCallCoordinatorImpl.runNextAction", "running " + actions.get(currentActionIndex));
    currentAction = actions.get(currentActionIndex);
    actions.get(currentActionIndex).runWithUi(this);
    if (pendingAction == null) {
      onActionFinished();
    }
  }

  private void onActionFinished() {
    LogUtil.enterBlock("PreCallCoordinatorImpl.onActionFinished");
    Assert.isNotNull(currentAction);
    currentAction = null;
    currentActionIndex++;
    if (!aborted) {
      runNextAction();
    } else {
      activity.finish();
    }
  }

  @Override
  public void abortCall() {
    Assert.checkState(currentAction != null);
    aborted = true;
    Logger.get(getActivity()).logImpression(Type.PRECALL_CANCELED);
  }

  @NonNull
  @Override
  public CallIntentBuilder getBuilder() {
    return builder;
  }

  @NonNull
  @Override
  public Activity getActivity() {
    return activity;
  }

  @Override
  @NonNull
  public PendingAction startPendingAction() {
    Assert.isMainThread();
    Assert.isNotNull(currentAction);
    Assert.checkArgument(pendingAction == null);
    pendingAction = new PendingActionImpl();
    return pendingAction;
  }

  private class PendingActionImpl implements PendingAction {

    @Override
    public void finish() {
      Assert.checkArgument(pendingAction == this);
      pendingAction = null;
      onActionFinished();
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <OutputT> void listen(
      ListenableFuture<OutputT> future,
      Consumer<OutputT> successListener,
      Consumer<Throwable> failureListener) {

    uiListener.listen(
        activity,
        Futures.transform(future, (output) -> (Object) output, MoreExecutors.directExecutor()),
        output -> successListener.accept((OutputT) output),
        failureListener::accept);
  }

  private void placeCall() {
    if (builder.isDuoCall()) {
      Optional<Intent> intent =
          DuoComponent.get(activity)
              .getDuo()
              .getCallIntent(builder.getUri().getSchemeSpecificPart());
      if (intent.isPresent()) {
        activity.startActivityForResult(intent.get(), 0);
        return;
      } else {
        LogUtil.e("PreCallCoordinatorImpl.placeCall", "duo.getCallIntent() returned absent");
      }
    }
    TelecomUtil.placeCall(activity, builder.build());
  }
}
