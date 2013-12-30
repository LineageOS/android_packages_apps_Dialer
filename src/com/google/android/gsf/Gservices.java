/*
  * Copyright (C) 2009 The Android Open Source Project
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

package com.google.android.gsf;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.TreeMap;

/**
  * Gservices provides access to a key-value store that is can be
  * updated remote (by the google checkin service).
  */
public class Gservices {
    public static final String TAG = "Gservices";

    public final static Uri CONTENT_URI =
        Uri.parse("content://com.google.android.gsf.gservices");
    public final static Uri CONTENT_PREFIX_URI =
        Uri.parse("content://com.google.android.gsf.gservices/prefix");

    public static final Pattern TRUE_PATTERN =
        Pattern.compile("^(1|true|t|on|yes|y)$", Pattern.CASE_INSENSITIVE);
    public static final Pattern FALSE_PATTERN =
        Pattern.compile("^(0|false|f|off|no|n)$", Pattern.CASE_INSENSITIVE);

    private static HashMap<String, String> sCache;
    private static Object sVersionToken;
    private static String[] sPreloadedPrefixes = new String[0];

    public static synchronized void bulkCacheByPrefix(
            ContentResolver resolver, String... prefixes) {
        Map<String, String> stringsByPrefix = getStringsByPrefix(resolver, prefixes);
        ensureCacheInitializedLocked(resolver);
        sPreloadedPrefixes = prefixes;
        for (Map.Entry<String, String> entry : stringsByPrefix.entrySet()) {
            sCache.put(entry.getKey(), entry.getValue());
        }
    }

    private static void ensureCacheInitializedLocked(final ContentResolver cr) {
        if (sCache == null) {
            sCache = new HashMap<String, String>();
            sVersionToken = new Object();

            // Create a thread to host a Handler for ContentObserver callbacks.
            // The callback will clear the cache to force the resolver to be consulted
            // on future gets. The version is also updated.
            new Thread(TAG) {
                public void run() {
                    Looper.prepare();
                    cr.registerContentObserver(CONTENT_URI, true,
                        new ContentObserver(new Handler(Looper.myLooper())) {
                            public void onChange(boolean selfChange) {
                                synchronized (Gservices.class) {
                                    sCache.clear();
                                    sVersionToken = new Object();
                                    if (sPreloadedPrefixes.length > 0) {
                                        bulkCacheByPrefix(cr, sPreloadedPrefixes);
                                    }
                                }
                            } });
                    Looper.loop();
                }
            }.start();
        }
    }

    /**
      * Look up a key in the database.
      * @param cr to access the database with
      * @param key to look up in the table
      * @param defValue the value to return if the value from the database is null
      * @return the corresponding value, or defValue if not present
      */
    public static String getString(ContentResolver cr, String key, String defValue) {
        final Object version;
        synchronized (Gservices.class) {
            ensureCacheInitializedLocked(cr);
            version = sVersionToken;

            if (sCache.containsKey(key)) {
                String value = sCache.get(key);
                return (value != null) ? value : defValue;
            }
        }

        final String[] prefixes = sPreloadedPrefixes;
        for (int length = prefixes.length, i = 0; i < length; i++) {
            if (key.startsWith(prefixes[i])) {
                return defValue;
            }
        }

        Cursor cursor = cr.query(CONTENT_URI, null, null, new String[]{ key }, null);
        if (cursor == null) return defValue;

        try {
            if (!cursor.moveToFirst()) {
                sCache.put(key, null);
                return defValue;
            }
            String value = cursor.getString(1);
            synchronized (Gservices.class) {
                // There is a chance that the version change, and thus the cache clearing,
                // happened after the query, meaning the value we got could be stale. Don't
                // store it in the cache in this case.
                if (version == sVersionToken) {
                    sCache.put(key, value);
                }
            }
            return (value != null) ? value : defValue;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
      * Look up a key in the database.
      * @param cr to access the database with
      * @param key to look up in the table
      * @return the corresponding value, or null if not present
      */
    public static String getString(ContentResolver cr, String key) {
        return getString(cr, key, null);
    }

    /**
      * Look up the value for key in the database, convert it to an int
      * using Integer.parseInt and return it. If it is null or if a
      * NumberFormatException is caught during the conversion then
      * return defValue.
      */
    public static int getInt(ContentResolver cr, String key, int defValue) {
        String valString = getString(cr, key);
        int value;
        try {
            value = valString != null ? Integer.parseInt(valString) : defValue;
        } catch (NumberFormatException e) {
            value = defValue;
        }
        return value;
    }

    /**
      * Look up the value for key in the database, convert it to a long
      * using Long.parseLong and return it. If it is null or if a
      * NumberFormatException is caught during the conversion then
      * return defValue.
      */
    public static long getLong(ContentResolver cr, String key, long defValue) {
        String valString = getString(cr, key);
        long value;
        try {
            value = valString != null ? Long.parseLong(valString) : defValue;
        } catch (NumberFormatException e) {
            value = defValue;
        }
        return value;
    }

    public static boolean getBoolean(ContentResolver cr, String key, boolean defValue) {
        String valString = getString(cr, key);
        if (valString == null || valString.equals("")) {
            return defValue;
        } else if (TRUE_PATTERN.matcher(valString).matches()) {
            return true;
        } else if (FALSE_PATTERN.matcher(valString).matches()) {
            return false;
        } else {
            // Log a possible app bug
            Log.w(TAG, "attempt to read gservices key " + key + " (value \"" +
                  valString + "\") as boolean");
            return defValue;
        }
    }

    /**
      * Look up values for all keys beginning with any of the given prefixes.
      *
      * @return a Map<String, String> of the matching key-value pairs.
      */
    public static Map<String, String> getStringsByPrefix(ContentResolver cr,
                                                          String... prefixes) {
        Cursor c = cr.query(CONTENT_PREFIX_URI, null, null, prefixes, null);
        TreeMap<String, String> out = new TreeMap<String, String>();
        if (c == null) return out;

        try {
            while (c.moveToNext()) {
                out.put(c.getString(0), c.getString(1));
            }
        } finally {
            c.close();
        }
        return out;
    }

    /**
      * Returns a token that represents the current version of the data within gservices
      * @param cr the ContentResolver that Gservices should use to fill its cache
      * @return an Object that represents the current version of the Gservices values.
      */
    public static Object getVersionToken(ContentResolver cr) {
        synchronized (Gservices.class) {
            // Even though we don't need the cache itself, we need the cache version, so we make
            // that the cache has been initialized before we return its version.
            ensureCacheInitializedLocked(cr);
            return sVersionToken;
        }
    }
}
