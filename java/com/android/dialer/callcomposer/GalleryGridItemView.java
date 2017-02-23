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

import android.content.Context;
import android.database.Cursor;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import java.util.concurrent.TimeUnit;

/** Shows an item in the gallery picker grid view. Hosts an FileImageView with a checkbox. */
public class GalleryGridItemView extends FrameLayout {

  private final GalleryGridItemData data = new GalleryGridItemData();

  private ImageView image;
  private View checkbox;
  private View gallery;
  private String currentFilePath;
  private boolean isGallery;

  public GalleryGridItemView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    image = (ImageView) findViewById(R.id.image);
    checkbox = findViewById(R.id.checkbox);
    gallery = findViewById(R.id.gallery);

    image.setClipToOutline(true);
    checkbox.setClipToOutline(true);
    gallery.setClipToOutline(true);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    // The grid view auto-fit the columns, so we want to let the height match the width
    // to make the image square.
    super.onMeasure(widthMeasureSpec, widthMeasureSpec);
  }

  public GalleryGridItemData getData() {
    return data;
  }

  @Override
  public void setSelected(boolean selected) {
    if (selected) {
      checkbox.setVisibility(VISIBLE);
      int paddingPx = getResources().getDimensionPixelSize(R.dimen.gallery_item_selected_padding);
      setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
    } else {
      checkbox.setVisibility(GONE);
      int paddingPx = getResources().getDimensionPixelOffset(R.dimen.gallery_item_padding);
      setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
    }
  }

  public boolean isGallery() {
    return isGallery;
  }

  public void showGallery(boolean show) {
    isGallery = show;
    gallery.setVisibility(show ? VISIBLE : INVISIBLE);
  }

  public void bind(Cursor cursor) {
    data.bind(cursor);
    showGallery(false);
    updateImageView();
  }

  private void updateImageView() {
    image.setScaleType(ScaleType.CENTER_CROP);

    if (currentFilePath == null || !currentFilePath.equals(data.getFilePath())) {
      currentFilePath = data.getFilePath();

      // Downloads/loads an image from the given URI so that the image's largest dimension is
      // between 1/2 the given dimensions and the given dimensions, with no restrictions on the
      // image's smallest dimension. We skip the memory cache, but glide still applies it's disk
      // cache to optimize loads.
      Glide.with(getContext())
          .load(data.getFileUri())
          .apply(RequestOptions.downsampleOf(DownsampleStrategy.AT_MOST).skipMemoryCache(true))
          .transition(DrawableTransitionOptions.withCrossFade())
          .into(image);
    }
    long dateModifiedSeconds = data.getDateModifiedSeconds();
    if (dateModifiedSeconds > 0) {
      image.setContentDescription(
          getResources()
              .getString(
                  R.string.gallery_item_description,
                  TimeUnit.SECONDS.toMillis(dateModifiedSeconds)));
    } else {
      image.setContentDescription(
          getResources().getString(R.string.gallery_item_description_no_date));
    }
  }
}
