/*
 * Copyright (C) 2014 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
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

package com.android.dialer.lookup.whitepages;

import com.android.dialer.lookup.LookupSettings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.net.Uri;
import android.text.Html;
import android.util.Log;

public class WhitePagesApi {
    private static final String TAG = WhitePagesApi.class.getSimpleName();

    public static final int UNITED_STATES = 0;
    public static final int CANADA = 1;

    private static final String NEARBY_URL_UNITED_STATES =
            "http://www.whitepages.com/search/ReversePhone?full_phone=";
    private static final String NEARBY_URL_CANADA =
            "http://www.whitepages.ca/search/ReversePhone?full_phone=";

    private static final String PEOPLE_URL_UNITED_STATES =
            "http://whitepages.com/search/FindPerson";

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:26.0) Gecko/20100101 Firefox/26.0";
    private static final String COOKIE_REGEX = "distil_RID=([A-Za-z0-9\\-]+)";
    private static final String COOKIE = "D_UID";

    private static String mCookie;

    private WhitePagesApi() {
    }

    public static ContactInfo[] peopleLookup(Context context, String name,
            int maxResults) throws IOException {
        String provider = LookupSettings.getPeopleLookupProvider(context);

        if (LookupSettings.PLP_WHITEPAGES.equals(provider)) {
            Uri.Builder builder = Uri.parse(PEOPLE_URL_UNITED_STATES)
                    .buildUpon();
            builder.appendQueryParameter("who", name);
            String lookupUrl = builder.build().toString();
            String output = httpGet(lookupUrl);
            return parseOutputUnitedStates(output, maxResults);
        }
        // no-op
        return null;
    }

    private static ContactInfo[] parseOutputUnitedStates(String output,
            int maxResults) throws IOException {
        ArrayList<ContactInfo> people = new ArrayList<ContactInfo>();

        Pattern regex = Pattern.compile(
                "<li\\s[^>]+?http:\\/\\/schema\\.org\\/Person", Pattern.DOTALL);
        Matcher m = regex.matcher(output);

        while (m.find()) {
            if (people.size() == maxResults) {
                break;
            }

            // Find section of HTML with contact information
            String section = extractXmlTag(output, m.start(), m.end(), "li");

            // Skip entries with no phone number
            if (section.contains("has-no-phone-icon")) {
                continue;
            }

            String name = unHtml(extractXmlRegex(section,
                    "<span[^>]+?itemprop=\"name\">", "span"));

            if (name == null) {
                continue;
            }

            // Address
            String addrCountry = unHtml(extractXmlRegex(section,
                    "<span[^>]+?itemprop=\"addressCountry\">", "span"));
            String addrState = unHtml(extractXmlRegex(section,
                    "<span[^>]+?itemprop=\"addressRegion\">", "span"));
            String addrCity = unHtml(extractXmlRegex(section,
                    "<span[^>]+?itemprop=\"addressLocality\">", "span"));

            StringBuilder sb = new StringBuilder();

            if (addrCity != null) {
                sb.append(addrCity);
            }
            if (addrState != null) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(addrState);
            }
            if (addrCountry != null) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(addrCountry);
            }

            // Website
            Pattern p = Pattern.compile("href=\"(.+?)\"");
            Matcher m2 = p.matcher(section);
            String website = null;
            if (m2.find()) {
                website = "http://www.whitepages.com" + m2.group(1);
            }

            // Phone number is on profile page, so skip if we can't get the
            // website
            if (website == null) {
                continue;
            }

            String profile = httpGet(website);
            String phoneNumber = unHtml(extractXmlRegex(profile,
                    "<li[^>]+?class=\"no-overflow tel\">", "li"));
            String address = parseAddressUnitedStates(profile);

            if (phoneNumber == null) {
                Log.e(TAG,
                        "Phone number is null. Either cookie is bad or regex is broken");
                continue;
            }

            ContactInfo info = new ContactInfo();
            info.name = name;
            info.city = sb.toString();
            info.address = address;
            info.formattedNumber = phoneNumber;
            info.website = website;

            people.add(info);
        }

        return people.toArray(new ContactInfo[people.size()]);
    }

    private static String extractXmlRegex(String str, String regex, String tag) {
        Pattern p = Pattern.compile(regex, Pattern.DOTALL);
        Matcher m = p.matcher(str);
        if (m.find()) {
            return extractXmlTag(str, m.start(), m.end(), tag);
        }
        return null;
    }

    private static String extractXmlTag(String str, int realBegin, int begin,
            String tag) {
        int end = begin;
        int tags = 1;
        int maxLoop = 30;

        while (tags > 0) {
            end = str.indexOf(tag, end + 1);
            if (end < 0 || maxLoop < 0) {
                break;
            }

            if (str.charAt(end - 1) == '/'
                    && str.charAt(end + tag.length()) == '>') {
                tags--;
            } else if (str.charAt(end - 1) == '<') {
                tags++;
            }

            maxLoop--;
        }

        int realEnd = str.indexOf(">", end) + 1;

        if (tags != 0) {
            Log.e(TAG, "Failed to extract tag <" + tag + "> from XML/HTML");
            return null;
        }

        return str.substring(realBegin, realEnd);
    }

    private static String unHtml(String html) {
        if (html == null) {
            return null;
        }

        return Html.fromHtml(html).toString().trim();
    }

    public static ContactInfo reverseLookup(Context context, String number)
            throws IOException {
        String provider = LookupSettings.getReverseLookupProvider(context);

        String lookupUrl = null;
        if (LookupSettings.RLP_WHITEPAGES.equals(provider)) {
            lookupUrl = NEARBY_URL_UNITED_STATES;
        } else if (LookupSettings.RLP_WHITEPAGES_CA.equals(provider)) {
            lookupUrl = NEARBY_URL_CANADA;
        }
        String newLookupUrl = lookupUrl + number;

        String output = httpGet(newLookupUrl);

        //

        String name = null;
        String phoneNumber = null;
        String address = null;

        if (LookupSettings.RLP_WHITEPAGES.equals(provider)) {
            name = parseNameUnitedStates(output);
            phoneNumber = parseNumberUnitedStates(output);
            address = parseAddressUnitedStates(output);
        } else if (LookupSettings.RLP_WHITEPAGES_CA.equals(provider)) {
            name = parseNameCanada(output);
            // Canada's WhitePages does not provide a formatted number
            address = parseAddressCanada(output);
        }

        ContactInfo info = new ContactInfo();
        info.name = name;
        info.address = address;
        info.formattedNumber = phoneNumber != null ? phoneNumber : number;
        info.website = lookupUrl + info.formattedNumber;

        return info;
    }

    private static String httpGet(String url) throws IOException {
        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(url);

        get.setHeader("User-Agent", USER_AGENT);

        if (mCookie != null) {
            get.setHeader("Cookie", COOKIE + "=" + mCookie);
        }

        HttpResponse response = client.execute(get);
        int status = response.getStatusLine().getStatusCode();

        // Android's org.apache.http doesn't have the RedirectStrategy class
        if (status == HttpStatus.SC_MOVED_PERMANENTLY
                || status == HttpStatus.SC_MOVED_TEMPORARILY) {
            Header[] headers = response.getHeaders("Location");

            if (headers != null && headers.length != 0) {
                String newUrl = headers[headers.length - 1].getValue();
                return httpGet(newUrl);
            } else {
                return null;
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getEntity().writeTo(out);

        String output = new String(out.toByteArray());

        // If we can find a new cookie, use it
        Pattern p = Pattern.compile(COOKIE_REGEX, Pattern.DOTALL);
        Matcher m = p.matcher(output);
        if (m.find()) {
            mCookie = m.group(1).trim();
            Log.v(TAG, "Got new cookie");
        }

        // If we hit a page with a <meta> refresh and the error URL, reload. If
        // this results in infinite recursion, then whatever. The thread is
        // killed after 10 seconds.
        p = Pattern.compile("<meta[^>]+http-equiv=\"refresh\"", Pattern.DOTALL);
        m = p.matcher(output);
        if (m.find() && output.contains("distil_r_captcha.html")) {
            Log.w(TAG, "Got <meta> refresh. Reloading...");
            return httpGet(url);
        }

        return output;
    }

    private static String parseNameUnitedStates(String output) {
        Matcher m;

        Pattern regexName = Pattern
                .compile("<h2.*?>Send (.*?)&#39;s details to phone</h2>",
                        Pattern.DOTALL);
        String name = null;

        m = regexName.matcher(output);
        if (m.find()) {
            name = m.group(1).trim();
        }

        // Use summary if name doesn't exist
        if (name == null) {
            Pattern regexSummary = Pattern.compile(
                    "<span\\s*class=\"subtitle.*?>\\s*\n?(.*?)\n?\\s*</span>",
                    Pattern.DOTALL);

            m = regexSummary.matcher(output);
            if (m.find()) {
                name = m.group(1).trim();
            }
        }

        if (name != null) {
            name = name.replaceAll("&amp;", "&");
        }

        return name;
    }

    private static String parseNameCanada(String output) {
        Matcher m;

        Pattern regexName = Pattern.compile(
                "(<li\\s+class=\"listing_info\">.*?</li>)", Pattern.DOTALL);
        String name = null;

        m = regexName.matcher(output);
        if (m.find()) {
            name = m.group(1).trim();
        }

        if (name != null) {
            name = Html.fromHtml(name).toString().trim();
        }

        return name;
    }

    private static String parseNumberUnitedStates(String output) {
        Matcher m;

        Pattern regexPhoneNumber = Pattern.compile(
                "Full Number:</span>([0-9\\-\\+\\(\\)]+)</li>", Pattern.DOTALL);
        String phoneNumber = null;

        m = regexPhoneNumber.matcher(output);
        if (m.find()) {
            phoneNumber = m.group(1).trim();
        }

        return phoneNumber;
    }

    private static String parseAddressUnitedStates(String output) {
        Matcher m;

        String regexBase = "<span\\s+class=\"%s[^\"]+\"\\s*>([^<]*)</span>";

        Pattern regexAddressPrimary = Pattern.compile(
                String.format(regexBase, "address-primary"), Pattern.DOTALL);
        Pattern regexAddressSecondary = Pattern.compile(
                String.format(regexBase, "address-secondary"), Pattern.DOTALL);
        Pattern regexAddressLocation = Pattern.compile(
                String.format(regexBase, "address-location"), Pattern.DOTALL);

        String addressPrimary = null;
        String addressSecondary = null;
        String addressLocation = null;

        m = regexAddressPrimary.matcher(output);
        if (m.find()) {
            addressPrimary = m.group(1).trim();
        }

        m = regexAddressSecondary.matcher(output);
        if (m.find()) {
            addressSecondary = m.group(1).trim();
        }

        m = regexAddressLocation.matcher(output);
        if (m.find()) {
            addressLocation = m.group(1).trim();
        }

        StringBuilder sb = new StringBuilder();

        if (addressPrimary != null && addressPrimary.length() != 0) {
            sb.append(addressPrimary);
        }
        if (addressSecondary != null && addressSecondary.length() != 0) {
            sb.append(", ");
            sb.append(addressSecondary);
        }
        if (addressLocation != null && addressLocation.length() != 0) {
            sb.append(", ");
            sb.append(addressLocation);
        }

        String address = sb.toString();
        if (address.length() == 0) {
            address = null;
        }

        return address;
    }

    private static String parseAddressCanada(String output) {
        Matcher m;

        Pattern regexAddress = Pattern
                .compile(
                        "<ol class=\"result people_result\">.*?(<li\\s+class=\"col_location\">.*?</li>).*?</ol>",
                        Pattern.DOTALL);
        String address = null;

        m = regexAddress.matcher(output);
        if (m.find()) {
            address = m.group(1).trim();
        }

        if (address != null) {
            address = Html.fromHtml(address).toString().replace("\n", ", ")
                    .trim();
        }

        return address;
    }

    public static class ContactInfo {
        String name;
        String city;
        String address;
        String formattedNumber;
        String website;
    }
}
