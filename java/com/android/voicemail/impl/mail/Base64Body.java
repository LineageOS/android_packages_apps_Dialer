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
package com.android.voicemail.impl.mail;

import android.util.Base64;
import android.util.Base64OutputStream;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Base64Body implements Body {
  private final InputStream source;
  // Because we consume the input stream, we can only write out once
  private boolean alreadyWritten;

  public Base64Body(InputStream source) {
    this.source = source;
  }

  @Override
  public InputStream getInputStream() throws MessagingException {
    return source;
  }

  /**
   * This method consumes the input stream, so can only be called once
   *
   * @param out Stream to write to
   * @throws IllegalStateException If called more than once
   * @throws IOException
   * @throws MessagingException
   */
  @Override
  public void writeTo(OutputStream out)
      throws IllegalStateException, IOException, MessagingException {
    if (alreadyWritten) {
      throw new IllegalStateException("Base64Body can only be written once");
    }
    alreadyWritten = true;
    try {
      final Base64OutputStream b64out = new Base64OutputStream(out, Base64.DEFAULT);
      IOUtils.copyLarge(source, b64out);
    } finally {
      source.close();
    }
  }
}
