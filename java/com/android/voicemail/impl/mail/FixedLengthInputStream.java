/*
 * Copyright (C) 2015 The Android Open Source Project
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

import java.io.IOException;
import java.io.InputStream;

/**
 * A filtering InputStream that stops allowing reads after the given length has been read. This is
 * used to allow a client to read directly from an underlying protocol stream without reading past
 * where the protocol handler intended the client to read.
 */
public class FixedLengthInputStream extends InputStream {
  private final InputStream in;
  private final int length;
  private int count;

  public FixedLengthInputStream(InputStream in, int length) {
    this.in = in;
    this.length = length;
  }

  @Override
  public int available() throws IOException {
    return length - count;
  }

  @Override
  public int read() throws IOException {
    if (count < length) {
      count++;
      return in.read();
    } else {
      return -1;
    }
  }

  @Override
  public int read(byte[] b, int offset, int length) throws IOException {
    if (count < this.length) {
      int d = in.read(b, offset, Math.min(this.length - count, length));
      if (d == -1) {
        return -1;
      } else {
        count += d;
        return d;
      }
    } else {
      return -1;
    }
  }

  @Override
  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  public int getLength() {
    return length;
  }

  @Override
  public String toString() {
    return String.format("FixedLengthInputStream(in=%s, length=%d)", in.toString(), length);
  }
}
