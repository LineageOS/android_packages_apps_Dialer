/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.incallui.bindings;

import android.graphics.Bitmap;
import com.android.dialer.logging.ContactLookupResult;

/** Provides phone number lookup services. */
public interface PhoneNumberService {

  /**
   * Get a phone number number asynchronously.
   *
   * @param phoneNumber The phone number to lookup.
   * @param listener The listener to notify when the phone number lookup is complete.
   * @param imageListener The listener to notify when the image lookup is complete.
   */
  void getPhoneNumberInfo(
      String phoneNumber,
      NumberLookupListener listener,
      ImageLookupListener imageListener,
      boolean isIncoming);

  interface NumberLookupListener {

    /**
     * Callback when a phone number has been looked up.
     *
     * @param info The looked up information. Or (@literal null} if there are no results.
     */
    void onPhoneNumberInfoComplete(PhoneNumberInfo info);
  }

  interface ImageLookupListener {

    /**
     * Callback when a image has been fetched.
     *
     * @param bitmap The fetched image.
     */
    void onImageFetchComplete(Bitmap bitmap);
  }

  interface PhoneNumberInfo {

    String getDisplayName();

    String getNumber();

    int getPhoneType();

    String getPhoneLabel();

    String getNormalizedNumber();

    String getImageUrl();

    String getLookupKey();

    boolean isBusiness();

    ContactLookupResult.Type getLookupSource();
  }
}
