/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.rtt;

import android.content.Context;
import android.content.res.Resources;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import com.android.dialer.glidephotomanager.GlidePhotoManagerComponent;
import com.android.dialer.glidephotomanager.PhotoInfo;

/** ViewHolder class for RTT chat message bubble. */
public class RttTranscriptMessageViewHolder extends ViewHolder {

  private final TextView messageTextView;
  private final Resources resources;
  private final ImageView avatarImageView;
  private final View container;
  private final TextView timestampTextView;

  RttTranscriptMessageViewHolder(View view) {
    super(view);
    container = view.findViewById(R.id.rtt_chat_message_container);
    messageTextView = view.findViewById(R.id.rtt_chat_message);
    avatarImageView = view.findViewById(R.id.rtt_chat_avatar);
    timestampTextView = view.findViewById(R.id.rtt_chat_timestamp);
    resources = view.getResources();
  }

  void setMessage(RttTranscriptMessage message, boolean isSameGroup, PhotoInfo photoInfo) {
    messageTextView.setText(message.getContent());
    LinearLayout.LayoutParams params = (LayoutParams) container.getLayoutParams();
    params.gravity = message.getIsRemote() ? Gravity.START : Gravity.END;
    params.topMargin =
        isSameGroup
            ? resources.getDimensionPixelSize(R.dimen.rtt_transcript_same_group_message_margin_top)
            : resources.getDimensionPixelSize(R.dimen.rtt_transcript_message_margin_top);
    container.setLayoutParams(params);
    messageTextView.setEnabled(message.getIsRemote());
    if (message.getIsRemote()) {
      if (isSameGroup) {
        avatarImageView.setVisibility(View.INVISIBLE);
      } else {
        avatarImageView.setVisibility(View.VISIBLE);
        GlidePhotoManagerComponent.get(container.getContext())
            .glidePhotoManager()
            .loadContactPhoto(avatarImageView, photoInfo);
      }
      messageTextView.setTextAppearance(R.style.RttTranscriptBubble_Remote);
    } else {
      avatarImageView.setVisibility(View.GONE);
      messageTextView.setTextAppearance(R.style.RttTranscriptBubble_Local);
    }
  }

  void showTimestamp(long timestamp, boolean isRemote, boolean showFullDate) {
    timestampTextView.setVisibility(View.VISIBLE);
    timestampTextView.setText(
        getTimestampText(timestampTextView.getContext(), timestamp, showFullDate));
    timestampTextView.setGravity(isRemote ? Gravity.START : Gravity.END);
  }

  void hideTimestamp() {
    timestampTextView.setVisibility(View.GONE);
  }

  private String getTimestampText(Context context, long timestamp, boolean showFullDate) {
    return DateUtils.formatDateTime(
        context,
        timestamp,
        showFullDate
            ? DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME
            : DateUtils.FORMAT_SHOW_TIME);
  }
}
