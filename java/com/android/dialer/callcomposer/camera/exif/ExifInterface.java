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

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.util.SparseIntArray;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.TimeZone;

/**
 * This class provides methods and constants for reading and writing jpeg file metadata. It contains
 * a collection of ExifTags, and a collection of definitions for creating valid ExifTags. The
 * collection of ExifTags can be updated by: reading new ones from a file, deleting or adding
 * existing ones, or building new ExifTags from a tag definition. These ExifTags can be written to a
 * valid jpeg image as exif metadata.
 *
 * <p>Each ExifTag has a tag ID (TID) and is stored in a specific image file directory (IFD) as
 * specified by the exif standard. A tag definition can be looked up with a constant that is a
 * combination of TID and IFD. This definition has information about the type, number of components,
 * and valid IFDs for a tag.
 *
 * @see ExifTag
 */
public class ExifInterface {
  private static final int IFD_NULL = -1;
  static final int DEFINITION_NULL = 0;

  /** Tag constants for Jeita EXIF 2.2 */
  // IFD 0
  public static final int TAG_ORIENTATION = defineTag(IfdId.TYPE_IFD_0, (short) 0x0112);

  static final int TAG_EXIF_IFD = defineTag(IfdId.TYPE_IFD_0, (short) 0x8769);
  static final int TAG_GPS_IFD = defineTag(IfdId.TYPE_IFD_0, (short) 0x8825);
  static final int TAG_STRIP_OFFSETS = defineTag(IfdId.TYPE_IFD_0, (short) 0x0111);
  static final int TAG_STRIP_BYTE_COUNTS = defineTag(IfdId.TYPE_IFD_0, (short) 0x0117);
  // IFD 1
  static final int TAG_JPEG_INTERCHANGE_FORMAT = defineTag(IfdId.TYPE_IFD_1, (short) 0x0201);
  static final int TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = defineTag(IfdId.TYPE_IFD_1, (short) 0x0202);
  // IFD Exif Tags
  static final int TAG_INTEROPERABILITY_IFD = defineTag(IfdId.TYPE_IFD_EXIF, (short) 0xA005);

  /** Tags that contain offset markers. These are included in the banned defines. */
  private static HashSet<Short> sOffsetTags = new HashSet<>();

  static {
    sOffsetTags.add(getTrueTagKey(TAG_GPS_IFD));
    sOffsetTags.add(getTrueTagKey(TAG_EXIF_IFD));
    sOffsetTags.add(getTrueTagKey(TAG_JPEG_INTERCHANGE_FORMAT));
    sOffsetTags.add(getTrueTagKey(TAG_INTEROPERABILITY_IFD));
    sOffsetTags.add(getTrueTagKey(TAG_STRIP_OFFSETS));
  }

  private static final String NULL_ARGUMENT_STRING = "Argument is null";

  private static final String GPS_DATE_FORMAT_STR = "yyyy:MM:dd";

  private ExifData mData = new ExifData();

  @SuppressLint("SimpleDateFormat")
  public ExifInterface() {
    DateFormat mGPSDateStampFormat = new SimpleDateFormat(GPS_DATE_FORMAT_STR);
    mGPSDateStampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  /**
   * Reads the exif tags from a byte array, clearing this ExifInterface object's existing exif tags.
   *
   * @param jpeg a byte array containing a jpeg compressed image.
   * @throws java.io.IOException
   */
  public void readExif(byte[] jpeg) throws IOException {
    readExif(new ByteArrayInputStream(jpeg));
  }

  /**
   * Reads the exif tags from an InputStream, clearing this ExifInterface object's existing exif
   * tags.
   *
   * @param inStream an InputStream containing a jpeg compressed image.
   * @throws java.io.IOException
   */
  private void readExif(InputStream inStream) throws IOException {
    if (inStream == null) {
      throw new IllegalArgumentException(NULL_ARGUMENT_STRING);
    }
    ExifData d;
    try {
      d = new ExifReader(this).read(inStream);
    } catch (ExifInvalidFormatException e) {
      throw new IOException("Invalid exif format : " + e);
    }
    mData = d;
  }

  /** Returns the TID for a tag constant. */
  static short getTrueTagKey(int tag) {
    // Truncate
    return (short) tag;
  }

  /** Returns the constant representing a tag with a given TID and default IFD. */
  private static int defineTag(int ifdId, short tagId) {
    return (tagId & 0x0000ffff) | (ifdId << 16);
  }

  static boolean isIfdAllowed(int info, int ifd) {
    int[] ifds = IfdData.getIfds();
    int ifdFlags = getAllowedIfdFlagsFromInfo(info);
    for (int i = 0; i < ifds.length; i++) {
      if (ifd == ifds[i] && ((ifdFlags >> i) & 1) == 1) {
        return true;
      }
    }
    return false;
  }

  private static int getAllowedIfdFlagsFromInfo(int info) {
    return info >>> 24;
  }

  /**
   * Returns true if tag TID is one of the following: {@code TAG_EXIF_IFD}, {@code TAG_GPS_IFD},
   * {@code TAG_JPEG_INTERCHANGE_FORMAT}, {@code TAG_STRIP_OFFSETS}, {@code
   * TAG_INTEROPERABILITY_IFD}
   *
   * <p>Note: defining tags with these TID's is disallowed.
   *
   * @param tag a tag's TID (can be obtained from a defined tag constant with {@link
   *     #getTrueTagKey}).
   * @return true if the TID is that of an offset tag.
   */
  static boolean isOffsetTag(short tag) {
    return sOffsetTags.contains(tag);
  }

  private SparseIntArray mTagInfo = null;

  SparseIntArray getTagInfo() {
    if (mTagInfo == null) {
      mTagInfo = new SparseIntArray();
      initTagInfo();
    }
    return mTagInfo;
  }

  private void initTagInfo() {
    /**
     * We put tag information in a 4-bytes integer. The first byte a bitmask representing the
     * allowed IFDs of the tag, the second byte is the data type, and the last two byte are a short
     * value indicating the default component count of this tag.
     */
    // IFD0 tags
    int[] ifdAllowedIfds = {IfdId.TYPE_IFD_0, IfdId.TYPE_IFD_1};
    int ifdFlags = getFlagsFromAllowedIfds(ifdAllowedIfds) << 24;
    mTagInfo.put(ExifInterface.TAG_STRIP_OFFSETS, ifdFlags | ExifTag.TYPE_UNSIGNED_LONG << 16);
    mTagInfo.put(ExifInterface.TAG_EXIF_IFD, ifdFlags | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1);
    mTagInfo.put(ExifInterface.TAG_GPS_IFD, ifdFlags | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1);
    mTagInfo.put(ExifInterface.TAG_ORIENTATION, ifdFlags | ExifTag.TYPE_UNSIGNED_SHORT << 16 | 1);
    mTagInfo.put(ExifInterface.TAG_STRIP_BYTE_COUNTS, ifdFlags | ExifTag.TYPE_UNSIGNED_LONG << 16);
    // IFD1 tags
    int[] ifd1AllowedIfds = {IfdId.TYPE_IFD_1};
    int ifdFlags1 = getFlagsFromAllowedIfds(ifd1AllowedIfds) << 24;
    mTagInfo.put(
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT,
        ifdFlags1 | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1);
    mTagInfo.put(
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH,
        ifdFlags1 | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1);
    // Exif tags
    int[] exifAllowedIfds = {IfdId.TYPE_IFD_EXIF};
    int exifFlags = getFlagsFromAllowedIfds(exifAllowedIfds) << 24;
    mTagInfo.put(
        ExifInterface.TAG_INTEROPERABILITY_IFD, exifFlags | ExifTag.TYPE_UNSIGNED_LONG << 16 | 1);
  }

  private static int getFlagsFromAllowedIfds(int[] allowedIfds) {
    if (allowedIfds == null || allowedIfds.length == 0) {
      return 0;
    }
    int flags = 0;
    int[] ifds = IfdData.getIfds();
    for (int i = 0; i < IfdId.TYPE_IFD_COUNT; i++) {
      for (int j : allowedIfds) {
        if (ifds[i] == j) {
          flags |= 1 << i;
          break;
        }
      }
    }
    return flags;
  }

  private Integer getTagIntValue(int tagId, int ifdId) {
    int[] l = getTagIntValues(tagId, ifdId);
    if (l == null || l.length <= 0) {
      return null;
    }
    return l[0];
  }

  private int[] getTagIntValues(int tagId, int ifdId) {
    ExifTag t = getTag(tagId, ifdId);
    if (t == null) {
      return null;
    }
    return t.getValueAsInts();
  }

  /** Gets an ExifTag for an IFD other than the tag's default. */
  public ExifTag getTag(int tagId, int ifdId) {
    if (!ExifTag.isValidIfd(ifdId)) {
      return null;
    }
    return mData.getTag(getTrueTagKey(tagId), ifdId);
  }

  public Integer getTagIntValue(int tagId) {
    int ifdId = getDefinedTagDefaultIfd(tagId);
    return getTagIntValue(tagId, ifdId);
  }

  /**
   * Gets the default IFD for a tag.
   *
   * @param tagId a defined tag constant, e.g. {@link #TAG_EXIF_IFD}.
   * @return the default IFD for a tag definition or {@link #IFD_NULL} if no definition exists.
   */
  private int getDefinedTagDefaultIfd(int tagId) {
    int info = getTagInfo().get(tagId);
    if (info == DEFINITION_NULL) {
      return IFD_NULL;
    }
    return getTrueIfd(tagId);
  }

  /** Returns the default IFD for a tag constant. */
  private static int getTrueIfd(int tag) {
    return tag >>> 16;
  }

  /**
   * Constants for {@code TAG_ORIENTATION}. They can be interpreted as follows:
   *
   * <ul>
   *   <li>TOP_LEFT is the normal orientation.
   *   <li>TOP_RIGHT is a left-right mirror.
   *   <li>BOTTOM_LEFT is a 180 degree rotation.
   *   <li>BOTTOM_RIGHT is a top-bottom mirror.
   *   <li>LEFT_TOP is mirrored about the top-left<->bottom-right axis.
   *   <li>RIGHT_TOP is a 90 degree clockwise rotation.
   *   <li>LEFT_BOTTOM is mirrored about the top-right<->bottom-left axis.
   *   <li>RIGHT_BOTTOM is a 270 degree clockwise rotation.
   * </ul>
   */
  interface Orientation {
    short TOP_LEFT = 1;
    short TOP_RIGHT = 2;
    short BOTTOM_LEFT = 3;
    short BOTTOM_RIGHT = 4;
    short LEFT_TOP = 5;
    short RIGHT_TOP = 6;
    short LEFT_BOTTOM = 7;
    short RIGHT_BOTTOM = 8;
  }

  /** Wrapper class to define some orientation parameters. */
  public static class OrientationParams {
    public int rotation = 0;
    int scaleX = 1;
    int scaleY = 1;
    public boolean invertDimensions = false;
  }

  public static OrientationParams getOrientationParams(int orientation) {
    OrientationParams params = new OrientationParams();
    switch (orientation) {
      case Orientation.TOP_RIGHT: // Flip horizontal
        params.scaleX = -1;
        break;
      case Orientation.BOTTOM_RIGHT: // Flip vertical
        params.scaleY = -1;
        break;
      case Orientation.BOTTOM_LEFT: // Rotate 180
        params.rotation = 180;
        break;
      case Orientation.RIGHT_BOTTOM: // Rotate 270
        params.rotation = 270;
        params.invertDimensions = true;
        break;
      case Orientation.RIGHT_TOP: // Rotate 90
        params.rotation = 90;
        params.invertDimensions = true;
        break;
      case Orientation.LEFT_TOP: // Transpose
        params.rotation = 90;
        params.scaleX = -1;
        params.invertDimensions = true;
        break;
      case Orientation.LEFT_BOTTOM: // Transverse
        params.rotation = 270;
        params.scaleX = -1;
        params.invertDimensions = true;
        break;
    }
    return params;
  }

  /** Clears this ExifInterface object's existing exif tags. */
  public void clearExif() {
    mData = new ExifData();
  }

  /**
   * Puts an ExifTag into this ExifInterface object's tags, removing a previous ExifTag with the
   * same TID and IFD. The IFD it is put into will be the one the tag was created with in {@link
   * #buildTag}.
   *
   * @param tag an ExifTag to put into this ExifInterface's tags.
   * @return the previous ExifTag with the same TID and IFD or null if none exists.
   */
  public ExifTag setTag(ExifTag tag) {
    return mData.addTag(tag);
  }

  /**
   * Returns the ExifTag in that tag's default IFD for a defined tag constant or null if none
   * exists.
   *
   * @param tagId a defined tag constant, e.g. {@link #TAG_EXIF_IFD}.
   * @return an {@link ExifTag} or null if none exists.
   */
  public ExifTag getTag(int tagId) {
    int ifdId = getDefinedTagDefaultIfd(tagId);
    return getTag(tagId, ifdId);
  }

  /**
   * Writes the tags from this ExifInterface object into a jpeg compressed bitmap, removing prior
   * exif tags.
   *
   * @param bmap a bitmap to compress and write exif into.
   * @param exifOutStream the OutputStream to which the jpeg image with added exif tags will be
   *     written.
   * @throws java.io.IOException
   */
  public void writeExif(Bitmap bmap, OutputStream exifOutStream) throws IOException {
    if (bmap == null || exifOutStream == null) {
      throw new IllegalArgumentException(NULL_ARGUMENT_STRING);
    }
    bmap.compress(Bitmap.CompressFormat.JPEG, 90, exifOutStream);
    exifOutStream.flush();
  }
}
