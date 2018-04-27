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
import android.support.design.widget.BottomSheetDialog;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
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
    super(context);
    this.modules = modules;
    this.historyItemBottomSheetHeaderInfo = historyItemBottomSheetHeaderInfo;
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
    super.onCreate(bundle);
    LinearLayout container = Assert.isNotNull(findViewById(R.id.action_container));
    container.addView(getContactView(container));

    for (HistoryItemActionModule module : modules) {
      if (module instanceof DividerModule) {
        container.addView(getDividerView(container));
      } else {
        container.addView(getModuleView(container, module));
      }
    }
  }

  private View getContactView(ViewGroup container) {
    LayoutInflater inflater = LayoutInflater.from(getContext());
    View contactView = inflater.inflate(R.layout.contact_layout, container, false);

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
    return contactView;
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
