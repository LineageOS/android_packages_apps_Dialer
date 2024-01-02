/*
 * Copyright (C) 2015 The Android Open Source Project
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Subclass of {@link ImapString} used for non literals. */
public class ImapSimpleString extends ImapString {
  private static final String TAG = "ImapSimpleString";
  private String string;

  /* package */ ImapSimpleString(String string) {
    this.string = (string != null) ? string : "";
  }

  @Override
  public void destroy() {
    string = null;
    super.destroy();
  }

  @Override
  public String getString() {
    return string;
  }

  @Override
  public InputStream getAsStream() {
    return new ByteArrayInputStream(string.getBytes(StandardCharsets.US_ASCII));
  }

  @Override
  public String toString() {
    // Purposefully not return just mString, in order to prevent using it instead of getString.
    return "\"" + string + "\"";
  }
}
