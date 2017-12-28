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

import com.android.dialer.common.Assert;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

class CountedDataInputStream extends FilterInputStream {

  private int count = 0;

  // allocate a byte buffer for a long value;
  private final byte[] byteArray = new byte[8];
  private final ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);

  CountedDataInputStream(InputStream in) {
    super(in);
  }

  int getReadByteCount() {
    return count;
  }

  @Override
  public int read(byte[] b) throws IOException {
    int r = in.read(b);
    count += (r >= 0) ? r : 0;
    return r;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int r = in.read(b, off, len);
    count += (r >= 0) ? r : 0;
    return r;
  }

  @Override
  public int read() throws IOException {
    int r = in.read();
    count += (r >= 0) ? 1 : 0;
    return r;
  }

  @Override
  public long skip(long length) throws IOException {
    long skip = in.skip(length);
    count += skip;
    return skip;
  }

  private void skipOrThrow(long length) throws IOException {
    if (skip(length) != length) {
      throw new EOFException();
    }
  }

  void skipTo(long target) throws IOException {
    long cur = count;
    long diff = target - cur;
    Assert.checkArgument(diff >= 0);
    skipOrThrow(diff);
  }

  private void readOrThrow(byte[] b, int off, int len) throws IOException {
    int r = read(b, off, len);
    if (r != len) {
      throw new EOFException();
    }
  }

  private void readOrThrow(byte[] b) throws IOException {
    readOrThrow(b, 0, b.length);
  }

  void setByteOrder(ByteOrder order) {
    byteBuffer.order(order);
  }

  ByteOrder getByteOrder() {
    return byteBuffer.order();
  }

  short readShort() throws IOException {
    readOrThrow(byteArray, 0, 2);
    byteBuffer.rewind();
    return byteBuffer.getShort();
  }

  int readUnsignedShort() throws IOException {
    return readShort() & 0xffff;
  }

  int readInt() throws IOException {
    readOrThrow(byteArray, 0, 4);
    byteBuffer.rewind();
    return byteBuffer.getInt();
  }

  long readUnsignedInt() throws IOException {
    return readInt() & 0xffffffffL;
  }

  String readString(int n, Charset charset) throws IOException {
    byte[] buf = new byte[n];
    readOrThrow(buf);
    return new String(buf, charset);
  }
}
