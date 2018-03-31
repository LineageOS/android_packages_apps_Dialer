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

package com.android.dialer.calllog.ui.menu;

import android.content.Context;
import android.provider.CallLog.Calls;
import android.view.View;
import com.android.dialer.calllog.CallLogComponent;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.common.concurrent.DefaultFutureCallback;
import com.android.dialer.historyitemactions.HistoryItemActionBottomSheet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

/** Handles configuration of the bottom sheet menus for call log entries. */
public final class NewCallLogMenu {

  /** Creates and returns the OnClickListener which opens the menu for the provided row. */
  public static View.OnClickListener createOnClickListener(Context context, CoalescedRow row) {
    return view -> {
      HistoryItemActionBottomSheet.show(
          context, BottomSheetHeader.fromRow(context, row), Modules.fromRow(context, row));

      // If the user opens the bottom sheet for an unread call, clear the notifications and make the
      // row not bold immediately. To do this, mark all of the calls in group as read.
      if (!row.getIsRead() && row.getCallType() == Calls.MISSED_TYPE) {
        Futures.addCallback(
            CallLogComponent.get(context)
                .getClearMissedCalls()
                .clearBySystemCallLogId(row.getCoalescedIds().getCoalescedIdList()),
            new DefaultFutureCallback<>(),
            MoreExecutors.directExecutor());
      }
    };
  }
}
