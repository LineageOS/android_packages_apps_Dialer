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
import android.provider.CallLog.Calls;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.android.dialer.calllogutils.CallEntryFormatter;
import com.android.dialer.calllogutils.CallTypeHelper;
import com.android.dialer.calllogutils.CallTypeIconsView;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.AppCompatConstants;
import com.android.dialer.enrichedcall.historyquery.proto.HistoryResult;
import com.android.dialer.enrichedcall.historyquery.proto.HistoryResult.Type;
import com.android.dialer.oem.MotorolaUtils;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;

/** ViewHolder for call entries in {@link CallDetailsActivity}. */
public class CallDetailsEntryViewHolder extends ViewHolder {

  private final CallTypeIconsView callTypeIcon;
  private final TextView callTypeText;
  private final TextView callTime;
  private final TextView callDuration;

  private final View multimediaImageContainer;
  private final View multimediaDetailsContainer;
  private final View multimediaDivider;

  private final TextView multimediaDetails;
  private final TextView postCallNote;

  private final ImageView multimediaImage;

  // TODO: Display this when location is stored - b/36160042
  @SuppressWarnings("unused")
  private final TextView multimediaAttachmentsNumber;

  private final Context context;

  public CallDetailsEntryViewHolder(View container) {
    super(container);
    context = container.getContext();

    callTypeIcon = (CallTypeIconsView) container.findViewById(R.id.call_direction);
    callTypeText = (TextView) container.findViewById(R.id.call_type);
    callTime = (TextView) container.findViewById(R.id.call_time);
    callDuration = (TextView) container.findViewById(R.id.call_duration);

    multimediaImageContainer = container.findViewById(R.id.multimedia_image_container);
    multimediaDetailsContainer = container.findViewById(R.id.ec_container);
    multimediaDivider = container.findViewById(R.id.divider);
    multimediaDetails = (TextView) container.findViewById(R.id.multimedia_details);
    postCallNote = (TextView) container.findViewById(R.id.post_call_note);
    multimediaImage = (ImageView) container.findViewById(R.id.multimedia_image);
    multimediaAttachmentsNumber =
        (TextView) container.findViewById(R.id.multimedia_attachments_number);
  }

  void setCallDetails(
      String number,
      CallDetailsEntry entry,
      CallTypeHelper callTypeHelper,
      boolean showMultimediaDivider) {
    int callType = entry.getCallType();
    boolean isVideoCall = (entry.getFeatures() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO;
    boolean isPulledCall =
        (entry.getFeatures() & Calls.FEATURES_PULLED_EXTERNALLY)
            == Calls.FEATURES_PULLED_EXTERNALLY;

    callTime.setTextColor(getColorForCallType(context, callType));
    callTypeIcon.clear();
    callTypeIcon.add(callType);
    callTypeIcon.setShowVideo(isVideoCall);
    callTypeIcon.setShowHd(MotorolaUtils.shouldShowHdIconInCallLog(context, entry.getFeatures()));
    callTypeIcon.setShowWifi(
        MotorolaUtils.shouldShowWifiIconInCallLog(context, entry.getFeatures()));

    callTypeText.setText(callTypeHelper.getCallTypeText(callType, isVideoCall, isPulledCall));
    callTime.setText(CallEntryFormatter.formatDate(context, entry.getDate()));
    if (CallTypeHelper.isMissedCallType(callType)) {
      callDuration.setVisibility(View.GONE);
    } else {
      callDuration.setVisibility(View.VISIBLE);
      callDuration.setText(
          CallEntryFormatter.formatDurationAndDataUsage(
              context, entry.getDuration(), entry.getDataUsage()));
      callDuration.setContentDescription(
          CallEntryFormatter.formatDurationAndDataUsageA11y(
              context, entry.getDuration(), entry.getDataUsage()));
    }
    setMultimediaDetails(number, entry, showMultimediaDivider);
  }

  private void setMultimediaDetails(String number, CallDetailsEntry entry, boolean showDivider) {
    multimediaDivider.setVisibility(showDivider ? View.VISIBLE : View.GONE);
    if (entry.getHistoryResultsList().isEmpty()) {
      LogUtil.i("CallDetailsEntryViewHolder.setMultimediaDetails", "no data, hiding UI");
      multimediaDetailsContainer.setVisibility(View.GONE);
    } else {

      HistoryResult historyResult = entry.getHistoryResults(0);
      multimediaDetailsContainer.setVisibility(View.VISIBLE);
      multimediaDetailsContainer.setOnClickListener((v) -> startSmsIntent(context, number));
      multimediaImageContainer.setClipToOutline(true);

      if (!TextUtils.isEmpty(historyResult.getImageUri())) {
        LogUtil.i("CallDetailsEntryViewHolder.setMultimediaDetails", "setting image");
        multimediaImageContainer.setVisibility(View.VISIBLE);
        multimediaImage.setImageURI(Uri.parse(historyResult.getImageUri()));
        multimediaDetails.setText(
            isIncoming(historyResult) ? R.string.received_a_photo : R.string.sent_a_photo);
      } else {
        LogUtil.i("CallDetailsEntryViewHolder.setMultimediaDetails", "no image");
      }

      // Set text after image to overwrite the received/sent a photo text
      if (!TextUtils.isEmpty(historyResult.getText())) {
        LogUtil.i("CallDetailsEntryViewHolder.setMultimediaDetails", "showing text");
        multimediaDetails.setText(
            context.getString(R.string.message_in_quotes, historyResult.getText()));
      } else {
        LogUtil.i("CallDetailsEntryViewHolder.setMultimediaDetails", "no text");
      }

      if (entry.getHistoryResultsList().size() > 1
          && !TextUtils.isEmpty(entry.getHistoryResults(1).getText())) {
        LogUtil.i("CallDetailsEntryViewHolder.setMultimediaDetails", "showing post call note");
        postCallNote.setVisibility(View.VISIBLE);
        postCallNote.setText(
            context.getString(R.string.message_in_quotes, entry.getHistoryResults(1).getText()));
        postCallNote.setOnClickListener((v) -> startSmsIntent(context, number));
      } else {
        LogUtil.i("CallDetailsEntryViewHolder.setMultimediaDetails", "no post call note");
      }
    }
  }

  private void startSmsIntent(Context context, String number) {
    DialerUtils.startActivityWithErrorToast(context, IntentUtil.getSendSmsIntent(number));
  }

  private static boolean isIncoming(@NonNull HistoryResult historyResult) {
    return historyResult.getType() == Type.INCOMING_POST_CALL
        || historyResult.getType() == Type.INCOMING_CALL_COMPOSER;
  }

  private static @ColorInt int getColorForCallType(Context context, int callType) {
    switch (callType) {
      case AppCompatConstants.CALLS_OUTGOING_TYPE:
      case AppCompatConstants.CALLS_VOICEMAIL_TYPE:
      case AppCompatConstants.CALLS_BLOCKED_TYPE:
      case AppCompatConstants.CALLS_INCOMING_TYPE:
      case AppCompatConstants.CALLS_ANSWERED_EXTERNALLY_TYPE:
      case AppCompatConstants.CALLS_REJECTED_TYPE:
        return ContextCompat.getColor(context, R.color.dialer_secondary_text_color);
      case AppCompatConstants.CALLS_MISSED_TYPE:
      default:
        // It is possible for users to end up with calls with unknown call types in their
        // call history, possibly due to 3rd party call log implementations (e.g. to
        // distinguish between rejected and missed calls). Instead of crashing, just
        // assume that all unknown call types are missed calls.
        return ContextCompat.getColor(context, R.color.missed_call);
    }
  }
}
