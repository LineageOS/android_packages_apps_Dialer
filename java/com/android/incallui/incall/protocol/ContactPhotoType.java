/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.incallui.incall.protocol;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Types of contact photos we can have. */
@Retention(RetentionPolicy.SOURCE)
@IntDef({
  ContactPhotoType.DEFAULT_PLACEHOLDER,
  ContactPhotoType.BUSINESS,
  ContactPhotoType.CONTACT,
})
public @interface ContactPhotoType {

  int DEFAULT_PLACEHOLDER = 0;
  int BUSINESS = 1;
  int CONTACT = 2;
}
