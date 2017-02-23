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

package com.android.dialer.callcomposer.camera.exif;

class JpegHeader {
  static final short SOI = (short) 0xFFD8;
  static final short APP1 = (short) 0xFFE1;
  static final short EOI = (short) 0xFFD9;

  /**
   * SOF (start of frame). All value between SOF0 and SOF15 is SOF marker except for DHT, JPG, and
   * DAC marker.
   */
  private static final short SOF0 = (short) 0xFFC0;

  private static final short SOF15 = (short) 0xFFCF;
  private static final short DHT = (short) 0xFFC4;
  private static final short JPG = (short) 0xFFC8;
  private static final short DAC = (short) 0xFFCC;

  static boolean isSofMarker(short marker) {
    return marker >= SOF0 && marker <= SOF15 && marker != DHT && marker != JPG && marker != DAC;
  }
}
