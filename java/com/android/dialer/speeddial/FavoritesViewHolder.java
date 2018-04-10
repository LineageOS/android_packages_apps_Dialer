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
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.FrameLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.glidephotomanager.GlidePhotoManagerComponent;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.android.dialer.speeddial.loader.SpeedDialUiItem;
import java.util.ArrayList;
import java.util.List;

/** ViewHolder for starred/favorite contacts in {@link SpeedDialFragment}. */
public class FavoritesViewHolder extends RecyclerView.ViewHolder
    implements OnClickListener, OnLongClickListener {

  private final FavoriteContactsListener listener;

  private final QuickContactBadge photoView;
  private final TextView nameView;
  private final TextView phoneType;
  private final FrameLayout videoCallIcon;

  private boolean hasDefaultNumber;
  private boolean isVideoCall;
  private String number;
  private List<Channel> channels;

  public FavoritesViewHolder(View view, FavoriteContactsListener listener) {
    super(view);
    photoView = view.findViewById(R.id.avatar);
    nameView = view.findViewById(R.id.name);
    phoneType = view.findViewById(R.id.phone_type);
    videoCallIcon = view.findViewById(R.id.video_call_container);
    view.setOnClickListener(this);
    view.setOnLongClickListener(this);
    photoView.setClickable(false);
    this.listener = listener;
  }

  public void bind(Context context, SpeedDialUiItem speedDialUiItem) {
    Assert.checkArgument(speedDialUiItem.isStarred());

    nameView.setText(speedDialUiItem.name());
    hasDefaultNumber = speedDialUiItem.defaultChannel() != null;
    if (hasDefaultNumber) {
      channels = new ArrayList<>();
      isVideoCall = speedDialUiItem.defaultChannel().isVideoTechnology();
      number = speedDialUiItem.defaultChannel().number();
      phoneType.setText(speedDialUiItem.defaultChannel().label());
      videoCallIcon.setVisibility(isVideoCall ? View.VISIBLE : View.GONE);
    } else {
      channels = speedDialUiItem.channels();
      isVideoCall = false;
      number = null;
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
    if (hasDefaultNumber) {
      listener.onClick(number, isVideoCall);
    } else {
      listener.onAmbiguousContactClicked(channels);
    }
  }

  @Override
  public boolean onLongClick(View v) {
    // TODO(calderwoodra): implement drag and drop logic
    listener.onLongClick(number);
    return true;
  }

  /** Listener/callback for {@link FavoritesViewHolder} actions. */
  public interface FavoriteContactsListener {

    /** Called when the user clicks on a favorite contact that doesn't have a default number. */
    void onAmbiguousContactClicked(List<Channel> channels);

    /** Called when the user clicks on a favorite contact. */
    void onClick(String number, boolean isVideoCall);

    /** Called when the user long clicks on a favorite contact. */
    void onLongClick(String number);
  }
}
