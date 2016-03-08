/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.dialer.contactinfo;

import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.test.AndroidTestCase;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.lettertiles.LetterTileDrawable;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.tests.R;

public class ContactPhotoLoaderTest extends InstrumentationTestCase {

    private Context mContext;

    @Override
    public void setUp() {
        mContext = getInstrumentation().getTargetContext();
    }

    public void testConstructor() {
        ContactPhotoLoader loader = new ContactPhotoLoader(mContext, new ContactInfo());
    }

    public void testConstructor_NullContext() {
        try {
            ContactPhotoLoader loader = new ContactPhotoLoader(null, new ContactInfo());
            fail();
        } catch (NullPointerException e) {
            //expected
        }
    }

    public void testConstructor_NullContactInfo() {
        try {
            ContactPhotoLoader loader = new ContactPhotoLoader(mContext, null);
            fail();
        } catch (NullPointerException e) {
            //expected
        }
    }

    public void testGetIcon_Photo() {
        ContactInfo info = getTestContactInfo();
        info.photoUri = getResourceUri(R.drawable.phone_icon);
        ContactPhotoLoader loader = new ContactPhotoLoader(mContext, info);
        assertTrue(loader.getIcon() instanceof RoundedBitmapDrawable);
    }

    public void testGetIcon_Photo_Invalid() {
        ContactInfo info = getTestContactInfo();
        info.photoUri = Uri.parse("file://invalid/uri");
        ContactPhotoLoader loader = new ContactPhotoLoader(mContext, info);
        //Should fall back to LetterTileDrawable
        assertTrue(loader.getIcon() instanceof LetterTileDrawable);
    }

    public void testGetIcon_LetterTile() {
        ContactInfo info = getTestContactInfo();
        ContactPhotoLoader loader = new ContactPhotoLoader(mContext, info);
        assertTrue(loader.getIcon() instanceof LetterTileDrawable);
    }

    private Uri getResourceUri(int resId) {
        Context testContext = getInstrumentation().getContext();
        Resources resources = testContext.getResources();

        assertNotNull(resources.getDrawable(resId));
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                + testContext.getPackageName()
                + '/' + resId);
    }

    private ContactInfo getTestContactInfo() {
        ContactInfo info = new ContactInfo();
        info.name = "foo";
        info.lookupKey = "bar";
        return info;
    }
}