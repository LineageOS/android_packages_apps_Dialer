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
import android.view.View;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** {@link RecyclerView.ViewHolder} for the new call log. */
final class NewCallLogViewHolder extends RecyclerView.ViewHolder {

  // TODO(zachh): Format correctly using current locale.
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

  private final TextView primaryTextView;
  private final TextView secondaryTextView;

  NewCallLogViewHolder(View view) {
    super(view);
    primaryTextView = view.findViewById(R.id.primary_text);
    secondaryTextView = view.findViewById(R.id.secondary_text);
  }

  /** @param cursor a cursor from {@link CoalescedAnnotatedCallLogCursorLoader}. */
  void bind(Cursor cursor) {
    CoalescedAnnotatedCallLogCursorLoader.Row row =
        new CoalescedAnnotatedCallLogCursorLoader.Row(cursor);
    primaryTextView.setText(row.primaryText());
    secondaryTextView.setText(dateFormat.format(row.timestamp()));
  }
}
