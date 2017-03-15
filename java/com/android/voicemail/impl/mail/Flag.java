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

/** Flags that can be applied to Messages. */
public class Flag {
  // If adding new flags: ALL FLAGS MUST BE UPPER CASE.
  public static final String DELETED = "deleted";
  public static final String SEEN = "seen";
  public static final String ANSWERED = "answered";
  public static final String FLAGGED = "flagged";
  public static final String DRAFT = "draft";
  public static final String RECENT = "recent";
}
