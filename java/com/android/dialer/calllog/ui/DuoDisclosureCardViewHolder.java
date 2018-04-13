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
 * limitations under the License.
 */

package com.android.dialer.calllog.ui;

import android.content.Context;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.spannable.ContentWithLearnMoreSpanner;

/** ViewHolder for {@link NewCallLogAdapter} to display the Duo disclosure card. */
public class DuoDisclosureCardViewHolder extends ViewHolder {

  private final Button okButton;

  DuoDisclosureCardViewHolder(View itemView) {
    super(itemView);

    Context context = itemView.getContext();

    // Set the Duo logo.
    ImageView duoLogoView = itemView.findViewById(R.id.new_call_log_duo_disclosure_card_logo);
    duoLogoView.setImageResource(DuoComponent.get(context).getDuo().getLogo());

    // Set detailed text with a "learn more" link.
    TextView cardDetailsView = itemView.findViewById(R.id.new_call_log_duo_disclosure_card_details);
    cardDetailsView.setText(
        new ContentWithLearnMoreSpanner(context)
            .create(
                context.getResources().getString(R.string.new_call_log_duo_disclosure_card_details),
                ConfigProviderBindings.get(context)
                    .getString(
                        "duo_disclosure_link_full_url",
                        "http://support.google.com/pixelphone/?p=dialer_duo")));
    cardDetailsView.setMovementMethod(LinkMovementMethod.getInstance()); // make the link clickable

    // Obtain a reference to the "OK, got it" button.
    okButton = itemView.findViewById(R.id.new_call_log_duo_disclosure_card_ok);
  }

  void setDismissListener(OnClickListener listener) {
    okButton.setOnClickListener(listener);
  }
}
