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

package com.android.dialer.speeddial;

import android.content.Context;
import android.provider.ContactsContract.Contacts;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.glidephotomanager.GlidePhotoManagerComponent;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.speeddial.loader.SpeedDialUiItem;

/** ViewHolder for displaying suggested contacts in {@link SpeedDialFragment}. */
public class SuggestionViewHolder extends RecyclerView.ViewHolder implements OnClickListener {

  private final SuggestedContactsListener listener;

  private final QuickContactBadge photoView;
  private final TextView nameOrNumberView;
  private final TextView numberView;

  private String number;

  SuggestionViewHolder(View view, SuggestedContactsListener listener) {
    super(view);
    photoView = view.findViewById(R.id.avatar);
    nameOrNumberView = view.findViewById(R.id.name);
    numberView = view.findViewById(R.id.number);
    itemView.setOnClickListener(this);
    view.findViewById(R.id.overflow).setOnClickListener(this);
    this.listener = listener;
  }

  public void bind(Context context, SpeedDialUiItem speedDialUiItem) {
    Assert.isNotNull(speedDialUiItem.defaultChannel());
    number =
        PhoneNumberHelper.formatNumber(
            context,
            speedDialUiItem.defaultChannel().number(),
            GeoUtil.getCurrentCountryIso(context));

    String label = speedDialUiItem.defaultChannel().label();
    String secondaryInfo =
        TextUtils.isEmpty(label)
            ? number
            : context.getString(R.string.call_subject_type_and_number, label, number);

    nameOrNumberView.setText(speedDialUiItem.name());
    numberView.setText(secondaryInfo);

    GlidePhotoManagerComponent.get(context)
        .glidePhotoManager()
        .loadQuickContactBadge(
            photoView,
            PhotoInfo.newBuilder()
                .setPhotoId(speedDialUiItem.photoId())
                .setPhotoUri(speedDialUiItem.photoUri())
                .setName(speedDialUiItem.name())
                .setLookupUri(
                    Contacts.getLookupUri(speedDialUiItem.contactId(), speedDialUiItem.lookupKey())
                        .toString())
                .build());
  }

  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.overflow) {
      listener.onOverFlowMenuClicked(number);
    } else {
      listener.onRowClicked(number);
    }
  }

  /** Listener/Callback for {@link SuggestionViewHolder} parents. */
  public interface SuggestedContactsListener {

    void onOverFlowMenuClicked(String number);

    /** Called when a suggested contact is clicked. */
    void onRowClicked(String number);
  }
}
