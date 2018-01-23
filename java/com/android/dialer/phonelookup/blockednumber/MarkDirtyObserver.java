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

package com.android.dialer.phonelookup.blockednumber;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.support.annotation.MainThread;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.phonelookup.PhoneLookup.ContentObserverCallbacks;

/** Calls {@link ContentObserverCallbacks#markDirtyAndNotify(Context)} when the content changed */
class MarkDirtyObserver extends ContentObserver {

  private final Context appContext;
  private final ContentObserverCallbacks contentObserverCallbacks;

  MarkDirtyObserver(Context appContext, ContentObserverCallbacks contentObserverCallbacks) {
    super(ThreadUtil.getUiThreadHandler());
    this.appContext = appContext;
    this.contentObserverCallbacks = contentObserverCallbacks;
  }

  @MainThread
  @Override
  public void onChange(boolean selfChange, Uri uri) {
    Assert.isMainThread();
    LogUtil.enterBlock("SystemBlockedNumberPhoneLookup.FilteredNumberObserver.onChange");
    contentObserverCallbacks.markDirtyAndNotify(appContext);
  }
}
