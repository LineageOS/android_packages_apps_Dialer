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

package com.android.dialer.calllog.ui.menu;

import android.Manifest.permission;
import android.content.Context;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import com.android.dialer.CoalescedIds;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.database.Selection;
import com.android.dialer.historyitemactions.HistoryItemActionModule;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** {@link HistoryItemActionModule} for deleting a call log item in the new call log. */
final class DeleteCallLogItemModule implements HistoryItemActionModule {
  private static final String TAG = DeleteCallLogItemModule.class.getName();

  private final Context context;
  private final CoalescedIds coalescedIds;

  DeleteCallLogItemModule(Context context, CoalescedIds coalescedIds) {
    this.context = context;
    this.coalescedIds = coalescedIds;
  }

  @Override
  public int getStringId() {
    return R.string.delete;
  }

  @Override
  public int getDrawableId() {
    return R.drawable.quantum_ic_delete_vd_theme_24;
  }

  @Override
  public boolean onClick() {
    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new CallLogItemDeletionWorker(context))
        .build()
        .executeSerial(coalescedIds);

    Logger.get(context).logImpression(DialerImpression.Type.USER_DELETED_CALL_LOG_ITEM);
    return true;
  }

  /**
   * A {@link Worker} that deletes a call log item.
   *
   * <p>It takes as input the IDs of all call log records that are coalesced into the item to be
   * deleted.
   */
  private static class CallLogItemDeletionWorker implements Worker<CoalescedIds, Void> {
    private final WeakReference<Context> contextWeakReference;

    CallLogItemDeletionWorker(Context context) {
      contextWeakReference = new WeakReference<>(context);
    }

    @Nullable
    @Override
    @RequiresPermission(value = permission.WRITE_CALL_LOG)
    public Void doInBackground(CoalescedIds coalescedIds) throws Throwable {
      Context context = contextWeakReference.get();
      if (context == null) {
        LogUtil.e(TAG, "Unable to delete an call log item due to null context.");
        return null;
      }

      Selection selection =
          Selection.builder()
              .and(Selection.column(CallLog.Calls._ID).in(getCallLogIdsAsStrings(coalescedIds)))
              .build();
      int numRowsDeleted =
          context
              .getContentResolver()
              .delete(Calls.CONTENT_URI, selection.getSelection(), selection.getSelectionArgs());
      context
          .getContentResolver()
          .notifyChange(Calls.CONTENT_URI, null);

      if (numRowsDeleted != coalescedIds.getCoalescedIdCount()) {
        LogUtil.e(
            TAG,
            "Deleting call log item is unsuccessful. %d of %d rows are deleted.",
            numRowsDeleted,
            coalescedIds.getCoalescedIdCount());
      }

      return null;
    }

    private static List<String> getCallLogIdsAsStrings(CoalescedIds coalescedIds) {
      Assert.checkArgument(coalescedIds.getCoalescedIdCount() > 0);

      List<String> idStrings = new ArrayList<>(coalescedIds.getCoalescedIdCount());

      for (long callLogId : coalescedIds.getCoalescedIdList()) {
        idStrings.add(String.valueOf(callLogId));
      }

      return idStrings;
    }
  }
}
