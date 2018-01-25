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
 * limitations under the License.
 */

package com.android.dialer.voicemail.listui;

import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.android.dialer.voicemail.listui.error.VoicemailErrorMessage.Action;

/** ViewHolder for {@link NewVoicemailAdapter} to display voicemail error states. */
final class NewVoicemailAlertViewHolder extends ViewHolder {

  private final TextView voicemailErrorTitleTextView;
  private final TextView voicemailErrorDetailsTextView;
  private final Button primaryButton;
  private final Button secondaryButton;

  NewVoicemailAlertViewHolder(View view) {
    super(view);
    voicemailErrorTitleTextView = view.findViewById(R.id.voicemail_alert_header);
    voicemailErrorDetailsTextView = view.findViewById(R.id.voicemail_alert_details);
    primaryButton = view.findViewById(R.id.voicemail_alert_primary_button);
    secondaryButton = view.findViewById(R.id.voicemail_alert_primary_button);
  }

  void setTitle(CharSequence error) {
    voicemailErrorTitleTextView.setText(error);
  }

  void setDescription(CharSequence error) {
    voicemailErrorDetailsTextView.setText(error);
  }

  void setPrimaryButton(Action action) {
    primaryButton.setVisibility(View.VISIBLE);
    primaryButton.setText(action.getText());
    primaryButton.setOnClickListener(action.getListener());
  }

  void setSecondaryButton(Action action) {
    secondaryButton.setVisibility(View.VISIBLE);
    secondaryButton.setText(action.getText());
    secondaryButton.setOnClickListener(action.getListener());
  }
}
