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

package com.android.incallui.calllocation.impl;

import android.content.Context;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.SystemClock;
import android.util.Pair;
import com.android.dialer.common.LogUtil;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.MoreStrings;
import com.google.android.common.http.UrlRules;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Utility for making http requests. */
public class HttpFetcher {

  // Phone number
  public static final String PARAM_ID = "id";
  // auth token
  public static final String PARAM_ACCESS_TOKEN = "access_token";
  private static final String TAG = HttpFetcher.class.getSimpleName();

  /**
   * Send a http request to the given url.
   *
   * @param urlString The url to request.
   * @return The response body as a byte array. Or {@literal null} if status code is not 2xx.
   * @throws java.io.IOException when an error occurs.
   */
  public static byte[] sendRequestAsByteArray(
      Context context, String urlString, String requestMethod, List<Pair<String, String>> headers)
      throws IOException, AuthException {
    Objects.requireNonNull(urlString);

    URL url = reWriteUrl(context, urlString);
    if (url == null) {
      return null;
    }

    HttpURLConnection conn = null;
    InputStream is = null;
    boolean isError = false;
    final long start = SystemClock.uptimeMillis();
    try {
      conn = (HttpURLConnection) url.openConnection();
      setMethodAndHeaders(conn, requestMethod, headers);
      int responseCode = conn.getResponseCode();
      LogUtil.i("HttpFetcher.sendRequestAsByteArray", "response code: " + responseCode);
      // All 2xx codes are successful.
      if (responseCode / 100 == 2) {
        is = conn.getInputStream();
      } else {
        is = conn.getErrorStream();
        isError = true;
      }

      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      final byte[] buffer = new byte[1024];
      int bytesRead;

      while ((bytesRead = is.read(buffer)) != -1) {
        baos.write(buffer, 0, bytesRead);
      }

      if (isError) {
        handleBadResponse(url.toString(), baos.toByteArray());
        if (responseCode == 401) {
          throw new AuthException("Auth error");
        }
        return null;
      }

      byte[] response = baos.toByteArray();
      LogUtil.i("HttpFetcher.sendRequestAsByteArray", "received " + response.length + " bytes");
      long end = SystemClock.uptimeMillis();
      LogUtil.i("HttpFetcher.sendRequestAsByteArray", "fetch took " + (end - start) + " ms");
      return response;
    } finally {
      DialerUtils.closeQuietly(is);
      if (conn != null) {
        conn.disconnect();
      }
    }
  }

  /**
   * Send a http request to the given url.
   *
   * @return The response body as a InputStream. Or {@literal null} if status code is not 2xx.
   * @throws java.io.IOException when an error occurs.
   */
  public static InputStream sendRequestAsInputStream(
      Context context, String urlString, String requestMethod, List<Pair<String, String>> headers)
      throws IOException, AuthException {
    Objects.requireNonNull(urlString);

    URL url = reWriteUrl(context, urlString);
    if (url == null) {
      return null;
    }

    HttpURLConnection httpUrlConnection = null;
    boolean isSuccess = false;
    try {
      httpUrlConnection = (HttpURLConnection) url.openConnection();
      setMethodAndHeaders(httpUrlConnection, requestMethod, headers);
      int responseCode = httpUrlConnection.getResponseCode();
      LogUtil.i("HttpFetcher.sendRequestAsInputStream", "response code: " + responseCode);

      if (responseCode == 401) {
        throw new AuthException("Auth error");
      } else if (responseCode / 100 == 2) { // All 2xx codes are successful.
        InputStream is = httpUrlConnection.getInputStream();
        if (is != null) {
          is = new HttpInputStreamWrapper(httpUrlConnection, is);
          isSuccess = true;
          return is;
        }
      }

      return null;
    } finally {
      if (httpUrlConnection != null && !isSuccess) {
        httpUrlConnection.disconnect();
      }
    }
  }

  /**
   * Set http method and headers.
   *
   * @param conn The connection to add headers to.
   * @param requestMethod request method
   * @param headers http headers where the first item in the pair is the key and second item is the
   *     value.
   */
  private static void setMethodAndHeaders(
      HttpURLConnection conn, String requestMethod, List<Pair<String, String>> headers)
      throws ProtocolException {
    conn.setRequestMethod(requestMethod);
    if (headers != null) {
      for (Pair<String, String> pair : headers) {
        conn.setRequestProperty(pair.first, pair.second);
      }
    }
  }

  private static String obfuscateUrl(String urlString) {
    final Uri uri = Uri.parse(urlString);
    final Builder builder =
        new Builder().scheme(uri.getScheme()).authority(uri.getAuthority()).path(uri.getPath());
    final Set<String> names = uri.getQueryParameterNames();
    for (String name : names) {
      if (PARAM_ACCESS_TOKEN.equals(name)) {
        builder.appendQueryParameter(name, "token");
      } else {
        final String value = uri.getQueryParameter(name);
        if (PARAM_ID.equals(name)) {
          builder.appendQueryParameter(name, MoreStrings.toSafeString(value));
        } else {
          builder.appendQueryParameter(name, value);
        }
      }
    }
    return builder.toString();
  }

  /** Same as {@link #getRequestAsString(Context, String, String, List)} with null headers. */
  public static String getRequestAsString(Context context, String urlString)
      throws IOException, AuthException {
    return getRequestAsString(context, urlString, "GET" /* Default to get. */, null);
  }

  /**
   * Send a http request to the given url.
   *
   * @param context The android context.
   * @param urlString The url to request.
   * @param headers Http headers to pass in the request. {@literal null} is allowed.
   * @return The response body as a String. Or {@literal null} if status code is not 2xx.
   * @throws java.io.IOException when an error occurs.
   */
  public static String getRequestAsString(
      Context context, String urlString, String requestMethod, List<Pair<String, String>> headers)
      throws IOException, AuthException {
    final byte[] byteArr = sendRequestAsByteArray(context, urlString, requestMethod, headers);
    if (byteArr == null) {
      // Encountered error response... just return.
      return null;
    }
    final String response = new String(byteArr);
    LogUtil.i("HttpFetcher.getRequestAsString", "response body: " + response);
    return response;
  }

  /**
   * Lookup up url re-write rules from gServices and apply to the given url.
   *
   * @return The new url.
   */
  private static URL reWriteUrl(Context context, String url) {
    final UrlRules rules = UrlRules.getRules(context.getContentResolver());
    final UrlRules.Rule rule = rules.matchRule(url);
    final String newUrl = rule.apply(url);

    if (newUrl == null) {
      if (LogUtil.isDebugEnabled()) {
        // Url is blocked by re-write.
        LogUtil.i(
            "HttpFetcher.reWriteUrl",
            "url " + obfuscateUrl(url) + " is blocked.  Ignoring request.");
      }
      return null;
    }

    if (LogUtil.isDebugEnabled()) {
      LogUtil.i("HttpFetcher.reWriteUrl", "fetching " + obfuscateUrl(newUrl));
      if (!newUrl.equals(url)) {
        LogUtil.i(
            "HttpFetcher.reWriteUrl",
            "Original url: " + obfuscateUrl(url) + ", after re-write: " + obfuscateUrl(newUrl));
      }
    }

    URL urlObject = null;
    try {
      urlObject = new URL(newUrl);
    } catch (MalformedURLException e) {
      LogUtil.e("HttpFetcher.reWriteUrl", "failed to parse url: " + url, e);
    }
    return urlObject;
  }

  private static void handleBadResponse(String url, byte[] response) {
    LogUtil.i("HttpFetcher.handleBadResponse", "Got bad response code from url: " + url);
    LogUtil.i("HttpFetcher.handleBadResponse", new String(response));
  }

  /** Disconnect {@link HttpURLConnection} when InputStream is closed */
  private static class HttpInputStreamWrapper extends FilterInputStream {

    final HttpURLConnection httpUrlConnection;
    final long startMillis = SystemClock.uptimeMillis();

    public HttpInputStreamWrapper(HttpURLConnection conn, InputStream in) {
      super(in);
      httpUrlConnection = conn;
    }

    @Override
    public void close() throws IOException {
      super.close();
      httpUrlConnection.disconnect();
      if (LogUtil.isDebugEnabled()) {
        long endMillis = SystemClock.uptimeMillis();
        LogUtil.i("HttpFetcher.close", "fetch took " + (endMillis - startMillis) + " ms");
      }
    }
  }
}
