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

package com.android.dialer.calllog;

import com.android.dialer.calllog.datasources.CallLogDataSource;
import com.android.dialer.calllog.datasources.DataSources;
import com.android.dialer.calllog.datasources.contacts.ContactsDataSource;
import com.android.dialer.calllog.datasources.systemcalllog.SystemCallLogDataSource;
import dagger.Module;
import dagger.Provides;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Dagger module which satisfies call log dependencies. */
@Module
public abstract class CallLogModule {

  @Provides
  static DataSources provideCallLogDataSources(
      SystemCallLogDataSource systemCallLogDataSource, ContactsDataSource contactsDataSource) {
    // System call log must be first, see getDataSourcesExcludingSystemCallLog below.
    List<CallLogDataSource> allDataSources =
        Collections.unmodifiableList(Arrays.asList(systemCallLogDataSource, contactsDataSource));
    return new DataSources() {
      @Override
      public SystemCallLogDataSource getSystemCallLogDataSource() {
        return systemCallLogDataSource;
      }

      @Override
      public List<CallLogDataSource> getDataSourcesIncludingSystemCallLog() {
        return allDataSources;
      }

      @Override
      public List<CallLogDataSource> getDataSourcesExcludingSystemCallLog() {
        return allDataSources.subList(1, allDataSources.size());
      }
    };
  }
}
