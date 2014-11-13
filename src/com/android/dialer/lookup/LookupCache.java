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

package com.android.dialer.lookup;

import com.android.dialer.calllog.ContactInfo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import libcore.io.IoUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

public class LookupCache {
    private static final String TAG = LookupCache.class.getSimpleName();

    public static final String NAME = "Name";
    public static final String TYPE = "Type";
    public static final String LABEL = "Label";
    public static final String NUMBER = "Number";
    public static final String FORMATTED_NUMBER = "FormattedNumber";
    public static final String NORMALIZED_NUMBER = "NormalizedNumber";
    public static final String PHOTO_ID = "PhotoID";
    //public static final String PHOTO_URI = "PhotoURI";
    public static final String LOOKUP_URI = "LookupURI";

    public static boolean hasCachedContact(Context context, String number) {
        String normalizedNumber = formatE164(context, number);

        if (normalizedNumber == null) {
            return false;
        }

        File file = getFilePath(context, normalizedNumber);
        return file.exists();
    }

    public static void cacheContact(Context context, ContactInfo info) {
        File file = getFilePath(context, info.normalizedNumber);

        if (file.exists()) {
            file.delete();
        }

        FileOutputStream out = null;
        JsonWriter writer = null;

        try {
            out = new FileOutputStream(file);
            writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
            writer.setIndent("  ");
            List messages = new ArrayList();

            writer.beginObject();
            if (info.name != null) writer.name(NAME).value(info.name);
            writer.name(TYPE).value(info.type);
            if (info.label != null) writer.name(LABEL).value(info.label);
            if (info.number != null) writer.name(NUMBER).value(info.number);
            if (info.formattedNumber != null) {
                writer.name(FORMATTED_NUMBER).value(info.formattedNumber);
            }
            if (info.normalizedNumber != null) {
                writer.name(NORMALIZED_NUMBER).value(info.normalizedNumber);
            }
            writer.name(PHOTO_ID).value(info.photoId);

            if (info.lookupUri != null) {
                writer.name(LOOKUP_URI).value(info.lookupUri.toString());
            }

            // We do not save the photo URI. If there's a cached image, that
            // will be used when the contact is retrieved. Otherwise, photoUri
            // will be set to null.

            writer.endObject();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IoUtils.closeQuietly(writer);
            IoUtils.closeQuietly(out);
        }
    }

    public static ContactInfo getCachedContact(Context context, String number) {
        String normalizedNumber = formatE164(context, number);

        if (normalizedNumber == null) {
            return null;
        }

        File file = getFilePath(context, normalizedNumber);
        if (!file.exists()) {
            // Whatever is calling this should probably check anyway
            return null;
        }

        ContactInfo info = new ContactInfo();

        FileInputStream in = null;
        JsonReader reader = null;

        try {
            in = new FileInputStream(file);
            reader = new JsonReader(new InputStreamReader(in, "UTF-8"));

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();

                if (NAME.equals(name)) {
                    info.name = reader.nextString();
                } else if (TYPE.equals(name)) {
                    info.type = reader.nextInt();
                } else if (LABEL.equals(name)) {
                    info.label = reader.nextString();
                } else if (NUMBER.equals(name)) {
                    info.number = reader.nextString();
                } else if (FORMATTED_NUMBER.equals(name)) {
                    info.formattedNumber = reader.nextString();
                } else if (NORMALIZED_NUMBER.equals(name)) {
                    info.normalizedNumber = reader.nextString();
                } else if (PHOTO_ID.equals(name)) {
                    info.photoId = reader.nextInt();
                } else if (LOOKUP_URI.equals(name)) {
                    Uri lookupUri = Uri.parse(reader.nextString());

                    if (hasCachedImage(context, normalizedNumber)) {
                        // Insert cached photo URI
                        Uri image = Uri.withAppendedPath(
                                LookupProvider.IMAGE_CACHE_URI,
                                Uri.encode(normalizedNumber));

                        String json = lookupUri.getEncodedFragment();
                        if (json != null) {
                            try {
                                JSONObject jsonObj = new JSONObject(json);
                                jsonObj.putOpt(Contacts.PHOTO_URI, image.toString());
                                lookupUri = lookupUri.buildUpon()
                                        .encodedFragment(jsonObj.toString())
                                        .build();
                            } catch (JSONException e) {
                                Log.e(TAG, "Failed to add image URI to json", e);
                            }
                        }

                        info.photoUri = image;
                    }

                    info.lookupUri = lookupUri;
                }
            }
            reader.endObject();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IoUtils.closeQuietly(reader);
            IoUtils.closeQuietly(in);
        }

        return info;
    }

    public static void deleteCachedContacts(Context context) {
        File dir = new File(context.getCacheDir()
                + File.separator + "lookup");

        if (!dir.exists()) {
            Log.v(TAG, "Lookup cache directory does not exist. Not clearing it.");
            return;
        }

        if (!dir.isDirectory()) {
            Log.e(TAG, "Path " + dir + " is not a directory");
            return;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        }
    }

    public static void deleteCachedContact(
            Context context, String normalizedNumber) {
        File f = getFilePath(context, normalizedNumber);
        if (f.exists()) {
            f.delete();
        }

        f = getImagePath(context, normalizedNumber);
        if (f.exists()) {
            f.delete();
        }
    }

    public static boolean hasCachedImage(Context context, String number) {
        String normalizedNumber = formatE164(context, number);

        if (normalizedNumber == null) {
            return false;
        }

        File file = getImagePath(context, normalizedNumber);
        return file.exists();
    }

    public static void cacheImage(Context context,
            String normalizedNumber, Bitmap bmp) {
        // Compress the cached images to save space
        if (bmp == null) {
            Log.e(TAG, "Failed to cache image");
            return;
        }

        File image = getImagePath(context, normalizedNumber);

        FileOutputStream out = null;

        try {
            out = new FileOutputStream(image);
            bmp.compress(Bitmap.CompressFormat.WEBP, 100, out);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IoUtils.closeQuietly(out);
        }
    }

    public static Bitmap getCachedImage(Context context, String normalizedNumber) {
        File image = getImagePath(context, normalizedNumber);
        if (!image.exists()) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(image.getPath(), options);
    }

    private static String formatE164(Context context, String number) {
        String countryIso = ((TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE)).getSimCountryIso().toUpperCase();
        return PhoneNumberUtils.formatNumberToE164(number, countryIso);
    }

    private static File getFilePath(Context context, String normalizedNumber) {
        File dir = new File(context.getCacheDir()
                + File.separator + "lookup");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        return new File(dir, normalizedNumber + ".json");
    }

    public static File getImagePath(Context context, String normalizedNumber) {
        File dir = new File(context.getCacheDir()
                + File.separator + "lookup");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        return new File(dir, normalizedNumber + ".webp");
    }
}
