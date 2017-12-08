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
package com.android.dialer.calllog.ui;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.calllog.ui.menu.NewCallLogMenu;
import com.android.dialer.calllogutils.CallLogEntryText;
import com.android.dialer.calllogutils.CallLogIntents;
import com.android.dialer.calllogutils.CallTypeIconsView;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.oem.MotorolaUtils;
import com.android.dialer.time.Clock;
import java.util.Locale;

/** {@link RecyclerView.ViewHolder} for the new call log. */
final class NewCallLogViewHolder extends RecyclerView.ViewHolder {

  private final Context context;
  private final TextView primaryTextView;
  private final TextView secondaryTextView;
  private final QuickContactBadge quickContactBadge;
  private final CallTypeIconsView primaryCallTypeIconsView; // Used for Wifi, HD icons
  private final CallTypeIconsView secondaryCallTypeIconsView; // Used for call types
  private final TextView phoneAccountView;
  private final ImageView menuButton;

  private final Clock clock;

  NewCallLogViewHolder(View view, Clock clock) {
    super(view);
    this.context = view.getContext();
    primaryTextView = view.findViewById(R.id.primary_text);
    secondaryTextView = view.findViewById(R.id.secondary_text);
    quickContactBadge = view.findViewById(R.id.quick_contact_photo);
    primaryCallTypeIconsView = view.findViewById(R.id.primary_call_type_icons);
    secondaryCallTypeIconsView = view.findViewById(R.id.secondary_call_type_icons);
    phoneAccountView = view.findViewById(R.id.phone_account);
    menuButton = view.findViewById(R.id.menu_button);

    this.clock = clock;
  }

  /** @param cursor a cursor from {@link CoalescedAnnotatedCallLogCursorLoader}. */
  void bind(Cursor cursor) {
    CoalescedRow row = CoalescedAnnotatedCallLogCursorLoader.toRow(cursor);

    // TODO(zachh): Handle RTL properly.
    primaryTextView.setText(CallLogEntryText.buildPrimaryText(context, row));
    secondaryTextView.setText(CallLogEntryText.buildSecondaryTextForEntries(context, clock, row));

    if (isNewMissedCall(row)) {
      primaryTextView.setTextAppearance(R.style.primary_textview_new_call);
      // TODO(zachh): Styling for call type icons when the call is new.
      secondaryTextView.setTextAppearance(R.style.secondary_textview_new_call);
    }

    setNumberCalls(row);
    setPhoto(row);
    setPrimaryCallTypes(row);
    setSecondaryCallTypes(row);
    setPhoneAccounts(row);
    setOnClickListenerForRow(row);
    setOnClickListenerForMenuButon(row);
  }

  private void setNumberCalls(CoalescedRow row) {
    // TODO(zachh): Number of calls shouldn't be text, but a circle with a number inside.
    int numberCalls = row.coalescedIds().getCoalescedIdCount();
    if (numberCalls > 1) {
      primaryTextView.append(String.format(Locale.getDefault(), " (%d)", numberCalls));
    }
  }

  private boolean isNewMissedCall(CoalescedRow row) {
    // Show missed call styling if the most recent call in the group was missed and it is still
    // marked as NEW. It is not clear what IS_READ should be used for and it is currently not used.
    return row.callType() == Calls.MISSED_TYPE && row.isNew();
  }

  private void setPhoto(CoalescedRow row) {
    // TODO(zachh): Set the contact type.
    ContactPhotoManager.getInstance(context)
        .loadDialerThumbnailOrPhoto(
            quickContactBadge,
            row.lookupUri() == null ? null : Uri.parse(row.lookupUri()),
            row.photoId(),
            row.photoUri() == null ? null : Uri.parse(row.photoUri()),
            row.name(),
            LetterTileDrawable.TYPE_DEFAULT);
  }

  private void setPrimaryCallTypes(CoalescedRow row) {
    // Only HD and Wifi icons are shown following the primary text.
    primaryCallTypeIconsView.setShowHd(
        (row.features() & Calls.FEATURES_HD_CALL) == Calls.FEATURES_HD_CALL);
    primaryCallTypeIconsView.setShowWifi(
        MotorolaUtils.shouldShowWifiIconInCallLog(context, row.features()));
  }

  private void setSecondaryCallTypes(CoalescedRow row) {
    // Only call type icon is shown before the secondary text.
    secondaryCallTypeIconsView.add(row.callType());

    // TODO(zachh): Per new mocks, may need to add method to CallTypeIconsView to disable coloring.
  }

  private void setPhoneAccounts(CoalescedRow row) {
    if (row.phoneAccountLabel() != null) {
      phoneAccountView.setText(row.phoneAccountLabel());
      phoneAccountView.setTextColor(row.phoneAccountColor());
      phoneAccountView.setVisibility(View.VISIBLE);
    }
  }

  private void setOnClickListenerForRow(CoalescedRow row) {
    itemView.setOnClickListener(
        (view) -> {
          Intent callbackIntent = CallLogIntents.getCallBackIntent(context, row);
          if (callbackIntent != null) {
            context.startActivity(callbackIntent);
          }
        });
  }

  private void setOnClickListenerForMenuButon(CoalescedRow row) {
    menuButton.setOnClickListener(NewCallLogMenu.createOnClickListener(context, row));
  }
}
