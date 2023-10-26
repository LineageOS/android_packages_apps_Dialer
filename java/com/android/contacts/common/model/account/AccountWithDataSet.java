/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.common.model.account;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.util.Objects;

/** Wrapper for an account that includes a data set (which may be null). */
public class AccountWithDataSet implements Parcelable {

  // For Parcelable
  public static final Creator<AccountWithDataSet> CREATOR =
      new Creator<AccountWithDataSet>() {
        public AccountWithDataSet createFromParcel(Parcel source) {
          return new AccountWithDataSet(source);
        }

        public AccountWithDataSet[] newArray(int size) {
          return new AccountWithDataSet[size];
        }
      };

  public final String name;
  public final String type;
  public final String dataSet;
  private final AccountTypeWithDataSet mAccountTypeWithDataSet;

  public AccountWithDataSet(String name, String type, String dataSet) {
    this.name = emptyToNull(name);
    this.type = emptyToNull(type);
    this.dataSet = emptyToNull(dataSet);
    mAccountTypeWithDataSet = AccountTypeWithDataSet.get(type, dataSet);
  }

  public AccountWithDataSet(Parcel in) {
    this.name = in.readString();
    this.type = in.readString();
    this.dataSet = in.readString();
    mAccountTypeWithDataSet = AccountTypeWithDataSet.get(type, dataSet);
  }

  private static String emptyToNull(String text) {
    return TextUtils.isEmpty(text) ? null : text;
  }

  public int describeContents() {
    return 0;
  }

  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(name);
    dest.writeString(type);
    dest.writeString(dataSet);
  }

  public AccountTypeWithDataSet getAccountTypeWithDataSet() {
    return mAccountTypeWithDataSet;
  }

  public boolean equals(Object obj) {
    if (obj instanceof AccountWithDataSet) {
      AccountWithDataSet other = (AccountWithDataSet) obj;
      return Objects.equals(name, other.name)
          && Objects.equals(type, other.type)
          && Objects.equals(dataSet, other.dataSet);
    }
    return false;
  }

  public int hashCode() {
    int result = 17;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (dataSet != null ? dataSet.hashCode() : 0);
    return result;
  }

  public String toString() {
    return "AccountWithDataSet {name=" + name + ", type=" + type + ", dataSet=" + dataSet + "}";
  }
}
