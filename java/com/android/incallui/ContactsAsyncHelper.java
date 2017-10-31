/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.incallui;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import java.io.IOException;
import java.io.InputStream;

/** Helper class for loading contacts photo asynchronously. */
public class ContactsAsyncHelper {

  /** Interface for a WorkerHandler result return. */
  interface OnImageLoadCompleteListener {

    /**
     * Called when the image load is complete. Must be called in main thread.
     *
     * @param token Integer passed in {@link ContactsAsyncHelper#startObtainPhotoAsync(int, Context,
     *     Uri, OnImageLoadCompleteListener, Object)}.
     * @param photo Drawable object obtained by the async load.
     * @param photoIcon Bitmap object obtained by the async load.
     * @param cookie Object passed in {@link ContactsAsyncHelper#startObtainPhotoAsync(int, Context,
     *     Uri, OnImageLoadCompleteListener, Object)}. Can be null iff. the original cookie is null.
     */
    @MainThread
    void onImageLoadComplete(int token, Drawable photo, Bitmap photoIcon, Object cookie);

    /** Called when image is loaded to udpate data. Must be called in worker thread. */
    @WorkerThread
    void onImageLoaded(int token, Drawable photo, Bitmap photoIcon, Object cookie);
  }

  /**
   * Starts an asynchronous image load. After finishing the load, {@link
   * OnImageLoadCompleteListener#onImageLoadComplete(int, Drawable, Bitmap, Object)} will be called.
   *
   * @param token Arbitrary integer which will be returned as the first argument of {@link
   *     OnImageLoadCompleteListener#onImageLoadComplete(int, Drawable, Bitmap, Object)}
   * @param context Context object used to do the time-consuming operation.
   * @param displayPhotoUri Uri to be used to fetch the photo
   * @param listener Callback object which will be used when the asynchronous load is done. Can be
   *     null, which means only the asynchronous load is done while there's no way to obtain the
   *     loaded photos.
   * @param cookie Arbitrary object the caller wants to remember, which will become the fourth
   *     argument of {@link OnImageLoadCompleteListener#onImageLoadComplete(int, Drawable, Bitmap,
   *     Object)}. Can be null, at which the callback will also has null for the argument.
   */
  static void startObtainPhotoAsync(
      int token,
      Context context,
      Uri displayPhotoUri,
      OnImageLoadCompleteListener listener,
      Object cookie) {
    // in case the source caller info is null, the URI will be null as well.
    // just update using the placeholder image in this case.
    if (displayPhotoUri == null) {
      LogUtil.e("ContactsAsyncHelper.startObjectPhotoAsync", "uri is missing");
      return;
    }

    // Added additional Cookie field in the callee to handle arguments
    // sent to the callback function.

    // setup arguments
    WorkerArgs args = new WorkerArgs();
    args.token = token;
    args.cookie = cookie;
    args.context = context;
    args.displayPhotoUri = displayPhotoUri;
    args.listener = listener;

    DialerExecutorComponent.get(context)
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new Worker())
        .onSuccess(
            output -> {
              if (args.listener != null) {
                LogUtil.d(
                    "ContactsAsyncHelper.startObtainPhotoAsync",
                    "notifying listener: "
                        + args.listener
                        + " image: "
                        + args.displayPhotoUri
                        + " completed");
                args.listener.onImageLoadComplete(
                    args.token, args.photo, args.photoIcon, args.cookie);
              }
            })
        .build()
        .executeParallel(args);
  }

  private static final class WorkerArgs {

    public int token;
    public Context context;
    public Uri displayPhotoUri;
    public Drawable photo;
    public Bitmap photoIcon;
    public Object cookie;
    public OnImageLoadCompleteListener listener;
  }

  private static class Worker implements DialerExecutor.Worker<WorkerArgs, Void> {

    @Nullable
    @Override
    public Void doInBackground(WorkerArgs args) throws Throwable {
      InputStream inputStream = null;
      try {
        try {
          inputStream = args.context.getContentResolver().openInputStream(args.displayPhotoUri);
        } catch (Exception e) {
          LogUtil.e(
              "ContactsAsyncHelper.Worker.doInBackground", "error opening photo input stream", e);
        }

        if (inputStream != null) {
          args.photo = Drawable.createFromStream(inputStream, args.displayPhotoUri.toString());

          // This assumes Drawable coming from contact database is usually
          // BitmapDrawable and thus we can have (down)scaled version of it.
          args.photoIcon = getPhotoIconWhenAppropriate(args.context, args.photo);

          LogUtil.d(
              "ContactsAsyncHelper.Worker.doInBackground",
              "loading image, URI: %s",
              args.displayPhotoUri);
        } else {
          args.photo = null;
          args.photoIcon = null;
          LogUtil.d(
              "ContactsAsyncHelper.Worker.doInBackground",
              "problem with image, URI: %s, using default image.",
              args.displayPhotoUri);
        }
        if (args.listener != null) {
          args.listener.onImageLoaded(args.token, args.photo, args.photoIcon, args.cookie);
        }
      } finally {
        if (inputStream != null) {
          try {
            inputStream.close();
          } catch (IOException e) {
            LogUtil.e(
                "ContactsAsyncHelper.Worker.doInBackground", "Unable to close input stream.", e);
          }
        }
      }
      return null;
    }

    /**
     * Returns a Bitmap object suitable for {@link Notification}'s large icon. This might return
     * null when the given Drawable isn't BitmapDrawable, or if the system fails to create a scaled
     * Bitmap for the Drawable.
     */
    private Bitmap getPhotoIconWhenAppropriate(Context context, Drawable photo) {
      if (!(photo instanceof BitmapDrawable)) {
        return null;
      }
      int iconSize = context.getResources().getDimensionPixelSize(R.dimen.notification_icon_size);
      Bitmap orgBitmap = ((BitmapDrawable) photo).getBitmap();
      int orgWidth = orgBitmap.getWidth();
      int orgHeight = orgBitmap.getHeight();
      int longerEdge = orgWidth > orgHeight ? orgWidth : orgHeight;
      // We want downscaled one only when the original icon is too big.
      if (longerEdge > iconSize) {
        float ratio = ((float) longerEdge) / iconSize;
        int newWidth = (int) (orgWidth / ratio);
        int newHeight = (int) (orgHeight / ratio);
        // If the longer edge is much longer than the shorter edge, the latter may
        // become 0 which will cause a crash.
        if (newWidth <= 0 || newHeight <= 0) {
          LogUtil.w(
              "ContactsAsyncHelper.Worker.getPhotoIconWhenAppropriate",
              "Photo icon's width or height become 0.");
          return null;
        }

        // It is sure ratio >= 1.0f in any case and thus the newly created Bitmap
        // should be smaller than the original.
        return Bitmap.createScaledBitmap(orgBitmap, newWidth, newHeight, true);
      } else {
        return orgBitmap;
      }
    }
  }
}
