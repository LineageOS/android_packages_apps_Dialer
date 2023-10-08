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

package com.android.contacts.common.model.dataitem;

import android.content.ContentValues;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts.Data;

/**
 * Represents a structured name data item, wrapping the columns in {@link
 * ContactsContract.CommonDataKinds.StructuredName}.
 */
public class StructuredNameDataItem extends DataItem {

  public StructuredNameDataItem() {
    super(new ContentValues());
    getContentValues().put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
  }

  /* package */ StructuredNameDataItem(ContentValues values) {
    super(values);
  }

  public String getDisplayName() {
    return getContentValues().getAsString(StructuredName.DISPLAY_NAME);
  }

  public void setDisplayName(String name) {
    getContentValues().put(StructuredName.DISPLAY_NAME, name);
  }

  public String getPrefix() {
    return getContentValues().getAsString(StructuredName.PREFIX);
  }

  public String getMiddleName() {
    return getContentValues().getAsString(StructuredName.MIDDLE_NAME);
  }

  public String getSuffix() {
    return getContentValues().getAsString(StructuredName.SUFFIX);
  }

  public boolean isSuperPrimary() {
    final ContentValues contentValues = getContentValues();
    return contentValues == null || !contentValues.containsKey(StructuredName.IS_SUPER_PRIMARY)
        ? false
        : contentValues.getAsBoolean(StructuredName.IS_SUPER_PRIMARY);
  }
}
