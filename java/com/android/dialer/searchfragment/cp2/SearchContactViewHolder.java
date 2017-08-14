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
 * limitations under the License
 */

package com.android.dialer.searchfragment.cp2;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.support.annotation.IntDef;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.lettertiles.LetterTileDrawable;
import com.android.dialer.callintent.CallInitiationType.Type;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.searchfragment.common.Projections;
import com.android.dialer.searchfragment.common.QueryBoldingUtil;
import com.android.dialer.searchfragment.common.R;
import com.android.dialer.telecom.TelecomUtil;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** ViewHolder for a contact row. */
public final class SearchContactViewHolder extends ViewHolder implements OnClickListener {

  /** IntDef for the different types of actions that can be shown. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({CallToAction.NONE, CallToAction.VIDEO_CALL, CallToAction.SHARE_AND_CALL})
  @interface CallToAction {
    int NONE = 0;
    int VIDEO_CALL = 1;
    int SHARE_AND_CALL = 2;
  }

  private final QuickContactBadge photo;
  private final TextView nameOrNumberView;
  private final TextView numberView;
  private final ImageView callToActionView;
  private final Context context;

  private String number;
  private @CallToAction int currentAction;

  public SearchContactViewHolder(View view) {
    super(view);
    view.setOnClickListener(this);
    photo = view.findViewById(R.id.photo);
    nameOrNumberView = view.findViewById(R.id.primary);
    numberView = view.findViewById(R.id.secondary);
    callToActionView = view.findViewById(R.id.call_to_action);
    context = view.getContext();
  }

  /**
   * Binds the ViewHolder with a cursor from {@link SearchContactsCursorLoader} with the data found
   * at the cursors set position.
   */
  public void bind(Cursor cursor, String query) {
    number = cursor.getString(Projections.PHONE_NUMBER);
    String name = cursor.getString(Projections.PHONE_DISPLAY_NAME);
    String label = getLabel(context.getResources(), cursor);
    String secondaryInfo =
        TextUtils.isEmpty(label)
            ? number
            : context.getString(
                com.android.contacts.common.R.string.call_subject_type_and_number, label, number);

    nameOrNumberView.setText(QueryBoldingUtil.getNameWithQueryBolded(query, name));
    numberView.setText(QueryBoldingUtil.getNumberWithQueryBolded(query, secondaryInfo));
    setCallToAction(cursor);

    if (shouldShowPhoto(cursor, name)) {
      nameOrNumberView.setVisibility(View.VISIBLE);
      photo.setVisibility(View.VISIBLE);
      String photoUri = cursor.getString(Projections.PHONE_PHOTO_URI);
      ContactPhotoManager.getInstance(context)
          .loadDialerThumbnailOrPhoto(
              photo,
              getContactUri(cursor),
              cursor.getLong(Projections.PHONE_PHOTO_ID),
              photoUri == null ? null : Uri.parse(photoUri),
              name,
              LetterTileDrawable.TYPE_DEFAULT);
    } else {
      nameOrNumberView.setVisibility(View.GONE);
      photo.setVisibility(View.INVISIBLE);
    }
  }

  private boolean shouldShowPhoto(Cursor cursor, String currentName) {
    int currentPosition = cursor.getPosition();
    if (currentPosition == 0) {
      return true;
    } else {
      cursor.moveToPosition(currentPosition - 1);
      String previousName = cursor.getString(Projections.PHONE_DISPLAY_NAME);
      cursor.moveToPosition(currentPosition);
      return !currentName.equals(previousName);
    }
  }

  private static Uri getContactUri(Cursor cursor) {
    long contactId = cursor.getLong(Projections.PHONE_ID);
    String lookupKey = cursor.getString(Projections.PHONE_LOOKUP_KEY);
    return Contacts.getLookupUri(contactId, lookupKey);
  }

  // TODO: handle CNAP and cequint types.
  // TODO: unify this into a utility method with CallLogAdapter#getNumberType
  private static String getLabel(Resources resources, Cursor cursor) {
    int numberType = cursor.getInt(Projections.PHONE_TYPE);
    String numberLabel = cursor.getString(Projections.PHONE_LABEL);

    // Returns empty label instead of "custom" if the custom label is empty.
    if (numberType == Phone.TYPE_CUSTOM && TextUtils.isEmpty(numberLabel)) {
      return "";
    }
    return (String) Phone.getTypeLabel(resources, numberType, numberLabel);
  }

  private void setCallToAction(Cursor cursor) {
    currentAction = getCallToAction(cursor);
    switch (currentAction) {
      case CallToAction.NONE:
        callToActionView.setVisibility(View.GONE);
        callToActionView.setOnClickListener(null);
        break;
      case CallToAction.SHARE_AND_CALL:
        callToActionView.setVisibility(View.VISIBLE);
        callToActionView.setImageDrawable(
            context.getDrawable(com.android.contacts.common.R.drawable.ic_phone_attach));
        callToActionView.setOnClickListener(this);
        break;
      case CallToAction.VIDEO_CALL:
        callToActionView.setVisibility(View.VISIBLE);
        callToActionView.setImageDrawable(
            context.getDrawable(R.drawable.quantum_ic_videocam_white_24));
        callToActionView.setOnClickListener(this);
        break;
      default:
        throw Assert.createIllegalStateFailException(
            "Invalid Call to action type: " + currentAction);
    }
  }

  private static @CallToAction int getCallToAction(Cursor cursor) {
    int carrierPresence = cursor.getInt(Projections.PHONE_CARRIER_PRESENCE);
    if ((carrierPresence & Phone.CARRIER_PRESENCE_VT_CAPABLE) == 1) {
      return CallToAction.VIDEO_CALL;
    }

    // TODO: enriched calling
    return CallToAction.NONE;
  }

  @Override
  public void onClick(View view) {
    if (view == callToActionView) {
      switch (currentAction) {
        case CallToAction.SHARE_AND_CALL:
          callToActionView.setVisibility(View.VISIBLE);
          callToActionView.setImageDrawable(
              context.getDrawable(com.android.contacts.common.R.drawable.ic_phone_attach));
          // TODO: open call composer.
          break;
        case CallToAction.VIDEO_CALL:
          callToActionView.setVisibility(View.VISIBLE);
          callToActionView.setImageDrawable(
              context.getDrawable(R.drawable.quantum_ic_videocam_white_24));
          // TODO: place a video call
          break;
        case CallToAction.NONE:
        default:
          throw Assert.createIllegalStateFailException(
              "Invalid Call to action type: " + currentAction);
      }
    } else {
      // TODO: set the correct call initiation type.
      TelecomUtil.placeCall(context, new CallIntentBuilder(number, Type.REGULAR_SEARCH).build());
    }
  }
}
