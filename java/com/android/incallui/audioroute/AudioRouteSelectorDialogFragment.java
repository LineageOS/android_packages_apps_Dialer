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

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff.Mode;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.CallAudioState;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.dialer.R;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.theme.base.ThemeComponent;
import com.android.incallui.call.TelecomAdapter;
import com.android.incallui.util.BluetoothUtil;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Collection;

/** Shows picker for audio routes */
public class AudioRouteSelectorDialogFragment extends BottomSheetDialogFragment {

  public static final String TAG = "AudioRouteSelectorDialogFragment";
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
    dialog.setCancelable(true);
    dialog.setCanceledOnTouchOutside(true);
    if (Settings.canDrawOverlays(getContext())) {
      dialog
          .getWindow()
          .setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
    }
    return dialog;
  }

  @Nullable
  @Override
  @SuppressLint("NewApi")
  public View onCreateView(
      LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
    View view = layoutInflater.inflate(R.layout.audioroute_selector, viewGroup, false);
    CallAudioState audioState = getArguments().getParcelable(ARG_AUDIO_STATE);

    // Create items for all connected Bluetooth devices
    Collection<BluetoothDevice> bluetoothDeviceSet = audioState.getSupportedBluetoothDevices();
    for (BluetoothDevice device : bluetoothDeviceSet) {
      boolean selected =
          (audioState.getRoute() == CallAudioState.ROUTE_BLUETOOTH)
              && (bluetoothDeviceSet.size() == 1
                  || device.equals(audioState.getActiveBluetoothDevice()));
      TextView textView = createBluetoothItem(device, selected);
      ((LinearLayout) view).addView(textView, 0);
    }

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

    // TODO(a bug): set peak height correctly to fully expand it in landscape mode.
    return view;
  }

  @Override
  public void onCancel(DialogInterface dialogInterface) {
    super.onCancel(dialogInterface);
    FragmentUtils.getParentUnsafe(
            AudioRouteSelectorDialogFragment.this, AudioRouteSelectorPresenter.class)
        .onAudioRouteSelectorDismiss();
  }

  private void initItem(
      TextView item,
      final int itemRoute,
      CallAudioState audioState) {
    if ((audioState.getSupportedRouteMask() & itemRoute) == 0) {
      item.setVisibility(View.GONE);
    } else if (audioState.getRoute() == itemRoute) {
      item.setSelected(true);
      setColor(item, true);
    } else {
      setColor(item, false);
    }
    item.setOnClickListener(
        (v) -> {
          FragmentUtils.getParentUnsafe(
                  AudioRouteSelectorDialogFragment.this, AudioRouteSelectorPresenter.class)
              .onAudioRouteSelected(itemRoute);
          dismiss();
        });
  }

  private TextView createBluetoothItem(BluetoothDevice bluetoothDevice, boolean selected) {
    TextView textView =
        (TextView) getLayoutInflater().inflate(R.layout.audioroute_item, null, false);
    textView.setText(BluetoothUtil.getAliasName(bluetoothDevice));
    if (selected) {
      textView.setSelected(true);
    }
    setColor(textView, selected);
    textView.setOnClickListener(
        (v) -> {
          // Set Bluetooth audio route
          FragmentUtils.getParentUnsafe(
                  AudioRouteSelectorDialogFragment.this, AudioRouteSelectorPresenter.class)
              .onAudioRouteSelected(CallAudioState.ROUTE_BLUETOOTH);
          // Set active Bluetooth device
          TelecomAdapter.getInstance().requestBluetoothAudio(bluetoothDevice);
          dismiss();
        });

    return textView;
  }

  private void setColor(TextView item, boolean isSelected) {
    int color = isSelected
            ? ThemeComponent.get(getContext()).theme().getColorAccent()
            : getContext().getColor(R.color.nav_item);
    item.setTextColor(color);
    item.setCompoundDrawableTintList(ColorStateList.valueOf(color));
    item.setCompoundDrawableTintMode(Mode.SRC_ATOP);
  }
}
