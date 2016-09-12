/*
 * Copyright (C) 2014 Xiao-Long Chen <chillermillerlong@hotmail.com>
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

package com.android.dialer.lookup.yellowpages;

import android.content.Context;
import android.text.TextUtils;

import com.android.dialer.lookup.LookupUtils;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YellowPagesApi {
  private static final String TAG = YellowPagesApi.class.getSimpleName();

  static final String LOOKUP_URL_UNITED_STATES =
      "https://www.yellowpages.com/phone?phone_search_terms=";
  static final String LOOKUP_URL_CANADA =
      "https://www.yellowpages.ca/search/si/1/";

  private final String number;
  private String output = null;
  private ContactInfo info = null;
  private final String lookupUrl;

  public YellowPagesApi(String number, String lookupUrl) {
    this.number = number;
    this.lookupUrl = lookupUrl;
  }

  private void fetchPage() throws IOException {
    output = LookupUtils.httpGet(lookupUrl + number, null);
  }

  private String getPhotoUrl(String website) throws IOException {
    String output = LookupUtils.httpGet(website, null);
    String galleryRef = LookupUtils.firstRegexResult(output,
        "href=\"([^\"]+gallery\\?lid=[^\"]+)\"", true);
    if (galleryRef == null) {
      return null;
    }

    // Get first image
    return LookupUtils.firstRegexResult(
        LookupUtils.httpGet("https://www.yellowpages.com" + galleryRef, null),
        "\"type\":\"image\",\"src\":\"([^\"]+)\"", true);
  }

  private String[] parseNameWebsiteUnitedStates() {
    Pattern regexNameAndWebsite = Pattern.compile(
        "<a href=\"([^>]+?)\"[^>]+?class=\"url[^>]+?>([^<]+)</a>",
        Pattern.DOTALL);
    String name = null;
    String website = null;

    Matcher m = regexNameAndWebsite.matcher(output);
    if (m.find()) {
      website = m.group(1).trim();
      name = m.group(2).trim();
    }

    return new String[] { name, website };
  }

  private String[] parseNameWebsiteCanada() {
    Pattern regexNameAndWebsite = Pattern.compile(
        "class=\"ypgListingTitleLink utagLink\".*?href=\"(.*?)\">"
        + "(<span\\s+class=\"listingTitle\">.*?</span>)",
        Pattern.DOTALL);
    String name = null;
    String website = null;

    Matcher m = regexNameAndWebsite.matcher(output);
    if (m.find()) {
      website = m.group(1).trim();
      name = LookupUtils.fromHtml(m.group(2).trim());
    }

    if (website != null) {
      website = "https://www.yellowpages.ca" + website;
    }

    return new String[] { name, website };
  }

  private String parseNumberUnitedStates() {
    return LookupUtils.firstRegexResult(output,
        "business-phone.*?>\n*([^\n<]+)\n*<", true);
  }

  private String parseNumberCanada() {
    return LookupUtils.firstRegexResult(output,
        "<div\\s+class=\"phoneNumber\">(.*?)</div>", true);
  }

  private String parseAddressUnitedStates() {
    String addressStreet = LookupUtils.firstRegexResult(output,
        "street-address.*?>\n*([^\n<]+)\n*<", true);
    if (addressStreet != null && addressStreet.endsWith(",")) {
      addressStreet = addressStreet.substring(0, addressStreet.length() - 1);
    }

    String addressCity = LookupUtils.firstRegexResult(output,
        "locality.*?>\n*([^\n<]+)\n*<", true);
    String addressState = LookupUtils.firstRegexResult(output,
        "region.*?>\n*([^\n<]+)\n*<", true);
    String addressZip = LookupUtils.firstRegexResult(output,
        "postal-code.*?>\n*([^\n<]+)\n*<", true);

    StringBuilder sb = new StringBuilder();

    if (!TextUtils.isEmpty(addressStreet)) {
      sb.append(addressStreet);
    }
    if (!TextUtils.isEmpty(addressCity)) {
      sb.append(", ");
      sb.append(addressCity);
    }
    if (!TextUtils.isEmpty(addressState)) {
      sb.append(", ");
      sb.append(addressState);
    }
    if (!TextUtils.isEmpty(addressZip)) {
      sb.append(", ");
      sb.append(addressZip);
    }

    String address = sb.toString();
    return address.isEmpty() ? null : address;
  }

  private String parseAddressCanada() {
    String address = LookupUtils.firstRegexResult(output,
        "<div\\s+class=\"address\">(.*?)</div>", true);
    return LookupUtils.fromHtml(address);
  }

  private void buildContactInfo() throws IOException {
    Matcher m;

    String name = null;
    String website = null;
    String phoneNumber = null;
    String address = null;
    String photoUrl = null;

    if (lookupUrl.equals(LOOKUP_URL_UNITED_STATES)) {
      String[] ret = parseNameWebsiteUnitedStates();
      name = ret[0];
      website = ret[1];
      phoneNumber = parseNumberUnitedStates();
      address = parseAddressUnitedStates();
      if (website != null) {
        photoUrl = getPhotoUrl(website);
      }
    } else {
      String[] ret = parseNameWebsiteCanada();
      name = ret[0];
      website = ret[1];
      phoneNumber = parseNumberCanada();
      address = parseAddressCanada();
      // AFAIK, Canada's YellowPages doesn't have photos
    }

    info = new ContactInfo();
    info.name = name;
    info.address = address;
    info.formattedNumber = phoneNumber != null ? phoneNumber : number;
    info.website = website;
    info.photoUrl = photoUrl;
  }

  public ContactInfo getContactInfo() throws IOException {
    if (info == null) {
      fetchPage();
      buildContactInfo();
    }

    return info;
  }

  public static class ContactInfo {
    String name;
    String address;
    String formattedNumber;
    String website;
    String photoUrl;
  }
}
