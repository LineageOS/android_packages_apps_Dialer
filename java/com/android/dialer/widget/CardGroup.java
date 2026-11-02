/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.widget;

import com.android.dialer.R;

import com.google.android.material.card.MaterialCardView;

public final class CardGroup {

  private CardGroup() {}

  public static void apply(MaterialCardView card, boolean first, boolean last) {
    float large = card.getResources().getDimension(R.dimen.card_group_corner_radius);
    float small = card.getResources().getDimension(R.dimen.card_group_inner_corner_radius);
    float top = first ? large : small;
    float bottom = last ? large : small;
    card.setShapeAppearanceModel(
        card.getShapeAppearanceModel().toBuilder()
            .setTopLeftCornerSize(top)
            .setTopRightCornerSize(top)
            .setBottomLeftCornerSize(bottom)
            .setBottomRightCornerSize(bottom)
            .build());
  }
}
