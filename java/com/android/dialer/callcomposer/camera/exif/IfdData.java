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

import androidx.collection.ArrayMap;

import java.util.Map;
import java.util.Objects;

/**
 * This class stores all the tags in an IFD.
 *
 * @see ExifData
 * @see ExifTag
 */
class IfdData {

  private final int ifdId;
  private final Map<Short, ExifTag> exifTags = new ArrayMap<>();
  private static final int[] ifds = {
    IfdId.TYPE_IFD_0,
    IfdId.TYPE_IFD_1,
    IfdId.TYPE_IFD_EXIF,
    IfdId.TYPE_IFD_INTEROPERABILITY,
    IfdId.TYPE_IFD_GPS
  };
  /**
   * Creates an IfdData with given IFD ID.
   *
   * @see IfdId#TYPE_IFD_0
   * @see IfdId#TYPE_IFD_1
   * @see IfdId#TYPE_IFD_EXIF
   * @see IfdId#TYPE_IFD_GPS
   * @see IfdId#TYPE_IFD_INTEROPERABILITY
   */
  IfdData(int ifdId) {
    this.ifdId = ifdId;
  }

  static int[] getIfds() {
    return ifds;
  }

  /** Get a array the contains all {@link ExifTag} in this IFD. */
  private ExifTag[] getAllTags() {
    return exifTags.values().toArray(new ExifTag[exifTags.size()]);
  }

  /**
   * Gets the ID of this IFD.
   *
   * @see IfdId#TYPE_IFD_0
   * @see IfdId#TYPE_IFD_1
   * @see IfdId#TYPE_IFD_EXIF
   * @see IfdId#TYPE_IFD_GPS
   * @see IfdId#TYPE_IFD_INTEROPERABILITY
   */
  protected int getId() {
    return ifdId;
  }

  /** Gets the {@link ExifTag} with given tag id. Return null if there is no such tag. */
  protected ExifTag getTag(short tagId) {
    return exifTags.get(tagId);
  }

  /** Adds or replaces a {@link ExifTag}. */
  protected ExifTag setTag(ExifTag tag) {
    tag.setIfd(ifdId);
    return exifTags.put(tag.getTagId(), tag);
  }

  /** Gets the tags count in the IFD. */
  private int getTagCount() {
    return exifTags.size();
  }

  /**
   * Returns true if all tags in this two IFDs are equal. Note that tags of IFDs offset or thumbnail
   * offset will be ignored.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (obj instanceof IfdData) {
      IfdData data = (IfdData) obj;
      if (data.getId() == ifdId && data.getTagCount() == getTagCount()) {
        ExifTag[] tags = data.getAllTags();
        for (ExifTag tag : tags) {
          if (ExifInterface.isOffsetTag(tag.getTagId())) {
            continue;
          }
          ExifTag tag2 = exifTags.get(tag.getTagId());
          if (!tag.equals(tag2)) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(ifdId, exifTags);
  }
}
