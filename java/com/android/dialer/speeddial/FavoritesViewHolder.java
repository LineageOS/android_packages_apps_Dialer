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
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.FrameLayout;
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
  public boolean onLongClick(View v) {
    // TODO(calderwoodra): implement drag and drop logic
    listener.onLongClick(number);
    return true;
  }

  /** Listener/callback for {@link FavoritesViewHolder} actions. */
  public interface FavoriteContactsListener {

    /** Called when the user clicks on a favorite contact that doesn't have a default number. */
    void onAmbiguousContactClicked(String contactId);

    /** Called when the user clicks on a favorite contact. */
    void onClick(String number, boolean isVideoCall);

    /** Called when the user long clicks on a favorite contact. */
    void onLongClick(String number);
  }
}
