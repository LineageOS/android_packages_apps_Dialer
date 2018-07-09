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
import android.support.v7.widget.RecyclerView;
import android.view.View;
import com.android.dialer.calldetails.CallDetailsEntryViewHolder.CallDetailsEntryListener;
import com.android.dialer.calldetails.CallDetailsFooterViewHolder.DeleteCallDetailsListener;
import com.android.dialer.calldetails.CallDetailsHeaderViewHolder.CallDetailsHeaderListener;
import com.android.dialer.callrecord.CallRecordingDataStore;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.android.dialer.lettertile.LetterTileDrawable;

/**
 * A {@link RecyclerView.Adapter} for {@link OldCallDetailsActivity}.
 *
 * <p>See {@link CallDetailsAdapterCommon} for logic shared between this adapter and {@link
 * CallDetailsAdapter}.
 */
final class OldCallDetailsAdapter extends CallDetailsAdapterCommon {

  /** Contains info to be shown in the header. */
  private final DialerContact contact;

  OldCallDetailsAdapter(
      Context context,
      DialerContact contact,
      CallDetailsEntries callDetailsEntries,
      CallDetailsEntryListener callDetailsEntryListener,
      CallDetailsHeaderListener callDetailsHeaderListener,
      CallDetailsFooterViewHolder.ReportCallIdListener reportCallIdListener,
      DeleteCallDetailsListener deleteCallDetailsListener,
      CallRecordingDataStore callRecordingDataStore) {
    super(
        context,
        callDetailsEntries,
        callDetailsEntryListener,
        callDetailsHeaderListener,
        reportCallIdListener,
        deleteCallDetailsListener,
        callRecordingDataStore);
    this.contact = contact;
  }

  @Override
  protected CallDetailsHeaderViewHolder createCallDetailsHeaderViewHolder(
      View container, CallDetailsHeaderListener callDetailsHeaderListener) {
    return new CallDetailsHeaderViewHolder(
        container, contact.getNumber(), contact.getPostDialDigits(), callDetailsHeaderListener);
  }

  @Override
  protected void bindCallDetailsHeaderViewHolder(
      CallDetailsHeaderViewHolder callDetailsHeaderViewHolder, int position) {
    callDetailsHeaderViewHolder.updateContactInfo(contact, getCallbackAction());
    callDetailsHeaderViewHolder.updateAssistedDialingInfo(
        getCallDetailsEntries().getEntries(position));
  }

  @Override
  protected String getNumber() {
    return contact.getNumber();
  }

  @Override
  protected String getPrimaryText() {
    return contact.getNameOrNumber();
  }

  @Override
  protected PhotoInfo getPhotoInfo() {
    PhotoInfo.Builder builder =
        PhotoInfo.newBuilder()
            .setPhotoUri(contact.getPhotoUri())
            .setPhotoId(contact.getPhotoId())
            .setName(contact.getNameOrNumber())
            .setLookupUri(contact.getContactUri());
    switch (contact.getContactType()) {
      case LetterTileDrawable.TYPE_VOICEMAIL:
        builder.setIsVoicemail(true);
        break;
      case LetterTileDrawable.TYPE_BUSINESS:
        builder.setIsBusiness(true);
        break;
      case LetterTileDrawable.TYPE_SPAM:
        builder.setIsSpam(true);
        break;
      default: // fall out
    }
    return builder.build();
  }
}
