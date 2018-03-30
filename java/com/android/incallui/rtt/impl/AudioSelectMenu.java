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
 * limitations under the License
 */

package com.android.incallui.rtt.impl;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff.Mode;
import android.telecom.CallAudioState;
import android.view.View;
import android.widget.PopupWindow;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;

/** Audio select menu for RTT call. */
public class AudioSelectMenu extends PopupWindow {

  private final InCallButtonUiDelegate inCallButtonUiDelegate;
  private final Context context;
  private final OnButtonClickListener onButtonClickListener;

  interface OnButtonClickListener {
    void onBackPressed();
  }

  AudioSelectMenu(
      Context context,
      InCallButtonUiDelegate inCallButtonUiDelegate,
      OnButtonClickListener onButtonClickListener) {
    super(context, null, 0, R.style.OverflowMenu);
    this.context = context;
    this.inCallButtonUiDelegate = inCallButtonUiDelegate;
    this.onButtonClickListener = onButtonClickListener;
    View view = View.inflate(context, R.layout.audio_route, null);
    setContentView(view);
    setOnDismissListener(this::dismiss);
    setFocusable(true);
    setWidth(context.getResources().getDimensionPixelSize(R.dimen.rtt_overflow_menu_width));
    view.findViewById(R.id.audioroute_back)
        .setOnClickListener(
            v -> {
              dismiss();
              this.onButtonClickListener.onBackPressed();
            });
    CallAudioState audioState = inCallButtonUiDelegate.getCurrentAudioState();
    initItem(
        view.findViewById(R.id.audioroute_bluetooth), CallAudioState.ROUTE_BLUETOOTH, audioState);
    initItem(view.findViewById(R.id.audioroute_speaker), CallAudioState.ROUTE_SPEAKER, audioState);
    initItem(
        view.findViewById(R.id.audioroute_headset), CallAudioState.ROUTE_WIRED_HEADSET, audioState);
    initItem(
        view.findViewById(R.id.audioroute_earpiece), CallAudioState.ROUTE_EARPIECE, audioState);
  }

  private void initItem(RttCheckableButton item, final int itemRoute, CallAudioState audioState) {
    int selectedColor =
        context.getColor(com.android.incallui.audioroute.R.color.dialer_theme_color);
    if ((audioState.getSupportedRouteMask() & itemRoute) == 0) {
      item.setVisibility(View.GONE);
    } else if (audioState.getRoute() == itemRoute) {
      item.setTextColor(selectedColor);
      item.setCompoundDrawableTintList(ColorStateList.valueOf(selectedColor));
      item.setCompoundDrawableTintMode(Mode.SRC_ATOP);
    }
    item.setOnClickListener(
        (v) -> {
          inCallButtonUiDelegate.setAudioRoute(itemRoute);
        });
  }
}
