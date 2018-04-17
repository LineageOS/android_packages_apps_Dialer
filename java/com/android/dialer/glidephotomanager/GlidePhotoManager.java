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

package com.android.dialer.glidephotomanager;

import android.support.annotation.MainThread;
import android.widget.ImageView;
import android.widget.QuickContactBadge;

/** Class to load photo for call/contacts */
public interface GlidePhotoManager {

  /**
   * Load {@code photoInfo} into the {@code badge}. The loading is performed in the background and a
   * placeholder will be used appropriately. {@code badge} must be already attached to an
   * activity/fragment, and the load will be automatically canceled if the lifecycle of the activity
   * ends.
   */
  @MainThread
  void loadQuickContactBadge(QuickContactBadge badge, PhotoInfo photoInfo);

  /**
   * Load {@code photoInfo} into the {@code imageView}. The loading is performed in the background
   * and a placeholder will be used appropriately. {@code imageView} must be already attached to an
   * activity/fragment, and the load will be automatically canceled if the lifecycle of the activity
   * ends.
   */
  @MainThread
  void loadContactPhoto(ImageView imageView, PhotoInfo photoInfo);
}
