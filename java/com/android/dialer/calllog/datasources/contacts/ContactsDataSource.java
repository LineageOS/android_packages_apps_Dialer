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

package com.android.dialer.calllog.datasources.contacts;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;
import com.android.dialer.calllog.database.CallLogMutations;
import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.common.Assert;
import javax.inject.Inject;

/** Responsible for maintaining the contacts related columns in the annotated call log. */
public final class ContactsDataSource implements CallLogDataSource {

  @Inject
  public ContactsDataSource() {}

  @WorkerThread
  @Override
  public boolean isDirty(Context appContext) {
    Assert.isWorkerThread();

    // TODO: Implementation.
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
    // TODO: Implementation.
  }

  @MainThread
  @Override
  public void registerContentObservers(
      Context appContext, ContentObserverCallbacks contentObserverCallbacks) {
    // TODO: Guard against missing permissions during callback registration.
  }
}
