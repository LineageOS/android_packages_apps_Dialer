/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.voicemail.impl.utils;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;

/**
 * Lightweight wrapper around {@link PrintWriter} that automatically indents newlines based on
 * internal state. It also automatically wraps long lines based on given line length.
 *
 * <p>Delays writing indent until first actual write on a newline, enabling indent modification
 * after newline.
 */
public class IndentingPrintWriter extends PrintWriter {

  private final String singleIndent;
  private final int wrapLength;

  /** Mutable version of current indent */
  private final StringBuilder indentBuilder = new StringBuilder();
  /** Cache of current {@link #indentBuilder} value */
  private char[] currentIndent;
  /** Length of current line being built, excluding any indent */
  private int currentLength;

  /**
   * Flag indicating if we're currently sitting on an empty line, and that next write should be
   * prefixed with the current indent.
   */
  private boolean emptyLine = true;

  private final char[] singleChar = new char[1];

  public IndentingPrintWriter(Writer writer, String singleIndent) {
    this(writer, singleIndent, -1);
  }

  public IndentingPrintWriter(Writer writer, String singleIndent, int wrapLength) {
    super(writer);
    this.singleIndent = singleIndent;
    this.wrapLength = wrapLength;
  }

  public void increaseIndent() {
    indentBuilder.append(singleIndent);
    currentIndent = null;
  }

  public void decreaseIndent() {
    indentBuilder.delete(0, singleIndent.length());
    currentIndent = null;
  }

  public void printPair(String key, Object value) {
    print(key + "=" + value + " ");
  }

  public void printPair(String key, Object[] value) {
    print(key + "=" + Arrays.toString(value) + " ");
  }

  public void printHexPair(String key, int value) {
    print(key + "=0x" + Integer.toHexString(value) + " ");
  }

  @Override
  public void println() {
    write('\n');
  }

  @Override
  public void write(int c) {
    singleChar[0] = (char) c;
    write(singleChar, 0, 1);
  }

  @Override
  public void write(String s, int off, int len) {
    final char[] buf = new char[len];
    s.getChars(off, len - off, buf, 0);
    write(buf, 0, len);
  }

  @Override
  public void write(char[] buf, int offset, int count) {
    final int indentLength = indentBuilder.length();
    final int bufferEnd = offset + count;
    int lineStart = offset;
    int lineEnd = offset;

    // March through incoming buffer looking for newlines
    while (lineEnd < bufferEnd) {
      char ch = buf[lineEnd++];
      currentLength++;
      if (ch == '\n') {
        maybeWriteIndent();
        super.write(buf, lineStart, lineEnd - lineStart);
        lineStart = lineEnd;
        emptyLine = true;
        currentLength = 0;
      }

      // Wrap if we've pushed beyond line length
      if (wrapLength > 0 && currentLength >= wrapLength - indentLength) {
        if (!emptyLine) {
          // Give ourselves a fresh line to work with
          super.write('\n');
          emptyLine = true;
          currentLength = lineEnd - lineStart;
        } else {
          // We need more than a dedicated line, slice it hard
          maybeWriteIndent();
          super.write(buf, lineStart, lineEnd - lineStart);
          super.write('\n');
          emptyLine = true;
          lineStart = lineEnd;
          currentLength = 0;
        }
      }
    }

    if (lineStart != lineEnd) {
      maybeWriteIndent();
      super.write(buf, lineStart, lineEnd - lineStart);
    }
  }

  private void maybeWriteIndent() {
    if (emptyLine) {
      emptyLine = false;
      if (indentBuilder.length() != 0) {
        if (currentIndent == null) {
          currentIndent = indentBuilder.toString().toCharArray();
        }
        super.write(currentIndent, 0, currentIndent.length);
      }
    }
  }
}
