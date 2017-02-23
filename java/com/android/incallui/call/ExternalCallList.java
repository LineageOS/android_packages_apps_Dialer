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

package com.android.incallui.call;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.telecom.Call;
import android.util.ArraySet;
import com.android.contacts.common.compat.CallCompat;
import com.android.dialer.common.LogUtil;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the external calls known to the InCall UI.
 *
 * <p>External calls are those with {@code android.telecom.Call.Details#PROPERTY_IS_EXTERNAL_CALL}.
 */
public class ExternalCallList {

  private final Set<Call> mExternalCalls = new ArraySet<>();
  private final Set<ExternalCallListener> mExternalCallListeners =
      Collections.newSetFromMap(new ConcurrentHashMap<ExternalCallListener, Boolean>(8, 0.9f, 1));
  /** Handles {@link android.telecom.Call.Callback} callbacks. */
  private final Call.Callback mTelecomCallCallback =
      new Call.Callback() {
        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
          notifyExternalCallUpdated(call);
        }
      };

  /** Begins tracking an external call and notifies listeners of the new call. */
  public void onCallAdded(Call telecomCall) {
    if (!telecomCall.getDetails().hasProperty(CallCompat.Details.PROPERTY_IS_EXTERNAL_CALL)) {
      throw new IllegalArgumentException();
    }
    mExternalCalls.add(telecomCall);
    telecomCall.registerCallback(mTelecomCallCallback, new Handler(Looper.getMainLooper()));
    notifyExternalCallAdded(telecomCall);
  }

  /** Stops tracking an external call and notifies listeners of the removal of the call. */
  public void onCallRemoved(Call telecomCall) {
    if (!mExternalCalls.contains(telecomCall)) {
      // This can happen on M for external calls from blocked numbers
      LogUtil.i("ExternalCallList.onCallRemoved", "attempted to remove unregistered call");
      return;
    }
    mExternalCalls.remove(telecomCall);
    telecomCall.unregisterCallback(mTelecomCallCallback);
    notifyExternalCallRemoved(telecomCall);
  }

  /** Adds a new listener to external call events. */
  public void addExternalCallListener(@NonNull ExternalCallListener listener) {
    mExternalCallListeners.add(listener);
  }

  /** Removes a listener to external call events. */
  public void removeExternalCallListener(@NonNull ExternalCallListener listener) {
    if (!mExternalCallListeners.contains(listener)) {
      LogUtil.i(
          "ExternalCallList.removeExternalCallListener",
          "attempt to remove unregistered listener.");
    }
    mExternalCallListeners.remove(listener);
  }

  public boolean isCallTracked(@NonNull android.telecom.Call telecomCall) {
    return mExternalCalls.contains(telecomCall);
  }

  /** Notifies listeners of the addition of a new external call. */
  private void notifyExternalCallAdded(Call call) {
    for (ExternalCallListener listener : mExternalCallListeners) {
      listener.onExternalCallAdded(call);
    }
  }

  /** Notifies listeners of the removal of an external call. */
  private void notifyExternalCallRemoved(Call call) {
    for (ExternalCallListener listener : mExternalCallListeners) {
      listener.onExternalCallRemoved(call);
    }
  }

  /** Notifies listeners of changes to an external call. */
  private void notifyExternalCallUpdated(Call call) {
    if (!call.getDetails().hasProperty(CallCompat.Details.PROPERTY_IS_EXTERNAL_CALL)) {
      // A previous external call has been pulled and is now a regular call, so we will remove
      // it from the external call listener and ensure that the CallList is informed of the
      // change.
      onCallRemoved(call);

      for (ExternalCallListener listener : mExternalCallListeners) {
        listener.onExternalCallPulled(call);
      }
    } else {
      for (ExternalCallListener listener : mExternalCallListeners) {
        listener.onExternalCallUpdated(call);
      }
    }
  }

  /**
   * Defines events which the {@link ExternalCallList} exposes to interested components (e.g. {@link
   * com.android.incallui.ExternalCallNotifier ExternalCallNotifier}).
   */
  public interface ExternalCallListener {

    void onExternalCallAdded(Call call);

    void onExternalCallRemoved(Call call);

    void onExternalCallUpdated(Call call);

    void onExternalCallPulled(Call call);
  }
}
