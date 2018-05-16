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
 * limitations under the License
 */

package com.android.dialer.widget;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import com.android.dialer.common.Assert;
import com.android.dialer.glidephotomanager.GlidePhotoManager;
import com.android.dialer.glidephotomanager.GlidePhotoManagerComponent;
import com.android.dialer.glidephotomanager.PhotoInfo;

/**
 * A {@link FrameLayout} for displaying a contact photo and its optional badge (such as one for a
 * video call).
 */
public final class ContactPhotoView extends FrameLayout {
  private final QuickContactBadge contactPhoto;
  private final FrameLayout contactBadgeContainer;
  private final ImageView videoCallBadge;
  private final ImageView rttCallBadge;

  private final GlidePhotoManager glidePhotoManager;

  public ContactPhotoView(Context context) {
    this(context, /* attrs = */ null);
  }

  public ContactPhotoView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, /* defStyleAttr = */ 0);
  }

  public ContactPhotoView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    this(context, attrs, defStyleAttr, /* defStyleRes = */ 0);
  }

  public ContactPhotoView(
      Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);

    inflateLayout();

    contactPhoto = findViewById(R.id.quick_contact_photo);
    contactBadgeContainer = findViewById(R.id.contact_badge_container);
    videoCallBadge = findViewById(R.id.video_call_badge);
    rttCallBadge = findViewById(R.id.rtt_call_badge);

    glidePhotoManager = GlidePhotoManagerComponent.get(context).glidePhotoManager();

    hideBadge(); // Hide badges by default.
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    // We require both the height and the width to be WRAP_CONTENT to prevent users of
    // this widget from clipping the view (by setting a size that is too small) or altering the
    // relative position of the contact photo and its badge (by setting a size that is too large).
    ViewGroup.LayoutParams layoutParams = Assert.isNotNull(getLayoutParams());
    Assert.checkState(
        layoutParams.height == LayoutParams.WRAP_CONTENT,
        "The layout height must be WRAP_CONTENT!");
    Assert.checkState(
        layoutParams.width == LayoutParams.WRAP_CONTENT, "The layout width must be WRAP_CONTENT!");
  }

  private void inflateLayout() {
    LayoutInflater inflater = Assert.isNotNull(getContext().getSystemService(LayoutInflater.class));
    inflater.inflate(R.layout.contact_photo_view, /* root = */ this);
  }

  private void hideBadge() {
    contactBadgeContainer.setVisibility(View.GONE);
    videoCallBadge.setVisibility(View.GONE);
    rttCallBadge.setVisibility(View.GONE);
  }

  /** Sets the contact photo and its badge to be displayed. */
  public void setPhoto(PhotoInfo photoInfo) {
    glidePhotoManager.loadQuickContactBadge(contactPhoto, photoInfo);
    setBadge(photoInfo);
  }

  private void setBadge(PhotoInfo photoInfo) {
    // No badge for spam numbers.
    if (photoInfo.getIsSpam()) {
      hideBadge();
      return;
    }

    if (photoInfo.getIsVideo()) {
      contactBadgeContainer.setVisibility(View.VISIBLE);
      videoCallBadge.setVisibility(View.VISIBLE);
      // Normally a video call can't be RTT call and vice versa.
      // (a bug): In theory a video call could be downgraded to voice and upgraded to RTT call
      // again, this might end up a call with video and RTT features both set. Update logic here if
      // that could happen. Also update {@link Coalescer#meetsCallFeatureCriteria}.
      rttCallBadge.setVisibility(View.GONE);
    } else if (photoInfo.getIsRtt()) {
      contactBadgeContainer.setVisibility(View.VISIBLE);
      videoCallBadge.setVisibility(View.GONE);
      rttCallBadge.setVisibility(View.VISIBLE);
    } else {
      hideBadge();
    }
  }
}
