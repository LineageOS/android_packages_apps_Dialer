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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import com.android.dialer.callcomposer.util.BitmapResizer;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.util.DialerUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Task for copying and resizing images to be shared with RCS process. */
@TargetApi(VERSION_CODES.M)
class CopyAndResizeImageWorker implements Worker<Uri, Pair<File, String>> {
  private static final String MIME_TYPE = "image/jpeg";

  private final Context context;

  CopyAndResizeImageWorker(@NonNull Context context) {
    this.context = Assert.isNotNull(context);
  }

  /**
   * @param input The input Uri is expected to be a image openable by {@link
   *     android.content.ContentResolver#openInputStream(Uri)}.
   * @return a Pair where the File contains the resized image, and the String is the result File's
   *     MIME type.
   */
  @Nullable
  @Override
  public Pair<File, String> doInBackground(@Nullable Uri input) throws Throwable {
    try (InputStream inputStream = context.getContentResolver().openInputStream(input)) {
      Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
      bitmap = BitmapResizer.resizeForEnrichedCalling(bitmap);

      File outputFile = DialerUtils.createShareableFile(context);
      try (OutputStream outputStream = new FileOutputStream(outputFile)) {
        // Encode images to jpeg as it is better for camera pictures which we expect to be sending
        bitmap.compress(CompressFormat.JPEG, 80, outputStream);
        return new Pair<>(outputFile, MIME_TYPE);
      }
    }
  }
}
