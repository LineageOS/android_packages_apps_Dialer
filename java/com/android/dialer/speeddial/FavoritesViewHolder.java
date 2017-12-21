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
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.PopupWindow;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.lettertile.LetterTileDrawable;

/** ViewHolder for starred/favorite contacts in {@link SpeedDialFragment}. */
public class FavoritesViewHolder extends RecyclerView.ViewHolder
    implements OnClickListener, OnLongClickListener {

  private final FavoriteContactsListener listener;

  private final QuickContactBadge photoView;
  private final TextView nameView;
  private final TextView phoneType;
  private final FrameLayout videoCallIcon;
  private final View contextMenuAnchor;

  private PopupWindow contextMenu;

  private boolean hasDefaultNumber;
  private boolean isVideoCall;
  private String number;
  private String lookupKey;

  public FavoritesViewHolder(View view, FavoriteContactsListener listener) {
    super(view);
    photoView = view.findViewById(R.id.avatar);
    nameView = view.findViewById(R.id.name);
    phoneType = view.findViewById(R.id.phone_type);
    videoCallIcon = view.findViewById(R.id.video_call_container);
    contextMenuAnchor = view.findViewById(R.id.avatar_container);
    view.setOnClickListener(this);
    view.setOnLongClickListener(this);
    photoView.setClickable(false);
    this.listener = listener;
  }

  public void bind(Context context, Cursor cursor) {
    Assert.checkArgument(cursor.getInt(StrequentContactsCursorLoader.PHONE_STARRED) == 1);
    isVideoCall = false; // TODO(calderwoodra): get from disambig data
    number = cursor.getString(StrequentContactsCursorLoader.PHONE_NUMBER);

    String name = cursor.getString(StrequentContactsCursorLoader.PHONE_DISPLAY_NAME);
    long contactId = cursor.getLong(StrequentContactsCursorLoader.PHONE_ID);
    lookupKey = cursor.getString(StrequentContactsCursorLoader.PHONE_LOOKUP_KEY);
    Uri contactUri = Contacts.getLookupUri(contactId, lookupKey);

    String photoUri = cursor.getString(StrequentContactsCursorLoader.PHONE_PHOTO_URI);
    ContactPhotoManager.getInstance(context)
        .loadDialerThumbnailOrPhoto(
            photoView,
            contactUri,
            cursor.getLong(StrequentContactsCursorLoader.PHONE_PHOTO_ID),
            photoUri == null ? null : Uri.parse(photoUri),
            name,
            LetterTileDrawable.TYPE_DEFAULT);
    nameView.setText(name);
    phoneType.setText(getLabel(context.getResources(), cursor));
    videoCallIcon.setVisibility(isVideoCall ? View.VISIBLE : View.GONE);

    // TODO(calderwoodra): Update this to include communication avenues also
    hasDefaultNumber = cursor.getInt(StrequentContactsCursorLoader.PHONE_IS_SUPER_PRIMARY) != 0;
  }

  // TODO(calderwoodra): handle CNAP and cequint types.
  // TODO(calderwoodra): unify this into a utility method with CallLogAdapter#getNumberType
  private static String getLabel(Resources resources, Cursor cursor) {
    int numberType = cursor.getInt(StrequentContactsCursorLoader.PHONE_TYPE);
    String numberLabel = cursor.getString(StrequentContactsCursorLoader.PHONE_LABEL);

    // Returns empty label instead of "custom" if the custom label is empty.
    if (numberType == Phone.TYPE_CUSTOM && TextUtils.isEmpty(numberLabel)) {
      return "";
    }
    return (String) Phone.getTypeLabel(resources, numberType, numberLabel);
  }

  @Override
  public void onClick(View v) {
    if (hasDefaultNumber) {
      listener.onClick(number, isVideoCall);
    } else {
      listener.onAmbiguousContactClicked(lookupKey);
    }
  }

  @Override
  public boolean onLongClick(View view) {
    Context context = itemView.getContext();
    View contentView = LayoutInflater.from(context).inflate(R.layout.favorite_context_menu, null);
    contentView
        .findViewById(R.id.voice_call_container)
        .setOnClickListener(v -> listener.onClick(Assert.isNotNull(number), false));
    contentView
        .findViewById(R.id.video_call_container)
        .setOnClickListener(v -> listener.onClick(Assert.isNotNull(number), true));
    contentView
        .findViewById(R.id.send_message_container)
        .setOnClickListener(v -> listener.openSmsConversation(Assert.isNotNull(number)));
    contentView
        .findViewById(R.id.remove_container)
        .setOnClickListener(v -> listener.removeFavoriteContact());
    contentView
        .findViewById(R.id.contact_info_container)
        .setOnClickListener(v -> listener.openContactInfo());

    int offset =
        context.getResources().getDimensionPixelSize(R.dimen.speed_dial_context_menu_x_offset);
    int padding =
        context.getResources().getDimensionPixelSize(R.dimen.speed_dial_context_menu_extra_width);
    int width = padding + itemView.getWidth();
    int elevation = context.getResources().getDimensionPixelSize(R.dimen.context_menu_elevation);
    contextMenu = new PopupWindow(contentView, width, LayoutParams.WRAP_CONTENT, true);
    contextMenu.setBackgroundDrawable(context.getDrawable(R.drawable.context_menu_background));
    contextMenu.setElevation(elevation);
    contextMenu.setOnDismissListener(() -> contextMenu = null);
    contextMenu.showAsDropDown(contextMenuAnchor, offset, 0);
    return true;
  }

  @VisibleForTesting
  public PopupWindow getContextMenu() {
    return contextMenu;
  }

  /** Listener/callback for {@link FavoritesViewHolder} actions. */
  public interface FavoriteContactsListener {

    /** Called when the user clicks on a favorite contact that doesn't have a default number. */
    void onAmbiguousContactClicked(String lookupKey);

    /** Called when the user clicks on a favorite contact. */
    void onClick(String number, boolean isVideoCall);

    /** Called when the user selects send message from the context menu. */
    void openSmsConversation(String number);

    /** Called when the user selects remove from the context menu. */
    void removeFavoriteContact();

    /** Called when the user selects contact info from the context menu. */
    void openContactInfo();
  }
}
