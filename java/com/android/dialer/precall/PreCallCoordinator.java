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

package com.android.dialer.precall;

import android.app.Activity;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.callintent.CallIntentBuilder;

/**
 * Runs {@link PreCallAction} one by one to prepare a {@link
 * com.android.dialer.callintent.CallIntentBuilder} for a call.
 */
public interface PreCallCoordinator {

  @VisibleForTesting public String EXTRA_CALL_INTENT_BUILDER = "extra_call_intent_builder";

  @NonNull
  CallIntentBuilder getBuilder();

  /** @return the activity to attach the UI to. */
  @NonNull
  Activity getActivity();

  /**
   * Called by a {@link PreCallAction} to abort the call. For example, the user has dismissed the
   * dialog and must start over.
   */
  void abortCall();

  /**
   * Callback from a {@link PreCallAction} to signal the action started by {@link
   * PreCallCoordinator#startPendingAction()} has finished.
   */
  interface PendingAction {

    @MainThread
    void finish();
  }

  /**
   * Called by the current running {@link PreCallAction} to release the main thread and resume
   * pre-call later.
   *
   * @return a {@link PendingAction} which {@link PendingAction#finish()} should be called to resume
   *     pre-call. For example the action shows a dialog to the user, startPendingAction() should be
   *     called as the action will not be finished immediately. When the dialog is completed, {@code
   *     finish()} is then called to continue the next step.
   */
  @MainThread
  @NonNull
  PendingAction startPendingAction();
}
