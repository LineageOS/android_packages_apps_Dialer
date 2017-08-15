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

package com.android.dialer.constants;

/** Registry of tags for {@link android.net.TrafficStats#setThreadStatsTag(int)} */
public class TrafficStatsTags {
  public static final int CONTACT_PHOTO_DOWNLOAD_TAG = 0x0001;
  public static final int NEARBY_PLACES_TAG = 0xaaaa;
  public static final int REVERSE_LOOKUP_CONTACT_TAG = 0xbaaa;
  public static final int REVERSE_LOOKUP_IMAGE_TAG = 0xbaab;
  public static final int DOWNLOAD_LOCATION_MAP_TAG = 0xd000;
  public static final int REVERSE_GEOCODE_TAG = 0xd001;
  public static final int VISUAL_VOICEMAIL_TAG = 0xd002;
  public static final int DIALER_VOIP_TAG = 0xd003;
}
