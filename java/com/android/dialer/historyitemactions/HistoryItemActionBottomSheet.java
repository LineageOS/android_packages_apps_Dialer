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

package com.android.dialer.historyitemactions;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.bottomsheet.BottomSheetBehavior;
import android.support.design.bottomsheet.BottomSheetBehavior.BottomSheetCallback;
import android.support.design.bottomsheet.BottomSheetDialog;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.widget.ContactPhotoView;
import java.util.List;

/**
 * {@link BottomSheetDialog} used to show a list of actions in a bottom sheet menu.
 *
 * <p>{@link #show(Context, HistoryItemBottomSheetHeaderInfo, List)} should be used to create and
 * display the menu. Modules are built using {@link HistoryItemActionModule} and some defaults are
 * provided by {@link IntentModule} and {@link DividerModule}.
 */
public class HistoryItemActionBottomSheet extends BottomSheetDialog implements OnClickListener {

  private final List<HistoryItemActionModule> modules;
  private final HistoryItemBottomSheetHeaderInfo historyItemBottomSheetHeaderInfo;

  private HistoryItemActionBottomSheet(
      Context context,
      HistoryItemBottomSheetHeaderInfo historyItemBottomSheetHeaderInfo,
      List<HistoryItemActionModule> modules) {
    super(context, R.style.HistoryItemBottomSheet);
    this.modules = modules;
    this.historyItemBottomSheetHeaderInfo = Assert.isNotNull(historyItemBottomSheetHeaderInfo);
    setContentView(LayoutInflater.from(context).inflate(R.layout.sheet_layout, null));
  }

  public static HistoryItemActionBottomSheet show(
      Context context,
      HistoryItemBottomSheetHeaderInfo historyItemBottomSheetHeaderInfo,
      List<HistoryItemActionModule> modules) {
    HistoryItemActionBottomSheet sheet =
        new HistoryItemActionBottomSheet(context, historyItemBottomSheetHeaderInfo, modules);
    sheet.show();
    return sheet;
  }

  @Override
  protected void onCreate(Bundle bundle) {
    setupWindow();
    setupContactLayout();

    LinearLayout container = Assert.isNotNull(findViewById(R.id.action_container));
    for (HistoryItemActionModule module : modules) {
      if (module instanceof DividerModule) {
        container.addView(getDividerView(container));
      } else {
        container.addView(getModuleView(container, module));
      }
    }
  }

  // Overrwrites the window size since Dialog's don't match parent.
  private void setupWindow() {
    Window window = getWindow();
    if (window == null) {
      return;
    }
    // TODO(calderwoodra): set the nav bar color
    window.setLayout(
        /* width = */ ViewGroup.LayoutParams.MATCH_PARENT,
        /* height = */ ViewGroup.LayoutParams.MATCH_PARENT);
  }

  private void setupContactLayout() {
    View contactView = Assert.isNotNull(findViewById(R.id.contact_layout_root));

    ContactPhotoView contactPhotoView = contactView.findViewById(R.id.contact_photo_view);
    contactPhotoView.setPhoto(historyItemBottomSheetHeaderInfo.getPhotoInfo());

    TextView primaryTextView = contactView.findViewById(R.id.primary_text);
    TextView secondaryTextView = contactView.findViewById(R.id.secondary_text);

    primaryTextView.setText(historyItemBottomSheetHeaderInfo.getPrimaryText());
    if (!TextUtils.isEmpty(historyItemBottomSheetHeaderInfo.getSecondaryText())) {
      secondaryTextView.setText(historyItemBottomSheetHeaderInfo.getSecondaryText());
    } else {
      secondaryTextView.setVisibility(View.GONE);
      secondaryTextView.setText(null);
    }

    View background = findViewById(android.support.design.R.id.touch_outside);
    BottomSheetBehavior behavior =
        BottomSheetBehavior.from(findViewById(android.support.design.R.id.design_bottom_sheet));
    behavior.setBottomSheetCallback(
        new BottomSheetCallback() {
          @Override
          public void onStateChanged(@NonNull View bottomSheet, int newState) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
              cancel();
              return;
            }

            // If the bottomsheet can expand to full screen, set the header's elevation when it's
            // fully expanded.
            if (background.getHeight() == bottomSheet.getHeight()) {
              contactView.setElevation(
                  newState == BottomSheetBehavior.STATE_EXPANDED
                      ? getContext()
                          .getResources()
                          .getDimensionPixelSize(R.dimen.contact_actions_header_elevation)
                      : 0);
              // TODO(calderwoodra): set the status bar color when expanded, else translucent
            }
          }

          @Override
          public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
        });
  }

  private View getDividerView(ViewGroup container) {
    LayoutInflater inflater = LayoutInflater.from(getContext());
    return inflater.inflate(R.layout.divider_layout, container, false);
  }

  private View getModuleView(ViewGroup container, HistoryItemActionModule module) {
    LayoutInflater inflater = LayoutInflater.from(getContext());
    View moduleView = inflater.inflate(R.layout.module_layout, container, false);
    ((TextView) moduleView.findViewById(R.id.module_text)).setText(module.getStringId());
    ((ImageView) moduleView.findViewById(R.id.module_image))
        .setImageResource(module.getDrawableId());
    if (module.tintDrawable()) {
      ((ImageView) moduleView.findViewById(R.id.module_image))
          .setImageTintList(
              ColorStateList.valueOf(
                  ContextCompat.getColor(getContext(), R.color.secondary_text_color)));
    }
    moduleView.setOnClickListener(this);
    moduleView.setTag(module);
    return moduleView;
  }

  @Override
  public void onClick(View view) {
    if (((HistoryItemActionModule) view.getTag()).onClick()) {
      dismiss();
    }
  }
}
