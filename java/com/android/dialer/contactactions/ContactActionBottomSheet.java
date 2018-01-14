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

package com.android.dialer.contactactions;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.BottomSheetDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.contactactions.ContactPrimaryActionInfo.PhotoInfo;
import com.android.dialer.contactphoto.ContactPhotoManager;
import java.util.List;

/**
 * {@link BottomSheetDialog} used for building a list of contact actions in a bottom sheet menu.
 *
 * <p>{@link #show(Context, ContactPrimaryActionInfo, List)} should be used to create and display
 * the menu. Modules are built using {@link ContactActionModule} and some defaults are provided by
 * {@link IntentModule} and {@link DividerModule}.
 */
public class ContactActionBottomSheet extends BottomSheetDialog implements OnClickListener {

  private final List<ContactActionModule> modules;
  private final ContactPrimaryActionInfo contactPrimaryActionInfo;

  private ContactActionBottomSheet(
      Context context,
      ContactPrimaryActionInfo contactPrimaryActionInfo,
      List<ContactActionModule> modules) {
    super(context);
    this.modules = modules;
    this.contactPrimaryActionInfo = contactPrimaryActionInfo;
    setContentView(LayoutInflater.from(context).inflate(R.layout.sheet_layout, null));
  }

  public static ContactActionBottomSheet show(
      Context context,
      ContactPrimaryActionInfo contactPrimaryActionInfo,
      List<ContactActionModule> modules) {
    ContactActionBottomSheet sheet =
        new ContactActionBottomSheet(context, contactPrimaryActionInfo, modules);
    sheet.show();
    return sheet;
  }

  @Override
  protected void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    LinearLayout container = Assert.isNotNull(findViewById(R.id.action_container));
    container.addView(getContactView(container));

    for (ContactActionModule module : modules) {
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

    // TODO(zachh): The contact image should be badged with a video icon if it is for a video call.
    PhotoInfo photoInfo = contactPrimaryActionInfo.photoInfo();
    ContactPhotoManager.getInstance(getContext())
        .loadDialerThumbnailOrPhoto(
            contactView.findViewById(R.id.quick_contact_photo),
            !TextUtils.isEmpty(photoInfo.lookupUri()) ? Uri.parse(photoInfo.lookupUri()) : null,
            photoInfo.photoId(),
            !TextUtils.isEmpty(photoInfo.photoUri()) ? Uri.parse(photoInfo.photoUri()) : null,
            photoInfo.displayName(),
            photoInfo.contactType());

    TextView primaryTextView = contactView.findViewById(R.id.primary_text);
    TextView secondaryTextView = contactView.findViewById(R.id.secondary_text);

    primaryTextView.setText(contactPrimaryActionInfo.primaryText());
    if (!TextUtils.isEmpty(contactPrimaryActionInfo.secondaryText())) {
      secondaryTextView.setText(contactPrimaryActionInfo.secondaryText());
    } else {
      secondaryTextView.setVisibility(View.GONE);
      secondaryTextView.setText(null);
    }
    if (contactPrimaryActionInfo.intent() != null) {
      contactView.setOnClickListener(
          (view) -> {
            getContext().startActivity(contactPrimaryActionInfo.intent());
            dismiss();
          });
    }
    return contactView;
  }

  private View getDividerView(ViewGroup container) {
    LayoutInflater inflater = LayoutInflater.from(getContext());
    return inflater.inflate(R.layout.divider_layout, container, false);
  }

  private View getModuleView(ViewGroup container, ContactActionModule module) {
    LayoutInflater inflater = LayoutInflater.from(getContext());
    View moduleView = inflater.inflate(R.layout.module_layout, container, false);
    ((TextView) moduleView.findViewById(R.id.module_text)).setText(module.getStringId());
    ((ImageView) moduleView.findViewById(R.id.module_image))
        .setImageResource(module.getDrawableId());
    moduleView.setOnClickListener(this);
    moduleView.setTag(module);
    return moduleView;
  }

  @Override
  public void onClick(View view) {
    if (((ContactActionModule) view.getTag()).onClick()) {
      dismiss();
    }
  }
}
