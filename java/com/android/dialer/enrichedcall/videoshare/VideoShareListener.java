/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.enrichedcall.videoshare;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

/** Receives updates when video share status has changed. */
public interface VideoShareListener {

  /**
   * Callback fired when video share has changed (service connected / disconnected, video share
   * invite received or canceled, or when a session changes).
   */
  @MainThread
  void onVideoShareChanged(@NonNull Context context);
}
