/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.app.contactinfo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import com.android.contacts.common.lettertiles.LetterTileDrawable;
import com.android.dialer.app.R;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.location.GeoUtil;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.phonenumbercache.ContactInfoHelper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Class to create the appropriate contact icon from a ContactInfo. This class is for synchronous,
 * blocking calls to generate bitmaps, while ContactCommons.ContactPhotoManager is to cache, manage
 * and update a ImageView asynchronously.
 */
public class ContactPhotoLoader {

  private final Context mContext;
  private final ContactInfo mContactInfo;

  public ContactPhotoLoader(Context context, ContactInfo contactInfo) {
    mContext = Objects.requireNonNull(context);
    mContactInfo = Objects.requireNonNull(contactInfo);
  }

  private static Bitmap drawableToBitmap(Drawable drawable, int width, int height) {
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
    drawable.draw(canvas);
    return bitmap;
  }

  /** Create a contact photo icon bitmap appropriate for the ContactInfo. */
  public Bitmap loadPhotoIcon() {
    Assert.isWorkerThread();
    int photoSize = mContext.getResources().getDimensionPixelSize(R.dimen.contact_photo_size);
    return drawableToBitmap(getIcon(), photoSize, photoSize);
  }

  @VisibleForTesting
  Drawable getIcon() {
    Drawable drawable = createPhotoIconDrawable();
    if (drawable == null) {
      drawable = createLetterTileDrawable();
    }
    return drawable;
  }

  /**
   * @return a {@link Drawable} of circular photo icon if the photo can be loaded, {@code null}
   *     otherwise.
   */
  @Nullable
  private Drawable createPhotoIconDrawable() {
    if (mContactInfo.photoUri == null) {
      return null;
    }
    try {
      InputStream input = mContext.getContentResolver().openInputStream(mContactInfo.photoUri);
      if (input == null) {
        LogUtil.w(
            "ContactPhotoLoader.createPhotoIconDrawable",
            "createPhotoIconDrawable: InputStream is null");
        return null;
      }
      Bitmap bitmap = BitmapFactory.decodeStream(input);
      input.close();

      if (bitmap == null) {
        LogUtil.w(
            "ContactPhotoLoader.createPhotoIconDrawable",
            "createPhotoIconDrawable: Bitmap is null");
        return null;
      }
      final RoundedBitmapDrawable drawable =
          RoundedBitmapDrawableFactory.create(mContext.getResources(), bitmap);
      drawable.setAntiAlias(true);
      drawable.setCircular(true);
      return drawable;
    } catch (IOException e) {
      LogUtil.e("ContactPhotoLoader.createPhotoIconDrawable", e.toString());
      return null;
    }
  }

  /** @return a {@link LetterTileDrawable} based on the ContactInfo. */
  private Drawable createLetterTileDrawable() {
    ContactInfoHelper helper =
        new ContactInfoHelper(mContext, GeoUtil.getCurrentCountryIso(mContext));
    LetterTileDrawable drawable = new LetterTileDrawable(mContext.getResources());
    drawable.setCanonicalDialerLetterTileDetails(
        mContactInfo.name,
        mContactInfo.lookupKey,
        LetterTileDrawable.SHAPE_CIRCLE,
        helper.isBusiness(mContactInfo.sourceType)
            ? LetterTileDrawable.TYPE_BUSINESS
            : LetterTileDrawable.TYPE_DEFAULT);
    return drawable;
  }
}
