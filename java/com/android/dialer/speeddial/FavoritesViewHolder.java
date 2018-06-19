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
 * limitations under the License.
 */

package com.android.dialer.speeddial;

import android.content.Context;
import android.provider.ContactsContract.Contacts;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.glidephotomanager.GlidePhotoManagerComponent;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.android.dialer.speeddial.draghelper.SpeedDialFavoritesViewHolderOnTouchListener;
import com.android.dialer.speeddial.draghelper.SpeedDialFavoritesViewHolderOnTouchListener.OnTouchFinishCallback;
import com.android.dialer.speeddial.loader.SpeedDialUiItem;

/** ViewHolder for starred/favorite contacts in {@link SpeedDialFragment}. */
public class FavoritesViewHolder extends RecyclerView.ViewHolder
    implements OnClickListener, OnLongClickListener, OnTouchFinishCallback {

  private final FavoriteContactsListener listener;

  private final QuickContactBadge photoView;
  private final TextView nameView;
  private final TextView phoneType;
  private final FrameLayout videoCallIcon;

  private final FrameLayout avatarContainer;

  private SpeedDialUiItem speedDialUiItem;

  public FavoritesViewHolder(View view, ItemTouchHelper helper, FavoriteContactsListener listener) {
    super(view);
    photoView = view.findViewById(R.id.avatar);
    nameView = view.findViewById(R.id.name);
    phoneType = view.findViewById(R.id.phone_type);
    videoCallIcon = view.findViewById(R.id.video_call_container);
    avatarContainer = view.findViewById(R.id.avatar_container);
    view.setOnClickListener(this);
    view.setOnLongClickListener(this);
    view.setOnTouchListener(
        new SpeedDialFavoritesViewHolderOnTouchListener(
            ViewConfiguration.get(view.getContext()), helper, this, this));
    photoView.setClickable(false);
    this.listener = listener;
  }

  public void bind(Context context, SpeedDialUiItem speedDialUiItem) {
    this.speedDialUiItem = Assert.isNotNull(speedDialUiItem);
    Assert.checkArgument(speedDialUiItem.isStarred());

    nameView.setText(speedDialUiItem.name());

    Channel channel = speedDialUiItem.defaultChannel();
    if (channel == null) {
      channel = speedDialUiItem.getDefaultVoiceChannel();
    }

    if (channel != null) {
      phoneType.setText(channel.label());
      videoCallIcon.setVisibility(channel.isVideoTechnology() ? View.VISIBLE : View.GONE);
    } else {
      phoneType.setText("");
      videoCallIcon.setVisibility(View.GONE);
    }

    GlidePhotoManagerComponent.get(context)
        .glidePhotoManager()
        .loadQuickContactBadge(
            photoView,
            PhotoInfo.newBuilder()
                .setPhotoId(speedDialUiItem.photoId())
                .setPhotoUri(speedDialUiItem.photoUri())
                .setName(speedDialUiItem.name())
                .setLookupUri(
                    Contacts.getLookupUri(speedDialUiItem.contactId(), speedDialUiItem.lookupKey())
                        .toString())
                .build());
  }

  @Override
  public void onClick(View v) {
    if (speedDialUiItem.defaultChannel() != null) {
      listener.onClick(speedDialUiItem.defaultChannel());
    } else {
      listener.onAmbiguousContactClicked(speedDialUiItem);
    }
  }

  @Override
  public boolean onLongClick(View view) {
    // TODO(calderwoodra): add bounce/sin wave scale animation
    listener.showContextMenu(photoView, speedDialUiItem);
    return true;
  }

  @Override
  public void onTouchFinished(boolean closeContextMenu) {
    listener.onTouchFinished(closeContextMenu);
  }

  FrameLayout getAvatarContainer() {
    return avatarContainer;
  }

  void onSelectedChanged(boolean selected) {
    nameView.setVisibility(selected ? View.GONE : View.VISIBLE);
    phoneType.setVisibility(selected ? View.GONE : View.VISIBLE);
  }

  /** Listener/callback for {@link FavoritesViewHolder} actions. */
  public interface FavoriteContactsListener {

    /** Called when the user clicks on a favorite contact that doesn't have a default number. */
    void onAmbiguousContactClicked(SpeedDialUiItem speedDialUiItem);

    /** Called when the user clicks on a favorite contact. */
    void onClick(Channel channel);

    /** Called when the user long clicks on a favorite contact. */
    void showContextMenu(View view, SpeedDialUiItem speedDialUiItem);

    /** Called when the user is no longer touching the favorite contact. */
    void onTouchFinished(boolean closeContextMenu);

    /** Called when the user drag the favorite to remove. */
    void onRequestRemove(SpeedDialUiItem speedDialUiItem);
  }
}
