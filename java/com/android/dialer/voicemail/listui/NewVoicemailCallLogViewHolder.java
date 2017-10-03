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
import android.view.View;
import android.widget.TextView;
import com.android.dialer.voicemail.datasources.VoicemailData;

/** {@link RecyclerView.ViewHolder} for the new voicemail call log. */
final class NewVoicemailCallLogViewHolder extends RecyclerView.ViewHolder {

  private final TextView primaryTextView;
  private final TextView secondaryTextView;
  private final TextView transcriptionTextView;

  NewVoicemailCallLogViewHolder(View view) {
    super(view);
    primaryTextView = (TextView) view.findViewById(R.id.primary_text);
    secondaryTextView = (TextView) view.findViewById(R.id.secondary_text);
    transcriptionTextView = (TextView) view.findViewById(R.id.transcription_text);
  }

  void bind(VoicemailData voicemailData) {
    primaryTextView.setText(voicemailData.name());
    secondaryTextView.setText(getVoicemailLocationDateAndDuration(voicemailData));
    transcriptionTextView.setText(voicemailData.transcription());
  }

  private String getVoicemailLocationDateAndDuration(VoicemailData voicemailData) {
    return voicemailData.location()
        + " · "
        + voicemailData.date()
        + " · "
        + voicemailData.duration();
  }
}
