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

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.codec.EncoderUtil;

/**
 * This class represent email address.
 *
 * <p>RFC822 email address may have following format. "name" <address> (comment) "name" <address>
 * name <address> address Name and comment part should be MIME/base64 encoded in header if
 * necessary.
 */
public class Address implements Parcelable {
  public static final String ADDRESS_DELIMETER = ",";
  /** Address part, in the form local_part@domain_part. No surrounding angle brackets. */
  private String address;

  /**
   * Name part. No surrounding double quote, and no MIME/base64 encoding. This must be null if
   * Address has no name part.
   */
  private String personal;

  // Regex that matches address surrounded by '<>' optionally. '^<?([^>]+)>?$'
  private static final Pattern REMOVE_OPTIONAL_BRACKET = Pattern.compile("^<?([^>]+)>?$");
  // Regex that matches personal name surrounded by '""' optionally. '^"?([^"]+)"?$'
  private static final Pattern REMOVE_OPTIONAL_DQUOTE = Pattern.compile("^\"?([^\"]*)\"?$");
  // Regex that matches escaped character '\\([\\"])'
  private static final Pattern UNQUOTE = Pattern.compile("\\\\([\\\\\"])");

  // TODO: LOCAL_PART and DOMAIN_PART_PART are too permissive and can be improved.
  // TODO: Fix this to better constrain comments.
  /** Regex for the local part of an email address. */
  private static final String LOCAL_PART = "[^@]+";
  /** Regex for each part of the domain part, i.e. the thing between the dots. */
  private static final String DOMAIN_PART_PART = "[[\\w][\\d]\\-\\(\\)\\[\\]]+";
  /** Regex for the domain part, which is two or more {@link #DOMAIN_PART_PART} separated by . */
  private static final String DOMAIN_PART = "(" + DOMAIN_PART_PART + "\\.)+" + DOMAIN_PART_PART;

  /** Pattern to check if an email address is valid. */
  private static final Pattern EMAIL_ADDRESS =
      Pattern.compile("\\A" + LOCAL_PART + "@" + DOMAIN_PART + "\\z");

  private static final Address[] EMPTY_ADDRESS_ARRAY = new Address[0];

  public Address(String address) {
    setAddress(address);
  }

  public Address(String address, String personal) {
    setPersonal(personal);
    setAddress(address);
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = REMOVE_OPTIONAL_BRACKET.matcher(address).replaceAll("$1");
  }

  /**
   * Set personal part from UTF-16 string. Optional surrounding double quote will be removed. It
   * will be also unquoted and MIME/base64 decoded.
   *
   * @param personal name part of email address as UTF-16 string. Null is acceptable.
   */
  public void setPersonal(String personal) {
    this.personal = decodeAddressPersonal(personal);
  }

  /**
   * Decodes name from UTF-16 string. Optional surrounding double quote will be removed. It will be
   * also unquoted and MIME/base64 decoded.
   *
   * @param personal name part of email address as UTF-16 string. Null is acceptable.
   */
  public static String decodeAddressPersonal(String personal) {
    if (personal != null) {
      personal = REMOVE_OPTIONAL_DQUOTE.matcher(personal).replaceAll("$1");
      personal = UNQUOTE.matcher(personal).replaceAll("$1");
      personal = DecoderUtil.decodeEncodedWords(personal, DecodeMonitor.STRICT);
      if (personal.length() == 0) {
        personal = null;
      }
    }
    return personal;
  }

  /**
   * Parse a comma-delimited list of addresses in RFC822 format and return an array of Address
   * objects.
   *
   * @param addressList Address list in comma-delimited string.
   * @return An array of 0 or more Addresses.
   */
  public static Address[] parse(String addressList) {
    if (addressList == null || addressList.length() == 0) {
      return EMPTY_ADDRESS_ARRAY;
    }
    Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(addressList);
    ArrayList<Address> addresses = new ArrayList<>();
    for (int i = 0, length = tokens.length; i < length; ++i) {
      Rfc822Token token = tokens[i];
      String address = token.getAddress();
      if (!TextUtils.isEmpty(address)) {
        if (isValidAddress(address)) {
          String name = token.getName();
          if (TextUtils.isEmpty(name)) {
            name = null;
          }
          addresses.add(new Address(address, name));
        }
      }
    }
    return addresses.toArray(new Address[0]);
  }

  /** Checks whether a string email address is valid. E.g. name@domain.com is valid. */
  static boolean isValidAddress(final String address) {
    return EMAIL_ADDRESS.matcher(address).find();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Address) {
      // It seems that the spec says that the "user" part is case-sensitive,
      // while the domain part in case-insesitive.
      // So foo@yahoo.com and Foo@yahoo.com are different.
      // This may seem non-intuitive from the user POV, so we
      // may re-consider it if it creates UI trouble.
      // A problem case is "replyAll" sending to both
      // a@b.c and to A@b.c, which turn out to be the same on the server.
      // Leave unchanged for now (i.e. case-sensitive).
      return getAddress().equals(((Address) o).getAddress());
    }
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return getAddress().hashCode();
  }

  /**
   * Get human readable address string. Do not use this for email header.
   *
   * @return Human readable address string. Not quoted and not encoded.
   */
  @Override
  public String toString() {
    if (personal != null && !personal.equals(address)) {
      if (personal.matches(".*[\\(\\)<>@,;:\\\\\".\\[\\]].*")) {
        return ensureQuotedString(personal) + " <" + address + ">";
      } else {
        return personal + " <" + address + ">";
      }
    } else {
      return address;
    }
  }

  /**
   * Ensures that the given string starts and ends with the double quote character. The string is
   * not modified in any way except to add the double quote character to start and end if it's not
   * already there.
   *
   * <p>sample -> "sample" "sample" -> "sample" ""sample"" -> "sample" "sample"" -> "sample"
   * sa"mp"le -> "sa"mp"le" "sa"mp"le" -> "sa"mp"le" (empty string) -> "" " -> ""
   */
  private static String ensureQuotedString(String s) {
    if (s == null) {
      return null;
    }
    if (!s.matches("^\".*\"$")) {
      return "\"" + s + "\"";
    } else {
      return s;
    }
  }

  /**
   * Get RFC822/MIME compatible address string.
   *
   * @return RFC822/MIME compatible address string. It may be surrounded by double quote or quoted
   *     and MIME/base64 encoded if necessary.
   */
  public String toHeader() {
    if (personal != null) {
      return EncoderUtil.encodeAddressDisplayName(personal) + " <" + address + ">";
    } else {
      return address;
    }
  }

  public static final Creator<Address> CREATOR =
      new Creator<Address>() {
        @Override
        public Address createFromParcel(Parcel parcel) {
          return new Address(parcel);
        }

        @Override
        public Address[] newArray(int size) {
          return new Address[size];
        }
      };

  public Address(Parcel in) {
    setPersonal(in.readString());
    setAddress(in.readString());
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeString(personal);
    out.writeString(address);
  }
}
