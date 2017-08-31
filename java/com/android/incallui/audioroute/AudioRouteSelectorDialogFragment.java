/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.incallui.audioroute;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff.Mode;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.telecom.CallAudioState;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;

/** Shows picker for audio routes */
public class AudioRouteSelectorDialogFragment extends BottomSheetDialogFragment {

  private static final String ARG_AUDIO_STATE = "audio_state";

  /** Called when an audio route is picked */
  public interface AudioRouteSelectorPresenter {
    void onAudioRouteSelected(int audioRoute);

    void onAudioRouteSelectorDismiss();
  }

  public static AudioRouteSelectorDialogFragment newInstance(CallAudioState audioState) {
    AudioRouteSelectorDialogFragment fragment = new AudioRouteSelectorDialogFragment();
    Bundle args = new Bundle();
    args.putParcelable(ARG_AUDIO_STATE, audioState);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    FragmentUtils.checkParent(this, AudioRouteSelectorPresenter.class);
  }

  @Override
  public Dialog onCreateDialog(final Bundle savedInstanceState) {
    LogUtil.i("AudioRouteSelectorDialogFragment.onCreateDialog", null);
    Dialog dialog = super.onCreateDialog(savedInstanceState);
    dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    return dialog;
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
    View view = layoutInflater.inflate(R.layout.audioroute_selector, viewGroup, false);
    CallAudioState audioState = getArguments().getParcelable(ARG_AUDIO_STATE);

    initItem(
        (TextView) view.findViewById(R.id.audioroute_bluetooth),
        CallAudioState.ROUTE_BLUETOOTH,
        audioState);
    initItem(
        (TextView) view.findViewById(R.id.audioroute_speaker),
        CallAudioState.ROUTE_SPEAKER,
        audioState);
    initItem(
        (TextView) view.findViewById(R.id.audioroute_headset),
        CallAudioState.ROUTE_WIRED_HEADSET,
        audioState);
    initItem(
        (TextView) view.findViewById(R.id.audioroute_earpiece),
        CallAudioState.ROUTE_EARPIECE,
        audioState);
    return view;
  }

  @Override
  public void onDismiss(DialogInterface dialogInterface) {
    super.onDismiss(dialogInterface);
    FragmentUtils.getParentUnsafe(
            AudioRouteSelectorDialogFragment.this, AudioRouteSelectorPresenter.class)
        .onAudioRouteSelectorDismiss();
  }

  private void initItem(TextView item, final int itemRoute, CallAudioState audioState) {
    int selectedColor = getResources().getColor(R.color.dialer_theme_color);
    if ((audioState.getSupportedRouteMask() & itemRoute) == 0) {
      item.setVisibility(View.GONE);
    } else if (audioState.getRoute() == itemRoute) {
      item.setTextColor(selectedColor);
      item.setCompoundDrawableTintList(ColorStateList.valueOf(selectedColor));
      item.setCompoundDrawableTintMode(Mode.SRC_ATOP);
    }
    item.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            dismiss();
            FragmentUtils.getParentUnsafe(
                    AudioRouteSelectorDialogFragment.this, AudioRouteSelectorPresenter.class)
                .onAudioRouteSelected(itemRoute);
          }
        });
  }
}
