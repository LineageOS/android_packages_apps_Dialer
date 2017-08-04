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
import com.android.contacts.common.ContactPhotoManager;
import com.android.dialer.common.Assert;
import com.android.dialer.dialercontact.DialerContact;
import java.util.List;

/**
 * {@link BottomSheetDialog} used for building a list of contact actions in a bottom sheet menu.
 *
 * <p>{@link #show(Context, DialerContact, List)} should be used to create and display the menu.
 * Modules are built using {@link ContactActionModule} and some defaults are provided by {@link
 * IntentModule} and {@link DividerModule}.
 */
public class ContactActionBottomSheet extends BottomSheetDialog implements OnClickListener {

  private final List<ContactActionModule> modules;
  private final DialerContact contact;

  private ContactActionBottomSheet(
      Context context, DialerContact contact, List<ContactActionModule> modules) {
    super(context);
    this.modules = modules;
    this.contact = contact;
    setContentView(LayoutInflater.from(context).inflate(R.layout.sheet_layout, null));
  }

  public static ContactActionBottomSheet show(
      Context context, DialerContact contact, List<ContactActionModule> modules) {
    ContactActionBottomSheet sheet = new ContactActionBottomSheet(context, contact, modules);
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

  // TODO: add on click action to contact.
  private View getContactView(ViewGroup container) {
    LayoutInflater inflater = LayoutInflater.from(getContext());
    View contactView = inflater.inflate(R.layout.contact_layout, container, false);

    ContactPhotoManager.getInstance(getContext())
        .loadDialerThumbnailOrPhoto(
            contactView.findViewById(R.id.quick_contact_photo),
            contact.hasContactUri() ? Uri.parse(contact.getContactUri()) : null,
            contact.getPhotoId(),
            contact.hasPhotoUri() ? Uri.parse(contact.getPhotoUri()) : null,
            contact.getNameOrNumber(),
            contact.getContactType());

    TextView nameView = contactView.findViewById(R.id.contact_name);
    TextView numberView = contactView.findViewById(R.id.phone_number);

    nameView.setText(contact.getNameOrNumber());
    if (!TextUtils.isEmpty(contact.getDisplayNumber())) {
      numberView.setVisibility(View.VISIBLE);
      String secondaryInfo =
          TextUtils.isEmpty(contact.getNumberLabel())
              ? contact.getDisplayNumber()
              : getContext()
                  .getString(
                      com.android.contacts.common.R.string.call_subject_type_and_number,
                      contact.getNumberLabel(),
                      contact.getDisplayNumber());
      numberView.setText(secondaryInfo);
    } else {
      numberView.setVisibility(View.GONE);
      numberView.setText(null);
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
