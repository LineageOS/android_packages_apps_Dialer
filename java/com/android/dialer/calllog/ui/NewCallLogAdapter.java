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
package com.android.dialer.calllog.ui;

import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.CoalescedAnnotatedCallLog;

/** {@link RecyclerView.Adapter} for the new call log fragment. */
final class NewCallLogAdapter extends RecyclerView.Adapter<NewCallLogViewHolder> {

  private final Cursor cursor;
  private final int timestampIndex;

  NewCallLogAdapter(Cursor cursor) {
    this.cursor = cursor;
    timestampIndex = cursor.getColumnIndexOrThrow(CoalescedAnnotatedCallLog.TIMESTAMP);
  }

  @Override
  public NewCallLogViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
    return new NewCallLogViewHolder(
        LayoutInflater.from(viewGroup.getContext())
            .inflate(R.layout.new_call_log_entry, viewGroup, false));
  }

  @Override
  public void onBindViewHolder(NewCallLogViewHolder viewHolder, int position) {
    cursor.moveToPosition(position);
    long timestamp = cursor.getLong(timestampIndex);
    viewHolder.bind(timestamp);
  }

  @Override
  public int getItemCount() {
    return cursor.getCount();
  }
}
