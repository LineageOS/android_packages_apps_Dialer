/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.voicemail.impl.mail.store.imap;

import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.mail.FixedLengthInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Subclass of {@link ImapString} used for literals backed by an in-memory byte array. */
public class ImapMemoryLiteral extends ImapString {
  private final String TAG = "ImapMemoryLiteral";
  private byte[] data;

  /* package */ ImapMemoryLiteral(FixedLengthInputStream in) throws IOException {
    // We could use ByteArrayOutputStream and IOUtils.copy, but it'd perform an unnecessary
    // copy....
    data = new byte[in.getLength()];
    int pos = 0;
    while (pos < data.length) {
      int read = in.read(data, pos, data.length - pos);
      if (read < 0) {
        break;
      }
      pos += read;
    }
    if (pos != data.length) {
      VvmLog.w(TAG, "length mismatch");
    }
  }

  @Override
  public void destroy() {
    data = null;
    super.destroy();
  }

  @Override
  public String getString() {
    return new String(data, StandardCharsets.US_ASCII);
  }

  @Override
  public InputStream getAsStream() {
    return new ByteArrayInputStream(data);
  }

  @Override
  public String toString() {
    return String.format("{%d byte literal(memory)}", data.length);
  }
}
