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

import com.android.contacts.common.GeoUtil;
import com.android.dialer.calllog.ContactInfo;
import com.android.incallui.ContactInfoCache;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class ReverseLookupThread extends Thread {
    private static final String TAG = ReverseLookupThread.class.getSimpleName();

    private static final ExecutorService mExecutorService =
            Executors.newFixedThreadPool(2);
    private static final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Context mContext;
    private final ContactInfoCache.ReverseLookupListener mListener;
    private final String mNormalizedNumber;
    private final String mFormattedNumber;

    public static void performLookup(Context context, String number,
            ContactInfoCache.ReverseLookupListener listener) {
        try {
            mExecutorService.execute(
                    new ReverseLookupThread(context, number, listener));
        } catch (Exception e) {
            Log.e(TAG, "Failed to perform reverse lookup", e);
        }
    }

    private ReverseLookupThread(Context context, String number,
            ContactInfoCache.ReverseLookupListener listener) {
        mContext = context;
        mListener = listener;
        String countryIso = ((TelephonyManager) mContext.getSystemService(
                Context.TELEPHONY_SERVICE)).getSimCountryIso().toUpperCase();
        mNormalizedNumber = PhoneNumberUtils
                .formatNumberToE164(number, countryIso);
        mFormattedNumber = PhoneNumberUtils.formatNumber(number,
                mNormalizedNumber, GeoUtil.getCurrentCountryIso(mContext));
    }

    @Override
    public void run() {
        if (!LookupSettings.isReverseLookupEnabled(mContext)) {
            LookupCache.deleteCachedContacts(mContext);
            return;
        }

        // Can't do reverse lookup without a number
        if (mNormalizedNumber == null || mFormattedNumber == null) {
            return;
        }

        ContactInfo info = null;

        if (LookupCache.hasCachedContact(mContext, mNormalizedNumber)) {
            info = LookupCache.getCachedContact(mContext, mNormalizedNumber);

            if (ContactInfo.EMPTY.equals(info)) {
                // If we have an empty cached contact, remove it and redo lookup
                LookupCache.deleteCachedContact(mContext, mNormalizedNumber);
                info = null;
            }
        }

        // Lookup contact if it's not cached
        Object data = null;
        if (info == null) {
            Pair<ContactInfo, Object> results =
                    ReverseLookup.getInstance(mContext).lookupNumber(
                            mContext, mNormalizedNumber, mFormattedNumber);

            if (results == null) {
                return;
            }

            info = results.first;
            data = results.second;

            // Put in cache only if the contact is valid
            if (info != null) {
                if (info.equals(ContactInfo.EMPTY)) {
                    return;
                } else if (info.name != null) {
                    LookupCache.cacheContact(mContext, info);
                }
            }
        }

        final ContactInfo infoFinal = info;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onLookupComplete(infoFinal);
            }
        });

        if (info.photoUri != null) {
            if (!LookupCache.hasCachedImage(mContext, mNormalizedNumber)) {
                Bitmap bmp = ReverseLookup.getInstance(mContext).lookupImage(
                        mContext, info.photoUri, data);

                if (bmp != null) {
                    LookupCache.cacheImage(mContext, mNormalizedNumber, bmp);
                }
            }

            final Bitmap bmp = LookupCache.getCachedImage(
                    mContext, mNormalizedNumber);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onImageFetchComplete(bmp);
                }
            });
        }
    }
}
