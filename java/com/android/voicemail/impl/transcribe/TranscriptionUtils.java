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
 * limitations under the License
 */
package com.android.voicemail.impl.transcribe;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.support.annotation.Nullable;
import android.util.Base64;
import com.android.dialer.common.Assert;
import com.google.internal.communications.voicemailtranscription.v1.AudioFormat;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Utility methods used by this transcription package. */
public class TranscriptionUtils {
  static final String AMR_PREFIX = "#!AMR\n";

  // Uses try-with-resource
  @TargetApi(android.os.Build.VERSION_CODES.M)
  static ByteString getAudioData(Context context, Uri voicemailUri) {
    try (InputStream in = context.getContentResolver().openInputStream(voicemailUri)) {
      return ByteString.readFrom(in);
    } catch (IOException e) {
      return null;
    }
  }

  static AudioFormat getAudioFormat(ByteString audioData) {
    return audioData != null && audioData.startsWith(ByteString.copyFromUtf8(AMR_PREFIX))
        ? AudioFormat.AMR_NB_8KHZ
        : AudioFormat.AUDIO_FORMAT_UNSPECIFIED;
  }

  @TargetApi(VERSION_CODES.O)
  static String getFingerprintFor(ByteString data, @Nullable String salt) {
    Assert.checkArgument(data != null);
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      if (salt != null) {
        md.update(salt.getBytes());
      }
      byte[] md5Bytes = md.digest(data.toByteArray());
      return Base64.encodeToString(md5Bytes, Base64.DEFAULT);
    } catch (NoSuchAlgorithmException e) {
      Assert.fail(e.toString());
    }
    return null;
  }
}
