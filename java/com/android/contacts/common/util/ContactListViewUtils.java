/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.contacts.common.util;

import android.view.View;
import android.widget.ListView;

/** Utilities for configuring ListViews with a card background. */
public class ContactListViewUtils {

  // These two constants will help add more padding for the text inside the card.
  private static final double TEXT_LEFT_PADDING_TO_CARD_PADDING_RATIO = 1.1;

  private static void addPaddingToView(
      ListView listView, int parentWidth, int listSpaceWeight, int listViewWeight) {
    if (listSpaceWeight > 0 && listViewWeight > 0) {
      double paddingPercent =
          (double) listSpaceWeight / (double) (listSpaceWeight * 2 + listViewWeight);
      listView.setPadding(
          (int) (parentWidth * paddingPercent * TEXT_LEFT_PADDING_TO_CARD_PADDING_RATIO),
          listView.getPaddingTop(),
          (int) (parentWidth * paddingPercent * TEXT_LEFT_PADDING_TO_CARD_PADDING_RATIO),
          listView.getPaddingBottom());
      // The EdgeEffect and ScrollBar need to span to the edge of the ListView's padding.
      listView.setClipToPadding(false);
      listView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
    }
  }
}
