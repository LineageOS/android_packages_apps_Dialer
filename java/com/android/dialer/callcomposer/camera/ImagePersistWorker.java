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

package com.android.dialer.callcomposer.camera;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import com.android.dialer.callcomposer.camera.ImagePersistWorker.Result;
import com.android.dialer.callcomposer.camera.exif.ExifInterface;
import com.android.dialer.callcomposer.util.BitmapResizer;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.constants.Constants;
import com.android.dialer.util.DialerUtils;
import com.google.auto.value.AutoValue;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Persisting image routine. */
@TargetApi(VERSION_CODES.M)
public class ImagePersistWorker implements Worker<Void, Result> {
  private int mWidth;
  private int mHeight;
  private final float mHeightPercent;
  private final byte[] mBytes;
  private final Context mContext;

  @AutoValue
  abstract static class Result {

    public static Builder builder() {
      return new AutoValue_ImagePersistWorker_Result.Builder();
    }

    @NonNull
    abstract Uri getUri();

    abstract int getWidth();

    abstract int getHeight();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setUri(@NonNull Uri uri);

      abstract Builder setWidth(int width);

      abstract Builder setHeight(int height);

      abstract Result build();
    }
  }

  ImagePersistWorker(
      final int width,
      final int height,
      final float heightPercent,
      final byte[] bytes,
      final Context context) {
    Assert.checkArgument(heightPercent >= 0 && heightPercent <= 1);
    Assert.isNotNull(bytes);
    Assert.isNotNull(context);
    mWidth = width;
    mHeight = height;
    mHeightPercent = heightPercent;
    mBytes = bytes;
    mContext = context;
  }

  @Override
  public Result doInBackground(Void unused) throws Exception {
    File outputFile = DialerUtils.createShareableFile(mContext);

    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
      writeClippedBitmap(outputStream);
    }

    return Result.builder()
        .setUri(
            FileProvider.getUriForFile(
                mContext, Constants.get().getFileProviderAuthority(), outputFile))
        .setWidth(mWidth)
        .setHeight(mHeight)
        .build();
  }

  private void writeClippedBitmap(OutputStream outputStream) throws IOException {
    int orientation = android.media.ExifInterface.ORIENTATION_UNDEFINED;
    final ExifInterface exifInterface = new ExifInterface();
    try {
      exifInterface.readExif(mBytes);
      final Integer orientationValue = exifInterface.getTagIntValue(ExifInterface.TAG_ORIENTATION);
      if (orientationValue != null) {
        orientation = orientationValue.intValue();
      }
    } catch (final IOException e) {
      // Couldn't get exif tags, not the end of the world
    }

    ExifInterface.OrientationParams params = ExifInterface.getOrientationParams(orientation);
    Bitmap bitmap = BitmapFactory.decodeByteArray(mBytes, 0, mBytes.length);
    final int clippedWidth;
    final int clippedHeight;
    if (params.invertDimensions) {
      Assert.checkState(mWidth == bitmap.getHeight());
      Assert.checkState(mHeight == bitmap.getWidth());
      clippedWidth = (int) (mHeight * mHeightPercent);
      clippedHeight = mWidth;
    } else {
      Assert.checkState(mWidth == bitmap.getWidth());
      Assert.checkState(mHeight == bitmap.getHeight());
      clippedWidth = mWidth;
      clippedHeight = (int) (mHeight * mHeightPercent);
    }

    int offsetTop = (bitmap.getHeight() - clippedHeight) / 2;
    int offsetLeft = (bitmap.getWidth() - clippedWidth) / 2;
    mWidth = clippedWidth;
    mHeight = clippedHeight;

    Bitmap clippedBitmap =
        Bitmap.createBitmap(bitmap, offsetLeft, offsetTop, clippedWidth, clippedHeight);
    clippedBitmap = BitmapResizer.resizeForEnrichedCalling(clippedBitmap, params.rotation);
    // EXIF data can take a big chunk of the file size and we've already manually rotated our image,
    // so remove all of the exif data.
    exifInterface.clearExif();
    exifInterface.writeExif(clippedBitmap, outputStream);

    clippedBitmap.recycle();
    bitmap.recycle();
  }
}
