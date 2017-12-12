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
 * limitations under the License.
 */

package com.android.dialer.voicemail.listui;

import android.support.annotation.StringRes;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.widget.TextView;

/** ViewHolder for {@link NewVoicemailAdapter} to display "Today" or "Older" divider row. */
final class NewVoicemailHeaderViewHolder extends ViewHolder {

  private final TextView headerTextView;

  NewVoicemailHeaderViewHolder(View view) {
    super(view);
    headerTextView = view.findViewById(R.id.new_voicemail_header_text);
  }

  void setHeader(@StringRes int header) {
    headerTextView.setText(header);
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  String getHeaderText() {
    return headerTextView.getText().toString();
  }
}
