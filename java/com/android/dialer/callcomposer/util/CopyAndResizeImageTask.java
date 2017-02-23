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

package com.android.dialer.callcomposer.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FallibleAsyncTask;
import com.android.dialer.common.LogUtil;
import com.android.dialer.util.DialerUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** Task for copying and resizing images to be shared with RCS process. */
@TargetApi(VERSION_CODES.M)
public class CopyAndResizeImageTask extends FallibleAsyncTask<Void, Void, File> {
  public static final int MAX_OUTPUT_RESOLUTION = 1024;
  private static final String MIME_TYPE = "image/jpeg";

  private final Context context;
  private final Uri uri;
  private final Callback callback;

  public CopyAndResizeImageTask(
      @NonNull Context context, @NonNull Uri uri, @NonNull Callback callback) {
    this.context = Assert.isNotNull(context);
    this.uri = Assert.isNotNull(uri);
    this.callback = Assert.isNotNull(callback);
  }

  @Nullable
  @Override
  protected File doInBackgroundFallible(Void... params) throws Throwable {
    Bitmap bitmap = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri));
    bitmap = resizeForEnrichedCalling(bitmap);

    File outputFile = DialerUtils.createShareableFile(context);
    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
      // Encode images to jpeg as it is better for camera pictures which we expect to be sending
      bitmap.compress(CompressFormat.JPEG, 90, outputStream);
      return outputFile;
    }
  }

  @Override
  protected void onPostExecute(FallibleTaskResult<File> result) {
    if (result.isFailure()) {
      callback.onCopyFailed(result.getThrowable());
    } else {
      callback.onCopySuccessful(result.getResult(), MIME_TYPE);
    }
  }

  public static Bitmap resizeForEnrichedCalling(Bitmap image) {
    Assert.isWorkerThread();

    int width = image.getWidth();
    int height = image.getHeight();

    LogUtil.i(
        "CopyAndResizeImageTask.resizeForEnrichedCalling",
        "starting height: %d, width: %d",
        height,
        width);

    if (width <= MAX_OUTPUT_RESOLUTION && height <= MAX_OUTPUT_RESOLUTION) {
      LogUtil.i("CopyAndResizeImageTask.resizeForEnrichedCalling", "no resizing needed");
      return image;
    }

    if (width > height) {
      // landscape
      float ratio = width / (float) MAX_OUTPUT_RESOLUTION;
      width = MAX_OUTPUT_RESOLUTION;
      height = (int) (height / ratio);
    } else if (height > width) {
      // portrait
      float ratio = height / (float) MAX_OUTPUT_RESOLUTION;
      height = MAX_OUTPUT_RESOLUTION;
      width = (int) (width / ratio);
    } else {
      // square
      height = MAX_OUTPUT_RESOLUTION;
      width = MAX_OUTPUT_RESOLUTION;
    }

    LogUtil.i(
        "CopyAndResizeImageTask.resizeForEnrichedCalling",
        "ending height: %d, width: %d",
        height,
        width);

    return Bitmap.createScaledBitmap(image, width, height, true);
  }

  /** Callback for callers to know when the task has finished */
  public interface Callback {
    void onCopySuccessful(File file, String mimeType);

    void onCopyFailed(Throwable throwable);
  }
}
