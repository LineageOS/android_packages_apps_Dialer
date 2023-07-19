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

package com.android.dialer.metrics;

import android.app.Application;
import android.support.annotation.Nullable;

/** Logs metrics. */
public interface Metrics {

  // Events related to refreshing the annotated call log.
  String NEW_CALL_LOG_COALESCE = "NewCallLog.Coalesce";
  String ANNOTATED_CALL_LOG_NOT_DIRTY = "RefreshAnnotatedCallLogReceiver.NotDirty";
  String ANNOTATED_CALL_LOG_CHANGES_NEEDED = "RefreshAnnotatedCallLogReceiver.ChangesNeeded";
  String ANNOTATED_LOG_NO_CHANGES_NEEDED = "RefreshAnnotatedCallLogReceiver.NoChangesNeeded";
  String ANNOTATED_CALL_LOG_FORCE_REFRESH_CHANGES_NEEDED =
      "RefreshAnnotatedCallLogReceiver.ForceRefreshChangesNeeded";
  String NEW_CALL_LOG_FORCE_REFRESH_NO_CHANGES_NEEDED =
      "RefreshAnnotatedCallLogReceiver.ForceRefreshNoChangesNeeded";

  String INITIAL_FILL_EVENT_NAME = "RefreshAnnotatedCallLog.Initial.Fill";
  String INITIAL_ON_SUCCESSFUL_FILL_EVENT_NAME = "RefreshAnnotatedCallLog.Initial.OnSuccessfulFill";
  String INITIAL_APPLY_MUTATIONS_EVENT_NAME = "RefreshAnnotatedCallLog.Initial.ApplyMutations";

  String IS_DIRTY_EVENT_NAME = "RefreshAnnotatedCallLog.IsDirty";
  String FILL_EVENT_NAME = "RefreshAnnotatedCallLog.Fill";
  String ON_SUCCESSFUL_FILL_EVENT_NAME = "RefreshAnnotatedCallLog.OnSuccessfulFill";
  String APPLY_MUTATIONS_EVENT_NAME = "RefreshAnnotatedCallLog.ApplyMutations";

  // These templates are prefixed with a CallLogDataSource or PhoneLookup simple class name.
  String INITIAL_FILL_TEMPLATE = "%s.Initial.Fill";
  String INITIAL_GET_MOST_RECENT_INFO_TEMPLATE = "%s.Initial.GetMostRecentInfo";
  String INITIAL_ON_SUCCESSFUL_FILL_TEMPLATE = "%s.Initial.OnSuccessfulFill";
  String INITIAL_ON_SUCCESSFUL_BULK_UPDATE_TEMPLATE = "%s.Initial.OnSuccessfulBulkUpdate";

  String IS_DIRTY_TEMPLATE = "%s.IsDirty";
  String FILL_TEMPLATE = "%s.Fill";
  String GET_MOST_RECENT_INFO_TEMPLATE = "%s.GetMostRecentInfo";
  String ON_SUCCESSFUL_FILL_TEMPLATE = "%s.OnSuccessfulFill";
  String ON_SUCCESSFUL_BULK_UPDATE_TEMPLATE = "%s.OnSuccessfulBulkUpdate";
  String LOOKUP_FOR_CALL_TEMPLATE = "%s.LookupForCall";
  String LOOKUP_FOR_NUMBER_TEMPLATE = "%s.LookupForNumber";

  /** Initiazer for metrics. */
  interface Initializer {
    /** Initialize metrics for the application . */
    void initialize(Application application);
  }
}
