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

package com.android.dialer.callcomposer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images.Media;
import android.support.v4.content.CursorLoader;

/** A BoundCursorLoader that reads local media on the device. */
public class GalleryCursorLoader extends CursorLoader {
  public static final String MEDIA_SCANNER_VOLUME_EXTERNAL = "external";
  public static final String[] ACCEPTABLE_IMAGE_TYPES =
      new String[] {"image/jpeg", "image/jpg", "image/png", "image/webp"};

  private static final Uri STORAGE_URI = Files.getContentUri(MEDIA_SCANNER_VOLUME_EXTERNAL);
  private static final String SORT_ORDER = Media.DATE_MODIFIED + " DESC";
  private static final String IMAGE_SELECTION = createSelection();

  public GalleryCursorLoader(Context context) {
    super(
        context,
        STORAGE_URI,
        GalleryGridItemData.IMAGE_PROJECTION,
        IMAGE_SELECTION,
        null,
        SORT_ORDER);
  }

  @SuppressLint("DefaultLocale")
  private static String createSelection() {
    return String.format(
        "mime_type IN ('image/jpeg', 'image/jpg', 'image/png', 'image/webp')"
            + " AND media_type in (%d)",
        FileColumns.MEDIA_TYPE_IMAGE);
  }
}
