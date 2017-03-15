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
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.callcomposer.nano.CallComposerContact;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.callintent.nano.CallInitiationType;
import com.android.dialer.common.Assert;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.nano.DialerImpression;
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

  /**
   * Populates the contact info fields based on the current contact information. Copied from {@link
   * com.android.contacts.common.dialog.CallSubjectDialog}.
   */
  public void updateContactInfo(CallComposerContact contact) {
    this.contact = contact;
    setPhoto(
        contact.photoId,
        Uri.parse(contact.photoUri),
        Uri.parse(contact.contactUri),
        contact.nameOrNumber,
        contact.isBusiness);

    nameView.setText(contact.nameOrNumber);
    if (!TextUtils.isEmpty(contact.numberLabel) && !TextUtils.isEmpty(contact.displayNumber)) {
      numberView.setVisibility(View.VISIBLE);
      String secondaryInfo =
          context.getString(
              com.android.contacts.common.R.string.call_subject_type_and_number,
              contact.numberLabel,
              contact.displayNumber);
      numberView.setText(secondaryInfo);
    } else {
      numberView.setVisibility(View.GONE);
      numberView.setText(null);
    }
  }

  /**
   * Sets the photo on the quick contact galleryIcon. Copied from {@link
   * com.android.contacts.common.dialog.CallSubjectDialog}.
   */
  private void setPhoto(
      long photoId, Uri photoUri, Uri contactUri, String displayName, boolean isBusiness) {
    contactPhoto.assignContactUri(contactUri);
    contactPhoto.setOverlay(null);

    int contactType =
        isBusiness ? ContactPhotoManager.TYPE_BUSINESS : ContactPhotoManager.TYPE_DEFAULT;
    String lookupKey = contactUri == null ? null : UriUtils.getLookupKeyFromUri(contactUri);

    ContactPhotoManager.DefaultImageRequest request =
        new ContactPhotoManager.DefaultImageRequest(
            displayName, lookupKey, contactType, true /* isCircular */);

    if (photoId == 0 && photoUri != null) {
      contactPhoto.setImageDrawable(
          context.getDrawable(R.drawable.product_logo_avatar_anonymous_color_120));
    } else {
      ContactPhotoManager.getInstance(context)
          .loadThumbnail(
              contactPhoto, photoId, false /* darkTheme */, true /* isCircular */, request);
    }
  }

  @Override
  public void onClick(View view) {
    if (view == callBackButton) {
      Logger.get(view.getContext()).logImpression(DialerImpression.Type.CALL_DETAILS_CALL_BACK);
      DialerUtils.startActivityWithErrorToast(
          view.getContext(),
          new CallIntentBuilder(contact.number, CallInitiationType.Type.CALL_DETAILS).build());
    } else {
      Assert.fail("View OnClickListener not implemented: " + view);
    }
  }
}
