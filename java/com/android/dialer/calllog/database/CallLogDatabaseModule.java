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

package com.android.dialer.calllog.database;

import dagger.Module;
import dagger.Provides;

/** Binds database dependencies. */
@Module
public class CallLogDatabaseModule {

  @Provides
  @AnnotatedCallLogMaxRows
  static int provideMaxRows() {
    /*
     * We sometimes run queries where we potentially pass every ID into a where clause using the
     * (?,?,?,...) syntax. The maximum number of host parameters is 999, so that's the maximum size
     * this table can be. See https://www.sqlite.org/limits.html for more details.
     */
    return 999;
  }
}
