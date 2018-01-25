/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.contactphoto;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.dialer.NumberAttributes;

/**
 * Convert photo information in {@link NumberAttributes} to an URI suitable for {@link
 * ContactPhotoManager}.
 *
 * <p>This class is temporary. The new photo manager should take NumberAttributes directly.
 */
public final class NumberAttributeConverter {

  /**
   * Computes the photo URI from NumberAttributes.
   *
   * <p>The photo URI is shown in the quick contact badge in the main call log list or in the top
   * item of the bottom sheet menu.
   */
  @Nullable
  public static Uri getPhotoUri(Context context, NumberAttributes numberAttributes) {
    if (numberAttributes.getIsBlocked()) {
      return getResourceUri(context.getResources(), R.drawable.ic_block_grey_48dp);
    } else {
      return parseUri(numberAttributes.getPhotoUri());
    }
  }

  @Nullable
  private static Uri parseUri(@Nullable String uri) {
    return TextUtils.isEmpty(uri) ? null : Uri.parse(uri);
  }

  private static Uri getResourceUri(Resources resources, @DrawableRes int drawable) {
    return Uri.parse(
        ContentResolver.SCHEME_ANDROID_RESOURCE
            + "://"
            + resources.getResourcePackageName(drawable)
            + "/"
            + resources.getResourceTypeName(drawable)
            + "/"
            + resources.getResourceEntryName(drawable));
  }
}
