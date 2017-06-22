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

package com.android.dialer.enrichedcall.simulator;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

/** ViewHolder for an Enriched call session. */
class SessionViewHolder extends RecyclerView.ViewHolder {

  private final TextView sessionStringView;

  SessionViewHolder(View view) {
    super(view);
    sessionStringView = view.findViewById(R.id.session_string);
  }

  void updateSession(@NonNull String sessionString) {
    sessionStringView.setText(sessionString);
  }
}
