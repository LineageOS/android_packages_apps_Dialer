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
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.PopupMenu.OnMenuItemClickListener;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import com.android.dialer.common.Assert;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.android.dialer.speeddial.loader.SpeedDialUiItem;

/** {@link PopupMenu} which presents contact options for starred contacts. */
public class ContextMenu extends PopupMenu implements OnMenuItemClickListener {

  private final ContextMenuItemListener listener;

  private final SpeedDialUiItem speedDialUiItem;
  private final Channel voiceChannel;
  private final Channel videoChannel;

  private boolean visible;

  /**
   * Creates a new context menu and displays it.
   *
   * @see #show()
   */
  public static ContextMenu show(
      Context context,
      View anchor,
      ContextMenuItemListener contextMenuListener,
      SpeedDialUiItem speedDialUiItem) {
    ContextMenu menu = new ContextMenu(context, anchor, contextMenuListener, speedDialUiItem);
    menu.show();
    menu.visible = true;
    return menu;
  }

  /**
   * Hides the context menu.
   *
   * @see #dismiss()
   */
  public void hide() {
    dismiss();
    visible = false;
  }

  private ContextMenu(
      @NonNull Context context,
      @NonNull View anchor,
      ContextMenuItemListener listener,
      SpeedDialUiItem speedDialUiItem) {
    super(context, anchor, Gravity.CENTER);
    this.listener = listener;
    this.speedDialUiItem = speedDialUiItem;
    voiceChannel = speedDialUiItem.getDefaultVoiceChannel();
    videoChannel = speedDialUiItem.getDefaultVideoChannel();

    setOnMenuItemClickListener(this);
    getMenuInflater().inflate(R.menu.starred_contact_context_menu, getMenu());
    getMenu().findItem(R.id.voice_call_container).setVisible(voiceChannel != null);
    getMenu().findItem(R.id.video_call_container).setVisible(videoChannel != null);
    getMenu().findItem(R.id.send_message_container).setVisible(voiceChannel != null);
    if (voiceChannel != null) {
      String secondaryInfo =
          TextUtils.isEmpty(voiceChannel.label())
              ? voiceChannel.number()
              : context.getString(
                  R.string.call_subject_type_and_number,
                  voiceChannel.label(),
                  voiceChannel.number());
      getMenu().findItem(R.id.starred_contact_context_menu_title).setTitle(secondaryInfo);
      getMenu().findItem(R.id.starred_contact_context_menu_title).setVisible(true);
    } else {
      getMenu().findItem(R.id.starred_contact_context_menu_title).setVisible(false);
    }
  }

  @Override
  public boolean onMenuItemClick(MenuItem menuItem) {
    if (menuItem.getItemId() == R.id.voice_call_container) {
      listener.placeCall(Assert.isNotNull(voiceChannel));
    } else if (menuItem.getItemId() == R.id.video_call_container) {
      listener.placeCall(Assert.isNotNull(videoChannel));
    } else if (menuItem.getItemId() == R.id.send_message_container) {
      listener.openSmsConversation(voiceChannel.number());
    } else if (menuItem.getItemId() == R.id.remove_container) {
      listener.removeFavoriteContact(speedDialUiItem);
    } else if (menuItem.getItemId() == R.id.contact_info_container) {
      listener.openContactInfo(speedDialUiItem);
    } else {
      throw Assert.createIllegalStateFailException("Menu option click not handled");
    }
    return true;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public boolean isVisible() {
    return visible;
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
