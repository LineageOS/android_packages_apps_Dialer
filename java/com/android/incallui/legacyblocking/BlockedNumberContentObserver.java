/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.incallui.legacyblocking;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.CallLog;
import android.support.annotation.NonNull;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.AsyncTaskExecutor;
import com.android.dialer.common.concurrent.AsyncTaskExecutors;
import com.android.dialer.util.PermissionsUtil;
import java.util.Objects;

/**
 * Observes the {@link CallLog} to delete the CallLog entry for a blocked call after it is added.
 * Automatically de-registers itself {@link #TIMEOUT_MS} ms after registration or if the entry is
 * found and deleted.
 */
public class BlockedNumberContentObserver extends ContentObserver
    implements DeleteBlockedCallTask.Listener {

  /**
   * The time after which a {@link BlockedNumberContentObserver} will be automatically unregistered.
   */
  public static final int TIMEOUT_MS = 5000;

  @NonNull private final Context context;
  @NonNull private final Handler handler;
  private final String number;
  private final long timeAddedMillis;
  private final Runnable timeoutRunnable =
      new Runnable() {
        @Override
        public void run() {
          unregister();
        }
      };

  private final AsyncTaskExecutor asyncTaskExecutor = AsyncTaskExecutors.createThreadPoolExecutor();

  /**
   * Creates the BlockedNumberContentObserver to delete the new {@link CallLog} entry from the given
   * blocked number.
   *
   * @param number The blocked number.
   * @param timeAddedMillis The time at which the call from the blocked number was placed.
   */
  public BlockedNumberContentObserver(
      @NonNull Context context, @NonNull Handler handler, String number, long timeAddedMillis) {
    super(handler);
    this.context = Objects.requireNonNull(context, "context").getApplicationContext();
    this.handler = Objects.requireNonNull(handler);
    this.number = number;
    this.timeAddedMillis = timeAddedMillis;
  }

  @Override
  public void onChange(boolean selfChange) {
    LogUtil.i(
        "BlockedNumberContentObserver.onChange",
        "attempting to remove call log entry from blocked number");
    asyncTaskExecutor.submit(
        DeleteBlockedCallTask.IDENTIFIER,
        new DeleteBlockedCallTask(context, this, number, timeAddedMillis));
  }

  @Override
  public void onDeleteBlockedCallTaskComplete(boolean didFindEntry) {
    if (didFindEntry) {
      unregister();
    }
  }

  /**
   * Registers this {@link ContentObserver} to listen for changes to the {@link CallLog}. If the
   * CallLog entry is not found before {@link #TIMEOUT_MS}, this ContentObserver automatically
   * un-registers itself.
   */
  public void register() {
    LogUtil.i("BlockedNumberContentObserver.register", null);
    if (PermissionsUtil.hasCallLogReadPermissions(context)
        && PermissionsUtil.hasCallLogWritePermissions(context)) {
      context.getContentResolver().registerContentObserver(CallLog.CONTENT_URI, true, this);
      handler.postDelayed(timeoutRunnable, TIMEOUT_MS);
    } else {
      LogUtil.w("BlockedNumberContentObserver.register", "no call log read/write permissions.");
    }
  }

  private void unregister() {
    LogUtil.i("BlockedNumberContentObserver.unregister", null);
    handler.removeCallbacks(timeoutRunnable);
    context.getContentResolver().unregisterContentObserver(this);
  }
}
