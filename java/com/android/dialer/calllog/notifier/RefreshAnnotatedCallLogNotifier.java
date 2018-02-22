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

package com.android.dialer.calllog.notifier;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;
import com.android.dialer.calllog.constants.IntentNames;
import com.android.dialer.calllog.constants.SharedPrefKeys;
import com.android.dialer.common.LogUtil;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.storage.Unencrypted;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Notifies that a refresh of the annotated call log needs to be started/cancelled.
 *
 * <p>Methods in this class are usually invoked when the underlying data backing the annotated call
 * log change.
 *
 * <p>For example, a {@link android.database.ContentObserver} for the system call log can use {@link
 * #markDirtyAndNotify()} to force the annotated call log to be rebuilt.
 */
@Singleton
public class RefreshAnnotatedCallLogNotifier {

  private final Context appContext;
  private final SharedPreferences sharedPreferences;

  @Inject
  RefreshAnnotatedCallLogNotifier(
      @ApplicationContext Context appContext, @Unencrypted SharedPreferences sharedPreferences) {
    this.appContext = appContext;
    this.sharedPreferences = sharedPreferences;
  }

  /**
   * Mark the annotated call log as "dirty" and notify that it needs to be refreshed.
   *
   * <p>This will force a rebuild by skip checking whether the annotated call log is "dirty".
   */
  public void markDirtyAndNotify() {
    LogUtil.enterBlock("RefreshAnnotatedCallLogNotifier.markDirtyAndNotify");

    sharedPreferences.edit().putBoolean(SharedPrefKeys.FORCE_REBUILD, true).apply();
    notify(/* checkDirty = */ false);
  }

  /**
   * Notifies that the annotated call log needs to be refreshed.
   *
   * <p>Note that the notification is sent as a broadcast, which means the annotated call log might
   * not be refreshed if there is no corresponding receiver registered.
   *
   * @param checkDirty Whether to check if the annotated call log is "dirty" before proceeding to
   *     rebuild it.
   */
  public void notify(boolean checkDirty) {
    LogUtil.i("RefreshAnnotatedCallLogNotifier.notify", "checkDirty = %s", checkDirty);

    Intent intent = new Intent();
    intent.setAction(IntentNames.ACTION_REFRESH_ANNOTATED_CALL_LOG);
    intent.putExtra(IntentNames.EXTRA_CHECK_DIRTY, checkDirty);

    LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
  }

  /**
   * Notifies to cancel refreshing the annotated call log.
   *
   * <p>Note that this method does not guarantee the job to be cancelled. As the notification is
   * sent as a broadcast, please see the corresponding receiver for details about cancelling the
   * job.
   */
  public void cancel() {
    LogUtil.enterBlock("RefreshAnnotatedCallLogNotifier.cancel");

    Intent intent = new Intent();
    intent.setAction(IntentNames.ACTION_CANCEL_REFRESHING_ANNOTATED_CALL_LOG);

    LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
  }
}
