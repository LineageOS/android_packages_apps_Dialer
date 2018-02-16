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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Returns a {@link FutureCallback} which does nothing on success and crashes the application on
 * failure.
 *
 * <p>You generally shouldn't use this for futures which should be tied to UI, for those use {@link
 * UiListener}.
 *
 * <p>Can be safely used with {@link MoreExecutors#directExecutor()}
 */
public final class DefaultFutureCallback<T> implements FutureCallback<T> {

  @Override
  public void onSuccess(T unused) {}

  @Override
  public void onFailure(Throwable throwable) {
    ThreadUtil.getUiThreadHandler()
        .post(
            () -> {
              throw new RuntimeException(throwable);
            });
  }
}
