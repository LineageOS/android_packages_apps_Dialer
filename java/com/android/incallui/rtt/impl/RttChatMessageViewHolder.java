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

package com.android.incallui.rtt.impl;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import com.android.incallui.rtt.protocol.RttChatMessage;

/** ViewHolder class for RTT chat message bubble. */
public class RttChatMessageViewHolder extends ViewHolder {

  private final TextView messageTextView;
  private final Resources resources;
  private final ImageView avatarImageView;
  private final View container;

  RttChatMessageViewHolder(View view) {
    super(view);
    container = view.findViewById(R.id.rtt_chat_message_container);
    messageTextView = view.findViewById(R.id.rtt_chat_message);
    avatarImageView = view.findViewById(R.id.rtt_chat_avatar);
    resources = view.getResources();
  }

  void setMessage(RttChatMessage message, boolean isSameGroup, Drawable imageDrawable) {
    messageTextView.setText(message.getContent());
    LinearLayout.LayoutParams params = (LayoutParams) container.getLayoutParams();
    params.gravity = message.isRemote ? Gravity.START : Gravity.END;
    params.topMargin =
        isSameGroup
            ? resources.getDimensionPixelSize(R.dimen.rtt_same_group_message_margin_top)
            : resources.getDimensionPixelSize(R.dimen.rtt_message_margin_top);
    container.setLayoutParams(params);
    messageTextView.setEnabled(message.isRemote);
    if (message.isRemote) {
      if (isSameGroup) {
        avatarImageView.setVisibility(View.INVISIBLE);
      } else {
        avatarImageView.setVisibility(View.VISIBLE);
        avatarImageView.setImageDrawable(imageDrawable);
      }
    } else {
      avatarImageView.setVisibility(View.GONE);
    }
  }
}
