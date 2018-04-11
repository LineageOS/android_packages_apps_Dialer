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

package com.android.dialer.speeddial;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.util.ArraySet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.precall.PreCall;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import java.util.List;
import java.util.Set;

/** Disambiguation dialog for favorite contacts in {@link SpeedDialFragment}. */
public class DisambigDialog extends DialogFragment {

  private final Set<String> phoneNumbers = new ArraySet<>();

  @VisibleForTesting public List<Channel> channels;
  @VisibleForTesting public LinearLayout container;

  /** Show a disambiguation dialog for a starred contact without a favorite communication avenue. */
  public static DisambigDialog show(List<Channel> channels, FragmentManager manager) {
    DisambigDialog dialog = new DisambigDialog();
    dialog.channels = channels;
    dialog.show(manager, null);
    return dialog;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    LayoutInflater inflater = getActivity().getLayoutInflater();
    // TODO(calderwoodra): set max height of the scrollview. Might need to override onMeasure.
    View view = inflater.inflate(R.layout.disambig_dialog_layout, null, false);
    container = view.findViewById(R.id.communication_avenue_container);
    insertOptions(container.findViewById(R.id.communication_avenue_container), channels);
    return new AlertDialog.Builder(getActivity()).setView(view).create();
  }

  @Override
  public void onPause() {
    super.onPause();
    // TODO(calderwoodra): for simplicity, just dismiss the dialog on configuration change and
    // consider changing this later.
    dismiss();
  }

  /**
   * Inflates and inserts the following in the dialog:
   *
   * <ul>
   *   <li>Header for each unique phone number
   *   <li>Clickable video option if the phone number is video reachable (ViLTE, Duo)
   *   <li>Clickable voice option
   * </ul>
   */
  private void insertOptions(LinearLayout container, List<Channel> channels) {
    for (Channel channel : channels) {
      // TODO(calderwoodra): use fuzzy number matcher
      if (phoneNumbers.add(channel.number())) {
        insertHeader(container, channel.number(), channel.label());
      }
      insertOption(container, channel);
    }
  }

  private void insertHeader(LinearLayout container, String number, String phoneType) {
    View view =
        getActivity()
            .getLayoutInflater()
            .inflate(R.layout.disambig_option_header_layout, container, false);
    ((TextView) view.findViewById(R.id.disambig_header_phone_type)).setText(phoneType);
    ((TextView) view.findViewById(R.id.disambig_header_phone_number)).setText(number);
    container.addView(view);
  }

  /** Inserts a group of options for a specific phone number. */
  private void insertOption(LinearLayout container, Channel channel) {
    View view =
        getActivity()
            .getLayoutInflater()
            .inflate(R.layout.disambig_option_layout, container, false);
    if (channel.isVideoTechnology()) {
      View videoOption = view.findViewById(R.id.option_container);
      videoOption.setOnClickListener(v -> onVideoOptionClicked(channel));
      videoOption.setContentDescription(
          getActivity().getString(R.string.disambig_option_video_call));
      ((ImageView) view.findViewById(R.id.disambig_option_image))
          .setImageResource(R.drawable.quantum_ic_videocam_vd_theme_24);
      ((TextView) view.findViewById(R.id.disambig_option_text))
          .setText(R.string.disambig_option_video_call);
    } else {
      View voiceOption = view.findViewById(R.id.option_container);
      voiceOption.setOnClickListener(v -> onVoiceOptionClicked(channel));
      voiceOption.setContentDescription(
          getActivity().getString(R.string.disambig_option_voice_call));
      ((ImageView) view.findViewById(R.id.disambig_option_image))
          .setImageResource(R.drawable.quantum_ic_phone_vd_theme_24);
      ((TextView) view.findViewById(R.id.disambig_option_text))
          .setText(R.string.disambig_option_voice_call);
    }
    container.addView(view);
  }

  private void onVideoOptionClicked(Channel channel) {
    // TODO(calderwoodra): save this option if remember is checked
    // TODO(calderwoodra): place a duo call if possible
    PreCall.start(
        getContext(),
        new CallIntentBuilder(channel.number(), CallInitiationType.Type.SPEED_DIAL)
            .setIsVideoCall(true));
    dismiss();
  }

  private void onVoiceOptionClicked(Channel channel) {
    // TODO(calderwoodra): save this option if remember is checked
    PreCall.start(
        getContext(), new CallIntentBuilder(channel.number(), CallInitiationType.Type.SPEED_DIAL));
    dismiss();
  }
}
