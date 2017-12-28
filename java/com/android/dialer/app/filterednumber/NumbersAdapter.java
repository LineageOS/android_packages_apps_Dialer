/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.dialer.app.filterednumber;

import android.app.FragmentManager;
import android.content.Context;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.view.View;
import android.widget.QuickContactBadge;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import com.android.dialer.app.R;
import com.android.dialer.compat.CompatUtils;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.contactphoto.ContactPhotoManager.DefaultImageRequest;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.phonenumbercache.ContactInfoHelper;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.util.UriUtils;

/** TODO(calderwoodra): documentation */
public class NumbersAdapter extends SimpleCursorAdapter {

  private final Context context;
  private final FragmentManager fragmentManager;
  private final ContactInfoHelper contactInfoHelper;
  private final BidiFormatter bidiFormatter = BidiFormatter.getInstance();
  private final ContactPhotoManager contactPhotoManager;

  public NumbersAdapter(
      Context context,
      FragmentManager fragmentManager,
      ContactInfoHelper contactInfoHelper,
      ContactPhotoManager contactPhotoManager) {
    super(context, R.layout.blocked_number_item, null, new String[] {}, new int[] {}, 0);
    this.context = context;
    this.fragmentManager = fragmentManager;
    this.contactInfoHelper = contactInfoHelper;
    this.contactPhotoManager = contactPhotoManager;
  }

  public void updateView(View view, String number, String countryIso) {
    final TextView callerName = (TextView) view.findViewById(R.id.caller_name);
    final TextView callerNumber = (TextView) view.findViewById(R.id.caller_number);
    final QuickContactBadge quickContactBadge =
        (QuickContactBadge) view.findViewById(R.id.quick_contact_photo);
    quickContactBadge.setOverlay(null);
    if (CompatUtils.hasPrioritizedMimeType()) {
      quickContactBadge.setPrioritizedMimeType(Phone.CONTENT_ITEM_TYPE);
    }

    ContactInfo info = contactInfoHelper.lookupNumber(number, countryIso);
    if (info == null) {
      info = new ContactInfo();
      info.number = number;
    }
    final CharSequence locationOrType = getNumberTypeOrLocation(info, countryIso);
    final String displayNumber = getDisplayNumber(info);
    final String displayNumberStr =
        bidiFormatter.unicodeWrap(displayNumber, TextDirectionHeuristics.LTR);

    String nameForDefaultImage;
    if (!TextUtils.isEmpty(info.name)) {
      nameForDefaultImage = info.name;
      callerName.setText(info.name);
      callerNumber.setText(locationOrType + " " + displayNumberStr);
    } else {
      nameForDefaultImage = displayNumber;
      callerName.setText(displayNumberStr);
      if (!TextUtils.isEmpty(locationOrType)) {
        callerNumber.setText(locationOrType);
        callerNumber.setVisibility(View.VISIBLE);
      } else {
        callerNumber.setVisibility(View.GONE);
      }
    }
    loadContactPhoto(info, nameForDefaultImage, quickContactBadge);
  }

  private void loadContactPhoto(ContactInfo info, String displayName, QuickContactBadge badge) {
    final String lookupKey =
        info.lookupUri == null ? null : UriUtils.getLookupKeyFromUri(info.lookupUri);
    final int contactType =
        contactInfoHelper.isBusiness(info.sourceType)
            ? LetterTileDrawable.TYPE_BUSINESS
            : LetterTileDrawable.TYPE_DEFAULT;
    final DefaultImageRequest request =
        new DefaultImageRequest(displayName, lookupKey, contactType, true /* isCircular */);
    badge.assignContactUri(info.lookupUri);
    badge.setContentDescription(
        context.getResources().getString(R.string.description_contact_details, displayName));
    contactPhotoManager.loadDirectoryPhoto(
        badge, info.photoUri, false /* darkTheme */, true /* isCircular */, request);
  }

  private String getDisplayNumber(ContactInfo info) {
    if (!TextUtils.isEmpty(info.formattedNumber)) {
      return info.formattedNumber;
    } else if (!TextUtils.isEmpty(info.number)) {
      return info.number;
    } else {
      return "";
    }
  }

  private CharSequence getNumberTypeOrLocation(ContactInfo info, String countryIso) {
    if (!TextUtils.isEmpty(info.name)) {
      return ContactsContract.CommonDataKinds.Phone.getTypeLabel(
          context.getResources(), info.type, info.label);
    } else {
      return PhoneNumberHelper.getGeoDescription(context, info.number, countryIso);
    }
  }

  protected Context getContext() {
    return context;
  }

  protected FragmentManager getFragmentManager() {
    return fragmentManager;
  }
}
