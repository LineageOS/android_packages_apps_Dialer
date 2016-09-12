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
  private final HandlerThread backgroundThread;
  private final Handler backgroundHandler;
  private final Handler handler;
  private final Context context;
  private final TelephonyManager telephonyManager;

  private static final int MSG_LOOKUP = 1;
  private static final int MSG_NOTIFY_NUMBER = 2;

  public ReverseLookupService(Context context) {
    this.context = context;
    telephonyManager = context.getSystemService(TelephonyManager.class);

    // TODO: stop after a while?
    backgroundThread = new HandlerThread("ReverseLookup");
    backgroundThread.start();

    backgroundHandler = new Handler(backgroundThread.getLooper(), this);
    handler = new Handler(this);
  }

  @Override
  public void getPhoneNumberInfo(String phoneNumber, NumberLookupListener numberListener) {
    if (!LookupSettings.isReverseLookupEnabled(context)) {
      LookupCache.deleteCachedContacts(context);
      return;
    }

    String countryIso = telephonyManager.getSimCountryIso().toUpperCase();
    String normalizedNumber = phoneNumber != null
        ? PhoneNumberUtils.formatNumberToE164(phoneNumber, countryIso) : null;

    // Can't do reverse lookup without a number
    if (normalizedNumber == null) {
      return;
    }

    LookupRequest request = new LookupRequest();
    request.normalizedNumber = normalizedNumber;
    request.formattedNumber = PhoneNumberUtils.formatNumber(phoneNumber,
        request.normalizedNumber, GeoUtil.getCurrentCountryIso(context));
    request.numberListener = numberListener;

    backgroundHandler.obtainMessage(MSG_LOOKUP, request).sendToTarget();
  }

  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_LOOKUP: {
        // background thread
        LookupRequest request = (LookupRequest) msg.obj;
        request.contactInfo = doLookup(request);
        if (request.contactInfo != null) {
          handler.obtainMessage(MSG_NOTIFY_NUMBER, request).sendToTarget();
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
    }

    return true;
  }

  private ContactInfo doLookup(LookupRequest request) {
    final String number = request.normalizedNumber;

    if (LookupCache.hasCachedContact(context, number)) {
      ContactInfo info = LookupCache.getCachedContact(context, number);
      if (!ContactInfo.EMPTY.equals(info)) {
        return info;
      } else if (info != null) {
        // If we have an empty cached contact, remove it and redo lookup
        LookupCache.deleteCachedContact(context, number);
      }
    }

    try {
      ReverseLookup inst = ReverseLookup.getInstance(context);
      ContactInfo info = inst.lookupNumber(context, number, request.formattedNumber);
      if (info != null && !info.equals(ContactInfo.EMPTY)) {
        LookupCache.cacheContact(context, info);
        return info;
      }
    } catch (IOException e) {
      // ignored
    }

    return null;
  }

  private Bitmap fetchImage(LookupRequest request, Uri uri) {
    if (!LookupCache.hasCachedImage(context, request.normalizedNumber)) {
      Bitmap bmp = ReverseLookup.getInstance(context).lookupImage(context, uri);
      if (bmp != null) {
        LookupCache.cacheImage(context, request.normalizedNumber, bmp);
      }
    }

    return LookupCache.getCachedImage(context, request.normalizedNumber);
  }

  private static class LookupRequest {
    String normalizedNumber;
    String formattedNumber;
    NumberLookupListener numberListener;
    ContactInfo contactInfo;
  }

  private static class LookupNumberInfo implements PhoneNumberInfo {
    private final ContactInfo info;
    private LookupNumberInfo(ContactInfo info) {
      this.info = info;
    }

    @Override
    public String getDisplayName() {
      return info.name;
    }
    @Override
    public String getNumber() {
      return info.number;
    }
    @Override
    public int getPhoneType() {
      return info.type;
    }
    @Override
    public String getPhoneLabel() {
      return info.label;
    }
    @Override
    public String getNormalizedNumber() {
      return info.normalizedNumber;
    }
    @Override
    public String getImageUrl() {
      return info.photoUri != null ? info.photoUri.toString() : null;
    }
    @Override
    public boolean isBusiness() {
      // FIXME
      return false;
    }
    @Override
    public String getLookupKey() {
      return info.lookupKey;
    }
    @Override
    public ContactLookupResult.Type getLookupSource() {
      return ContactLookupResult.Type.REMOTE;
    }
  }
}
