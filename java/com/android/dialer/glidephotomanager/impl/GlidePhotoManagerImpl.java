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

package com.android.dialer.glidephotomanager.impl;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract.Data;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.widget.QuickContactBadge;
import com.android.dialer.common.Assert;
import com.android.dialer.glide.GlideApp;
import com.android.dialer.glide.GlideRequest;
import com.android.dialer.glide.GlideRequests;
import com.android.dialer.glidephotomanager.GlidePhotoManager;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.lettertile.LetterTileDrawable;
import javax.inject.Inject;

/** Implementation of {@link GlidePhotoManager} */
public class GlidePhotoManagerImpl implements GlidePhotoManager {
  private final Context appContext;

  @Inject
  public GlidePhotoManagerImpl(@ApplicationContext Context appContext) {
    this.appContext = appContext;
  }

  @MainThread
  @Override
  public void loadQuickContactBadge(QuickContactBadge badge, PhotoInfo photoInfo) {
    Assert.isMainThread();
    badge.assignContactUri(parseUri(photoInfo.lookupUri()));
    badge.setOverlay(null);
    LetterTileDrawable defaultDrawable = getDefaultDrawable(photoInfo);
    GlideRequest<Drawable> request =
        buildRequest(GlideApp.with(badge), photoInfo)
            .placeholder(defaultDrawable) // when the photo is still loading.
            .fallback(defaultDrawable); // when there's nothing to load.

    if (photoInfo.isCircular()) {
      request.circleCrop();
    }

    request.into(badge);
  }

  private GlideRequest<Drawable> buildRequest(GlideRequests requestManager, PhotoInfo photoInfo) {
    // Warning: Glide ignores extra attributes on BitmapDrawable such as tint and draw the bitmap
    // directly so be sure not to set tint in the XML of any drawable referenced below.

    // The spam status takes precedence over whether the number is blocked.
    if (photoInfo.isSpam()) {
      return requestManager.load(R.drawable.ic_report_red_48dp);
    }
    if (photoInfo.isBlocked()) {
      return requestManager.load(R.drawable.ic_block_grey_48dp);
    }
    if (!TextUtils.isEmpty(photoInfo.photoUri())) {
      return requestManager.load(parseUri(photoInfo.photoUri()));
    }
    if (photoInfo.photoId() != 0) {
      return requestManager.load(ContentUris.withAppendedId(Data.CONTENT_URI, photoInfo.photoId()));
    }
    // load null to indicate fallback should be used.
    return requestManager.load((Object) null);
  }

  /**
   * Generate the default drawable when photos are not available. Used when the photo is loading or
   * no photo is available.
   */
  private LetterTileDrawable getDefaultDrawable(PhotoInfo photoInfo) {
    LetterTileDrawable letterTileDrawable = new LetterTileDrawable(appContext.getResources());
    String displayName;
    String identifier;
    if (TextUtils.isEmpty(photoInfo.lookupUri())) {
      // Use generic avatar instead of letter for non-contacts.
      displayName = null;
      identifier =
          TextUtils.isEmpty(photoInfo.name()) ? photoInfo.formattedNumber() : photoInfo.name();
    } else {
      displayName = photoInfo.name();
      identifier = photoInfo.lookupUri();
    }
    letterTileDrawable.setCanonicalDialerLetterTileDetails(
        displayName,
        identifier,
        LetterTileDrawable.SHAPE_CIRCLE,
        LetterTileDrawable.getContactTypeFromPrimitives(
            photoInfo.isVoicemail(),
            false, // TODO(twyen):implement
            photoInfo.isBusiness(),
            TelecomManager.PRESENTATION_ALLOWED, // TODO(twyen):implement
            false)); // TODO(twyen):implement
    return letterTileDrawable;
  }

  @Nullable
  private static Uri parseUri(@Nullable String uri) {
    return TextUtils.isEmpty(uri) ? null : Uri.parse(uri);
  }
}
