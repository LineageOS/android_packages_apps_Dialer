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
import java.io.OutputStream;

public interface Part extends Fetchable {
  void addHeader(String name, String value) throws MessagingException;

  void removeHeader(String name) throws MessagingException;

  void setHeader(String name, String value) throws MessagingException;

  Body getBody() throws MessagingException;

  String getContentType() throws MessagingException;

  String getDisposition() throws MessagingException;

  String getContentId() throws MessagingException;

  String[] getHeader(String name) throws MessagingException;

  void setExtendedHeader(String name, String value) throws MessagingException;

  String getExtendedHeader(String name) throws MessagingException;

  int getSize() throws MessagingException;

  boolean isMimeType(String mimeType) throws MessagingException;

  String getMimeType() throws MessagingException;

  void setBody(Body body) throws MessagingException;

  void writeTo(OutputStream out) throws IOException, MessagingException;
}
