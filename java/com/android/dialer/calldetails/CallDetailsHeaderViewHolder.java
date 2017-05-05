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

package com.android.dialer.calldetails;

import android.content.Context;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.contacts.common.ContactPhotoManager;
import com.android.dialer.callcomposer.CallComposerContact;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.util.DialerUtils;

/** ViewHolder for Header/Contact in {@link CallDetailsActivity}. */
public class CallDetailsHeaderViewHolder extends RecyclerView.ViewHolder
    implements OnClickListener {

  private final View callBackButton;
  private final TextView nameView;
  private final TextView numberView;
  private final QuickContactBadge contactPhoto;
  private final Context context;

  private CallComposerContact contact;

  CallDetailsHeaderViewHolder(View container) {
    super(container);
    context = container.getContext();
    callBackButton = container.findViewById(R.id.call_back_button);
    nameView = (TextView) container.findViewById(R.id.contact_name);
    numberView = (TextView) container.findViewById(R.id.phone_number);
    contactPhoto = (QuickContactBadge) container.findViewById(R.id.quick_contact_photo);
    callBackButton.setOnClickListener(this);
  }

  /** Populates the contact info fields based on the current contact information. */
  void updateContactInfo(CallComposerContact contact) {
    this.contact = contact;
    ContactPhotoManager.getInstance(context)
        .loadDialerThumbnailOrPhoto(
            contactPhoto,
            contact.hasContactUri() ? Uri.parse(contact.getContactUri()) : null,
            contact.getPhotoId(),
            contact.hasPhotoUri() ? Uri.parse(contact.getPhotoUri()) : null,
            contact.getNameOrNumber(),
            contact.getContactType());

    contactPhoto.setContentDescription(
        context.getString(R.string.description_contact_photo_details, contact.getNameOrNumber()));
    nameView.setText(contact.getNameOrNumber());
    if (!TextUtils.isEmpty(contact.getDisplayNumber())) {
      numberView.setVisibility(View.VISIBLE);
      String secondaryInfo =
          TextUtils.isEmpty(contact.getNumberLabel())
              ? contact.getDisplayNumber()
              : context.getString(
                  com.android.contacts.common.R.string.call_subject_type_and_number,
                  contact.getNumberLabel(),
                  contact.getDisplayNumber());
      numberView.setText(secondaryInfo);
    } else {
      numberView.setVisibility(View.GONE);
      numberView.setText(null);
    }

    if (TextUtils.isEmpty(contact.getNumber())) {
      callBackButton.setVisibility(View.GONE);
    }
  }

  @Override
  public void onClick(View view) {
    if (view == callBackButton) {
      Logger.get(view.getContext()).logImpression(DialerImpression.Type.CALL_DETAILS_CALL_BACK);
      DialerUtils.startActivityWithErrorToast(
          view.getContext(),
          new CallIntentBuilder(contact.getNumber(), CallInitiationType.Type.CALL_DETAILS).build());
    } else {
      Assert.fail("View OnClickListener not implemented: " + view);
    }
  }
}
