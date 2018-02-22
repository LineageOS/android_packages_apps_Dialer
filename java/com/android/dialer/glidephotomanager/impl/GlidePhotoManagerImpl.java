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
    GlideRequest<Drawable> request = buildRequest(GlideApp.with(badge), photoInfo);
    request.into(badge);
  }

  private GlideRequest<Drawable> buildRequest(GlideRequests requestManager, PhotoInfo photoInfo) {
    // Warning: Glide ignores extra attributes on BitmapDrawable such as tint and draw the bitmap
    // directly so be sure not to set tint in the XML of any drawable referenced below.

    GlideRequest<Drawable> request;
    boolean circleCrop = true; // Photos are cropped to a circle by default.

    if (photoInfo.isBlocked()) {
      // Whether the number is blocked takes precedence over the spam status.
      request = requestManager.load(R.drawable.ic_block_grey_48dp);

    } else if (photoInfo.isSpam()) {
      request = requestManager.load(R.drawable.quantum_ic_report_vd_red_24);
      circleCrop = false; // The spam icon is an octagon so we don't crop it.

    } else if (!TextUtils.isEmpty(photoInfo.photoUri())) {
      request = requestManager.load(parseUri(photoInfo.photoUri()));

    } else if (photoInfo.photoId() != 0) {
      request =
          requestManager.load(ContentUris.withAppendedId(Data.CONTENT_URI, photoInfo.photoId()));

    } else {
      // load null to indicate fallback should be used.
      request = requestManager.load((Object) null);
    }

    LetterTileDrawable defaultDrawable = getDefaultDrawable(photoInfo);
    request
        .placeholder(defaultDrawable) // when the photo is still loading.
        .fallback(defaultDrawable); // when there's nothing to load.

    if (circleCrop) {
      request.circleCrop();
    }

    return request;
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
            photoInfo.isSpam(),
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
