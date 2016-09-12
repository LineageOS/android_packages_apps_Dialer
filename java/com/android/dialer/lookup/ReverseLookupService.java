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

package com.android.dialer.lookup;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;

import com.android.dialer.location.GeoUtil;
import com.android.dialer.logging.ContactLookupResult;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.incallui.bindings.PhoneNumberService;

import java.io.IOException;

public class ReverseLookupService implements PhoneNumberService, Handler.Callback {
    private final HandlerThread mBackgroundThread;
    private final Handler mBackgroundHandler;
    private final Handler mHandler;
    private final Context mContext;
    private final TelephonyManager mTelephonyManager;

    private static final int MSG_LOOKUP = 1;
    private static final int MSG_NOTIFY_NUMBER = 2;
    private static final int MSG_NOTIFY_IMAGE = 3;

    public ReverseLookupService(Context context) {
        mContext = context;
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        // TODO: stop after a while?
        mBackgroundThread = new HandlerThread("ReverseLookup");
        mBackgroundThread.start();

        mBackgroundHandler = new Handler(mBackgroundThread.getLooper(), this);
        mHandler = new Handler(this);
    }

    @Override
    public void getPhoneNumberInfo(String phoneNumber, NumberLookupListener numberListener,
            ImageLookupListener imageListener, boolean isIncoming) {
        if (!LookupSettings.isReverseLookupEnabled(mContext)) {
            LookupCache.deleteCachedContacts(mContext);
            return;
        }

        String countryIso = mTelephonyManager.getSimCountryIso().toUpperCase();
        String normalizedNumber = phoneNumber != null
                ? PhoneNumberUtils.formatNumberToE164(phoneNumber, countryIso) : null;

        // Can't do reverse lookup without a number
        if (normalizedNumber == null) {
            return;
        }

        LookupRequest request = new LookupRequest();
        request.normalizedNumber = normalizedNumber;
        request.formattedNumber = PhoneNumberUtils.formatNumber(phoneNumber,
                request.normalizedNumber, GeoUtil.getCurrentCountryIso(mContext));
        request.numberListener = numberListener;
        request.imageListener = imageListener;

        mBackgroundHandler.obtainMessage(MSG_LOOKUP, request).sendToTarget();
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_LOOKUP: {
                // background thread
                LookupRequest request = (LookupRequest) msg.obj;
                request.contactInfo = doLookup(request);
                if (request.contactInfo != null) {
                    mHandler.obtainMessage(MSG_NOTIFY_NUMBER, request).sendToTarget();
                    if (request.imageListener != null && request.contactInfo.photoUri != null) {
                        request.photo = fetchImage(request, request.contactInfo.photoUri);
                        if (request.photo != null) {
                            mHandler.obtainMessage(MSG_NOTIFY_IMAGE, request).sendToTarget();
                        }
                    }
                }
                break;
            }
            case MSG_NOTIFY_NUMBER: {
                // main thread
                LookupRequest request = (LookupRequest) msg.obj;
                if (request.numberListener != null) {
                    LookupNumberInfo info = new LookupNumberInfo(request.contactInfo);
                    request.numberListener.onPhoneNumberInfoComplete(info);
                }
                break;
            }
            case MSG_NOTIFY_IMAGE:
                // main thread
                LookupRequest request = (LookupRequest) msg.obj;
                if (request.imageListener != null) {
                    request.imageListener.onImageFetchComplete(request.photo);
                }
                break;
        }

        return true;
    }

    private ContactInfo doLookup(LookupRequest request) {
        final String number = request.normalizedNumber;

        if (LookupCache.hasCachedContact(mContext, number)) {
            ContactInfo info = LookupCache.getCachedContact(mContext, number);
            if (!ContactInfo.EMPTY.equals(info)) {
                return info;
            } else if (info != null) {
                // If we have an empty cached contact, remove it and redo lookup
                LookupCache.deleteCachedContact(mContext, number);
            }
        }

        try {
            ContactInfo info = ReverseLookup.getInstance(mContext).lookupNumber(mContext,
                    number, request.formattedNumber);
            if (info != null && !info.equals(ContactInfo.EMPTY)) {
                LookupCache.cacheContact(mContext, info);
                return info;
            }
        } catch (IOException e) {
            // ignored
        }

        return null;
    }

    private Bitmap fetchImage(LookupRequest request, Uri uri) {
        if (!LookupCache.hasCachedImage(mContext, request.normalizedNumber)) {
            Bitmap bmp = ReverseLookup.getInstance(mContext).lookupImage(mContext, uri);
            if (bmp != null) {
                LookupCache.cacheImage(mContext, request.normalizedNumber, bmp);
            }
        }

        return LookupCache.getCachedImage(mContext, request.normalizedNumber);
    }

    private static class LookupRequest {
        String normalizedNumber;
        String formattedNumber;
        NumberLookupListener numberListener;
        ImageLookupListener imageListener;
        ContactInfo contactInfo;
        Bitmap photo;
    }

    private static class LookupNumberInfo implements PhoneNumberInfo {
        private ContactInfo mInfo;
        private LookupNumberInfo(ContactInfo info) {
            mInfo = info;
        }

        @Override
        public String getDisplayName() {
            return mInfo.name;
        }
        @Override
        public String getNumber() {
            return mInfo.number;
        }
        @Override
        public int getPhoneType() {
            return mInfo.type;
        }
        @Override
        public String getPhoneLabel() {
            return mInfo.label;
        }
        @Override
        public String getNormalizedNumber() {
            return mInfo.normalizedNumber;
        }
        @Override
        public String getImageUrl() {
            return mInfo.photoUri != null ? mInfo.photoUri.toString() : null;
        }
        @Override
        public boolean isBusiness() {
            // FIXME
            return false;
        }
        @Override
        public String getLookupKey() {
            return mInfo.lookupKey;
        }
        @Override
        public ContactLookupResult.Type getLookupSource() {
            return ContactLookupResult.Type.REMOTE;
        }
    }
}
