/*

* Copyright (C) 2011 The Android Open Source Project
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
package com.android.dialer.app.list;

import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.QuickContact;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import com.android.contacts.common.list.ContactEntry;
import com.android.dialer.app.R;
import com.android.dialer.widget.BidiTextView;

/** Displays the contact's picture overlaid with their name and number type in a tile. */
public class PhoneFavoriteSquareTileView extends PhoneFavoriteTileView {

  private final float heightToWidthRatio;

  private ImageButton secondaryButton;

  private ContactEntry contactEntry;

  public PhoneFavoriteSquareTileView(Context context, AttributeSet attrs) {
    super(context, attrs);

    heightToWidthRatio =
        getResources().getFraction(R.dimen.contact_tile_height_to_width_ratio, 1, 1);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    BidiTextView nameView = findViewById(R.id.contact_tile_name);
    nameView.setElegantTextHeight(false);

    TextView phoneTypeView = findViewById(R.id.contact_tile_phone_type);
    phoneTypeView.setElegantTextHeight(false);
    secondaryButton = findViewById(R.id.contact_tile_secondary_button);
  }

  @Override
  protected int getApproximateImageSize() {
    // The picture is the full size of the tile (minus some padding, but we can be generous)
    return getWidth();
  }

  private void launchQuickContact() {
    QuickContact.showQuickContact(
        getContext(),
        PhoneFavoriteSquareTileView.this,
        getLookupUri(),
        null,
        Phone.CONTENT_ITEM_TYPE);
  }

  @Override
  public void loadFromContact(ContactEntry entry) {
    super.loadFromContact(entry);
    if (entry != null) {
      secondaryButton.setOnClickListener(
          new OnClickListener() {
            @Override
            public void onClick(View v) {
              launchQuickContact();
            }
          });
    }
    contactEntry = entry;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    final int width = MeasureSpec.getSize(widthMeasureSpec);
    final int height = (int) (heightToWidthRatio * width);
    final int count = getChildCount();
    for (int i = 0; i < count; i++) {
      getChildAt(i)
          .measure(
              MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
              MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
    setMeasuredDimension(width, height);
  }

  @Override
  protected String getNameForView(ContactEntry contactEntry) {
    return contactEntry.getPreferredDisplayName(getContext());
  }

  public ContactEntry getContactEntry() {
    return contactEntry;
  }
}
