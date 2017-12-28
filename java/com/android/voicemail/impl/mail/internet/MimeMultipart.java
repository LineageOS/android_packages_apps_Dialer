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
package com.android.voicemail.impl.mail.internet;

import com.android.voicemail.impl.mail.BodyPart;
import com.android.voicemail.impl.mail.MessagingException;
import com.android.voicemail.impl.mail.Multipart;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class MimeMultipart extends Multipart {
  protected String preamble;

  protected String contentType;

  protected String boundary;

  protected String subType;

  public MimeMultipart() throws MessagingException {
    boundary = generateBoundary();
    setSubType("mixed");
  }

  public MimeMultipart(String contentType) throws MessagingException {
    this.contentType = contentType;
    try {
      subType = MimeUtility.getHeaderParameter(contentType, null).split("/")[1];
      boundary = MimeUtility.getHeaderParameter(contentType, "boundary");
      if (boundary == null) {
        throw new MessagingException("MultiPart does not contain boundary: " + contentType);
      }
    } catch (Exception e) {
      throw new MessagingException(
          "Invalid MultiPart Content-Type; must contain subtype and boundary. ("
              + contentType
              + ")",
          e);
    }
  }

  public String generateBoundary() {
    StringBuffer sb = new StringBuffer();
    sb.append("----");
    for (int i = 0; i < 30; i++) {
      sb.append(Integer.toString((int) (Math.random() * 35), 36));
    }
    return sb.toString().toUpperCase();
  }

  public String getPreamble() throws MessagingException {
    return preamble;
  }

  public void setPreamble(String preamble) throws MessagingException {
    this.preamble = preamble;
  }

  @Override
  public String getContentType() throws MessagingException {
    return contentType;
  }

  public void setSubType(String subType) throws MessagingException {
    this.subType = subType;
    contentType = String.format("multipart/%s; boundary=\"%s\"", subType, boundary);
  }

  @Override
  public void writeTo(OutputStream out) throws IOException, MessagingException {
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out), 1024);

    if (preamble != null) {
      writer.write(preamble + "\r\n");
    }

    for (int i = 0, count = parts.size(); i < count; i++) {
      BodyPart bodyPart = parts.get(i);
      writer.write("--" + boundary + "\r\n");
      writer.flush();
      bodyPart.writeTo(out);
      writer.write("\r\n");
    }

    writer.write("--" + boundary + "--\r\n");
    writer.flush();
  }

  @Override
  public InputStream getInputStream() throws MessagingException {
    return null;
  }

  public String getSubTypeForTest() {
    return subType;
  }
}
