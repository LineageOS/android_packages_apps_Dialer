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
import android.support.annotation.VisibleForTesting;
import android.text.Html;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import com.android.voicemail.impl.mail.utils.LogUtils;
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
  private String mAddress;

  /**
   * Name part. No surrounding double quote, and no MIME/base64 encoding. This must be null if
   * Address has no name part.
   */
  private String mPersonal;

  /**
   * When personal is set, it will return the first token of the personal string. Otherwise, it will
   * return the e-mail address up to the '@' sign.
   */
  private String mSimplifiedName;

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

  // delimiters are chars that do not appear in an email address, used by fromHeader
  private static final char LIST_DELIMITER_EMAIL = '\1';
  private static final char LIST_DELIMITER_PERSONAL = '\2';

  private static final String LOG_TAG = "Email Address";

  @VisibleForTesting
  public Address(String address) {
    setAddress(address);
  }

  public Address(String address, String personal) {
    setPersonal(personal);
    setAddress(address);
  }

  /**
   * Returns a simplified string for this e-mail address. When a name is known, it will return the
   * first token of that name. Otherwise, it will return the e-mail address up to the '@' sign.
   */
  public String getSimplifiedName() {
    if (mSimplifiedName == null) {
      if (TextUtils.isEmpty(mPersonal) && !TextUtils.isEmpty(mAddress)) {
        int atSign = mAddress.indexOf('@');
        mSimplifiedName = (atSign != -1) ? mAddress.substring(0, atSign) : "";
      } else if (!TextUtils.isEmpty(mPersonal)) {

        // TODO: use Contacts' NameSplitter for more reliable first-name extraction

        int end = mPersonal.indexOf(' ');
        while (end > 0 && mPersonal.charAt(end - 1) == ',') {
          end--;
        }
        mSimplifiedName = (end < 1) ? mPersonal : mPersonal.substring(0, end);

      } else {
        LogUtils.w(LOG_TAG, "Unable to get a simplified name");
        mSimplifiedName = "";
      }
    }
    return mSimplifiedName;
  }

  public static synchronized Address getEmailAddress(String rawAddress) {
    if (TextUtils.isEmpty(rawAddress)) {
      return null;
    }
    String name;
    String address;
    final Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(rawAddress);
    if (tokens.length > 0) {
      final String tokenizedName = tokens[0].getName();
      name = tokenizedName != null ? Html.fromHtml(tokenizedName.trim()).toString() : "";
      address = Html.fromHtml(tokens[0].getAddress()).toString();
    } else {
      name = "";
      address = rawAddress == null ? "" : Html.fromHtml(rawAddress).toString();
    }
    return new Address(address, name);
  }

  public String getAddress() {
    return mAddress;
  }

  public void setAddress(String address) {
    mAddress = REMOVE_OPTIONAL_BRACKET.matcher(address).replaceAll("$1");
  }

  /**
   * Get name part as UTF-16 string. No surrounding double quote, and no MIME/base64 encoding.
   *
   * @return Name part of email address. Returns null if it is omitted.
   */
  public String getPersonal() {
    return mPersonal;
  }

  /**
   * Set personal part from UTF-16 string. Optional surrounding double quote will be removed. It
   * will be also unquoted and MIME/base64 decoded.
   *
   * @param personal name part of email address as UTF-16 string. Null is acceptable.
   */
  public void setPersonal(String personal) {
    mPersonal = decodeAddressPersonal(personal);
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
   * This method is used to check that all the addresses that the user entered in a list (e.g. To:)
   * are valid, so that none is dropped.
   */
  @VisibleForTesting
  public static boolean isAllValid(String addressList) {
    // This code mimics the parse() method below.
    // I don't know how to better avoid the code-duplication.
    if (addressList != null && addressList.length() > 0) {
      Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(addressList);
      for (int i = 0, length = tokens.length; i < length; ++i) {
        Rfc822Token token = tokens[i];
        String address = token.getAddress();
        if (!TextUtils.isEmpty(address) && !isValidAddress(address)) {
          return false;
        }
      }
    }
    return true;
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
    ArrayList<Address> addresses = new ArrayList<Address>();
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
    return addresses.toArray(new Address[addresses.size()]);
  }

  /** Checks whether a string email address is valid. E.g. name@domain.com is valid. */
  @VisibleForTesting
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
    if (mPersonal != null && !mPersonal.equals(mAddress)) {
      if (mPersonal.matches(".*[\\(\\)<>@,;:\\\\\".\\[\\]].*")) {
        return ensureQuotedString(mPersonal) + " <" + mAddress + ">";
      } else {
        return mPersonal + " <" + mAddress + ">";
      }
    } else {
      return mAddress;
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
   * Get human readable comma-delimited address string.
   *
   * @param addresses Address array
   * @return Human readable comma-delimited address string.
   */
  @VisibleForTesting
  public static String toString(Address[] addresses) {
    return toString(addresses, ADDRESS_DELIMETER);
  }

  /**
   * Get human readable address strings joined with the specified separator.
   *
   * @param addresses Address array
   * @param separator Separator
   * @return Human readable comma-delimited address string.
   */
  public static String toString(Address[] addresses, String separator) {
    if (addresses == null || addresses.length == 0) {
      return null;
    }
    if (addresses.length == 1) {
      return addresses[0].toString();
    }
    StringBuilder sb = new StringBuilder(addresses[0].toString());
    for (int i = 1; i < addresses.length; i++) {
      sb.append(separator);
      // TODO: investigate why this .trim() is needed.
      sb.append(addresses[i].toString().trim());
    }
    return sb.toString();
  }

  /**
   * Get RFC822/MIME compatible address string.
   *
   * @return RFC822/MIME compatible address string. It may be surrounded by double quote or quoted
   *     and MIME/base64 encoded if necessary.
   */
  public String toHeader() {
    if (mPersonal != null) {
      return EncoderUtil.encodeAddressDisplayName(mPersonal) + " <" + mAddress + ">";
    } else {
      return mAddress;
    }
  }

  /**
   * Get RFC822/MIME compatible comma-delimited address string.
   *
   * @param addresses Address array
   * @return RFC822/MIME compatible comma-delimited address string. it may be surrounded by double
   *     quoted or quoted and MIME/base64 encoded if necessary.
   */
  public static String toHeader(Address[] addresses) {
    if (addresses == null || addresses.length == 0) {
      return null;
    }
    if (addresses.length == 1) {
      return addresses[0].toHeader();
    }
    StringBuilder sb = new StringBuilder(addresses[0].toHeader());
    for (int i = 1; i < addresses.length; i++) {
      // We need space character to be able to fold line.
      sb.append(", ");
      sb.append(addresses[i].toHeader());
    }
    return sb.toString();
  }

  /**
   * Get Human friendly address string.
   *
   * @return the personal part of this Address, or the address part if the personal part is not
   *     available
   */
  @VisibleForTesting
  public String toFriendly() {
    if (mPersonal != null && mPersonal.length() > 0) {
      return mPersonal;
    } else {
      return mAddress;
    }
  }

  /**
   * Creates a comma-delimited list of addresses in the "friendly" format (see toFriendly() for
   * details on the per-address conversion).
   *
   * @param addresses Array of Address[] values
   * @return A comma-delimited string listing all of the addresses supplied. Null if source was null
   *     or empty.
   */
  @VisibleForTesting
  public static String toFriendly(Address[] addresses) {
    if (addresses == null || addresses.length == 0) {
      return null;
    }
    if (addresses.length == 1) {
      return addresses[0].toFriendly();
    }
    StringBuilder sb = new StringBuilder(addresses[0].toFriendly());
    for (int i = 1; i < addresses.length; i++) {
      sb.append(", ");
      sb.append(addresses[i].toFriendly());
    }
    return sb.toString();
  }

  /** Returns exactly the same result as Address.toString(Address.fromHeader(addressList)). */
  @VisibleForTesting
  public static String fromHeaderToString(String addressList) {
    return toString(fromHeader(addressList));
  }

  /** Returns exactly the same result as Address.toHeader(Address.parse(addressList)). */
  @VisibleForTesting
  public static String parseToHeader(String addressList) {
    return Address.toHeader(Address.parse(addressList));
  }

  /**
   * Returns null if the addressList has 0 addresses, otherwise returns the first address. The same
   * as Address.fromHeader(addressList)[0] for non-empty list. This is an utility method that offers
   * some performance optimization opportunities.
   */
  @VisibleForTesting
  public static Address firstAddress(String addressList) {
    Address[] array = fromHeader(addressList);
    return array.length > 0 ? array[0] : null;
  }

  /**
   * This method exists to convert an address list formatted in a deprecated legacy format to the
   * standard RFC822 header format. {@link #fromHeader(String)} is capable of reading the legacy
   * format and the RFC822 format. {@link #toHeader()} always produces the RFC822 format.
   *
   * <p>This implementation is brute-force, and could be replaced with a more efficient version if
   * desired.
   */
  public static String reformatToHeader(String addressList) {
    return toHeader(fromHeader(addressList));
  }

  /**
   * @param addressList a CSV of RFC822 addresses or the deprecated legacy string format
   * @return array of addresses parsed from <code>addressList</code>
   */
  @VisibleForTesting
  public static Address[] fromHeader(String addressList) {
    if (addressList == null || addressList.length() == 0) {
      return EMPTY_ADDRESS_ARRAY;
    }
    // IF we're CSV, just parse
    if ((addressList.indexOf(LIST_DELIMITER_PERSONAL) == -1)
        && (addressList.indexOf(LIST_DELIMITER_EMAIL) == -1)) {
      return Address.parse(addressList);
    }
    // Otherwise, do backward-compatible unpack
    ArrayList<Address> addresses = new ArrayList<Address>();
    int length = addressList.length();
    int pairStartIndex = 0;
    int pairEndIndex;

    /* addressEndIndex is only re-scanned (indexOf()) when a LIST_DELIMITER_PERSONAL
       is used, not for every email address; i.e. not for every iteration of the while().
       This reduces the theoretical complexity from quadratic to linear,
       and provides some speed-up in practice by removing redundant scans of the string.
    */
    int addressEndIndex = addressList.indexOf(LIST_DELIMITER_PERSONAL);

    while (pairStartIndex < length) {
      pairEndIndex = addressList.indexOf(LIST_DELIMITER_EMAIL, pairStartIndex);
      if (pairEndIndex == -1) {
        pairEndIndex = length;
      }
      Address address;
      if (addressEndIndex == -1 || pairEndIndex <= addressEndIndex) {
        // in this case the DELIMITER_PERSONAL is in a future pair,
        // so don't use personal, and don't update addressEndIndex
        address = new Address(addressList.substring(pairStartIndex, pairEndIndex), null);
      } else {
        address =
            new Address(
                addressList.substring(pairStartIndex, addressEndIndex),
                addressList.substring(addressEndIndex + 1, pairEndIndex));
        // only update addressEndIndex when we use the LIST_DELIMITER_PERSONAL
        addressEndIndex = addressList.indexOf(LIST_DELIMITER_PERSONAL, pairEndIndex + 1);
      }
      addresses.add(address);
      pairStartIndex = pairEndIndex + 1;
    }
    return addresses.toArray(new Address[addresses.size()]);
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
    out.writeString(mPersonal);
    out.writeString(mAddress);
  }
}
