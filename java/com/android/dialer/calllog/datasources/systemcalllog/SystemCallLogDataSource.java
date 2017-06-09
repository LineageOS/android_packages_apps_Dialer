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

package com.android.dialer.calllog.datasources.systemcalllog;

import android.content.Context;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.provider.CallLog;
import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;
import com.android.dialer.calllog.database.CallLogMutations;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.util.PermissionsUtil;
import javax.inject.Inject;

/**
 * Responsible for defining the rows in the annotated call log and maintaining the columns in it
 * which are derived from the system call log.
 */
public class SystemCallLogDataSource implements CallLogDataSource {

  @Inject
  public SystemCallLogDataSource() {}

  @MainThread
  @Override
  public void registerContentObservers(
      Context appContext, ContentObserverCallbacks contentObserverCallbacks) {
    Assert.isMainThread();

    if (!PermissionsUtil.hasCallLogReadPermissions(appContext)) {
      LogUtil.i("SystemCallLogDataSource.registerContentObservers", "no call log permissions");
      return;
    }

    appContext
        .getContentResolver()
        .registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            new CallLogObserver(
                ThreadUtil.getUiThreadHandler(), appContext, contentObserverCallbacks));
  }

  @WorkerThread
  @Override
  public boolean isDirty(Context appContext) {
    Assert.isWorkerThread();

    /*
     * The system call log has a last updated timestamp, but deletes are physical (the "deleted"
     * column is unused). This means that we can't detect deletes without scanning the entire table,
     * which would be too slow. So, we just rely on content observers to trigger rebuilds when any
     * change is made to the system call log.
     */
    return false;
  }

  @WorkerThread
  @Override
  public void fill(
      Context appContext,
      SQLiteDatabase readableDatabase,
      long lastRebuildTimeMillis,
      CallLogMutations mutations) {
    Assert.isWorkerThread();

    // This data source should always run first so the mutations should always be empty.
    Assert.checkState(mutations.isEmpty());

    // TODO: Implementation.
  }

  private static class CallLogObserver extends ContentObserver {
    private final Context appContext;
    private final ContentObserverCallbacks contentObserverCallbacks;

    CallLogObserver(
        Handler handler, Context appContext, ContentObserverCallbacks contentObserverCallbacks) {
      super(handler);
      this.appContext = appContext;
      this.contentObserverCallbacks = contentObserverCallbacks;
    }

    @MainThread
    @Override
    public void onChange(boolean selfChange, Uri uri) {
      Assert.isMainThread();
      LogUtil.enterBlock("SystemCallLogDataSource.CallLogObserver.onChange");
      super.onChange(selfChange, uri);

      /*
       * The system call log has a last updated timestamp, but deletes are physical (the "deleted"
       * column is unused). This means that we can't detect deletes without scanning the entire
       * table, which would be too slow. So, we just rely on content observers to trigger rebuilds
       * when any change is made to the system call log.
       */
      contentObserverCallbacks.markDirtyAndNotify(appContext);
    }
  }
}
