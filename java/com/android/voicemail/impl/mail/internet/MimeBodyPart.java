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

import com.android.voicemail.impl.mail.Body;
import com.android.voicemail.impl.mail.BodyPart;
import com.android.voicemail.impl.mail.MessagingException;
import com.android.voicemail.impl.mail.Multipart;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.regex.Pattern;

/** TODO this is a close approximation of Message, need to update along with Message. */
public class MimeBodyPart extends BodyPart {
  protected final MimeHeader header = new MimeHeader();
  protected MimeHeader extendedHeader;
  protected Body body;
  protected int size;

  // regex that matches content id surrounded by "<>" optionally.
  private static final Pattern REMOVE_OPTIONAL_BRACKETS = Pattern.compile("^<?([^>]+)>?$");
  // regex that matches end of line.
  private static final Pattern END_OF_LINE = Pattern.compile("\r?\n");

  public MimeBodyPart() throws MessagingException {
    this(null);
  }

  public MimeBodyPart(Body body) throws MessagingException {
    this(body, null);
  }

  public MimeBodyPart(Body body, String mimeType) throws MessagingException {
    if (mimeType != null) {
      setHeader(MimeHeader.HEADER_CONTENT_TYPE, mimeType);
    }
    setBody(body);
  }

  protected String getFirstHeader(String name) throws MessagingException {
    return header.getFirstHeader(name);
  }

  @Override
  public void addHeader(String name, String value) throws MessagingException {
    header.addHeader(name, value);
  }

  @Override
  public void setHeader(String name, String value) throws MessagingException {
    header.setHeader(name, value);
  }

  @Override
  public String[] getHeader(String name) throws MessagingException {
    return header.getHeader(name);
  }

  @Override
  public void removeHeader(String name) throws MessagingException {
    header.removeHeader(name);
  }

  @Override
  public Body getBody() throws MessagingException {
    return body;
  }

  @Override
  public void setBody(Body body) throws MessagingException {
    this.body = body;
    if (body instanceof Multipart) {
      Multipart multipart =
          ((Multipart) body);
      multipart.setParent(this);
      setHeader(MimeHeader.HEADER_CONTENT_TYPE, multipart.getContentType());
    } else if (body instanceof TextBody) {
      String contentType = String.format("%s;\n charset=utf-8", getMimeType());
      String name = MimeUtility.getHeaderParameter(getContentType(), "name");
      if (name != null) {
        contentType += String.format(";\n name=\"%s\"", name);
      }
      setHeader(MimeHeader.HEADER_CONTENT_TYPE, contentType);
      setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING, "base64");
    }
  }

  @Override
  public String getContentType() throws MessagingException {
    String contentType = getFirstHeader(MimeHeader.HEADER_CONTENT_TYPE);
    if (contentType == null) {
      return "text/plain";
    } else {
      return contentType;
    }
  }

  @Override
  public String getDisposition() throws MessagingException {
    String contentDisposition = getFirstHeader(MimeHeader.HEADER_CONTENT_DISPOSITION);
    if (contentDisposition == null) {
      return null;
    } else {
      return contentDisposition;
    }
  }

  @Override
  public String getContentId() throws MessagingException {
    String contentId = getFirstHeader(MimeHeader.HEADER_CONTENT_ID);
    if (contentId == null) {
      return null;
    } else {
      // remove optionally surrounding brackets.
      return REMOVE_OPTIONAL_BRACKETS.matcher(contentId).replaceAll("$1");
    }
  }

  @Override
  public String getMimeType() throws MessagingException {
    return MimeUtility.getHeaderParameter(getContentType(), null);
  }

  @Override
  public boolean isMimeType(String mimeType) throws MessagingException {
    return getMimeType().equals(mimeType);
  }

  public void setSize(int size) {
    this.size = size;
  }

  @Override
  public int getSize() throws MessagingException {
    return size;
  }

  /**
   * Set extended header
   *
   * @param name Extended header name
   * @param value header value - flattened by removing CR-NL if any remove header if value is null
   * @throws MessagingException
   */
  @Override
  public void setExtendedHeader(String name, String value) throws MessagingException {
    if (value == null) {
      if (extendedHeader != null) {
        extendedHeader.removeHeader(name);
      }
      return;
    }
    if (extendedHeader == null) {
      extendedHeader = new MimeHeader();
    }
    extendedHeader.setHeader(name, END_OF_LINE.matcher(value).replaceAll(""));
  }

  /**
   * Get extended header
   *
   * @param name Extended header name
   * @return header value - null if header does not exist
   * @throws MessagingException
   */
  @Override
  public String getExtendedHeader(String name) throws MessagingException {
    if (extendedHeader == null) {
      return null;
    }
    return extendedHeader.getFirstHeader(name);
  }

  /** Write the MimeMessage out in MIME format. */
  @Override
  public void writeTo(OutputStream out) throws IOException, MessagingException {
    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out), 1024);
    header.writeTo(out);
    writer.write("\r\n");
    writer.flush();
    if (body != null) {
      body.writeTo(out);
    }
  }
}
