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

package com.google.android.dialer.reverselookup;

import com.android.incallui.Log;
import com.google.android.dialer.util.AuthException;
import com.google.android.dialer.util.HttpFetcher;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import android.content.Context;
import android.graphics.Point;
import android.util.Pair;
import android.view.WindowManager;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PeopleApiClient {
    private static final String TAG = PeopleApiClient.class.getSimpleName();

    private static final String[] SCOPES =
            new String[] { "https://www.googleapis.com/auth/plus.me",
                    "https://www.googleapis.com/auth/plus.peopleapi.readwrite" };
    private static final String SCOPE_STR =
            "oauth2:" + Joiner.on(' ').join(PeopleApiClient.SCOPES);
    private static final String[] IMAGE_SCOPES =
            new String[] { "https://www.googleapis.com/auth/plus.contactphotos" };
    private static final String IMAGE_SCOPE_STR =
            "oauth2:" + Joiner.on(' ').join(PeopleApiClient.IMAGE_SCOPES);

    private static HashMap<String, String> mTokens = Maps.newHashMap();
    private static HashMap<String, String> mImageTokens = Maps.newHashMap();

    private ArrayList<Pair<String, String>> buildAuthHeader(String bearer) {
        ArrayList<Pair<String, String>> list =
                Lists.newArrayListWithCapacity(1);
        list.add((Pair<String, String>)Pair.create("Authorization", "Bearer " + bearer));
        return list;
    }

    private String buildLookupUrl(Context context,
            String s, boolean includePlaces, boolean isIncoming) {
        StringBuilder sb = new StringBuilder(
                "https://www.googleapis.com/plus/v2whitelisted/people/lookup?");

        sb.append(ReverseLookupSettingUtil.getAdditionalQueryParams(context));
        sb.append("&type=phone&fields=");
        sb.append("kind," +
                "items(metadata(objectType,plusPageType,attributions)," +
                "names," +
                "phoneNumbers(value,type,formattedType,canonicalizedForm)," +
                "addresses(value,type,formattedType)," +
                "images(url,metadata(container))," +
                "urls(value))");
        if (includePlaces) {
            sb.append("&includePlaces=1");
        }

        sb.append("&callType=");
        if (isIncoming) {
            sb.append("incoming");
        } else {
            sb.append("outgoing");
        }

        try {
            sb.append("&id=");
            sb.append(URLEncoder.encode(s, "UTF-8"));
            return sb.toString();
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Error encoding phone number.  UTF-8 is not supported?!", e);
            return null;
        }
    }

    private PhoneNumberInfoImpl doLookup(Context context, String s,
            String s2, String s3, boolean includePlaces, boolean isIncoming) throws AuthException {
        String token = getToken(context, s);
        if (token != null) {
            String url = buildLookupUrl(context, s2, includePlaces, isIncoming);
            if (url != null) {
                return lookupPhoneNumber(context, url, s2, s3, token);
            }
        }
        return null;
    }

    private static synchronized String getImageToken(Context context, String s) {
        // TODO: Did decompiler mean synchronized?
        // monitorenter(PeopleApiClient.class)

        if (s == null) {
            return null;
        }

        String token = null;

        try {
            token = mImageTokens.get(s);
            if (token == null) {
                try {
                    token = GoogleAuthUtil.getTokenWithNotification(
                            context, s, IMAGE_SCOPE_STR, null);
                    mImageTokens.put(s, token);
                } catch (UserRecoverableAuthException e) {
                    Log.e(TAG, "Need user approval: " + e.getIntent());
                } catch (IOException e) {
                    Log.e(TAG, "Error fetching oauth token.", e);
                } catch (GoogleAuthException e) {
                    Log.e(TAG, "Error authenticating via oauth.", e);
                }
            }
        }
        finally {}

        // monitorexit(PeopleApiClient.class)
        return token;
    }

    private static synchronized String getToken(Context context, String s) {
        // TODO: Did decompiler mean synchronized?
        // monitorenter(PeopleApiClient.class)

        if (s == null) {
            return null;
        }

        String token = null;

        try {
            token = mTokens.get(s);
            if (token == null) {
                try {
                    token = GoogleAuthUtil.getTokenWithNotification(context, s, SCOPE_STR, null);
                    mTokens.put(s, token);
                } catch (UserRecoverableAuthException e) {
                    Log.e(TAG, "Need user approval: " + e.getIntent());
                } catch (IOException e) {
                    Log.e(TAG, "Error fetching oauth token.", e);
                } catch (GoogleAuthException e) {
                    Log.e(TAG, "Error authenticating via oauth.", e);
                }
            }
        }
        finally {}

        // monitorexit(PeopleApiClient.class)
        return token;
    }

    private static synchronized void invalidateImageToken(Context context, String s) {
        GoogleAuthUtil.invalidateToken(context, mImageTokens.get(s));
        mImageTokens.remove(s);
    }

    private static synchronized void invalidateToken(Context context, String s) {
        GoogleAuthUtil.invalidateToken(context, mTokens.get(s));
        mTokens.remove(s);
    }

    private PhoneNumberInfoImpl lookupPhoneNumber(Context context,
            String s, String s2, String s3, String s4) throws AuthException {
        // TODO: Check if this is correct. Decompiler screwed this one up.
        String req = null;

        try {
            req = HttpFetcher.getRequestAsString(context, s, buildAuthHeader(s4));
        } catch (IOException ex) {
            Log.e(PeopleApiClient.TAG, "Error looking up phone number.", ex);
        }

        if (req == null) {
            return null;
        } else {
            return PeopleJsonParser.parsePeopleJson(req, s2, s3,
                    ReverseLookupSettingUtil.getProtectedPhotoUrl(context));
        }
    }

    public int getScreenWidth(Context context) {
        WindowManager windowManager = (WindowManager)context
                .getSystemService(Context.WINDOW_SERVICE);
        Point point = new Point();
        windowManager.getDefaultDisplay().getSize(point);
        return point.x;
    }

    // TODO: What is s and sz?
    public byte[] imageLookup(Context context, String s, String s2) throws IOException {
        boolean startsWith = s2.startsWith(ReverseLookupSettingUtil.getProtectedPhotoUrl(context));

        List<Pair<String, String>> header = null;
        if (startsWith) {
            String token = getImageToken(context, s);
            if (token == null) {
                return null;
            }
            header = buildAuthHeader(token);
        }

        String string = s2 + "?sz=" + getScreenWidth(context) / 2;

        try {
            return HttpFetcher.getRequestAsByteArray(context, string, header);
        } catch (AuthException e) {
            Log.i(TAG, "Authentication error."
                    + " Already invalidated auth token and retried. Aborting lookup.");
            invalidateImageToken(context, s);
            try {
                return HttpFetcher.getRequestAsByteArray(context, string, header);
            } catch (AuthException ae) {
                Log.e(TAG, "Tried again but still got auth error during image lookup.", ae);
                return null;
            }
        }
    }

    // TODO: What is s, s2, s3?
    public PhoneNumberInfoImpl lookupByPhoneNumber(Context context, String s,
            String s2, String s3, boolean includePlaces, boolean isIncoming) {
        Preconditions.checkNotNull(s2);
        Preconditions.checkNotNull(s);
        try {
            return doLookup(context, s, s2, s3, includePlaces, isIncoming);
        } catch (AuthException e) {
            Log.i(TAG, "Authentication error."
                    + " Already invalidated auth token and retried. Aborting lookup.");
            invalidateToken(context, s);
            try {
                return doLookup(context, s, s2, s3, includePlaces, isIncoming);
            } catch (AuthException ae) {
                Log.e(TAG, "Tried again but still got auth error during phone number lookup.", ae);
                return null;
            }
        }
    }
}
