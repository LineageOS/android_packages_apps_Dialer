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

/**
 * This class stores the EXIF header in IFDs according to the JPEG specification. It is the result
 * produced by {@link ExifReader}.
 *
 * @see ExifReader
 * @see IfdData
 */
public class ExifData {

  private final IfdData[] mIfdDatas = new IfdData[IfdId.TYPE_IFD_COUNT];

  /**
   * Adds IFD data. If IFD data of the same type already exists, it will be replaced by the new
   * data.
   */
  void addIfdData(IfdData data) {
    mIfdDatas[data.getId()] = data;
  }

  /** Returns the {@link IfdData} object corresponding to a given IFD if it exists or null. */
  IfdData getIfdData(int ifdId) {
    if (ExifTag.isValidIfd(ifdId)) {
      return mIfdDatas[ifdId];
    }
    return null;
  }

  /**
   * Returns the tag with a given TID in the given IFD if the tag exists. Otherwise returns null.
   */
  protected ExifTag getTag(short tag, int ifd) {
    IfdData ifdData = mIfdDatas[ifd];
    return (ifdData == null) ? null : ifdData.getTag(tag);
  }

  /**
   * Adds the given ExifTag to its default IFD and returns an existing ExifTag with the same TID or
   * null if none exist.
   */
  ExifTag addTag(ExifTag tag) {
    if (tag != null) {
      int ifd = tag.getIfd();
      return addTag(tag, ifd);
    }
    return null;
  }

  /**
   * Adds the given ExifTag to the given IFD and returns an existing ExifTag with the same TID or
   * null if none exist.
   */
  private ExifTag addTag(ExifTag tag, int ifdId) {
    if (tag != null && ExifTag.isValidIfd(ifdId)) {
      IfdData ifdData = getOrCreateIfdData(ifdId);
      return ifdData.setTag(tag);
    }
    return null;
  }

  /**
   * Returns the {@link IfdData} object corresponding to a given IFD or generates one if none exist.
   */
  private IfdData getOrCreateIfdData(int ifdId) {
    IfdData ifdData = mIfdDatas[ifdId];
    if (ifdData == null) {
      ifdData = new IfdData(ifdId);
      mIfdDatas[ifdId] = ifdData;
    }
    return ifdData;
  }
}
