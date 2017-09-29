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

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.common.LogUtil;
import java.util.List;

/** {@link RecyclerView.Adapter} for the new voicemail call log fragment. */
final class NewVoicemailCallLogAdapter extends RecyclerView.Adapter<NewVoicemailCallLogViewHolder> {

  private final List<String> values;

  NewVoicemailCallLogAdapter(List<String> myDataset) {
    values = myDataset;
  }

  @Override
  public NewVoicemailCallLogViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {

    LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
    View v = inflater.inflate(R.layout.voicemail_call_log_entry, viewGroup, false);

    NewVoicemailCallLogViewHolder newVoicemailCallLogViewHolder =
        new NewVoicemailCallLogViewHolder(v);
    return newVoicemailCallLogViewHolder;
  }

  @Override
  public void onBindViewHolder(NewVoicemailCallLogViewHolder viewHolder, int position) {
    LogUtil.i("onBindViewHolder", "position" + position);
    String name = values.get(position);
    viewHolder.setPrimaryText(name);
  }

  @Override
  public int getItemCount() {
    return values.size();
  }
}
