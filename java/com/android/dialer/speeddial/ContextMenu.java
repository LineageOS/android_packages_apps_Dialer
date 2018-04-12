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

package com.android.dialer.speeddial;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import com.android.dialer.speeddial.loader.SpeedDialUiItem;

/** Floating menu which presents contact options available to the contact. */
public class ContextMenu extends LinearLayout {

  private SpeedDialUiItem speedDialUiItem;
  private ContextMenuItemListener listener;

  public ContextMenu(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    findViewById(R.id.voice_call_container)
        .setOnClickListener(v -> listener.placeVoiceCall(speedDialUiItem));
    findViewById(R.id.video_call_container)
        .setOnClickListener(v -> listener.placeVideoCall(speedDialUiItem));
    findViewById(R.id.send_message_container)
        .setOnClickListener(v -> listener.openSmsConversation(speedDialUiItem));
    findViewById(R.id.remove_container)
        .setOnClickListener(v -> listener.removeFavoriteContact(speedDialUiItem));
    findViewById(R.id.contact_info_container)
        .setOnClickListener(v -> listener.openContactInfo(speedDialUiItem));
  }

  /** Shows the menu and updates the menu's position w.r.t. the view it's related to. */
  public void showMenu(
      View parentLayout,
      View childLayout,
      SpeedDialUiItem speedDialUiItem,
      ContextMenuItemListener listener) {
    this.speedDialUiItem = speedDialUiItem;
    this.listener = listener;

    int[] childLocation = new int[2];
    int[] parentLocation = new int[2];
    childLayout.getLocationOnScreen(childLocation);
    parentLayout.getLocationOnScreen(parentLocation);

    setX((float) (childLocation[0] + .5 * childLayout.getWidth() - .5 * getWidth()));
    setY(childLocation[1] - parentLocation[1] + childLayout.getHeight());

    // TODO(calderwoodra): a11y
    // TODO(calderwoodra): animate this similar to the bubble menu
    setVisibility(View.VISIBLE);
  }

  /** Returns true if the view was hidden. */
  public void hideMenu() {
    this.speedDialUiItem = null;
    this.listener = null;
    if (getVisibility() == View.VISIBLE) {
      // TODO(calderwoodra): a11y
      // TODO(calderwoodra): animate this similar to the bubble menu
      setVisibility(View.INVISIBLE);
    }
  }

  /** Listener to report user clicks on menu items. */
  public interface ContextMenuItemListener {

    /** Called when the user selects "voice call" option from the context menu. */
    void placeVoiceCall(SpeedDialUiItem speedDialUiItem);

    /** Called when the user selects "video call" option from the context menu. */
    void placeVideoCall(SpeedDialUiItem speedDialUiItem);

    /** Called when the user selects "send message" from the context menu. */
    void openSmsConversation(SpeedDialUiItem speedDialUiItem);

    /** Called when the user selects "remove" from the context menu. */
    void removeFavoriteContact(SpeedDialUiItem speedDialUiItem);

    /** Called when the user selects "contact info" from the context menu. */
    void openContactInfo(SpeedDialUiItem speedDialUiItem);
  }
}
