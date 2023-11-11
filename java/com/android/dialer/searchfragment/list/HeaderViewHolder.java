/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.dialer.searchfragment.list;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.dialer.R;

/** ViewHolder for header rows in {@link NewSearchFragment}. */
final class HeaderViewHolder extends RecyclerView.ViewHolder {

  private final TextView header;

  HeaderViewHolder(View view) {
    super(view);
    header = view.findViewById(R.id.header);
  }

  public void setHeader(String header) {
    this.header.setText(header);
  }
}
