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
 * limitations under the License
 */

package com.android.incallui.answer.impl.hint;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION_CODES;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.common.Assert;
import com.android.dialer.common.ConfigProvider;
import com.android.dialer.common.ConfigProviderBindings;
import com.android.dialer.common.LogUtil;
import java.io.InputStream;
import java.util.TimeZone;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Decrypt the event payload to be shown if in a specific time range and the key is received. */
@TargetApi(VERSION_CODES.M)
public final class EventPayloadLoaderImpl implements EventPayloadLoader {

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String CONFIG_EVENT_KEY = "event_key";

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String CONFIG_EVENT_BINARY = "event_binary";

  // Time is stored as a UTC UNIX timestamp in milliseconds, but interpreted as local time.
  // For example, 946684800 (2000/1/1 00:00:00 @UTC) is the new year midnight at every timezone.
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String CONFIG_EVENT_START_UTC_AS_LOCAL_MILLIS = "event_time_start_millis";

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String CONFIG_EVENT_TIME_END_UTC_AS_LOCAL_MILLIS = "event_time_end_millis";

  @Override
  @Nullable
  public Drawable loadPayload(
      @NonNull Context context, long currentTimeUtcMillis, @NonNull TimeZone timeZone) {
    Assert.isNotNull(context);
    Assert.isNotNull(timeZone);
    ConfigProvider configProvider = ConfigProviderBindings.get(context);

    String pbeKey = configProvider.getString(CONFIG_EVENT_KEY, null);
    if (pbeKey == null) {
      return null;
    }
    long timeRangeStart = configProvider.getLong(CONFIG_EVENT_START_UTC_AS_LOCAL_MILLIS, 0);
    long timeRangeEnd = configProvider.getLong(CONFIG_EVENT_TIME_END_UTC_AS_LOCAL_MILLIS, 0);

    String eventBinary = configProvider.getString(CONFIG_EVENT_BINARY, null);
    if (eventBinary == null) {
      return null;
    }

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    if (!preferences.getBoolean(
        EventSecretCodeListener.EVENT_ENABLED_WITH_SECRET_CODE_KEY, false)) {
      long localTimestamp = currentTimeUtcMillis + timeZone.getRawOffset();

      if (localTimestamp < timeRangeStart) {
        return null;
      }

      if (localTimestamp > timeRangeEnd) {
        return null;
      }
    }

    // Use openssl aes-128-cbc -in <input> -out <output> -pass <PBEKey> to generate the asset
    try (InputStream input = context.getAssets().open(eventBinary)) {
      byte[] encryptedFile = new byte[input.available()];
      input.read(encryptedFile);

      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");

      byte[] salt = new byte[8];
      System.arraycopy(encryptedFile, 8, salt, 0, 8);
      SecretKey key =
          SecretKeyFactory.getInstance("PBEWITHMD5AND128BITAES-CBC-OPENSSL", "BC")
              .generateSecret(new PBEKeySpec(pbeKey.toCharArray(), salt, 100));
      cipher.init(Cipher.DECRYPT_MODE, key);

      byte[] decryptedFile = cipher.doFinal(encryptedFile, 16, encryptedFile.length - 16);

      return new BitmapDrawable(
          context.getResources(),
          BitmapFactory.decodeByteArray(decryptedFile, 0, decryptedFile.length));
    } catch (Exception e) {
      // Avoid crashing dialer for any reason.
      LogUtil.e("EventPayloadLoader.loadPayload", "error decrypting payload:", e);
      return null;
    }
  }
}
