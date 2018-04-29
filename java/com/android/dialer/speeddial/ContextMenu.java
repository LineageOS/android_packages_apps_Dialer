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
import android.support.annotation.VisibleForTesting;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.android.dialer.speeddial.loader.SpeedDialUiItem;

/** Floating menu which presents contact options available to the contact. */
public class ContextMenu extends LinearLayout {

  private ContextMenuItemListener listener;

  private TextView voiceView;
  private TextView videoView;
  private TextView smsView;

  private SpeedDialUiItem speedDialUiItem;
  private Channel voiceChannel;
  private Channel videoChannel;

  public ContextMenu(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    videoView = findViewById(R.id.video_call_container);
    videoView.setOnClickListener(v -> placeVideoCall());

    smsView = findViewById(R.id.send_message_container);
    smsView.setOnClickListener(v -> listener.openSmsConversation(voiceChannel.number()));

    voiceView = findViewById(R.id.voice_call_container);
    voiceView.setOnClickListener(v -> placeVoiceCall());

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

    voiceChannel = speedDialUiItem.getDefaultVoiceChannel();
    videoChannel = speedDialUiItem.getDefaultVideoChannel();
    voiceView.setVisibility(videoChannel == null ? View.GONE : View.VISIBLE);
    videoView.setVisibility(videoChannel == null ? View.GONE : View.VISIBLE);
    smsView.setVisibility(voiceChannel == null ? View.GONE : View.VISIBLE);

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

  private void placeVoiceCall() {
    listener.placeCall(Assert.isNotNull(voiceChannel));
  }

  private void placeVideoCall() {
    listener.placeCall(Assert.isNotNull(videoChannel));
  }

  public boolean isVisible() {
    return getVisibility() == View.VISIBLE;
  }

  /** Listener to report user clicks on menu items. */
  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  public interface ContextMenuItemListener {

    /** Called when the user selects "voice call" or "video call" option from the context menu. */
    void placeCall(Channel channel);

    /** Called when the user selects "send message" from the context menu. */
    void openSmsConversation(String number);

    /** Called when the user selects "remove" from the context menu. */
    void removeFavoriteContact(SpeedDialUiItem speedDialUiItem);

    /** Called when the user selects "contact info" from the context menu. */
    void openContactInfo(SpeedDialUiItem speedDialUiItem);
  }
}
