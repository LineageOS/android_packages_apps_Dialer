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

import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.dialer.promotion.Promotion;

/** ViewHolder for {@link NewCallLogAdapter} to display the Duo disclosure card. */
public class PromotionCardViewHolder extends ViewHolder {

  /** Listener to be called when promotion card is dismissed. */
  interface DismissListener {
    void onDismiss();
  }

  private final Button okButton;
  private final Promotion promotion;

  PromotionCardViewHolder(View itemView, Promotion promotion) {
    super(itemView);
    this.promotion = promotion;

    ImageView iconView = itemView.findViewById(R.id.new_call_log_promotion_card_icon);
    iconView.setImageResource(promotion.getIconRes());

    TextView cardTitleView = itemView.findViewById(R.id.new_call_log_promotion_card_title);
    cardTitleView.setText(promotion.getTitle());

    TextView cardDetailsView = itemView.findViewById(R.id.new_call_log_promotion_card_details);
    cardDetailsView.setText(promotion.getDetails());
    cardDetailsView.setMovementMethod(LinkMovementMethod.getInstance()); // make the link clickable

    // Obtain a reference to the "OK, got it" button.
    okButton = itemView.findViewById(R.id.new_call_log_promotion_card_ok);
  }

  void setDismissListener(DismissListener listener) {
    okButton.setOnClickListener(
        v -> {
          promotion.dismiss();
          listener.onDismiss();
        });
  }
}
