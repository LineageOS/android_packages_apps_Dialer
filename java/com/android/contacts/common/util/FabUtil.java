/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.res.Resources;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ListView;
import com.android.contacts.common.R;
import com.android.dialer.compat.CompatUtils;

/** Provides static functions to work with views */
public class FabUtil {

  private static final ViewOutlineProvider OVAL_OUTLINE_PROVIDER =
      new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
          outline.setOval(0, 0, view.getWidth(), view.getHeight());
        }
      };

  private FabUtil() {}

  /**
   * Configures the floating action button, clipping it to a circle and setting its translation z
   *
   * @param fabView the float action button's view
   * @param res the resources file
   */
  public static void setupFloatingActionButton(View fabView, Resources res) {
    if (CompatUtils.isLollipopCompatible()) {
      fabView.setOutlineProvider(OVAL_OUTLINE_PROVIDER);
      fabView.setTranslationZ(
          res.getDimensionPixelSize(R.dimen.floating_action_button_translation_z));
    }
  }

  /**
   * Adds padding to the bottom of the given {@link ListView} so that the floating action button
   * does not obscure any content.
   *
   * @param listView to add the padding to
   * @param res valid resources object
   */
  public static void addBottomPaddingToListViewForFab(ListView listView, Resources res) {
    final int fabPadding =
        res.getDimensionPixelSize(R.dimen.floating_action_button_list_bottom_padding);
    listView.setPaddingRelative(
        listView.getPaddingStart(),
        listView.getPaddingTop(),
        listView.getPaddingEnd(),
        listView.getPaddingBottom() + fabPadding);
    listView.setClipToPadding(false);
  }
}
