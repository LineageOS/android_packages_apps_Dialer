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
package com.android.dialer.oem;

import android.annotation.SuppressLint;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.Signature;
import android.support.annotation.Nullable;
import com.android.dialer.common.LogUtil;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Utility class to verify Cequint package information. */
final class CequintPackageUtils {

  private static final int SIGNED_1024 = 0;
  private static final int SIGNED_2048 = 1;
  private static final int SIGNED_VZW = 2;
  private static final int SIGNED_SPRINT = 3;
  // TODO(a bug): Add test for this signature.
  private static final int SIGNED_SPRINT_NEW = 4;

  // Known Caller Name ID fingerprints
  private static final List<byte[]> callerIdFingerprints = new ArrayList<>();

  static {
    // 1024 signed
    callerIdFingerprints.add(
        SIGNED_1024,
        new byte[] {
          0x1A,
          0x0C,
          (byte) 0xF8,
          (byte) 0x8D,
          0x5B,
          (byte) 0xE2,
          0x6A,
          (byte) 0xED,
          0x50,
          (byte) 0x85,
          (byte) 0xFE,
          (byte) 0x88,
          (byte) 0xA0,
          (byte) 0x9E,
          (byte) 0xEC,
          0x25,
          0x1E,
          (byte) 0xCA,
          0x16,
          (byte) 0x97,
          0x50,
          (byte) 0xDA,
          0x21,
          (byte) 0xCC,
          0x18,
          (byte) 0xC9,
          (byte) 0x98,
          (byte) 0xAF,
          0x26,
          (byte) 0xCD,
          0x06,
          0x71
        });
    // 2048 signed
    callerIdFingerprints.add(
        SIGNED_2048,
        new byte[] {
          (byte) 0xCA,
          0x2F,
          (byte) 0xAE,
          (byte) 0xF4,
          0x09,
          (byte) 0xEF,
          0x4C,
          0x79,
          (byte) 0xF8,
          0x4C,
          (byte) 0xD8,
          (byte) 0x97,
          (byte) 0xBF,
          0x1A,
          0x15,
          0x0F,
          (byte) 0xF0,
          0x5E,
          0x54,
          0x74,
          (byte) 0xB6,
          0x4A,
          (byte) 0xCA,
          (byte) 0xCD,
          0x05,
          0x7E,
          0x1E,
          (byte) 0x98,
          (byte) 0xC6,
          0x1F,
          0x5C,
          0x45
        });
    // VZW Package
    callerIdFingerprints.add(
        SIGNED_VZW,
        new byte[] {
          (byte) 0xE6,
          0x7A,
          0x0E,
          (byte) 0xB0,
          0x76,
          0x4E,
          (byte) 0xC3,
          0x28,
          (byte) 0xB7,
          (byte) 0xC1,
          0x1B,
          0x1B,
          (byte) 0xD0,
          (byte) 0x84,
          0x28,
          (byte) 0xA6,
          0x16,
          (byte) 0xD9,
          (byte) 0xF3,
          (byte) 0xEB,
          (byte) 0xB0,
          0x20,
          (byte) 0xA7,
          (byte) 0xD8,
          (byte) 0xDF,
          0x14,
          0x72,
          (byte) 0x81,
          0x4C,
          0x13,
          (byte) 0xF3,
          (byte) 0xC9
        });

    // Sprint Package
    callerIdFingerprints.add(
        SIGNED_SPRINT,
        new byte[] {
          0x1A,
          (byte) 0xBA,
          (byte) 0xA2,
          (byte) 0x84,
          0x0C,
          0x61,
          (byte) 0x96,
          0x09,
          (byte) 0x91,
          0x5E,
          (byte) 0x91,
          (byte) 0x95,
          0x3D,
          0x29,
          0x3C,
          (byte) 0x90,
          (byte) 0xEC,
          (byte) 0xB4,
          (byte) 0x89,
          0x1D,
          (byte) 0xC0,
          (byte) 0xB1,
          0x23,
          0x58,
          (byte) 0x98,
          (byte) 0xEB,
          (byte) 0xE6,
          (byte) 0xD4,
          0x09,
          (byte) 0xE5,
          (byte) 0x8E,
          (byte) 0x9D
        });
    callerIdFingerprints.add(
        SIGNED_SPRINT_NEW,
        new byte[] {
          0x27,
          (byte) 0xF9,
          0x6D,
          (byte) 0xBA,
          (byte) 0xB7,
          0x7B,
          0x31,
          (byte) 0xF6,
          (byte) 0x95,
          0x3E,
          0x4C,
          (byte) 0xD2,
          (byte) 0xC2,
          (byte) 0xDE,
          (byte) 0xFE,
          0x15,
          (byte) 0xF5,
          (byte) 0xD7,
          (byte) 0xC7,
          (byte) 0x8F,
          0x07,
          0x3D,
          (byte) 0xD7,
          0x16,
          0x20,
          0x18,
          (byte) 0xEF,
          0x47,
          0x6B,
          0x09,
          0x7C,
          0x34
        });
  }

  @SuppressLint("PackageManagerGetSignatures")
  static boolean isCallerIdInstalled(
      @Nullable PackageManager packageManager, @Nullable String authority) {
    if (packageManager == null) {
      LogUtil.i("CequintPackageUtils.isCallerIdInstalled", "failed to get PackageManager!");
      return false;
    }

    ProviderInfo providerInfo =
        packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA);
    if (providerInfo == null) {
      LogUtil.d(
          "CequintPackageUtils.isCallerIdInstalled",
          "no content provider with '%s' authority",
          authority);
      return false;
    }

    String packageName = providerInfo.packageName;
    if (packageName == null) {
      LogUtil.w("CequintPackageUtils.isCallerIdInstalled", "can't get valid package name.");
      return false;
    }

    LogUtil.i(
        "CequintPackageUtils.isCallerIdInstalled",
        "content provider package name : " + packageName);

    try {
      PackageInfo packageInfo =
          packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);

      Signature[] signatures = packageInfo.signatures;
      if (signatures.length > 1) {
        LogUtil.w(
            "CequintPackageUtils.isCallerIdInstalled", "package has more than one signature.");
        return false;
      }
      byte[] sha256Bytes = getSHA256(signatures[0].toByteArray());

      for (int i = 0; i < callerIdFingerprints.size(); i++) {
        if (Arrays.equals(callerIdFingerprints.get(i), sha256Bytes)) {
          LogUtil.i(
              "CequintPackageUtils.isCallerIdInstalled",
              "this is %s Caller Name ID APK.",
              getApkTypeString(i));
          return true;
        }
      }
    } catch (PackageManager.NameNotFoundException e) {
      LogUtil.e(
          "CequintPackageUtils.isCallerIdInstalled",
          "couldn't find package info for the package: %s",
          packageName,
          e);
    }
    LogUtil.w(
        "CequintPackageUtils.isCallerIdInstalled",
        "signature check failed for package: %s",
        packageName);
    return false;
  }

  // Returns sha256 hash of the signature
  @Nullable
  private static byte[] getSHA256(byte[] sig) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA256", "BC");
    } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
      LogUtil.e("CequintPackageUtils.getSHA256", "", e);
      return null;
    }

    digest.update(sig);
    return digest.digest();
  }

  private static String getApkTypeString(int index) {
    switch (index) {
      case SIGNED_1024:
        return "1024-signed";
      case SIGNED_2048:
        return "2048-signed";
      case SIGNED_VZW:
        return "VZWPackage";
      case SIGNED_SPRINT:
      default:
        return "SprintPackage";
    }
  }
}
