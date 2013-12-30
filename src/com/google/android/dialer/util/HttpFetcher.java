/*
  * Copyright (C) 2013 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
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

/*
 * This is a reverse-engineered implementation of com.google.android.Dialer.
 * There is no guarantee that this implementation will work correctly or even
 * work at all. Use at your own risk.
 */

package com.google.android.dialer.util;

import com.android.incallui.Log;
import com.android.services.telephony.common.MoreStrings;
import com.google.android.common.http.UrlRules;
import com.google.android.dialer.util.AuthException;
import com.google.common.base.Preconditions;
import com.google.common.io.Closeables;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Pair;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class HttpFetcher {
    private static final String TAG = HttpFetcher.class.getSimpleName();

    private static void addHeaders(HttpURLConnection connection, List<Pair<String, String>> list) {
        if (list != null) {
            for (Pair<String, String> pair : list) {
                connection.setRequestProperty(pair.first, pair.second);
            }
        }
    }

    // TODO: Check logic.
    public static byte[] getRequestAsByteArray(Context context, String url,
            List<Pair<String, String>> list) throws IOException, AuthException {
        Preconditions.checkNotNull(url);
        String rewrittenUrl = reWriteUrl(context, url);

        if (rewrittenUrl == null) {
            if (Log.DEBUG) {
                Log.d(TAG, "url " + obfuscateUrl(url) + " is blocked.  Ignoring request.");
            }
            return null;
        } else {
            if (!rewrittenUrl.equals(url) && Log.DEBUG) {
                Log.d(TAG, "Original url: " + obfuscateUrl(url) +
                        ", after re-write: " + obfuscateUrl(rewrittenUrl));
            }
            if (Log.DEBUG) {
                Log.d(TAG, "fetching " + obfuscateUrl(rewrittenUrl));
            }

            byte[] byteArray = null;
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            boolean failed = false;
            long uptimeMillis = SystemClock.uptimeMillis();;
            int responseCode = 0;
            ByteArrayOutputStream byteArrayOutputStream = null;

            try {
                connection = (HttpURLConnection)new URL(rewrittenUrl).openConnection();
                addHeaders(connection, list);
                responseCode = connection.getResponseCode();
                Log.d(TAG, "response code: " + responseCode);
                int n = responseCode / 100;
                if (n == 2) {
                    inputStream = connection.getInputStream();
                    byteArrayOutputStream = new ByteArrayOutputStream();
                    byte[] array = new byte[1024];
                    while (true) {
                        int read = inputStream.read(array);
                        if (read == -1) {
                            break;
                        }
                        byteArrayOutputStream.write(array, 0, read);
                    }
                } else {
                    failed = true;
                }
            } finally {
                Closeables.closeQuietly(inputStream);
                if (connection != null) {
                    connection.disconnect();
                }
            }

            if (failed) {
                handleBadResponse(rewrittenUrl, byteArrayOutputStream.toByteArray());
                if (responseCode == 401) {
                    throw new AuthException("Auth error");
                }
                return null;
            } else {
                byteArray = byteArrayOutputStream.toByteArray();
                Log.d(TAG, "received " + byteArray.length + " bytes");
                Log.d(TAG, "fetch took " + (SystemClock.uptimeMillis() - uptimeMillis) + " ms");
                return byteArray;
            }
        }
    }

    public static String getRequestAsString(Context context, String request,
            List<Pair<String, String>> list) throws IOException, AuthException {
        byte[] response = getRequestAsByteArray(context, request, list);
        if (response == null) {
            return null;
        }
        String responseStr = new String(response);
        Log.d(TAG, "response body: ");
        Log.d(TAG, responseStr);
        return responseStr;
    }

    private static void handleBadResponse(String url, byte[] response) {
        Log.w(TAG, "Got bad response code from url: " + url);
        Log.w(TAG, new String(response));
    }

    private static String obfuscateUrl(String url) {
        Uri uri = Uri.parse(url);
        Uri.Builder builder = new Uri.Builder().scheme(uri.getScheme())
                .authority(uri.getAuthority()).path(uri.getPath());
        for (String param : uri.getQueryParameterNames()) {
            if ("access_token".equals(param)) {
                builder.appendQueryParameter(param, "token");
            } else {
                String queryParam = uri.getQueryParameter(param);
                if ("id".equals(param)) {
                    builder.appendQueryParameter(param, MoreStrings.toSafeString(queryParam));
                } else {
                    builder.appendQueryParameter(param, queryParam);
                }
            }
        }
        return builder.toString();
    }

    private static String reWriteUrl(Context context, String url) {
        return UrlRules.getRules(context.getContentResolver()).matchRule(url).apply(url);
    }
}
