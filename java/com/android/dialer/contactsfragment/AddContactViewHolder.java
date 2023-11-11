/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.dialer.contactsfragment;

import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;

import androidx.recyclerview.widget.RecyclerView;

import com.android.dialer.R;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;

/** ViewHolder for {@link ContactsFragment} to display add contact row. */
final class AddContactViewHolder extends RecyclerView.ViewHolder implements OnClickListener {

  private final Context context;

  AddContactViewHolder(View view) {
    super(view);
    view.setOnClickListener(this);
    context = view.getContext();
  }

  @Override
  public void onClick(View v) {
    DialerUtils.startActivityWithErrorToast(
        context, IntentUtil.getNewContactIntent(), R.string.add_contact_not_available);
  }
}
