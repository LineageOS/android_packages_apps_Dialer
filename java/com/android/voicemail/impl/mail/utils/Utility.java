/**
 * Copyright (c) 2015 The Android Open Source Project
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.voicemail.impl.mail.utils;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

/** Simple utility methods used in email functions. */
public class Utility {
  public static final Charset ASCII = Charset.forName("US-ASCII");

  public static final String[] EMPTY_STRINGS = new String[0];

  /**
   * Returns a concatenated string containing the output of every Object's toString() method, each
   * separated by the given separator character.
   */
  public static String combine(Object[] parts, char separator) {
    if (parts == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      sb.append(parts[i].toString());
      if (i < parts.length - 1) {
        sb.append(separator);
      }
    }
    return sb.toString();
  }

  /** Converts a String to ASCII bytes */
  public static byte[] toAscii(String s) {
    return encode(ASCII, s);
  }

  /** Builds a String from ASCII bytes */
  public static String fromAscii(byte[] b) {
    return decode(ASCII, b);
  }

  private static byte[] encode(Charset charset, String s) {
    if (s == null) {
      return null;
    }
    final ByteBuffer buffer = charset.encode(CharBuffer.wrap(s));
    final byte[] bytes = new byte[buffer.limit()];
    buffer.get(bytes);
    return bytes;
  }

  private static String decode(Charset charset, byte[] b) {
    if (b == null) {
      return null;
    }
    final CharBuffer cb = charset.decode(ByteBuffer.wrap(b));
    return new String(cb.array(), 0, cb.length());
  }

  public static ByteArrayInputStream streamFromAsciiString(String ascii) {
    return new ByteArrayInputStream(toAscii(ascii));
  }
}
