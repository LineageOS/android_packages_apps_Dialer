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

import com.android.contacts.common.GeoUtil;
import com.android.dialer.calllog.ContactInfo;
// ??
import com.android.incallui.Log;
import com.android.incallui.service.PhoneNumberService;
import com.google.android.dialer.phonenumbercache.CachedNumberLookupServiceImpl;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Pair;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class PhoneNumberServiceImpl implements PhoneNumberService {
    private static final String TAG = PhoneNumberServiceImpl.class.getSimpleName();
    private static final CachedNumberLookupServiceImpl mCachedNumberLookupService =
            new CachedNumberLookupServiceImpl();
    private Context mContext;
    private String mCountryIso;
    private Handler mHandler;
    private ExecutorService mImageExecutorService;
    private ExecutorService mLookupExecutorService;
    private PeopleApiClient mPeopleClient;

    public PhoneNumberServiceImpl(Context context) {
        mPeopleClient = new PeopleApiClient();
        mLookupExecutorService = Executors.newFixedThreadPool(2);
        mImageExecutorService = Executors.newFixedThreadPool(2);

        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                default:
                    super.handleMessage(msg);
                    break;
                case 1:
                    Pair pair = (Pair)msg.obj;
                    ((NumberLookupListener) pair.first)
                            .onPhoneNumberInfoComplete(
                                    (PhoneNumberInfo) pair.second);
                    break;
                case 2:
                    Pair pair2 = (Pair)msg.obj;
                    ((ImageLookupListener) pair2.first)
                            .onImageFetchComplete((Bitmap) pair2.second);
                    break;
                }
            }
        };

        mContext = context;
        mCountryIso = getCountryCodeIso();
    }

    private String getCountryCodeIso() {
        return ((TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE))
                .getSimCountryIso().toUpperCase();
    }

    private Account[] getGoogleAccounts(Context context) {
        return AccountManager.get(context).getAccountsByType("com.google");
    }

    static String httpToHttps(String url) {
        if (url.length() > 4 && "http:".equals(url.substring(0, 5))) {
            url = "https" + url.substring(4);
        }
        return url;
    }

    private static byte[] loadPhotoFromContentUri(Context context, Uri uri) throws IOException {
        AssetFileDescriptor descriptor =
              context.getContentResolver().openAssetFileDescriptor(uri, "r");

        if (descriptor == null) {
            return null;
        }

        FileInputStream inputStream = descriptor.createInputStream();
        if (inputStream == null) {
            descriptor.close();
            return null;
        }

        byte[] array = new byte[16384];
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            while (true) {
                int read = inputStream.read(array);
                if (read == -1) {
                    break;
                }
                byteArrayOutputStream.write(array, 0, read);
            }
        } finally {
            inputStream.close();
            descriptor.close();
        }

        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public void getPhoneNumberInfo(String phoneNumber,
            NumberLookupListener listener,
            ImageLookupListener imageListener,
            boolean isIncoming) {
        try {
            mLookupExecutorService.execute(new LookupRunnable(
                    phoneNumber, listener, imageListener, isIncoming));
        } catch (Exception e) {
            Log.e(TAG, "Error performing reverse lookup.", e);
        }
    }

    private class ImageLookupRunnable implements Runnable {
        private String mAccount;
        private ImageLookupListener mListener;
        private String mNumber;
        private String mUrl;

        private ImageLookupRunnable(String account, String number, String url,
                ImageLookupListener listener) {
            mNumber = number;
            mUrl = url;
            mListener = listener;
            mAccount = account;
        }

        public void run() {
            try {
                String https = httpToHttps(mUrl);
                Uri uri = Uri.parse(https);
                String scheme = uri.getScheme();
                byte[] array;
                boolean b;

                if ("https".equals(scheme)) {
                    array = mPeopleClient.imageLookup(mContext, mAccount, https);
                    b = true;
                } else if ("content".equals(scheme) || "android.resource".equals(scheme)) {
                    array = loadPhotoFromContentUri(mContext, uri);
                    b = false;
                } else {
                    Log.e(TAG, scheme + " scheme not supported for image lookups.");
                    array = null;
                    b = false;
                }

                Object decodeByteArray = null;
                if (array != null) {
                    if (mCachedNumberLookupService != null
                            && mNumber != null
                            && (b || !mCachedNumberLookupService.isCacheUri(https))) {
                        mCachedNumberLookupService.addPhoto(mContext, mNumber, array);
                    }
                    decodeByteArray = BitmapFactory.decodeByteArray(array, 0, array.length);
                }
                mHandler.obtainMessage(2, Pair.create(mListener, decodeByteArray)).sendToTarget();
            } catch (Exception e) {
                Log.e(TAG, "Error fetching image.", e);
            } finally {
                mHandler.obtainMessage(2, Pair.create(mListener, null)).sendToTarget();
            }
        }
    }

    private class LookupRunnable implements Runnable {
        private final ImageLookupListener mImageListener;
        private final boolean mIsIncoming;
        private final NumberLookupListener mListener;
        private final String mPhoneNumber;

        public LookupRunnable(String phoneNumber,
                NumberLookupListener listener,
                ImageLookupListener imageListener,
                boolean isIncoming) {
            mPhoneNumber = phoneNumber;
            mListener = listener;
            mImageListener = imageListener;
            mIsIncoming = isIncoming;
        }

        private Pair<PhoneNumberInfoImpl, String> doLookup(String normalizedNumber) {
            if (!ReverseLookupSettingUtil.isEnabled(mContext)) {
                return null;
            }

            Account[] accounts = getGoogleAccounts(mContext);
            if (accounts.length == 0) {
                Log.d(TAG, "No google account found. Skipping reverse lookup.");
                return null;
            }

            PhoneNumberInfoImpl numberInfo = null;
            String name = null;
            String formattedNumber = PhoneNumberUtils.formatNumber(mPhoneNumber,
                    normalizedNumber, GeoUtil.getCurrentCountryIso(mContext));
            boolean includePlaces = true;

            for (int i = 0; i < accounts.length && i < 3; i++, includePlaces = false) {
                name = accounts[i].name;
                numberInfo = mPeopleClient.lookupByPhoneNumber(mContext, name,
                        normalizedNumber, formattedNumber, includePlaces, mIsIncoming);
                if (numberInfo != null && numberInfo.getDisplayName() != null) {
                    break;
                }
            }

            if (mCachedNumberLookupService != null && numberInfo != null
                    && numberInfo.getDisplayName() != null) {
                ContactInfo info = new ContactInfo();

                info.normalizedNumber = normalizedNumber;
                info.number = numberInfo.getNumber();
                if (info.number == null) {
                    info.number = formattedNumber;
                }
                info.name = numberInfo.getDisplayName();
                info.type = numberInfo.getPhoneType();
                info.label = numberInfo.getPhoneLabel();
                String imageUrl = numberInfo.getImageUrl();
                Uri uri;
                if (imageUrl == null) {
                    uri = null;
                } else {
                    uri = Uri.parse(imageUrl);
                }
                info.photoUri = uri;

                CachedNumberLookupServiceImpl.CachedContactInfoImpl cachedContactInfo =
                        mCachedNumberLookupService.buildCachedContactInfo(info);
                cachedContactInfo.setPeopleAPISource(numberInfo.isBusiness());
                cachedContactInfo.setLookupKey(numberInfo.getLookupKey());
                mCachedNumberLookupService.addContact(mContext, cachedContactInfo);
            }
            return Pair.create(numberInfo, name);
        }

        @Override
        public void run() {
            try {
                PhoneNumberInfoImpl numberInfo = null;
                // TODO: What is n?
                int n = 0;

                String number = PhoneNumberUtils.formatNumberToE164(mPhoneNumber, mCountryIso);

                Log.d(TAG, "raw number: " + mPhoneNumber + ", formatted e164: " + number);

                if (number == null) {
                    Log.d(TAG, "Could not normalize number to e164 standard.  Skipping lookup.");
                    return;
                }

                if (mCachedNumberLookupService == null) {
                    CachedNumberLookupServiceImpl.CachedContactInfoImpl cachedContact =
                            mCachedNumberLookupService.lookupCachedContactFromNumber(mContext, number);

                    if (cachedContact == null) {
                        ContactInfo contactInfo = cachedContact.getContactInfo();

                        if (contactInfo != null && contactInfo != ContactInfo.EMPTY) {
                            String photoUri;
                            if (contactInfo.photoUri != null) {
                                photoUri = contactInfo.photoUri.toString();
                            } else {
                                photoUri = null;
                            }

                            numberInfo = new PhoneNumberInfoImpl(
                                    contactInfo.name, contactInfo.normalizedNumber,
                                    contactInfo.number, contactInfo.type,
                                    contactInfo.label, photoUri, null,
                                    CachedNumberLookupServiceImpl.CachedContactInfoImpl
                                    .isBusiness(contactInfo.sourceType));

                            if (cachedContact.getSourceType() == 2) {
                                n = 1;
                            } else {
                                n = 0;
                            }
                        }
                    }
                }

                String name = null;
                if (n != 0 || numberInfo == null) {
                    Pair<PhoneNumberInfoImpl, String> lookup = doLookup(number);
                    numberInfo = (PhoneNumberInfoImpl) lookup.first;
                    name = (String) lookup.second;
                }

                if (numberInfo == null
                        || numberInfo.getDisplayName() == null
                        || numberInfo.getImageUrl() == null) {
                    Log.d(TAG, "Contact lookup. Remote contact found, no image.");
                } else {
                    Log.d(TAG, "Contact lookup. Remote contact found, loading image.");
                    mImageExecutorService.execute(new ImageLookupRunnable(name,
                        numberInfo.getNormalizedNumber(),
                        numberInfo.getImageUrl(), mImageListener));
                }

                mHandler.obtainMessage(1,
                        Pair.create(mListener, numberInfo))
                        .sendToTarget();


            }
            catch (Exception e) {
                Log.e(TAG, "Error running phone number lookup.", e);
                return;
            }
        }
    }
}
