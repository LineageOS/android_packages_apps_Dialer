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
package com.android.voicemail.impl.mail.internet;

import android.util.Base64;
import android.util.Base64OutputStream;

import com.android.voicemail.impl.mail.Body;
import com.android.voicemail.impl.mail.MessagingException;
import com.android.voicemail.impl.mail.TempDirectory;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * A Body that is backed by a temp file. The Body exposes a getOutputStream method that allows the
 * user to write to the temp file. After the write the body is available via getInputStream and
 * writeTo one time. After writeTo is called, or the InputStream returned from getInputStream is
 * closed the file is deleted and the Body should be considered disposed of.
 */
public class BinaryTempFileBody implements Body {
  private File file;

  /**
   * An alternate way to put data into a BinaryTempFileBody is to simply supply an already- created
   * file. Note that this file will be deleted after it is read.
   *
   * @param filePath The file containing the data to be stored on disk temporarily
   */
  public void setFile(String filePath) {
    file = new File(filePath);
  }

  public OutputStream getOutputStream() throws IOException {
    file = File.createTempFile("body", null, TempDirectory.getTempDirectory());
    file.deleteOnExit();
    return Files.newOutputStream(file.toPath());
  }

  @Override
  public InputStream getInputStream() throws MessagingException {
    try {
      return new BinaryTempFileBodyInputStream(Files.newInputStream(file.toPath()));
    } catch (IOException ioe) {
      throw new MessagingException("Unable to open body", ioe);
    }
  }

  @Override
  public void writeTo(OutputStream out) throws IOException, MessagingException {
    InputStream in = getInputStream();
    Base64OutputStream base64Out = new Base64OutputStream(out, Base64.CRLF | Base64.NO_CLOSE);
    IOUtils.copy(in, base64Out);
    base64Out.close();
    file.delete();
    in.close();
  }

  class BinaryTempFileBodyInputStream extends FilterInputStream {
    public BinaryTempFileBodyInputStream(InputStream in) {
      super(in);
    }

    @Override
    public void close() throws IOException {
      super.close();
      file.delete();
    }
  }
}
