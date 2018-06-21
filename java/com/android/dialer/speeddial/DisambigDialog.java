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
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.ArraySet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DefaultFutureCallback;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
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

  @VisibleForTesting public static final String FRAGMENT_TAG = "disambig_dialog";
  private final Set<String> phoneNumbers = new ArraySet<>();

  private SpeedDialUiItem speedDialUiItem;
  @VisibleForTesting public List<Channel> channels;
  @VisibleForTesting public LinearLayout container;
  @VisibleForTesting public CheckBox rememberThisChoice;

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
    getDialog()
        .getWindow()
        .setLayout(
            getContext().getResources().getDimensionPixelSize(R.dimen.disambig_dialog_width),
            LayoutParams.WRAP_CONTENT);
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
        if (phoneNumbers.size() != 1) {
          insertDivider(container);
        }
        insertHeader(container, channel.number(), channel.label());
      }
      insertOption(container, channel);
    }
  }

  private void insertDivider(LinearLayout container) {
    View view =
        getActivity()
            .getLayoutInflater()
            .inflate(R.layout.disambig_dialog_divider, container, false);
    container.addView(view);
  }

  private void insertHeader(LinearLayout container, String number, String label) {
    View view =
        getActivity()
            .getLayoutInflater()
            .inflate(R.layout.disambig_option_header_layout, container, false);
    String secondaryInfo =
        TextUtils.isEmpty(label)
            ? number
            : getContext().getString(R.string.call_subject_type_and_number, label, number);
    ((TextView) view.findViewById(R.id.disambig_header_phone_label)).setText(secondaryInfo);
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
    if (rememberThisChoice.isChecked()) {
      Logger.get(getContext()).logImpression(DialerImpression.Type.FAVORITE_SET_VIDEO_DEFAULT);
      setDefaultChannel(getContext().getApplicationContext(), speedDialUiItem, channel);
    }

    if (channel.technology() == Channel.DUO) {
      Logger.get(getContext())
          .logImpression(
              DialerImpression.Type.LIGHTBRINGER_VIDEO_REQUESTED_FOR_FAVORITE_CONTACT_DISAMBIG);
    }

    PreCall.start(
        getContext(),
        new CallIntentBuilder(channel.number(), CallInitiationType.Type.SPEED_DIAL_DISAMBIG_DIALOG)
            .setAllowAssistedDial(true)
            .setIsVideoCall(true)
            .setIsDuoCall(channel.technology() == Channel.DUO));
    dismiss();
  }

  private void onVoiceOptionClicked(Channel channel) {
    if (rememberThisChoice.isChecked()) {
      Logger.get(getContext()).logImpression(DialerImpression.Type.FAVORITE_SET_VOICE_DEFAULT);
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
