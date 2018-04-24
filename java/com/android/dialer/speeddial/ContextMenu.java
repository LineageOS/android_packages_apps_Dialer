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
import android.widget.TextView;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.android.dialer.speeddial.loader.SpeedDialUiItem;

/** Floating menu which presents contact options available to the contact. */
public class ContextMenu extends LinearLayout {

  private ContextMenuItemListener listener;

  private TextView videoView;
  private TextView smsView;

  private SpeedDialUiItem speedDialUiItem;
  private Channel voiceChannel;
  private Channel videoChannel;
  private Channel smsChannel;

  public ContextMenu(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    videoView = findViewById(R.id.video_call_container);
    videoView.setOnClickListener(v -> placeVideoCall());

    smsView = findViewById(R.id.send_message_container);
    smsView.setOnClickListener(v -> listener.openSmsConversation(smsChannel.number()));

    findViewById(R.id.voice_call_container).setOnClickListener(v -> placeVoiceCall());
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

    voiceChannel = speedDialUiItem.getDeterministicVoiceChannel();
    videoChannel = speedDialUiItem.getDeterministicVideoChannel();
    videoView.setVisibility(
        videoChannel == null && !speedDialUiItem.hasVideoChannels() ? View.GONE : View.VISIBLE);

    // TODO(calderwoodra): disambig dialog for texts?
    smsChannel = voiceChannel;
    smsView.setVisibility(smsChannel == null ? View.GONE : View.VISIBLE);

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
    if (voiceChannel == null) {
      listener.disambiguateCall(speedDialUiItem);
    } else {
      listener.placeCall(voiceChannel);
    }
  }

  private void placeVideoCall() {
    if (videoChannel == null) {
      listener.disambiguateCall(speedDialUiItem);
    } else {
      listener.placeCall(videoChannel);
    }
  }

  public boolean isVisible() {
    return getVisibility() == View.VISIBLE;
  }

  /** Listener to report user clicks on menu items. */
  public interface ContextMenuItemListener {

    /** Called when the user selects "voice call" or "video call" option from the context menu. */
    void placeCall(Channel channel);

    /**
     * Called when the user selects "voice call" or "video call" option from the context menu, but
     * it's not clear which channel they want to call.
     *
     * <p>TODO(calderwoodra): discuss with product how we want to handle these cases
     */
    void disambiguateCall(SpeedDialUiItem speedDialUiItem);

    /** Called when the user selects "send message" from the context menu. */
    void openSmsConversation(String number);

    /** Called when the user selects "remove" from the context menu. */
    void removeFavoriteContact(SpeedDialUiItem speedDialUiItem);

    /** Called when the user selects "contact info" from the context menu. */
    void openContactInfo(SpeedDialUiItem speedDialUiItem);
  }
}
