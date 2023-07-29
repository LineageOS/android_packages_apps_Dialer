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

import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.dialer.R;
import com.android.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.android.dialer.calllogutils.CallLogDates;
import com.android.dialer.calllogutils.CallLogDurations;
import com.android.dialer.calllogutils.CallTypeHelper;
import com.android.dialer.calllogutils.CallTypeIconsView;
import com.android.dialer.callrecord.CallRecording;
import com.android.dialer.callrecord.CallRecordingDataStore;
import com.android.dialer.callrecord.impl.CallRecorderService;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.android.dialer.oem.MotorolaUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** ViewHolder for call entries in {@link OldCallDetailsActivity} or {@link CallDetailsActivity}. */
public class CallDetailsEntryViewHolder extends RecyclerView.ViewHolder {

  /** Listener for the call details header */
  interface CallDetailsEntryListener {
    /** Shows RTT transcript. */
    void showRttTranscript(String transcriptId, String primaryText, PhotoInfo photoInfo);
  }

  private final CallDetailsEntryListener callDetailsEntryListener;

  private final CallTypeIconsView callTypeIcon;
  private final TextView callTypeText;
  private final TextView callTime;
  private final TextView callDuration;
  private final TextView rttTranscript;
  private final TextView playbackButton;

  private final Context context;

  public CallDetailsEntryViewHolder(
      View container, CallDetailsEntryListener callDetailsEntryListener) {
    super(container);
    context = container.getContext();

    callTypeIcon = (CallTypeIconsView) container.findViewById(R.id.call_direction);
    callTypeText = (TextView) container.findViewById(R.id.call_type);
    callTime = (TextView) container.findViewById(R.id.call_time);
    callDuration = (TextView) container.findViewById(R.id.call_duration);

    playbackButton = (TextView) container.findViewById(R.id.play_recordings);
    rttTranscript = container.findViewById(R.id.rtt_transcript);
    this.callDetailsEntryListener = callDetailsEntryListener;
  }

  void setCallDetails(
      String number,
      String primaryText,
      PhotoInfo photoInfo,
      CallDetailsEntry entry,
      CallTypeHelper callTypeHelper,
      CallRecordingDataStore callRecordingDataStore) {
    int callType = entry.getCallType();
    boolean isVideoCall = (entry.getFeatures() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO;
    boolean isPulledCall =
        (entry.getFeatures() & Calls.FEATURES_PULLED_EXTERNALLY)
            == Calls.FEATURES_PULLED_EXTERNALLY;
    boolean isRttCall = (entry.getFeatures() & Calls.FEATURES_RTT) == Calls.FEATURES_RTT;

    callTime.setTextColor(getColorForCallType(context, callType));
    callTypeIcon.clear();
    callTypeIcon.add(callType);
    callTypeIcon.setShowVideo(isVideoCall);
    callTypeIcon.setShowHd(
        (entry.getFeatures() & Calls.FEATURES_HD_CALL) == Calls.FEATURES_HD_CALL);
    callTypeIcon.setShowWifi(
        MotorolaUtils.shouldShowWifiIconInCallLog(context, entry.getFeatures()));
    callTypeIcon.setShowRtt((entry.getFeatures() & Calls.FEATURES_RTT) == Calls.FEATURES_RTT);

    callTypeText.setText(
        callTypeHelper.getCallTypeText(callType, isVideoCall, isPulledCall));
    callTime.setText(CallLogDates.formatDate(context, entry.getDate()));

    if (CallTypeHelper.isMissedCallType(callType)) {
      callDuration.setVisibility(View.GONE);
    } else {
      callDuration.setVisibility(View.VISIBLE);
      callDuration.setText(
          CallLogDurations.formatDurationAndDataUsage(
              context, entry.getDuration(), entry.getDataUsage()));
      callDuration.setContentDescription(
          CallLogDurations.formatDurationAndDataUsageA11y(
              context, entry.getDuration(), entry.getDataUsage()));
    }

    // do this synchronously to prevent recordings from "popping in" after detail item is displayed
    final List<CallRecording> recordings;
    if (CallRecorderService.isEnabled(context)) {
      callRecordingDataStore.open(context); // opens unless already open
      recordings = callRecordingDataStore.getRecordings(number, entry.getDate());
    } else {
      recordings = null;
    }

    int count = recordings != null ? recordings.size() : 0;
    playbackButton.setOnClickListener(v -> handleRecordingClick(v, recordings));
    playbackButton.setText(
        context.getResources().getQuantityString(R.plurals.play_recordings, count, count));
    playbackButton.setVisibility(count > 0 ? View.VISIBLE : View.GONE);

    if (isRttCall) {
      if (entry.getHasRttTranscript()) {
        rttTranscript.setText(R.string.rtt_transcript_link);
        rttTranscript.setTextAppearance(R.style.RttTranscriptLink);
        rttTranscript.setClickable(true);
        rttTranscript.setOnClickListener(
            v ->
                callDetailsEntryListener.showRttTranscript(
                    entry.getCallMappingId(), primaryText, photoInfo));
      } else {
        rttTranscript.setText(R.string.rtt_transcript_not_available);
        rttTranscript.setTextAppearance(R.style.RttTranscriptMessage);
        rttTranscript.setClickable(false);
      }
      rttTranscript.setVisibility(View.VISIBLE);
    } else {
      rttTranscript.setVisibility(View.GONE);
    }
  }

  private void handleRecordingClick(View v, List<CallRecording> recordings) {
    final Context context = v.getContext();
    if (recordings.size() == 1) {
      playRecording(context, recordings.get(0));
    } else {
      PopupMenu menu = new PopupMenu(context, v);
      String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(),
          DateFormat.is24HourFormat(context) ? "Hmss" : "hmssa");
      SimpleDateFormat format = new SimpleDateFormat(pattern);

      for (int i = 0; i < recordings.size(); i++) {
        final long startTime = recordings.get(i).startRecordingTime;
        final String formattedDate = format.format(new Date(startTime));
        menu.getMenu().add(Menu.NONE, i, i, formattedDate);
      }
      menu.setOnMenuItemClickListener(item -> {
        playRecording(context, recordings.get(item.getItemId()));
        return true;
      });
      menu.show();
    }
 }

  private void playRecording(Context context, CallRecording recording) {
    Uri uri = ContentUris.withAppendedId(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, recording.mediaId);
    String extension = MimeTypeMap.getFileExtensionFromUrl(recording.fileName);
    String mime = !TextUtils.isEmpty(extension)
        ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) : "audio/*";
    try {
      Intent intent = new Intent(Intent.ACTION_VIEW)
          .setDataAndType(uri, mime)
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      context.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(context, R.string.call_playback_no_app_found_toast, Toast.LENGTH_LONG)
          .show();
    }
  }

  private static @ColorInt int getColorForCallType(Context context, int callType) {
    switch (callType) {
      case Calls.OUTGOING_TYPE:
      case Calls.VOICEMAIL_TYPE:
      case Calls.BLOCKED_TYPE:
      case Calls.INCOMING_TYPE:
      case Calls.ANSWERED_EXTERNALLY_TYPE:
      case Calls.REJECTED_TYPE:
        return ContextCompat.getColor(context, R.color.dialer_secondary_text_color);
      case Calls.MISSED_TYPE:
      default:
        // It is possible for users to end up with calls with unknown call types in their
        // call history, possibly due to 3rd party call log implementations (e.g. to
        // distinguish between rejected and missed calls). Instead of crashing, just
        // assume that all unknown call types are missed calls.
        return ContextCompat.getColor(context, R.color.dialer_red);
    }
  }
}
