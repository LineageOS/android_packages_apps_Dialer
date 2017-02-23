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

package com.android.dialer.simulator.impl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
 final class AutoValue_SimulatorContacts_Contact extends SimulatorContacts.Contact {

  private final String accountType;
  private final String accountName;
  private final String name;
  private final boolean isStarred;
  private final ByteArrayOutputStream photoStream;
  private final List<SimulatorContacts.PhoneNumber> phoneNumbers;
  private final List<SimulatorContacts.Email> emails;

  private AutoValue_SimulatorContacts_Contact(
      String accountType,
      String accountName,
      @Nullable String name,
      boolean isStarred,
      @Nullable ByteArrayOutputStream photoStream,
      List<SimulatorContacts.PhoneNumber> phoneNumbers,
      List<SimulatorContacts.Email> emails) {
    this.accountType = accountType;
    this.accountName = accountName;
    this.name = name;
    this.isStarred = isStarred;
    this.photoStream = photoStream;
    this.phoneNumbers = phoneNumbers;
    this.emails = emails;
  }

  @NonNull
  @Override
  String getAccountType() {
    return accountType;
  }

  @NonNull
  @Override
  String getAccountName() {
    return accountName;
  }

  @Nullable
  @Override
  String getName() {
    return name;
  }

  @Override
  boolean getIsStarred() {
    return isStarred;
  }

  @Nullable
  @Override
  ByteArrayOutputStream getPhotoStream() {
    return photoStream;
  }

  @NonNull
  @Override
  List<SimulatorContacts.PhoneNumber> getPhoneNumbers() {
    return phoneNumbers;
  }

  @NonNull
  @Override
  List<SimulatorContacts.Email> getEmails() {
    return emails;
  }

  @Override
  public String toString() {
    return "Contact{"
        + "accountType=" + accountType + ", "
        + "accountName=" + accountName + ", "
        + "name=" + name + ", "
        + "isStarred=" + isStarred + ", "
        + "photoStream=" + photoStream + ", "
        + "phoneNumbers=" + phoneNumbers + ", "
        + "emails=" + emails
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof SimulatorContacts.Contact) {
      SimulatorContacts.Contact that = (SimulatorContacts.Contact) o;
      return (this.accountType.equals(that.getAccountType()))
           && (this.accountName.equals(that.getAccountName()))
           && ((this.name == null) ? (that.getName() == null) : this.name.equals(that.getName()))
           && (this.isStarred == that.getIsStarred())
           && ((this.photoStream == null) ? (that.getPhotoStream() == null) : this.photoStream.equals(that.getPhotoStream()))
           && (this.phoneNumbers.equals(that.getPhoneNumbers()))
           && (this.emails.equals(that.getEmails()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.accountType.hashCode();
    h *= 1000003;
    h ^= this.accountName.hashCode();
    h *= 1000003;
    h ^= (name == null) ? 0 : this.name.hashCode();
    h *= 1000003;
    h ^= this.isStarred ? 1231 : 1237;
    h *= 1000003;
    h ^= (photoStream == null) ? 0 : this.photoStream.hashCode();
    h *= 1000003;
    h ^= this.phoneNumbers.hashCode();
    h *= 1000003;
    h ^= this.emails.hashCode();
    return h;
  }

  static final class Builder extends SimulatorContacts.Contact.Builder {
    private String accountType;
    private String accountName;
    private String name;
    private Boolean isStarred;
    private ByteArrayOutputStream photoStream;
    private List<SimulatorContacts.PhoneNumber> phoneNumbers;
    private List<SimulatorContacts.Email> emails;
    Builder() {
    }
    private Builder(SimulatorContacts.Contact source) {
      this.accountType = source.getAccountType();
      this.accountName = source.getAccountName();
      this.name = source.getName();
      this.isStarred = source.getIsStarred();
      this.photoStream = source.getPhotoStream();
      this.phoneNumbers = source.getPhoneNumbers();
      this.emails = source.getEmails();
    }
    @Override
    SimulatorContacts.Contact.Builder setAccountType(String accountType) {
      this.accountType = accountType;
      return this;
    }
    @Override
    SimulatorContacts.Contact.Builder setAccountName(String accountName) {
      this.accountName = accountName;
      return this;
    }
    @Override
    SimulatorContacts.Contact.Builder setName(@Nullable String name) {
      this.name = name;
      return this;
    }
    @Override
    SimulatorContacts.Contact.Builder setIsStarred(boolean isStarred) {
      this.isStarred = isStarred;
      return this;
    }
    @Override
    SimulatorContacts.Contact.Builder setPhotoStream(@Nullable ByteArrayOutputStream photoStream) {
      this.photoStream = photoStream;
      return this;
    }
    @Override
    SimulatorContacts.Contact.Builder setPhoneNumbers(List<SimulatorContacts.PhoneNumber> phoneNumbers) {
      this.phoneNumbers = phoneNumbers;
      return this;
    }
    @Override
    SimulatorContacts.Contact.Builder setEmails(List<SimulatorContacts.Email> emails) {
      this.emails = emails;
      return this;
    }
    @Override
    SimulatorContacts.Contact build() {
      String missing = "";
      if (this.accountType == null) {
        missing += " accountType";
      }
      if (this.accountName == null) {
        missing += " accountName";
      }
      if (this.isStarred == null) {
        missing += " isStarred";
      }
      if (this.phoneNumbers == null) {
        missing += " phoneNumbers";
      }
      if (this.emails == null) {
        missing += " emails";
      }
      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_SimulatorContacts_Contact(
          this.accountType,
          this.accountName,
          this.name,
          this.isStarred,
          this.photoStream,
          this.phoneNumbers,
          this.emails);
    }
  }

}
