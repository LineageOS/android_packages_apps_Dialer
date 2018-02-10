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
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.provider.CallLog.Calls;
import android.support.annotation.DrawableRes;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.calllog.ui.menu.NewCallLogMenu;
import com.android.dialer.calllogutils.CallLogEntryText;
import com.android.dialer.calllogutils.CallLogIntents;
import com.android.dialer.calllogutils.NumberAttributesConverter;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.compat.AppCompatConstants;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.glidephotomanager.GlidePhotoManager;
import com.android.dialer.oem.MotorolaUtils;
import com.android.dialer.time.Clock;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/** {@link RecyclerView.ViewHolder} for the new call log. */
final class NewCallLogViewHolder extends RecyclerView.ViewHolder {

  private final Context context;
  private final TextView primaryTextView;
  private final TextView callCountTextView;
  private final TextView secondaryTextView;
  private final QuickContactBadge quickContactBadge;
  private final ImageView callTypeIcon;
  private final ImageView hdIcon;
  private final ImageView wifiIcon;
  private final ImageView assistedDialIcon;
  private final TextView phoneAccountView;
  private final ImageView menuButton;

  private final Clock clock;
  private final RealtimeRowProcessor realtimeRowProcessor;
  private final ExecutorService uiExecutorService;

  private final GlidePhotoManager glidePhotoManager;

  private int currentRowId;

  NewCallLogViewHolder(
      View view,
      Clock clock,
      RealtimeRowProcessor realtimeRowProcessor,
      GlidePhotoManager glidePhotoManager) {
    super(view);
    this.context = view.getContext();
    primaryTextView = view.findViewById(R.id.primary_text);
    callCountTextView = view.findViewById(R.id.call_count);
    secondaryTextView = view.findViewById(R.id.secondary_text);
    quickContactBadge = view.findViewById(R.id.quick_contact_photo);
    callTypeIcon = view.findViewById(R.id.call_type_icon);
    hdIcon = view.findViewById(R.id.hd_icon);
    wifiIcon = view.findViewById(R.id.wifi_icon);
    assistedDialIcon = view.findViewById(R.id.assisted_dial_icon);
    phoneAccountView = view.findViewById(R.id.phone_account);
    menuButton = view.findViewById(R.id.menu_button);

    this.clock = clock;
    this.realtimeRowProcessor = realtimeRowProcessor;
    this.glidePhotoManager = glidePhotoManager;
    uiExecutorService = DialerExecutorComponent.get(context).uiExecutor();
  }

  /** @param cursor a cursor from {@link CoalescedAnnotatedCallLogCursorLoader}. */
  void bind(Cursor cursor) {
    CoalescedRow row = CoalescedAnnotatedCallLogCursorLoader.toRow(cursor);
    currentRowId = row.id(); // Used to make sure async updates are applied to the correct views

    // Even if there is additional real time processing necessary, we still want to immediately show
    // what information we have, rather than an empty card. For example, if CP2 information needs to
    // be queried on the fly, we can still show the phone number until the contact name loads.
    displayRow(row);

    // Note: This leaks the view holder via the callback (which is an inner class), but this is OK
    // because we only create ~10 of them (and they'll be collected assuming all jobs finish).
    Futures.addCallback(
        realtimeRowProcessor.applyRealtimeProcessing(row),
        new RealtimeRowFutureCallback(row),
        uiExecutorService);
  }

  private void displayRow(CoalescedRow row) {
    // TODO(zachh): Handle RTL properly.
    primaryTextView.setText(CallLogEntryText.buildPrimaryText(context, row));
    secondaryTextView.setText(CallLogEntryText.buildSecondaryTextForEntries(context, clock, row));

    if (isNewMissedCall(row)) {
      primaryTextView.setTextAppearance(R.style.primary_textview_new_call);
      callCountTextView.setTextAppearance(R.style.primary_textview_new_call);
      secondaryTextView.setTextAppearance(R.style.secondary_textview_new_call);
      phoneAccountView.setTextAppearance(R.style.phoneaccount_textview_new_call);
    } else {
      primaryTextView.setTextAppearance(R.style.primary_textview);
      callCountTextView.setTextAppearance(R.style.primary_textview);
      secondaryTextView.setTextAppearance(R.style.secondary_textview);
      phoneAccountView.setTextAppearance(R.style.phoneaccount_textview);
    }

    setNumberCalls(row);
    setPhoto(row);
    setFeatureIcons(row);
    setCallTypeIcon(row);
    setPhoneAccounts(row);
    setOnClickListenerForRow(row);
    setOnClickListenerForMenuButon(row);
  }

  private void setNumberCalls(CoalescedRow row) {
    int numberCalls = row.coalescedIds().getCoalescedIdCount();
    if (numberCalls > 1) {
      callCountTextView.setText(String.format(Locale.getDefault(), "(%d)", numberCalls));
      callCountTextView.setVisibility(View.VISIBLE);
    } else {
      callCountTextView.setVisibility(View.GONE);
    }
  }

  private boolean isNewMissedCall(CoalescedRow row) {
    // Show missed call styling if the most recent call in the group was missed and it is still
    // marked as NEW. It is not clear what IS_READ should be used for and it is currently not used.
    return row.callType() == Calls.MISSED_TYPE && row.isNew();
  }

  private void setPhoto(CoalescedRow row) {
    glidePhotoManager.loadQuickContactBadge(
        quickContactBadge,
        NumberAttributesConverter.toPhotoInfoBuilder(row.numberAttributes())
            .setFormattedNumber(row.formattedNumber())
            .build());
  }

  private void setFeatureIcons(CoalescedRow row) {
    ColorStateList colorStateList =
        ColorStateList.valueOf(
            context.getColor(
                isNewMissedCall(row)
                    ? R.color.feature_icon_unread_color
                    : R.color.feature_icon_read_color));

    // Handle HD Icon
    if ((row.features() & Calls.FEATURES_HD_CALL) == Calls.FEATURES_HD_CALL) {
      hdIcon.setVisibility(View.VISIBLE);
      hdIcon.setImageTintList(colorStateList);
    } else {
      hdIcon.setVisibility(View.GONE);
    }

    // Handle Wifi Icon
    if (MotorolaUtils.shouldShowWifiIconInCallLog(context, row.features())) {
      wifiIcon.setVisibility(View.VISIBLE);
      wifiIcon.setImageTintList(colorStateList);
    } else {
      wifiIcon.setVisibility(View.GONE);
    }

    // Handle Assisted Dialing Icon
    if ((row.features() & TelephonyManagerCompat.FEATURES_ASSISTED_DIALING)
        == TelephonyManagerCompat.FEATURES_ASSISTED_DIALING) {
      assistedDialIcon.setVisibility(View.VISIBLE);
      assistedDialIcon.setImageTintList(colorStateList);
    } else {
      assistedDialIcon.setVisibility(View.GONE);
    }
  }

  private void setCallTypeIcon(CoalescedRow row) {
    @DrawableRes int resId;
    switch (row.callType()) {
      case AppCompatConstants.CALLS_INCOMING_TYPE:
      case AppCompatConstants.CALLS_ANSWERED_EXTERNALLY_TYPE:
        resId = R.drawable.quantum_ic_call_received_vd_theme_24;
        break;
      case AppCompatConstants.CALLS_OUTGOING_TYPE:
        resId = R.drawable.quantum_ic_call_made_vd_theme_24;
        break;
      case AppCompatConstants.CALLS_MISSED_TYPE:
        resId = R.drawable.quantum_ic_call_missed_vd_theme_24;
        break;
      case AppCompatConstants.CALLS_VOICEMAIL_TYPE:
        throw new IllegalStateException("Voicemails not expected in call log");
      case AppCompatConstants.CALLS_BLOCKED_TYPE:
        resId = R.drawable.quantum_ic_block_vd_theme_24;
        break;
      default:
        // It is possible for users to end up with calls with unknown call types in their
        // call history, possibly due to 3rd party call log implementations (e.g. to
        // distinguish between rejected and missed calls). Instead of crashing, just
        // assume that all unknown call types are missed calls.
        resId = R.drawable.quantum_ic_call_missed_vd_theme_24;
        break;
    }
    callTypeIcon.setImageResource(resId);

    if (isNewMissedCall(row)) {
      callTypeIcon.setImageTintList(
          ColorStateList.valueOf(context.getColor(R.color.call_type_icon_unread_color)));
    } else {
      callTypeIcon.setImageTintList(
          ColorStateList.valueOf(context.getColor(R.color.call_type_icon_read_color)));
    }
  }

  private void setPhoneAccounts(CoalescedRow row) {
    if (row.phoneAccountLabel() != null) {
      phoneAccountView.setText(row.phoneAccountLabel());
      phoneAccountView.setTextColor(row.phoneAccountColor());
      phoneAccountView.setVisibility(View.VISIBLE);
    } else {
      phoneAccountView.setVisibility(View.GONE);
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
    menuButton.setOnClickListener(
        NewCallLogMenu.createOnClickListener(context, row, glidePhotoManager));
  }

  private class RealtimeRowFutureCallback implements FutureCallback<CoalescedRow> {
    private final CoalescedRow originalRow;

    RealtimeRowFutureCallback(CoalescedRow originalRow) {
      this.originalRow = originalRow;
    }

    @Override
    public void onSuccess(CoalescedRow updatedRow) {
      // If the user scrolled then this ViewHolder may not correspond to the completed task and
      // there's nothing to do.
      if (originalRow.id() != currentRowId) {
        return;
      }
      // Only update the UI if the updated row differs from the original row (which has already
      // been displayed).
      if (!updatedRow.equals(originalRow)) {
        displayRow(updatedRow);
      }
    }

    @Override
    public void onFailure(Throwable throwable) {
      throw new RuntimeException("realtime processing failed", throwable);
    }
  }
}
