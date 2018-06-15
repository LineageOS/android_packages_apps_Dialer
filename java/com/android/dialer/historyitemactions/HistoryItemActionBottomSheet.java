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
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.BottomSheetBehavior.BottomSheetCallback;
import android.support.design.widget.BottomSheetDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.Window;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.compat.android.support.design.bottomsheet.BottomSheetStateCompat;
import com.android.dialer.theme.base.ThemeComponent;
import com.android.dialer.widget.ContactPhotoView;
import com.google.common.collect.ImmutableSet;
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

  /**
   * An {@link OnPreDrawListener} that sets the contact layout's elevation if
   *
   * <ul>
   *   <li>the bottom sheet can expand to full screen, and
   *   <li>the bottom sheet is fully expanded.
   * </ul>
   *
   * <p>The reason an {@link OnPreDrawListener} instead of a {@link BottomSheetCallback} is used to
   * handle this is that the initial state of the bottom sheet will be STATE_EXPANDED when the touch
   * exploration (e.g., TalkBack) is enabled and {@link BottomSheetCallback} won't be triggered in
   * this case. See {@link #setupBottomSheetBehavior()} for details.
   */
  private final OnPreDrawListener onPreDrawListenerForContactLayout =
      () -> {
        View contactLayout = findViewById(R.id.contact_layout_root);
        View background = findViewById(android.support.design.R.id.touch_outside);
        View bottomSheet = findViewById(android.support.design.R.id.design_bottom_sheet);

        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);

        // If the height of the background is equal to that of the bottom sheet, the bottom sheet
        // *can* be expanded to full screen.
        contactLayout.setElevation(
            background.getHeight() == bottomSheet.getHeight()
                    && behavior.getState() == BottomSheetStateCompat.STATE_EXPANDED
                ? getContext()
                    .getResources()
                    .getDimensionPixelSize(R.dimen.contact_actions_header_elevation)
                : 0);

        return true; // Return true to proceed with the current drawing pass.
      };

  private LinearLayout contactLayout;

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
    contactLayout = Assert.isNotNull(findViewById(R.id.contact_layout_root));

    initBottomSheetState();
    setupBottomSheetBehavior();
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

  @Override
  public void onAttachedToWindow() {
    super.onAttachedToWindow();
    contactLayout.getViewTreeObserver().addOnPreDrawListener(onPreDrawListenerForContactLayout);
  }

  @Override
  public void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    contactLayout.getViewTreeObserver().removeOnPreDrawListener(onPreDrawListenerForContactLayout);
  }

  private void initBottomSheetState() {
    // If the touch exploration in the system (e.g., TalkBack) is enabled, the bottom sheet should
    // be fully expanded because sometimes services like TalkBack won't read all items when the
    // bottom sheet is not fully expanded.
    if (isTouchExplorationEnabled()) {
      BottomSheetBehavior<View> behavior =
          BottomSheetBehavior.from(findViewById(android.support.design.R.id.design_bottom_sheet));
      behavior.setState(BottomSheetStateCompat.STATE_EXPANDED);
    }
  }

  /**
   * Configures the bottom sheet behavior when its state changes.
   *
   * <p>If the touch exploration in the system (e.g., TalkBack) is enabled, the bottom sheet will be
   * canceled if it is in a final state other than {@link BottomSheetBehavior#STATE_EXPANDED}. This
   * is because sometimes services like TalkBack won't read all items when the bottom sheet is not
   * fully expanded.
   *
   * <p>If the touch exploration is disabled, cancel the bottom sheet when it is in {@link
   * BottomSheetBehavior#STATE_HIDDEN}.
   */
  private void setupBottomSheetBehavior() {
    BottomSheetBehavior<View> behavior =
        BottomSheetBehavior.from(findViewById(android.support.design.R.id.design_bottom_sheet));
    behavior.setBottomSheetCallback(
        new BottomSheetCallback() {
          @Override
          public void onStateChanged(@NonNull View bottomSheet, int newState) {
            ImmutableSet<Integer> statesToCancelBottomSheet =
                isTouchExplorationEnabled()
                    ? ImmutableSet.of(
                        BottomSheetStateCompat.STATE_COLLAPSED,
                        BottomSheetStateCompat.STATE_HIDDEN,
                        BottomSheetStateCompat.STATE_HALF_EXPANDED)
                    : ImmutableSet.of(BottomSheetStateCompat.STATE_HIDDEN);

            if (statesToCancelBottomSheet.contains(newState)) {
              cancel();
            }

            // TODO(calderwoodra): set the status bar color when expanded, else translucent
          }

          @Override
          public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
        });
  }

  // Overwrites the window size since it doesn't match the parent.
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
    ContactPhotoView contactPhotoView = contactLayout.findViewById(R.id.contact_photo_view);
    contactPhotoView.setPhoto(historyItemBottomSheetHeaderInfo.getPhotoInfo());

    TextView primaryTextView = contactLayout.findViewById(R.id.primary_text);
    TextView secondaryTextView = contactLayout.findViewById(R.id.secondary_text);

    primaryTextView.setText(historyItemBottomSheetHeaderInfo.getPrimaryText());
    if (!TextUtils.isEmpty(historyItemBottomSheetHeaderInfo.getSecondaryText())) {
      secondaryTextView.setText(historyItemBottomSheetHeaderInfo.getSecondaryText());
    } else {
      secondaryTextView.setVisibility(View.GONE);
      secondaryTextView.setText(null);
    }
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
              ColorStateList.valueOf(ThemeComponent.get(getContext()).theme().getColorIcon()));
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

  private boolean isTouchExplorationEnabled() {
    AccessibilityManager accessibilityManager =
        getContext().getSystemService(AccessibilityManager.class);

    return accessibilityManager.isTouchExplorationEnabled();
  }
}
