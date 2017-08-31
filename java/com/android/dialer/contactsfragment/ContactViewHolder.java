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

package com.android.dialer.contactsfragment;

import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract.QuickContact;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.contactsfragment.ContactsFragment.ClickAction;
import com.android.dialer.logging.InteractionEvent;
import com.android.dialer.logging.Logger;

/** View holder for a contact. */
final class ContactViewHolder extends RecyclerView.ViewHolder implements OnClickListener {

  private final TextView header;
  private final TextView name;
  private final QuickContactBadge photo;
  private final Context context;
  private final @ClickAction int clickAction;

  private String headerText;
  private Uri contactUri;

  ContactViewHolder(View itemView, @ClickAction int clickAction) {
    super(itemView);
    Assert.checkArgument(clickAction != ClickAction.INVALID, "Invalid click action.");
    context = itemView.getContext();
    itemView.findViewById(R.id.click_target).setOnClickListener(this);
    header = itemView.findViewById(R.id.header);
    name = itemView.findViewById(R.id.contact_name);
    photo = itemView.findViewById(R.id.photo);
    this.clickAction = clickAction;
  }

  /**
   * Binds the ViewHolder with relevant data.
   *
   * @param headerText populates the header view.
   * @param displayName populates the name view.
   * @param contactUri to be shown by the contact card on photo click.
   * @param showHeader if header view should be shown {@code True}, {@code False} otherwise.
   */
  public void bind(String headerText, String displayName, Uri contactUri, boolean showHeader) {
    Assert.checkArgument(!TextUtils.isEmpty(displayName));
    this.contactUri = contactUri;
    this.headerText = headerText;

    name.setText(displayName);
    header.setText(headerText);
    header.setVisibility(showHeader ? View.VISIBLE : View.INVISIBLE);

    Logger.get(context)
        .logQuickContactOnTouch(
            photo, InteractionEvent.Type.OPEN_QUICK_CONTACT_FROM_CONTACTS_FRAGMENT_BADGE, true);
  }

  public QuickContactBadge getPhoto() {
    return photo;
  }

  public String getHeader() {
    return headerText;
  }

  public TextView getHeaderView() {
    return header;
  }

  @Override
  public void onClick(View v) {
    switch (clickAction) {
      case ClickAction.OPEN_CONTACT_CARD:
        Logger.get(context)
            .logInteraction(InteractionEvent.Type.OPEN_QUICK_CONTACT_FROM_CONTACTS_FRAGMENT_ITEM);
        QuickContact.showQuickContact(
            photo.getContext(),
            photo,
            contactUri,
            QuickContact.MODE_LARGE,
            null /* excludeMimes */);
        break;
      case ClickAction.INVALID:
      default:
        throw Assert.createIllegalStateFailException("Invalid click action.");
    }
  }
}
