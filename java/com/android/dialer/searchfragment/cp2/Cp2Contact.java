/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.searchfragment.cp2;

import android.database.Cursor;
import android.support.annotation.Nullable;
import com.android.dialer.searchfragment.common.Projections;
import com.google.auto.value.AutoValue;

/** POJO Representation for contacts returned in {@link SearchContactsCursorLoader}. */
@AutoValue
public abstract class Cp2Contact {

  public abstract long phoneId();

  public abstract int phoneType();

  @Nullable
  public abstract String phoneLabel();

  public abstract String phoneNumber();

  @Nullable
  public abstract String displayName();

  public abstract int photoId();

  @Nullable
  public abstract String photoUri();

  public abstract String lookupKey();

  public abstract int carrierPresence();

  public abstract int contactId();

  @Nullable
  public abstract String companyName();

  @Nullable
  public abstract String nickName();

  public abstract String mimeType();

  /** Builder for {@link Cp2Contact}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setPhoneId(long id);

    public abstract Builder setPhoneType(int type);

    public abstract Builder setPhoneLabel(@Nullable String label);

    public abstract Builder setPhoneNumber(String number);

    public abstract Builder setDisplayName(@Nullable String name);

    public abstract Builder setPhotoId(int id);

    public abstract Builder setPhotoUri(@Nullable String uri);

    public abstract Builder setLookupKey(String lookupKey);

    public abstract Builder setCarrierPresence(int presence);

    public abstract Builder setContactId(int id);

    public abstract Builder setCompanyName(@Nullable String name);

    public abstract Builder setNickName(@Nullable String nickName);

    public abstract Builder setMimeType(String mimeType);

    public abstract Cp2Contact build();
  }

  public static Builder builder() {
    return new AutoValue_Cp2Contact.Builder();
  }

  public static Cp2Contact fromCursor(Cursor cursor) {
    return Cp2Contact.builder()
        .setPhoneId(cursor.getLong(Projections.CONTACT_ID))
        .setPhoneType(cursor.getInt(Projections.PHONE_TYPE))
        .setPhoneLabel(cursor.getString(Projections.PHONE_LABEL))
        .setPhoneNumber(cursor.getString(Projections.PHONE_NUMBER))
        .setDisplayName(cursor.getString(Projections.DISPLAY_NAME))
        .setPhotoId(cursor.getInt(Projections.PHOTO_ID))
        .setPhotoUri(cursor.getString(Projections.PHOTO_URI))
        .setLookupKey(cursor.getString(Projections.LOOKUP_KEY))
        .setCarrierPresence(cursor.getInt(Projections.CARRIER_PRESENCE))
        .setContactId(cursor.getInt(Projections.CONTACT_ID))
        .setCompanyName(cursor.getString(Projections.COMPANY_NAME))
        .setNickName(cursor.getString(Projections.NICKNAME))
        .setMimeType(cursor.getString(Projections.MIME_TYPE))
        .build();
  }

  public Object[] toCursorRow() {
    Object[] row = new Object[Projections.CP2_PROJECTION.length];
    row[Projections.ID] = phoneId();
    row[Projections.PHONE_TYPE] = phoneType();
    row[Projections.PHONE_LABEL] = phoneLabel();
    row[Projections.PHONE_NUMBER] = phoneNumber();
    row[Projections.DISPLAY_NAME] = displayName();
    row[Projections.PHOTO_ID] = photoId();
    row[Projections.PHOTO_URI] = photoUri();
    row[Projections.LOOKUP_KEY] = lookupKey();
    row[Projections.CARRIER_PRESENCE] = carrierPresence();
    row[Projections.CONTACT_ID] = contactId();
    row[Projections.COMPANY_NAME] = companyName();
    row[Projections.NICKNAME] = nickName();
    row[Projections.MIME_TYPE] = mimeType();
    return row;
  }

  public abstract Builder toBuilder();
}
