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
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.dialer.calllog.ui.CoalescedAnnotatedCallLogCursorLoader.Row;
import com.android.dialer.calllogutils.CallLogDates;
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
    this.clock = clock;
  }

  /** @param cursor a cursor from {@link CoalescedAnnotatedCallLogCursorLoader}. */
  void bind(Cursor cursor) {
    CoalescedAnnotatedCallLogCursorLoader.Row row =
        new CoalescedAnnotatedCallLogCursorLoader.Row(cursor);

    // TODO(zachh): Handle RTL properly.
    primaryTextView.setText(buildPrimaryText(row));
    secondaryTextView.setText(buildSecondaryText(row));

    if (row.isNew()) {
      // TODO(zachh): Figure out correct styling for new/missed/unread calls.
      primaryTextView.setTextAppearance(R.style.primary_textview_new_call);
      // TODO(zachh): Styling for call type icons when the call is new.
      secondaryTextView.setTextAppearance(R.style.secondary_textview_new_call);
    }

    setPhoto(row);
    setPrimaryCallTypes(row);
    setSecondaryCallTypes(row);
    setPhoneAccounts(row);
  }

  private String buildPrimaryText(CoalescedAnnotatedCallLogCursorLoader.Row row) {
    StringBuilder primaryText = new StringBuilder();
    if (!TextUtils.isEmpty(row.name())) {
      primaryText.append(row.name());
    } else if (!TextUtils.isEmpty(row.formattedNumber())) {
      primaryText.append(row.formattedNumber());
    } else {
      // TODO(zachh): Handle CallLog.Calls.PRESENTATION_*, including Verizon restricted numbers.
      primaryText.append(context.getText(R.string.new_call_log_unknown));
    }
    if (row.numberCalls() > 1) {
      primaryText.append(String.format(Locale.getDefault(), " (%d)", row.numberCalls()));
    }
    return primaryText.toString();
  }

  private String buildSecondaryText(CoalescedAnnotatedCallLogCursorLoader.Row row) {
    /*
     * Rules: (Duo video, )?$Label|$Location • Date
     *
     * Examples:
     *   Duo Video, Mobile • Now
     *   Duo Video • 11:45pm
     *   Mobile • 11:45pm
     *   Mobile • Sunday
     *   Brooklyn, NJ • Jan 15
     *
     * Date rules:
     *   if < 1 minute ago: "Now"; else if today: HH:MM(am|pm); else if < 3 days: day; else: MON D
     */
    StringBuilder secondaryText = new StringBuilder();
    if ((row.features() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO) {
      // TODO(zachh): Add "Duo" prefix?
      secondaryText.append(context.getText(R.string.new_call_log_video));
    }
    String numberTypeLabel = row.numberTypeLabel();
    if (!TextUtils.isEmpty(numberTypeLabel)) {
      if (secondaryText.length() > 0) {
        secondaryText.append(", ");
      }
      secondaryText.append(numberTypeLabel);
    } else { // If there's a number type label, don't show the location.
      String location = row.geocodedLocation();
      if (!TextUtils.isEmpty(location)) {
        if (secondaryText.length() > 0) {
          secondaryText.append(", ");
        }
        secondaryText.append(location);
      }
    }
    if (secondaryText.length() > 0) {
      secondaryText.append(" • ");
    }
    secondaryText.append(
        CallLogDates.newCallLogTimestampLabel(context, clock.currentTimeMillis(), row.timestamp()));
    return secondaryText.toString();
  }

  private void setPhoto(Row row) {
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

  private void setPrimaryCallTypes(Row row) {
    // Only HD and Wifi icons are shown following the primary text.
    primaryCallTypeIconsView.setShowHd(
        MotorolaUtils.shouldShowHdIconInCallLog(context, row.features()));
    primaryCallTypeIconsView.setShowWifi(
        MotorolaUtils.shouldShowWifiIconInCallLog(context, row.features()));
  }

  private void setSecondaryCallTypes(Row row) {
    // Only call type icons are shown before the secondary text.
    for (int callType : row.callTypes().getTypeList()) {
      secondaryCallTypeIconsView.add(callType);
    }
    // TODO(zachh): Per new mocks, may need to add method to CallTypeIconsView to disable coloring.
  }

  private void setPhoneAccounts(Row row) {
    if (row.phoneAccountLabel() != null) {
      phoneAccountView.setText(row.phoneAccountLabel());
      phoneAccountView.setTextColor(row.phoneAccountColor());
      phoneAccountView.setVisibility(View.VISIBLE);
    }
  }
}
