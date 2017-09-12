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
import android.database.Cursor;
import android.view.View;
import com.android.dialer.app.R;
import com.android.dialer.blocking.FilteredNumbersUtil;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.phonenumbercache.ContactInfoHelper;

/** TODO(calderwoodra): documentation */
public class ViewNumbersToImportAdapter extends NumbersAdapter {

  private ViewNumbersToImportAdapter(
      Context context,
      FragmentManager fragmentManager,
      ContactInfoHelper contactInfoHelper,
      ContactPhotoManager contactPhotoManager) {
    super(context, fragmentManager, contactInfoHelper, contactPhotoManager);
  }

  public static ViewNumbersToImportAdapter newViewNumbersToImportAdapter(
      Context context, FragmentManager fragmentManager) {
    return new ViewNumbersToImportAdapter(
        context,
        fragmentManager,
        new ContactInfoHelper(context, GeoUtil.getCurrentCountryIso(context)),
        ContactPhotoManager.getInstance(context));
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    super.bindView(view, context, cursor);

    final String number = cursor.getString(FilteredNumbersUtil.PhoneQuery.NUMBER_COLUMN_INDEX);

    view.findViewById(R.id.delete_button).setVisibility(View.GONE);
    updateView(view, number, null /* countryIso */);
  }
}
