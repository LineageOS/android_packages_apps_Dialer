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
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.location.GeoUtil;

/** ViewHolder for displaying suggested contacts in {@link SpeedDialFragment}. */
public class SuggestionViewHolder extends RecyclerView.ViewHolder implements OnClickListener {

  private final SuggestedContactsListener listener;

  private final QuickContactBadge photoView;
  private final TextView nameOrNumberView;
  private final TextView numberView;

  private String number;

  SuggestionViewHolder(View view, SuggestedContactsListener listener) {
    super(view);
    photoView = view.findViewById(R.id.avatar);
    nameOrNumberView = view.findViewById(R.id.name);
    numberView = view.findViewById(R.id.number);
    itemView.setOnClickListener(this);
    view.findViewById(R.id.overflow).setOnClickListener(this);
    this.listener = listener;
  }

  public void bind(Context context, Cursor cursor) {
    number = cursor.getString(StrequentContactsCursorLoader.PHONE_NUMBER);
    number = PhoneNumberUtils.formatNumber(number, GeoUtil.getCurrentCountryIso(context));

    String name = cursor.getString(StrequentContactsCursorLoader.PHONE_DISPLAY_NAME);
    String label = getLabel(context.getResources(), cursor);
    String secondaryInfo =
        TextUtils.isEmpty(label)
            ? number
            : context.getString(
                com.android.contacts.common.R.string.call_subject_type_and_number, label, number);

    nameOrNumberView.setText(name);
    numberView.setText(secondaryInfo);

    long contactId = cursor.getLong(StrequentContactsCursorLoader.PHONE_ID);
    String lookupKey = cursor.getString(StrequentContactsCursorLoader.PHONE_LOOKUP_KEY);
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
    if (v.getId() == R.id.overflow) {
      listener.onOverFlowMenuClicked(number);
    } else {
      listener.onRowClicked(number);
    }
  }

  /** Listener/Callback for {@link SuggestionViewHolder} parents. */
  public interface SuggestedContactsListener {

    void onOverFlowMenuClicked(String number);

    /** Called when a suggested contact is clicked. */
    void onRowClicked(String number);
  }
}
