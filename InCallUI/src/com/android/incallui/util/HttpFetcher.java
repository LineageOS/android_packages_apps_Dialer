/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.incallui.util;

import android.os.SystemClock;

import com.android.incallui.Log;
import com.google.common.io.Closeables;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Utility for making http requests.
 */
public class HttpFetcher {

    private static final String TAG = HttpFetcher.class.getSimpleName();

    /**
     * Send a http request to the given url.
     *
     * @param urlString The url to request.
     * @return The response body as a byte array.  Or {@literal null} if status code is not 2xx.
     * @throws java.io.IOException when an error occurs.
     */
    public static byte[] getRequestAsByteArray(String urlString) throws IOException {
        Log.d(TAG, "fetching " + urlString);
        HttpURLConnection conn = null;
        InputStream is = null;
        boolean isError = false;
        final long start = SystemClock.uptimeMillis();
        try {
            final URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            Log.d(TAG, "response code: " + conn.getResponseCode());
            // All 2xx codes are successful.
            if (conn.getResponseCode() / 100 == 2) {
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
                handleBadResponse(urlString, baos.toByteArray());
                return null;
            }

            final byte[] response = baos.toByteArray();
            Log.d(TAG, "received " + response.length + " bytes");
            final long end = SystemClock.uptimeMillis();
            Log.d(TAG, "fetch took " + (end - start) + " ms");
            return response;
        } finally {
            Closeables.closeQuietly(is);
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Send a http request to the given url.
     *
     * @param urlString The url to request.
     * @return The response body as a String. Or {@literal null} if status code is not 2xx.
     * @throws java.io.IOException when an error occurs.
     */
    public static String getRequestAsString(String urlString) throws IOException {
        final byte[] byteArr = getRequestAsByteArray(urlString);
        if (byteArr == null) {
            // Encountered error response... just return.
            return null;
        }
        final String response = new String(byteArr);
        Log.d(TAG, "response body: ");
        Log.d(TAG, response);
        return response;
    }

    private static void handleBadResponse(String url, byte[] response) {
        Log.w(TAG, "Got bad response code from url: " + url);
        Log.w(TAG, new String(response));
    }
}
