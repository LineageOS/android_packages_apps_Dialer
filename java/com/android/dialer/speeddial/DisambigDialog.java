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

package com.android.dialer.speeddial;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.ArraySet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.android.dialer.R;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DefaultFutureCallback;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.precall.PreCall;
import com.android.dialer.speeddial.database.SpeedDialEntry;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.android.dialer.speeddial.database.SpeedDialEntryDatabaseHelper;
import com.android.dialer.speeddial.loader.SpeedDialUiItem;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Set;

/** Disambiguation dialog for favorite contacts in {@link SpeedDialFragment}. */
public class DisambigDialog extends DialogFragment {

  private static final String FRAGMENT_TAG = "disambig_dialog";
  private final Set<String> phoneNumbers = new ArraySet<>();

  private SpeedDialUiItem speedDialUiItem;
  private  List<Channel> channels;
  private LinearLayout container;
  private CheckBox rememberThisChoice;

  /** Show a disambiguation dialog for a starred contact without a favorite communication avenue. */
  public static DisambigDialog show(SpeedDialUiItem speedDialUiItem, FragmentManager manager) {
    DisambigDialog dialog = new DisambigDialog();
    dialog.speedDialUiItem = speedDialUiItem;
    dialog.channels = speedDialUiItem.channels();
    dialog.show(manager, FRAGMENT_TAG);
    return dialog;
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    LayoutInflater inflater = getActivity().getLayoutInflater();
    // TODO(calderwoodra): set max height of the scrollview. Might need to override onMeasure.
    View view = inflater.inflate(R.layout.disambig_dialog_layout, null, false);
    container = view.findViewById(R.id.communication_avenue_container);
    rememberThisChoice = view.findViewById(R.id.remember_this_choice_checkbox);
    insertOptions(container.findViewById(R.id.communication_avenue_container), channels);
    return new AlertDialog.Builder(getActivity()).setView(view).create();
  }

  @Override
  public void onResume() {
    super.onResume();
    getDialog().getWindow().setBackgroundDrawable(
            getActivity().getResources().getDrawable(R.drawable.dialog_background,
                    getActivity().getTheme()));
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
      insertOption(container, channel);
    }
  }

  private void insertOption(LinearLayout container, Channel channel) {
    View view = getActivity()
            .getLayoutInflater()
            .inflate(R.layout.disambig_option_layout, container, false);
    View option = view.findViewById(R.id.option_container);
    if (channel.isVideoTechnology()) {
      option.setOnClickListener(v -> onVideoOptionClicked(channel));
      option.setContentDescription(
              getActivity().getString(R.string.disambig_option_video_call));
      ((ImageView) view.findViewById(R.id.disambig_option_image))
              .setImageResource(R.drawable.quantum_ic_videocam_vd_theme_24);
    } else {
      option.setOnClickListener(v -> onVoiceOptionClicked(channel));
      option.setContentDescription(
              getActivity().getString(R.string.disambig_option_voice_call));
      ((ImageView) view.findViewById(R.id.disambig_option_image))
              .setImageResource(R.drawable.quantum_ic_phone_vd_theme_24);
    }
    ((TextView) option.findViewById(R.id.speed_dial_label)).setText(channel.label());
    ((TextView) option.findViewById(R.id.speed_dial_number)).setText(channel.number());
    container.addView(view);
  }

  private void onVideoOptionClicked(Channel channel) {
    if (rememberThisChoice.isChecked()) {
      setDefaultChannel(getContext().getApplicationContext(), speedDialUiItem, channel);
    }

    PreCall.start(
        getContext(),
        new CallIntentBuilder(channel.number(), CallInitiationType.Type.SPEED_DIAL_DISAMBIG_DIALOG)
            .setAllowAssistedDial(true)
            .setIsVideoCall(true));
    dismiss();
  }

  private void onVoiceOptionClicked(Channel channel) {
    if (rememberThisChoice.isChecked()) {
      setDefaultChannel(getContext().getApplicationContext(), speedDialUiItem, channel);
    }

    PreCall.start(
        getContext(),
        new CallIntentBuilder(channel.number(), CallInitiationType.Type.SPEED_DIAL_DISAMBIG_DIALOG)
            .setAllowAssistedDial(true));
    dismiss();
  }

  private static void setDefaultChannel(Context appContext, SpeedDialUiItem item, Channel channel) {
    LogUtil.enterBlock("DisambigDialog.setDefaultChannel");
    ListenableFuture<Void> future =
        DialerExecutorComponent.get(appContext)
            .backgroundExecutor()
            .submit(
                () -> {
                  updateDatabaseEntry(appContext, item, channel);
                  return null;
                });
    Futures.addCallback(
        future,
        new DefaultFutureCallback<>(),
        DialerExecutorComponent.get(appContext).backgroundExecutor());
  }

  @WorkerThread
  private static void updateDatabaseEntry(
      Context appContext, SpeedDialUiItem item, Channel channel) {
    Assert.isWorkerThread();
    SpeedDialEntry entry =
        SpeedDialEntry.builder()
            .setId(item.speedDialEntryId())
            .setContactId(item.contactId())
            .setLookupKey(item.lookupKey())
            .setDefaultChannel(channel)
            .build();
    new SpeedDialEntryDatabaseHelper(appContext).update(ImmutableList.of(entry));
  }
}
