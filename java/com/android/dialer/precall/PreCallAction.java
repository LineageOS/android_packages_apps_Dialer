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

import android.content.Context;
import android.support.annotation.MainThread;
import com.android.dialer.callintent.CallIntentBuilder;

/**
 * An action to perform before the call is made. The action should inspect and modify the {@link
 * CallIntentBuilder} to generate full information for the call. For example, showing a dialog to
 * select the phone account on a multi-SIM device, ask if RTT should be enabled, or rewrite the
 * number for roaming calls.
 */
public interface PreCallAction {

  /**
   * Whether the action requires an activity to operate. This method is called on all actions before
   * {@link #runWithUi(PreCallCoordinator)} is called. If {@link true} is returned, {@link
   * #runWithUi(PreCallCoordinator)} will be guaranteed to be called on the execution phase.
   * Otherwise {@link #runWithoutUi(Context, CallIntentBuilder)} may be called instead and the
   * action will not be able to show UI, perform async task, or abort the call. This method should
   * not make any state changes.
   */
  @MainThread
  boolean requiresUi(Context context, CallIntentBuilder builder);

  /**
   * Called when all actions returned {@code false} for {@link #requiresUi(Context,
   * CallIntentBuilder)}.
   */
  void runWithoutUi(Context context, CallIntentBuilder builder);

  /**
   * Runs the action. Should block on the main thread until the action is finished. If the action is
   * not instantaneous, {@link PreCallCoordinator#startPendingAction()} should be called to release
   * the thread and continue later.
   */
  @MainThread
  void runWithUi(PreCallCoordinator coordinator);

  /**
   * Called when the UI is being paused when a {@link PreCallCoordinator.PendingAction} is started,
   * and the action is going to be discarded. If the action is showing a dialog the dialog should be
   * dismissed. The action should not retain state, a new instance of the action will be re-run when
   * the UI is resumed.
   */
  @MainThread
  void onDiscard();
}
