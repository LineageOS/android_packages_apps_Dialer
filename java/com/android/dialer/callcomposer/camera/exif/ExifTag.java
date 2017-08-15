/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;

/**
 * This class stores information of an EXIF tag. For more information about defined EXIF tags,
 * please read the Jeita EXIF 2.2 standard. Tags should be instantiated using {@link
 * ExifInterface#buildTag}.
 *
 * @see ExifInterface
 */
public class ExifTag {
  /** The BYTE type in the EXIF standard. An 8-bit unsigned integer. */
  static final short TYPE_UNSIGNED_BYTE = 1;
  /**
   * The ASCII type in the EXIF standard. An 8-bit byte containing one 7-bit ASCII code. The final
   * byte is terminated with NULL.
   */
  static final short TYPE_ASCII = 2;
  /** The SHORT type in the EXIF standard. A 16-bit (2-byte) unsigned integer */
  static final short TYPE_UNSIGNED_SHORT = 3;
  /** The LONG type in the EXIF standard. A 32-bit (4-byte) unsigned integer */
  static final short TYPE_UNSIGNED_LONG = 4;
  /**
   * The RATIONAL type of EXIF standard. It consists of two LONGs. The first one is the numerator
   * and the second one expresses the denominator.
   */
  static final short TYPE_UNSIGNED_RATIONAL = 5;
  /**
   * The UNDEFINED type in the EXIF standard. An 8-bit byte that can take any value depending on the
   * field definition.
   */
  static final short TYPE_UNDEFINED = 7;
  /**
   * The SLONG type in the EXIF standard. A 32-bit (4-byte) signed integer (2's complement
   * notation).
   */
  static final short TYPE_LONG = 9;
  /**
   * The SRATIONAL type of EXIF standard. It consists of two SLONGs. The first one is the numerator
   * and the second one is the denominator.
   */
  static final short TYPE_RATIONAL = 10;

  private static final Charset US_ASCII = Charset.forName("US-ASCII");
  private static final int[] TYPE_TO_SIZE_MAP = new int[11];
  private static final int UNSIGNED_SHORT_MAX = 65535;
  private static final long UNSIGNED_LONG_MAX = 4294967295L;
  private static final long LONG_MAX = Integer.MAX_VALUE;
  private static final long LONG_MIN = Integer.MIN_VALUE;

  static {
    TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_BYTE] = 1;
    TYPE_TO_SIZE_MAP[TYPE_ASCII] = 1;
    TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_SHORT] = 2;
    TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_LONG] = 4;
    TYPE_TO_SIZE_MAP[TYPE_UNSIGNED_RATIONAL] = 8;
    TYPE_TO_SIZE_MAP[TYPE_UNDEFINED] = 1;
    TYPE_TO_SIZE_MAP[TYPE_LONG] = 4;
    TYPE_TO_SIZE_MAP[TYPE_RATIONAL] = 8;
  }

  static final int SIZE_UNDEFINED = 0;

  // Exif TagId
  private final short mTagId;
  // Exif Tag Type
  private final short mDataType;
  // If tag has defined count
  private boolean mHasDefinedDefaultComponentCount;
  // Actual data count in tag (should be number of elements in value array)
  private int mComponentCountActual;
  // The ifd that this tag should be put in
  private int mIfd;
  // The value (array of elements of type Tag Type)
  private Object mValue;
  // Value offset in exif header.
  private int mOffset;

  /** Returns true if the given IFD is a valid IFD. */
  static boolean isValidIfd(int ifdId) {
    return ifdId == IfdId.TYPE_IFD_0
        || ifdId == IfdId.TYPE_IFD_1
        || ifdId == IfdId.TYPE_IFD_EXIF
        || ifdId == IfdId.TYPE_IFD_INTEROPERABILITY
        || ifdId == IfdId.TYPE_IFD_GPS;
  }

  /** Returns true if a given type is a valid tag type. */
  static boolean isValidType(short type) {
    return type == TYPE_UNSIGNED_BYTE
        || type == TYPE_ASCII
        || type == TYPE_UNSIGNED_SHORT
        || type == TYPE_UNSIGNED_LONG
        || type == TYPE_UNSIGNED_RATIONAL
        || type == TYPE_UNDEFINED
        || type == TYPE_LONG
        || type == TYPE_RATIONAL;
  }

  // Use builtTag in ExifInterface instead of constructor.
  ExifTag(short tagId, short type, int componentCount, int ifd, boolean hasDefinedComponentCount) {
    mTagId = tagId;
    mDataType = type;
    mComponentCountActual = componentCount;
    mHasDefinedDefaultComponentCount = hasDefinedComponentCount;
    mIfd = ifd;
    mValue = null;
  }

  /**
   * Gets the element size of the given data type in bytes.
   *
   * @see #TYPE_ASCII
   * @see #TYPE_LONG
   * @see #TYPE_RATIONAL
   * @see #TYPE_UNDEFINED
   * @see #TYPE_UNSIGNED_BYTE
   * @see #TYPE_UNSIGNED_LONG
   * @see #TYPE_UNSIGNED_RATIONAL
   * @see #TYPE_UNSIGNED_SHORT
   */
  private static int getElementSize(short type) {
    return TYPE_TO_SIZE_MAP[type];
  }

  /**
   * Returns the ID of the IFD this tag belongs to.
   *
   * @see IfdId#TYPE_IFD_0
   * @see IfdId#TYPE_IFD_1
   * @see IfdId#TYPE_IFD_EXIF
   * @see IfdId#TYPE_IFD_GPS
   * @see IfdId#TYPE_IFD_INTEROPERABILITY
   */
  int getIfd() {
    return mIfd;
  }

  void setIfd(int ifdId) {
    mIfd = ifdId;
  }

  /** Gets the TID of this tag. */
  short getTagId() {
    return mTagId;
  }

  /**
   * Gets the data type of this tag
   *
   * @see #TYPE_ASCII
   * @see #TYPE_LONG
   * @see #TYPE_RATIONAL
   * @see #TYPE_UNDEFINED
   * @see #TYPE_UNSIGNED_BYTE
   * @see #TYPE_UNSIGNED_LONG
   * @see #TYPE_UNSIGNED_RATIONAL
   * @see #TYPE_UNSIGNED_SHORT
   */
  short getDataType() {
    return mDataType;
  }

  /** Gets the total data size in bytes of the value of this tag. */
  int getDataSize() {
    return getComponentCount() * getElementSize(getDataType());
  }

  /** Gets the component count of this tag. */

  // TODO: fix integer overflows with this
  int getComponentCount() {
    return mComponentCountActual;
  }

  /**
   * Sets the component count of this tag. Call this function before setValue() if the length of
   * value does not match the component count.
   */
  void forceSetComponentCount(int count) {
    mComponentCountActual = count;
  }

  /**
   * Returns true if this ExifTag contains value; otherwise, this tag will contain an offset value
   * that is determined when the tag is written.
   */
  boolean hasValue() {
    return mValue != null;
  }

  /**
   * Sets integer values into this tag. This method should be used for tags of type {@link
   * #TYPE_UNSIGNED_SHORT}. This method will fail if:
   *
   * <ul>
   *   <li>The component type of this tag is not {@link #TYPE_UNSIGNED_SHORT}, {@link
   *       #TYPE_UNSIGNED_LONG}, or {@link #TYPE_LONG}.
   *   <li>The value overflows.
   *   <li>The value.length does NOT match the component count in the definition for this tag.
   * </ul>
   */
  boolean setValue(int[] value) {
    if (checkBadComponentCount(value.length)) {
      return false;
    }
    if (mDataType != TYPE_UNSIGNED_SHORT
        && mDataType != TYPE_LONG
        && mDataType != TYPE_UNSIGNED_LONG) {
      return false;
    }
    if (mDataType == TYPE_UNSIGNED_SHORT && checkOverflowForUnsignedShort(value)) {
      return false;
    } else if (mDataType == TYPE_UNSIGNED_LONG && checkOverflowForUnsignedLong(value)) {
      return false;
    }

    long[] data = new long[value.length];
    for (int i = 0; i < value.length; i++) {
      data[i] = value[i];
    }
    mValue = data;
    mComponentCountActual = value.length;
    return true;
  }

  /**
   * Sets long values into this tag. This method should be used for tags of type {@link
   * #TYPE_UNSIGNED_LONG}. This method will fail if:
   *
   * <ul>
   *   <li>The component type of this tag is not {@link #TYPE_UNSIGNED_LONG}.
   *   <li>The value overflows.
   *   <li>The value.length does NOT match the component count in the definition for this tag.
   * </ul>
   */
  boolean setValue(long[] value) {
    if (checkBadComponentCount(value.length) || mDataType != TYPE_UNSIGNED_LONG) {
      return false;
    }
    if (checkOverflowForUnsignedLong(value)) {
      return false;
    }
    mValue = value;
    mComponentCountActual = value.length;
    return true;
  }

  /**
   * Sets a string value into this tag. This method should be used for tags of type {@link
   * #TYPE_ASCII}. The string is converted to an ASCII string. Characters that cannot be converted
   * are replaced with '?'. The length of the string must be equal to either (component count -1) or
   * (component count). The final byte will be set to the string null terminator '\0', overwriting
   * the last character in the string if the value.length is equal to the component count. This
   * method will fail if:
   *
   * <ul>
   *   <li>The data type is not {@link #TYPE_ASCII} or {@link #TYPE_UNDEFINED}.
   *   <li>The length of the string is not equal to (component count -1) or (component count) in the
   *       definition for this tag.
   * </ul>
   */
  boolean setValue(String value) {
    if (mDataType != TYPE_ASCII && mDataType != TYPE_UNDEFINED) {
      return false;
    }

    byte[] buf = value.getBytes(US_ASCII);
    byte[] finalBuf = buf;
    if (buf.length > 0) {
      finalBuf =
          (buf[buf.length - 1] == 0 || mDataType == TYPE_UNDEFINED)
              ? buf
              : Arrays.copyOf(buf, buf.length + 1);
    } else if (mDataType == TYPE_ASCII && mComponentCountActual == 1) {
      finalBuf = new byte[] {0};
    }
    int count = finalBuf.length;
    if (checkBadComponentCount(count)) {
      return false;
    }
    mComponentCountActual = count;
    mValue = finalBuf;
    return true;
  }

  /**
   * Sets Rational values into this tag. This method should be used for tags of type {@link
   * #TYPE_UNSIGNED_RATIONAL}, or {@link #TYPE_RATIONAL}. This method will fail if:
   *
   * <ul>
   *   <li>The component type of this tag is not {@link #TYPE_UNSIGNED_RATIONAL} or {@link
   *       #TYPE_RATIONAL}.
   *   <li>The value overflows.
   *   <li>The value.length does NOT match the component count in the definition for this tag.
   * </ul>
   *
   * @see Rational
   */
  boolean setValue(Rational[] value) {
    if (checkBadComponentCount(value.length)) {
      return false;
    }
    if (mDataType != TYPE_UNSIGNED_RATIONAL && mDataType != TYPE_RATIONAL) {
      return false;
    }
    if (mDataType == TYPE_UNSIGNED_RATIONAL && checkOverflowForUnsignedRational(value)) {
      return false;
    } else if (mDataType == TYPE_RATIONAL && checkOverflowForRational(value)) {
      return false;
    }

    mValue = value;
    mComponentCountActual = value.length;
    return true;
  }

  /**
   * Sets byte values into this tag. This method should be used for tags of type {@link
   * #TYPE_UNSIGNED_BYTE} or {@link #TYPE_UNDEFINED}. This method will fail if:
   *
   * <ul>
   *   <li>The component type of this tag is not {@link #TYPE_UNSIGNED_BYTE} or {@link
   *       #TYPE_UNDEFINED} .
   *   <li>The length does NOT match the component count in the definition for this tag.
   * </ul>
   */
  private boolean setValue(byte[] value, int offset, int length) {
    if (checkBadComponentCount(length)) {
      return false;
    }
    if (mDataType != TYPE_UNSIGNED_BYTE && mDataType != TYPE_UNDEFINED) {
      return false;
    }
    mValue = new byte[length];
    System.arraycopy(value, offset, mValue, 0, length);
    mComponentCountActual = length;
    return true;
  }

  /** Equivalent to setValue(value, 0, value.length). */
  boolean setValue(byte[] value) {
    return setValue(value, 0, value.length);
  }

  /**
   * Gets the value as an array of ints. This method should be used for tags of type {@link
   * #TYPE_UNSIGNED_SHORT}, {@link #TYPE_UNSIGNED_LONG}.
   *
   * @return the value as as an array of ints, or null if the tag's value does not exist or cannot
   *     be converted to an array of ints.
   */
  int[] getValueAsInts() {
    if (mValue == null) {
      return null;
    } else if (mValue instanceof long[]) {
      long[] val = (long[]) mValue;
      int[] arr = new int[val.length];
      for (int i = 0; i < val.length; i++) {
        arr[i] = (int) val[i]; // Truncates
      }
      return arr;
    }
    return null;
  }

  /** Gets the tag's value or null if none exists. */
  public Object getValue() {
    return mValue;
  }

  /** Gets a string representation of the value. */
  private String forceGetValueAsString() {
    if (mValue == null) {
      return "";
    } else if (mValue instanceof byte[]) {
      if (mDataType == TYPE_ASCII) {
        return new String((byte[]) mValue, US_ASCII);
      } else {
        return Arrays.toString((byte[]) mValue);
      }
    } else if (mValue instanceof long[]) {
      if (((long[]) mValue).length == 1) {
        return String.valueOf(((long[]) mValue)[0]);
      } else {
        return Arrays.toString((long[]) mValue);
      }
    } else if (mValue instanceof Object[]) {
      if (((Object[]) mValue).length == 1) {
        Object val = ((Object[]) mValue)[0];
        if (val == null) {
          return "";
        } else {
          return val.toString();
        }
      } else {
        return Arrays.toString((Object[]) mValue);
      }
    } else {
      return mValue.toString();
    }
  }

  /**
   * Gets the value for type {@link #TYPE_ASCII}, {@link #TYPE_LONG}, {@link #TYPE_UNDEFINED},
   * {@link #TYPE_UNSIGNED_BYTE}, {@link #TYPE_UNSIGNED_LONG}, or {@link #TYPE_UNSIGNED_SHORT}.
   *
   * @exception IllegalArgumentException if the data type is {@link #TYPE_RATIONAL} or {@link
   *     #TYPE_UNSIGNED_RATIONAL}.
   */
  long getValueAt(int index) {
    if (mValue instanceof long[]) {
      return ((long[]) mValue)[index];
    } else if (mValue instanceof byte[]) {
      return ((byte[]) mValue)[index];
    }
    throw new IllegalArgumentException(
        "Cannot get integer value from " + convertTypeToString(mDataType));
  }

  /**
   * Gets the {@link #TYPE_ASCII} data.
   *
   * @exception IllegalArgumentException If the type is NOT {@link #TYPE_ASCII}.
   */
  protected String getString() {
    if (mDataType != TYPE_ASCII) {
      throw new IllegalArgumentException(
          "Cannot get ASCII value from " + convertTypeToString(mDataType));
    }
    return new String((byte[]) mValue, US_ASCII);
  }

  /**
   * Gets the offset of this tag. This is only valid if this data size > 4 and contains an offset to
   * the location of the actual value.
   */
  protected int getOffset() {
    return mOffset;
  }

  /** Sets the offset of this tag. */
  protected void setOffset(int offset) {
    mOffset = offset;
  }

  void setHasDefinedCount(boolean d) {
    mHasDefinedDefaultComponentCount = d;
  }

  boolean hasDefinedCount() {
    return mHasDefinedDefaultComponentCount;
  }

  private boolean checkBadComponentCount(int count) {
    return mHasDefinedDefaultComponentCount && (mComponentCountActual != count);
  }

  private static String convertTypeToString(short type) {
    switch (type) {
      case TYPE_UNSIGNED_BYTE:
        return "UNSIGNED_BYTE";
      case TYPE_ASCII:
        return "ASCII";
      case TYPE_UNSIGNED_SHORT:
        return "UNSIGNED_SHORT";
      case TYPE_UNSIGNED_LONG:
        return "UNSIGNED_LONG";
      case TYPE_UNSIGNED_RATIONAL:
        return "UNSIGNED_RATIONAL";
      case TYPE_UNDEFINED:
        return "UNDEFINED";
      case TYPE_LONG:
        return "LONG";
      case TYPE_RATIONAL:
        return "RATIONAL";
      default:
        return "";
    }
  }

  private boolean checkOverflowForUnsignedShort(int[] value) {
    for (int v : value) {
      if (v > UNSIGNED_SHORT_MAX || v < 0) {
        return true;
      }
    }
    return false;
  }

  private boolean checkOverflowForUnsignedLong(long[] value) {
    for (long v : value) {
      if (v < 0 || v > UNSIGNED_LONG_MAX) {
        return true;
      }
    }
    return false;
  }

  private boolean checkOverflowForUnsignedLong(int[] value) {
    for (int v : value) {
      if (v < 0) {
        return true;
      }
    }
    return false;
  }

  private boolean checkOverflowForUnsignedRational(Rational[] value) {
    for (Rational v : value) {
      if (v.getNumerator() < 0
          || v.getDenominator() < 0
          || v.getNumerator() > UNSIGNED_LONG_MAX
          || v.getDenominator() > UNSIGNED_LONG_MAX) {
        return true;
      }
    }
    return false;
  }

  private boolean checkOverflowForRational(Rational[] value) {
    for (Rational v : value) {
      if (v.getNumerator() < LONG_MIN
          || v.getDenominator() < LONG_MIN
          || v.getNumerator() > LONG_MAX
          || v.getDenominator() > LONG_MAX) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (obj instanceof ExifTag) {
      ExifTag tag = (ExifTag) obj;
      if (tag.mTagId != this.mTagId
          || tag.mComponentCountActual != this.mComponentCountActual
          || tag.mDataType != this.mDataType) {
        return false;
      }
      if (mValue != null) {
        if (tag.mValue == null) {
          return false;
        } else if (mValue instanceof long[]) {
          if (!(tag.mValue instanceof long[])) {
            return false;
          }
          return Arrays.equals((long[]) mValue, (long[]) tag.mValue);
        } else if (mValue instanceof Rational[]) {
          if (!(tag.mValue instanceof Rational[])) {
            return false;
          }
          return Arrays.equals((Rational[]) mValue, (Rational[]) tag.mValue);
        } else if (mValue instanceof byte[]) {
          if (!(tag.mValue instanceof byte[])) {
            return false;
          }
          return Arrays.equals((byte[]) mValue, (byte[]) tag.mValue);
        } else {
          return mValue.equals(tag.mValue);
        }
      } else {
        return tag.mValue == null;
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mTagId,
        mDataType,
        mHasDefinedDefaultComponentCount,
        mComponentCountActual,
        mIfd,
        mValue,
        mOffset);
  }

  @Override
  public String toString() {
    return String.format("tag id: %04X\n", mTagId)
        + "ifd id: "
        + mIfd
        + "\ntype: "
        + convertTypeToString(mDataType)
        + "\ncount: "
        + mComponentCountActual
        + "\noffset: "
        + mOffset
        + "\nvalue: "
        + forceGetValueAsString()
        + "\n";
  }
}
