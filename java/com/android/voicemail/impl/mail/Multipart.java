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

import java.util.ArrayList;

public abstract class Multipart implements Body {
  protected Part parent;

  protected ArrayList<BodyPart> parts = new ArrayList<BodyPart>();

  protected String contentType;

  public void addBodyPart(BodyPart part) throws MessagingException {
    parts.add(part);
  }

  public void addBodyPart(BodyPart part, int index) throws MessagingException {
    parts.add(index, part);
  }

  public BodyPart getBodyPart(int index) throws MessagingException {
    return parts.get(index);
  }

  public String getContentType() throws MessagingException {
    return contentType;
  }

  public int getCount() throws MessagingException {
    return parts.size();
  }

  public boolean removeBodyPart(BodyPart part) throws MessagingException {
    return parts.remove(part);
  }

  public void removeBodyPart(int index) throws MessagingException {
    parts.remove(index);
  }

  public Part getParent() throws MessagingException {
    return parent;
  }

  public void setParent(Part parent) throws MessagingException {
    this.parent = parent;
  }
}
