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
package com.android.dialer.voicemail.listui;

import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.common.LogUtil;
import com.android.dialer.time.Clock;

/** {@link RecyclerView.Adapter} for the new voicemail call log fragment. */
final class NewVoicemailAdapter extends RecyclerView.Adapter<NewVoicemailViewHolder> {

  private final Cursor cursor;
  private final Clock clock;

  /** @param cursor whose projection is {@link VoicemailCursorLoader.VOICEMAIL_COLUMNS} */
  NewVoicemailAdapter(Cursor cursor, Clock clock) {
    this.cursor = cursor;
    this.clock = clock;
  }

  @Override
  public NewVoicemailViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
    View view = inflater.inflate(R.layout.new_voicemail_entry, viewGroup, false);
    NewVoicemailViewHolder newVoicemailViewHolder = new NewVoicemailViewHolder(view, clock);
    return newVoicemailViewHolder;
  }

  @Override
  public void onBindViewHolder(NewVoicemailViewHolder viewHolder, int position) {
    LogUtil.i("onBindViewHolder", "position" + position);
    cursor.moveToPosition(position);
    viewHolder.bind(cursor);
  }

  @Override
  public int getItemCount() {
    return cursor.getCount();
  }
}
