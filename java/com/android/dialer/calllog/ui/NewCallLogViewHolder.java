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

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** {@link RecyclerView.ViewHolder} for the new call log. */
final class NewCallLogViewHolder extends RecyclerView.ViewHolder {

  // TODO: Format correctly using current locale.
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

  private final TextView contactNameView;
  private final TextView timestampView;

  NewCallLogViewHolder(View view) {
    super(view);
    contactNameView = view.findViewById(R.id.contact_name);
    timestampView = view.findViewById(R.id.timestamp);
  }

  void bind(long timestamp) {
    contactNameView.setText("Contact Name Placeholder");
    timestampView.setText(dateFormat.format(timestamp));
  }
}
