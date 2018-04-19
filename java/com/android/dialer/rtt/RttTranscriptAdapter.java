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
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.common.LogUtil;
import com.android.dialer.glidephotomanager.PhotoInfo;

/** Adapter class for holding RTT chat data. */
public class RttTranscriptAdapter extends RecyclerView.Adapter<RttTranscriptMessageViewHolder> {

  private PhotoInfo photoInfo;

  private final Context context;
  private RttTranscript rttTranscript;
  private int firstPositionToShowTimestamp;

  RttTranscriptAdapter(Context context) {
    this.context = context;
  }

  @Override
  public RttTranscriptMessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    LayoutInflater layoutInflater = LayoutInflater.from(context);
    View view = layoutInflater.inflate(R.layout.rtt_transcript_list_item, parent, false);
    return new RttTranscriptMessageViewHolder(view);
  }

  @Override
  public int getItemViewType(int position) {
    return super.getItemViewType(position);
  }

  @Override
  public void onBindViewHolder(RttTranscriptMessageViewHolder rttChatMessageViewHolder, int i) {
    boolean isSameGroup = false;
    boolean hasMoreInSameGroup = false;
    RttTranscriptMessage rttTranscriptMessage = rttTranscript.getMessages(i);
    if (i > 0) {
      isSameGroup =
          rttTranscriptMessage.getIsRemote() == rttTranscript.getMessages(i - 1).getIsRemote();
    }
    if (i + 1 < getItemCount()) {
      hasMoreInSameGroup =
          rttTranscriptMessage.getIsRemote() == rttTranscript.getMessages(i + 1).getIsRemote();
    }
    rttChatMessageViewHolder.setMessage(rttTranscriptMessage, isSameGroup, photoInfo);
    if (hasMoreInSameGroup) {
      rttChatMessageViewHolder.hideTimestamp();
    } else {
      rttChatMessageViewHolder.showTimestamp(
          rttTranscriptMessage.getTimestamp(),
          rttTranscriptMessage.getIsRemote(),
          i == firstPositionToShowTimestamp);
    }
  }

  @Override
  public int getItemCount() {
    if (rttTranscript == null) {
      return 0;
    }
    return rttTranscript.getMessagesCount();
  }

  void setRttTranscript(RttTranscript rttTranscript) {
    if (rttTranscript == null) {
      LogUtil.w("RttTranscriptAdapter.setRttTranscript", "null RttTranscript");
      return;
    }
    this.rttTranscript = rttTranscript;
    firstPositionToShowTimestamp = findFirstPositionToShowTimestamp(rttTranscript);

    notifyDataSetChanged();
  }

  /**
   * Returns first position of message that should show time stamp. This is usually the last one of
   * first grouped messages.
   */
  protected static int findFirstPositionToShowTimestamp(RttTranscript rttTranscript) {
    int i = 0;
    while (i + 1 < rttTranscript.getMessagesCount()
        && rttTranscript.getMessages(i).getIsRemote()
            == rttTranscript.getMessages(i + 1).getIsRemote()) {
      i++;
    }
    return i;
  }

  void setPhotoInfo(PhotoInfo photoInfo) {
    this.photoInfo = photoInfo;
  }
}
