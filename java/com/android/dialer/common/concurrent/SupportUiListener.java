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

package com.android.dialer.common.concurrent;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.FailureListener;
import com.android.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;


/**
 * A headless fragment for use in UI components that interact with ListenableFutures.
 *
 * <p>Callbacks are only executed if the UI component is still alive.
 *
 * <p>Example usage: <code><pre>
 * public class MyActivity extends AppCompatActivity {
 *
 *   private SupportUiListener&lt;MyOutputType&gt uiListener;
 *
 *   public void onCreate(Bundle bundle) {
 *     super.onCreate(bundle);
 *
 *     // Must be called in onCreate!
 *     uiListener = DialerExecutorComponent.get(context).createUiListener(fragmentManager, taskId);
 *   }
 *
 *   private void onSuccess(MyOutputType output) { ... }
 *   private void onFailure(Throwable throwable) { ... }
 *
 *   private void userDidSomething() {
 *     ListenableFuture&lt;MyOutputType&gt; future = callSomeMethodReturningListenableFuture(input);
 *     uiListener.listen(this, future, this::onSuccess, this::onFailure);
 *   }
 * }
 * </pre></code>
 */
public class SupportUiListener<OutputT> extends Fragment {

  private CallbackWrapper<OutputT> callbackWrapper;

  @MainThread
  static <OutputT> SupportUiListener<OutputT> create(
      FragmentManager fragmentManager, String taskId) {
    @SuppressWarnings("unchecked")
    SupportUiListener<OutputT> uiListener =
        (SupportUiListener<OutputT>) fragmentManager.findFragmentByTag(taskId);

    if (uiListener == null) {
      LogUtil.i("SupportUiListener.create", "creating new SupportUiListener for " + taskId);
      uiListener = new SupportUiListener<>();
      // When launching an activity with the screen off, its onSaveInstanceState() is called before
      // its fragments are created, which means we can't use commit() and need to use
      // commitAllowingStateLoss(). This is not a problem for SupportUiListener which saves no
      // state.
      fragmentManager.beginTransaction().add(uiListener, taskId).commitAllowingStateLoss();
    }
    return uiListener;
  }

  /**
   * Adds the specified listeners to the provided future.
   *
   * <p>The listeners are not called if the UI component this {@link SupportUiListener} is declared
   * in is dead.
   */
  @MainThread
  public void listen(
      Context context,
      @NonNull ListenableFuture<OutputT> future,
      @NonNull SuccessListener<OutputT> successListener,
      @NonNull FailureListener failureListener) {
    callbackWrapper =
        new CallbackWrapper<>(Assert.isNotNull(successListener), Assert.isNotNull(failureListener));
    Futures.addCallback(
        Assert.isNotNull(future),
        callbackWrapper,
        DialerExecutorComponent.get(context).uiExecutor());
  }

  private static class CallbackWrapper<OutputT> implements FutureCallback<OutputT> {
    private SuccessListener<OutputT> successListener;
    private FailureListener failureListener;

    private CallbackWrapper(
        SuccessListener<OutputT> successListener, FailureListener failureListener) {
      this.successListener = successListener;
      this.failureListener = failureListener;
    }

    @Override
    public void onSuccess(@Nullable OutputT output) {
      if (successListener == null) {
        LogUtil.i("SupportUiListener.runTask", "task succeeded but UI is dead");
      } else {
        successListener.onSuccess(output);
      }
    }

    @Override
    public void onFailure(Throwable throwable) {
      LogUtil.e("SupportUiListener.runTask", "task failed", throwable);
      if (failureListener == null) {
        LogUtil.i("SupportUiListener.runTask", "task failed but UI is dead");
      } else {
        failureListener.onFailure(throwable);
      }
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setRetainInstance(true);
    // Note: We use commitAllowingStateLoss when attaching the fragment so it may not be safe to
    // read savedInstanceState in all situations. (But it's not anticipated that this fragment
    // should need to rely on saved state.)
  }

  @Override
  public void onDetach() {
    super.onDetach();
    LogUtil.enterBlock("SupportUiListener.onDetach");
    if (callbackWrapper != null) {
      callbackWrapper.successListener = null;
      callbackWrapper.failureListener = null;
    }
  }
}

