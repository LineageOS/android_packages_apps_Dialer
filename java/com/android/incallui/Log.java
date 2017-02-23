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
 * limitations under the License.
 */

package com.android.incallui;

import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telephony.PhoneNumberUtils;
import com.android.dialer.common.LogUtil;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Manages logging for the entire class. */
public class Log {

  public static void d(String tag, String msg) {
    LogUtil.d(tag, msg);
  }

  public static void d(Object obj, String msg) {
    LogUtil.d(getPrefix(obj), msg);
  }

  public static void d(Object obj, String str1, Object str2) {
    LogUtil.d(getPrefix(obj), str1 + str2);
  }

  public static void v(Object obj, String msg) {
    LogUtil.v(getPrefix(obj), msg);
  }

  public static void v(Object obj, String str1, Object str2) {
    LogUtil.v(getPrefix(obj), str1 + str2);
  }

  public static void e(String tag, String msg, Exception e) {
    LogUtil.e(tag, msg, e);
  }

  public static void e(String tag, String msg) {
    LogUtil.e(tag, msg);
  }

  public static void e(Object obj, String msg, Exception e) {
    LogUtil.e(getPrefix(obj), msg, e);
  }

  public static void e(Object obj, String msg) {
    LogUtil.e(getPrefix(obj), msg);
  }

  public static void i(String tag, String msg) {
    LogUtil.i(tag, msg);
  }

  public static void i(Object obj, String msg) {
    LogUtil.i(getPrefix(obj), msg);
  }

  public static void w(Object obj, String msg) {
    LogUtil.w(getPrefix(obj), msg);
  }

  public static String piiHandle(Object pii) {
    if (pii == null || LogUtil.isVerboseEnabled()) {
      return String.valueOf(pii);
    }

    if (pii instanceof Uri) {
      Uri uri = (Uri) pii;

      // All Uri's which are not "tel" go through normal pii() method.
      if (!PhoneAccount.SCHEME_TEL.equals(uri.getScheme())) {
        return pii(pii);
      } else {
        pii = uri.getSchemeSpecificPart();
      }
    }

    String originalString = String.valueOf(pii);
    StringBuilder stringBuilder = new StringBuilder(originalString.length());
    for (char c : originalString.toCharArray()) {
      if (PhoneNumberUtils.isDialable(c)) {
        stringBuilder.append('*');
      } else {
        stringBuilder.append(c);
      }
    }
    return stringBuilder.toString();
  }

  /**
   * Redact personally identifiable information for production users. If we are running in verbose
   * mode, return the original string, otherwise return a SHA-1 hash of the input string.
   */
  public static String pii(Object pii) {
    if (pii == null || LogUtil.isVerboseEnabled()) {
      return String.valueOf(pii);
    }
    return "[" + secureHash(String.valueOf(pii).getBytes()) + "]";
  }

  private static String secureHash(byte[] input) {
    MessageDigest messageDigest;
    try {
      messageDigest = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      return null;
    }
    messageDigest.update(input);
    byte[] result = messageDigest.digest();
    return encodeHex(result);
  }

  private static String encodeHex(byte[] bytes) {
    StringBuffer hex = new StringBuffer(bytes.length * 2);

    for (int i = 0; i < bytes.length; i++) {
      int byteIntValue = bytes[i] & 0xff;
      if (byteIntValue < 0x10) {
        hex.append("0");
      }
      hex.append(Integer.toString(byteIntValue, 16));
    }

    return hex.toString();
  }

  private static String getPrefix(Object obj) {
    return (obj == null ? "" : (obj.getClass().getSimpleName()));
  }
}
