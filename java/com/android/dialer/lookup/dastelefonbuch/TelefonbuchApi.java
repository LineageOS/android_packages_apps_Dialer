/*
 * Copyright (C) 2014 Danny Baumann <dannybaumann@web.de>
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.dialer.lookup.dastelefonbuch;

import android.content.Context;
import android.net.Uri;

import com.android.dialer.lookup.LookupUtils;

import java.io.IOException;

public class TelefonbuchApi {
  private static final String TAG = TelefonbuchApi.class.getSimpleName();

  private static final String REVERSE_LOOKUP_URL =
          "https://www.dastelefonbuch.de/R%C3%BCckw%C3%A4rts-Suche/";

  private static final String RELEVANT_CONTENT_REGEX =
          "<div class=\"vcard\">(.*)<div class=\"additional\">";
  private static final String NAME_REGEX ="<div class=\"name\" title=\"(.*?)\">";
  private static final String NUMBER_REGEX = "<!-- phoneTo: (.*?) -->";
  private static final String ADDRESS_REGEX = "<address.*?>\n?(.*?)</address>";
  private static final String WEBSITE_REGEX = "<div.*class=\"url\">.*<a.*?href=\"(.*?)\"";

  private TelefonbuchApi() {
  }

  public static ContactInfo reverseLookup(Context context, String number) throws IOException {
    Uri uri = Uri.parse(REVERSE_LOOKUP_URL)
            .buildUpon()
            .appendPath(number)
            .build();
    // Cut out everything we're not interested in (scripts etc.) to
    // speed up the subsequent matching.
    String output = LookupUtils.firstRegexResult(
            LookupUtils.httpGet(uri.toString(), null), RELEVANT_CONTENT_REGEX, true);

    String name = parseValue(output, NAME_REGEX, true, false);
    if (name == null) {
      return null;
    }

    String phoneNumber = parseValue(output, NUMBER_REGEX, false, true);
    String address = parseValue(output, ADDRESS_REGEX, true, true);
    String website = parseValue(output, WEBSITE_REGEX, true, false);

    ContactInfo info = new ContactInfo();
    info.name = name;
    info.address = address;
    info.formattedNumber = phoneNumber != null ? phoneNumber : number;
    info.website = website != null ? website : uri.toString();

    return info;
  }

  private static String parseValue(String output, String regex,
      boolean dotall, boolean removeSpans) {
    String result = LookupUtils.firstRegexResult(output, regex, dotall);
    if (result != null && removeSpans) {
      // completely remove hidden spans (including contents) ...
      result = result.replaceAll("<span class=\"hide\".*?\\/span>", "");
      // ... and remove span wrappers around data content
      result = result.replaceAll("</?span.*?>", "");
    }
    return LookupUtils.fromHtml(result);
  }

  public static class ContactInfo {
    String name;
    String address;
    String formattedNumber;
    String website;
  }
}
