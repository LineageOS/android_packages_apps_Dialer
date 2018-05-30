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

package com.android.incallui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.SystemClock;
import android.os.Trace;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DisplayNameSources;
import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.UserManagerCompat;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import com.android.contacts.common.ContactsUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.DialerExecutor;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.logging.ContactLookupResult;
import com.android.dialer.logging.ContactSource;
import com.android.dialer.oem.CequintCallerIdManager;
import com.android.dialer.oem.CequintCallerIdManager.CequintCallerIdContact;
import com.android.dialer.phonenumbercache.CachedNumberLookupService;
import com.android.dialer.phonenumbercache.CachedNumberLookupService.CachedContactInfo;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.phonenumbercache.PhoneNumberCache;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.util.MoreStrings;
import com.android.incallui.CallerInfoAsyncQuery.OnQueryCompleteListener;
import com.android.incallui.ContactsAsyncHelper.OnImageLoadCompleteListener;
import com.android.incallui.bindings.PhoneNumberService;
import com.android.incallui.call.DialerCall;
import com.android.incallui.incall.protocol.ContactPhotoType;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Class responsible for querying Contact Information for DialerCall objects. Can perform
 * asynchronous requests to the Contact Provider for information as well as respond synchronously
 * for any data that it currently has cached from previous queries. This class always gets called
 * from the UI thread so it does not need thread protection.
 */
public class ContactInfoCache implements OnImageLoadCompleteListener {

  private static final String TAG = ContactInfoCache.class.getSimpleName();
  private static final int TOKEN_UPDATE_PHOTO_FOR_CALL_STATE = 0;
  private static ContactInfoCache cache = null;
  private final Context context;
  private final PhoneNumberService phoneNumberService;
  // Cache info map needs to be thread-safe since it could be modified by both main thread and
  // worker thread.
  private final ConcurrentHashMap<String, ContactCacheEntry> infoMap = new ConcurrentHashMap<>();
  private final Map<String, Set<ContactInfoCacheCallback>> callBacks = new ArrayMap<>();
  private int queryId;
  private final DialerExecutor<CnapInformationWrapper> cachedNumberLookupExecutor;

  private static class CachedNumberLookupWorker implements Worker<CnapInformationWrapper, Void> {
    @Nullable
    @Override
    public Void doInBackground(@Nullable CnapInformationWrapper input) {
      if (input == null) {
        return null;
      }
      ContactInfo contactInfo = new ContactInfo();
      CachedContactInfo cacheInfo = input.service.buildCachedContactInfo(contactInfo);
      cacheInfo.setSource(ContactSource.Type.SOURCE_TYPE_CNAP, "CNAP", 0);
      contactInfo.name = input.cnapName;
      contactInfo.number = input.number;
      try {
        final JSONObject contactRows =
            new JSONObject()
                .put(
                    Phone.CONTENT_ITEM_TYPE,
                    new JSONObject().put(Phone.NUMBER, contactInfo.number));
        final String jsonString =
            new JSONObject()
                .put(Contacts.DISPLAY_NAME, contactInfo.name)
                .put(Contacts.DISPLAY_NAME_SOURCE, DisplayNameSources.STRUCTURED_NAME)
                .put(Contacts.CONTENT_ITEM_TYPE, contactRows)
                .toString();
        cacheInfo.setLookupKey(jsonString);
      } catch (JSONException e) {
        Log.w(TAG, "Creation of lookup key failed when caching CNAP information");
      }
      input.service.addContact(input.context.getApplicationContext(), cacheInfo);
      return null;
    }
  }

  private ContactInfoCache(Context context) {
    Trace.beginSection("ContactInfoCache constructor");
    this.context = context;
    phoneNumberService = Bindings.get(context).newPhoneNumberService(context);
    cachedNumberLookupExecutor =
        DialerExecutorComponent.get(this.context)
            .dialerExecutorFactory()
            .createNonUiTaskBuilder(new CachedNumberLookupWorker())
            .build();
    Trace.endSection();
  }

  public static synchronized ContactInfoCache getInstance(Context mContext) {
    if (cache == null) {
      cache = new ContactInfoCache(mContext.getApplicationContext());
    }
    return cache;
  }

  static ContactCacheEntry buildCacheEntryFromCall(Context context, DialerCall call) {
    final ContactCacheEntry entry = new ContactCacheEntry();

    // TODO: get rid of caller info.
    final CallerInfo info = CallerInfoUtils.buildCallerInfo(context, call);
    ContactInfoCache.populateCacheEntry(context, info, entry, call.getNumberPresentation());
    return entry;
  }

  /** Populate a cache entry from a call (which got converted into a caller info). */
  private static void populateCacheEntry(
      @NonNull Context context,
      @NonNull CallerInfo info,
      @NonNull ContactCacheEntry cce,
      int presentation) {
    Objects.requireNonNull(info);
    String displayName = null;
    String displayNumber = null;
    String label = null;
    boolean isSipCall = false;

    // It appears that there is a small change in behaviour with the
    // PhoneUtils' startGetCallerInfo whereby if we query with an
    // empty number, we will get a valid CallerInfo object, but with
    // fields that are all null, and the isTemporary boolean input
    // parameter as true.

    // In the past, we would see a NULL callerinfo object, but this
    // ends up causing null pointer exceptions elsewhere down the
    // line in other cases, so we need to make this fix instead. It
    // appears that this was the ONLY call to PhoneUtils
    // .getCallerInfo() that relied on a NULL CallerInfo to indicate
    // an unknown contact.

    // Currently, info.phoneNumber may actually be a SIP address, and
    // if so, it might sometimes include the "sip:" prefix. That
    // prefix isn't really useful to the user, though, so strip it off
    // if present. (For any other URI scheme, though, leave the
    // prefix alone.)
    // TODO: It would be cleaner for CallerInfo to explicitly support
    // SIP addresses instead of overloading the "phoneNumber" field.
    // Then we could remove this hack, and instead ask the CallerInfo
    // for a "user visible" form of the SIP address.
    String number = info.phoneNumber;

    if (!TextUtils.isEmpty(number)) {
      isSipCall = PhoneNumberHelper.isUriNumber(number);
      if (number.startsWith("sip:")) {
        number = number.substring(4);
      }
    }

    if (TextUtils.isEmpty(info.name)) {
      // No valid "name" in the CallerInfo, so fall back to
      // something else.
      // (Typically, we promote the phone number up to the "name" slot
      // onscreen, and possibly display a descriptive string in the
      // "number" slot.)
      if (TextUtils.isEmpty(number) && TextUtils.isEmpty(info.cnapName)) {
        // No name *or* number! Display a generic "unknown" string
        // (or potentially some other default based on the presentation.)
        displayName = getPresentationString(context, presentation, info.callSubject);
        Log.d(TAG, "  ==> no name *or* number! displayName = " + displayName);
      } else if (presentation != TelecomManager.PRESENTATION_ALLOWED) {
        // This case should never happen since the network should never send a phone #
        // AND a restricted presentation. However we leave it here in case of weird
        // network behavior
        displayName = getPresentationString(context, presentation, info.callSubject);
        Log.d(TAG, "  ==> presentation not allowed! displayName = " + displayName);
      } else if (!TextUtils.isEmpty(info.cnapName)) {
        // No name, but we do have a valid CNAP name, so use that.
        displayName = info.cnapName;
        info.name = info.cnapName;
        displayNumber = PhoneNumberHelper.formatNumber(context, number, info.countryIso);
        Log.d(
            TAG,
            "  ==> cnapName available: displayName '"
                + displayName
                + "', displayNumber '"
                + displayNumber
                + "'");
      } else {
        // No name; all we have is a number. This is the typical
        // case when an incoming call doesn't match any contact,
        // or if you manually dial an outgoing number using the
        // dialpad.
        displayNumber = PhoneNumberHelper.formatNumber(context, number, info.countryIso);

        Log.d(
            TAG,
            "  ==>  no name; falling back to number:"
                + " displayNumber '"
                + Log.pii(displayNumber)
                + "'");
      }
    } else {
      // We do have a valid "name" in the CallerInfo. Display that
      // in the "name" slot, and the phone number in the "number" slot.
      if (presentation != TelecomManager.PRESENTATION_ALLOWED) {
        // This case should never happen since the network should never send a name
        // AND a restricted presentation. However we leave it here in case of weird
        // network behavior
        displayName = getPresentationString(context, presentation, info.callSubject);
        Log.d(
            TAG,
            "  ==> valid name, but presentation not allowed!" + " displayName = " + displayName);
      } else {
        // Causes cce.namePrimary to be set as info.name below. CallCardPresenter will
        // later determine whether to use the name or nameAlternative when presenting
        displayName = info.name;
        cce.nameAlternative = info.nameAlternative;
        displayNumber = PhoneNumberHelper.formatNumber(context, number, info.countryIso);
        label = info.phoneLabel;
        Log.d(
            TAG,
            "  ==>  name is present in CallerInfo: displayName '"
                + displayName
                + "', displayNumber '"
                + displayNumber
                + "'");
      }
    }

    cce.namePrimary = displayName;
    cce.number = displayNumber;
    cce.location = info.geoDescription;
    cce.label = label;
    cce.isSipCall = isSipCall;
    cce.userType = info.userType;
    cce.originalPhoneNumber = info.phoneNumber;
    cce.shouldShowLocation = info.shouldShowGeoDescription;
    cce.isEmergencyNumber = info.isEmergencyNumber();
    cce.isVoicemailNumber = info.isVoiceMailNumber();

    if (info.contactExists) {
      cce.contactLookupResult = info.contactLookupResultType;
    }
  }

  /** Gets name strings based on some special presentation modes and the associated custom label. */
  private static String getPresentationString(
      Context context, int presentation, String customLabel) {
    String name = context.getString(R.string.unknown);
    if (!TextUtils.isEmpty(customLabel)
        && ((presentation == TelecomManager.PRESENTATION_UNKNOWN)
            || (presentation == TelecomManager.PRESENTATION_RESTRICTED))) {
      name = customLabel;
      return name;
    } else {
      if (presentation == TelecomManager.PRESENTATION_RESTRICTED) {
        name = PhoneNumberHelper.getDisplayNameForRestrictedNumber(context);
      } else if (presentation == TelecomManager.PRESENTATION_PAYPHONE) {
        name = context.getString(R.string.payphone);
      }
    }
    return name;
  }

  ContactCacheEntry getInfo(String callId) {
    return infoMap.get(callId);
  }

  private static final class CnapInformationWrapper {
    final String number;
    final String cnapName;
    final Context context;
    final CachedNumberLookupService service;

    CnapInformationWrapper(
        String number, String cnapName, Context context, CachedNumberLookupService service) {
      this.number = number;
      this.cnapName = cnapName;
      this.context = context;
      this.service = service;
    }
  }

  void maybeInsertCnapInformationIntoCache(
      Context context, final DialerCall call, final CallerInfo info) {
    final CachedNumberLookupService cachedNumberLookupService =
        PhoneNumberCache.get(context).getCachedNumberLookupService();
    if (!UserManagerCompat.isUserUnlocked(context)) {
      Log.i(TAG, "User locked, not inserting cnap info into cache");
      return;
    }
    if (cachedNumberLookupService == null
        || TextUtils.isEmpty(info.cnapName)
        || infoMap.get(call.getId()) != null) {
      return;
    }
    Log.i(TAG, "Found contact with CNAP name - inserting into cache");

    cachedNumberLookupExecutor.executeParallel(
        new CnapInformationWrapper(
            call.getNumber(), info.cnapName, context, cachedNumberLookupService));
  }

  /**
   * Requests contact data for the DialerCall object passed in. Returns the data through callback.
   * If callback is null, no response is made, however the query is still performed and cached.
   *
   * @param callback The function to call back when the call is found. Can be null.
   */
  @MainThread
  public void findInfo(
      @NonNull final DialerCall call,
      final boolean isIncoming,
      @NonNull ContactInfoCacheCallback callback) {
    Trace.beginSection("ContactInfoCache.findInfo");
    Assert.isMainThread();
    Objects.requireNonNull(callback);

    Trace.beginSection("prepare callback");
    final String callId = call.getId();
    final ContactCacheEntry cacheEntry = infoMap.get(callId);
    Set<ContactInfoCacheCallback> callBacks = this.callBacks.get(callId);

    // We need to force a new query if phone number has changed.
    boolean forceQuery = needForceQuery(call, cacheEntry);
    Trace.endSection();
    Log.d(TAG, "findInfo: callId = " + callId + "; forceQuery = " + forceQuery);

    // If we have a previously obtained intermediate result return that now except needs
    // force query.
    if (cacheEntry != null && !forceQuery) {
      Log.d(
          TAG,
          "Contact lookup. In memory cache hit; lookup "
              + (callBacks == null ? "complete" : "still running"));
      callback.onContactInfoComplete(callId, cacheEntry);
      // If no other callbacks are in flight, we're done.
      if (callBacks == null) {
        Trace.endSection();
        return;
      }
    }

    // If the entry already exists, add callback
    if (callBacks != null) {
      Log.d(TAG, "Another query is in progress, add callback only.");
      callBacks.add(callback);
      if (!forceQuery) {
        Log.d(TAG, "No need to query again, just return and wait for existing query to finish");
        Trace.endSection();
        return;
      }
    } else {
      Log.d(TAG, "Contact lookup. In memory cache miss; searching provider.");
      // New lookup
      callBacks = new ArraySet<>();
      callBacks.add(callback);
      this.callBacks.put(callId, callBacks);
    }

    Trace.beginSection("prepare query");
    /**
     * Performs a query for caller information. Save any immediate data we get from the query. An
     * asynchronous query may also be made for any data that we do not already have. Some queries,
     * such as those for voicemail and emergency call information, will not perform an additional
     * asynchronous query.
     */
    final CallerInfoQueryToken queryToken = new CallerInfoQueryToken(queryId, callId);
    queryId++;
    final CallerInfo callerInfo =
        CallerInfoUtils.getCallerInfoForCall(
            context,
            call,
            new DialerCallCookieWrapper(callId, call.getNumberPresentation(), call.getCnapName()),
            new FindInfoCallback(isIncoming, queryToken));
    Trace.endSection();

    if (cacheEntry != null) {
      // We should not override the old cache item until the new query is
      // back. We should only update the queryId. Otherwise, we may see
      // flicker of the name and image (old cache -> new cache before query
      // -> new cache after query)
      cacheEntry.queryId = queryToken.queryId;
      Log.d(TAG, "There is an existing cache. Do not override until new query is back");
    } else {
      ContactCacheEntry initialCacheEntry =
          updateCallerInfoInCacheOnAnyThread(
              callId, call.getNumberPresentation(), callerInfo, false, queryToken);
      sendInfoNotifications(callId, initialCacheEntry);
    }
    Trace.endSection();
  }

  @AnyThread
  private ContactCacheEntry updateCallerInfoInCacheOnAnyThread(
      String callId,
      int numberPresentation,
      CallerInfo callerInfo,
      boolean didLocalLookup,
      CallerInfoQueryToken queryToken) {
    Trace.beginSection("ContactInfoCache.updateCallerInfoInCacheOnAnyThread");
    Log.d(
        TAG,
        "updateCallerInfoInCacheOnAnyThread: callId = "
            + callId
            + "; queryId = "
            + queryToken.queryId
            + "; didLocalLookup = "
            + didLocalLookup);

    ContactCacheEntry existingCacheEntry = infoMap.get(callId);
    Log.d(TAG, "Existing cacheEntry in hashMap " + existingCacheEntry);

    // Mark it as emergency/voicemail if the cache exists and was emergency/voicemail before the
    // number changed.
    if (existingCacheEntry != null) {
      if (existingCacheEntry.isEmergencyNumber) {
        callerInfo.markAsEmergency(context);
      } else if (existingCacheEntry.isVoicemailNumber) {
        callerInfo.markAsVoiceMail(context);
      }
    }

    int presentationMode = numberPresentation;
    if (callerInfo.contactExists
        || callerInfo.isEmergencyNumber()
        || callerInfo.isVoiceMailNumber()) {
      presentationMode = TelecomManager.PRESENTATION_ALLOWED;
    }

    // We always replace the entry. The only exception is the same photo case.
    ContactCacheEntry cacheEntry = buildEntry(context, callerInfo, presentationMode);
    cacheEntry.queryId = queryToken.queryId;

    if (didLocalLookup) {
      if (cacheEntry.displayPhotoUri != null) {
        // When the difference between 2 numbers is only the prefix (e.g. + or IDD),
        // we will still trigger force query so that the number can be updated on
        // the calling screen. We need not query the image again if the previous
        // query already has the image to avoid flickering.
        if (existingCacheEntry != null
            && existingCacheEntry.displayPhotoUri != null
            && existingCacheEntry.displayPhotoUri.equals(cacheEntry.displayPhotoUri)
            && existingCacheEntry.photo != null) {
          Log.d(TAG, "Same picture. Do not need start image load.");
          cacheEntry.photo = existingCacheEntry.photo;
          cacheEntry.photoType = existingCacheEntry.photoType;
          return cacheEntry;
        }

        Log.d(TAG, "Contact lookup. Local contact found, starting image load");
        // Load the image with a callback to update the image state.
        // When the load is finished, onImageLoadComplete() will be called.
        cacheEntry.hasPendingQuery = true;
        ContactsAsyncHelper.startObtainPhotoAsync(
            TOKEN_UPDATE_PHOTO_FOR_CALL_STATE,
            context,
            cacheEntry.displayPhotoUri,
            ContactInfoCache.this,
            queryToken);
      }
      Log.d(TAG, "put entry into map: " + cacheEntry);
      infoMap.put(callId, cacheEntry);
    } else {
      // Don't overwrite if there is existing cache.
      Log.d(TAG, "put entry into map if not exists: " + cacheEntry);
      infoMap.putIfAbsent(callId, cacheEntry);
    }
    Trace.endSection();
    return cacheEntry;
  }

  private void maybeUpdateFromCequintCallerId(
      CallerInfo callerInfo, String cnapName, boolean isIncoming) {
    if (!CequintCallerIdManager.isCequintCallerIdEnabled(context)) {
      return;
    }
    if (callerInfo.phoneNumber == null) {
      return;
    }
    CequintCallerIdContact cequintCallerIdContact =
        CequintCallerIdManager.getCequintCallerIdContactForCall(
            context, callerInfo.phoneNumber, cnapName, isIncoming);

    if (cequintCallerIdContact == null) {
      return;
    }
    boolean hasUpdate = false;

    if (TextUtils.isEmpty(callerInfo.name) && !TextUtils.isEmpty(cequintCallerIdContact.name())) {
      callerInfo.name = cequintCallerIdContact.name();
      hasUpdate = true;
    }
    if (!TextUtils.isEmpty(cequintCallerIdContact.geolocation())) {
      callerInfo.geoDescription = cequintCallerIdContact.geolocation();
      callerInfo.shouldShowGeoDescription = true;
      hasUpdate = true;
    }
    // Don't overwrite photo in local contacts.
    if (!callerInfo.contactExists
        && callerInfo.contactDisplayPhotoUri == null
        && cequintCallerIdContact.photoUri() != null) {
      callerInfo.contactDisplayPhotoUri = Uri.parse(cequintCallerIdContact.photoUri());
      hasUpdate = true;
    }
    // Set contact to exist to avoid phone number service lookup.
    if (hasUpdate) {
      callerInfo.contactExists = true;
      callerInfo.contactLookupResultType = ContactLookupResult.Type.CEQUINT;
    }
  }

  /**
   * Implemented for ContactsAsyncHelper.OnImageLoadCompleteListener interface. Update contact photo
   * when image is loaded in worker thread.
   */
  @WorkerThread
  @Override
  public void onImageLoaded(int token, Drawable photo, Bitmap photoIcon, Object cookie) {
    Assert.isWorkerThread();
    CallerInfoQueryToken myCookie = (CallerInfoQueryToken) cookie;
    final String callId = myCookie.callId;
    final int queryId = myCookie.queryId;
    if (!isWaitingForThisQuery(callId, queryId)) {
      return;
    }
    loadImage(photo, photoIcon, cookie);
  }

  private void loadImage(Drawable photo, Bitmap photoIcon, Object cookie) {
    Log.d(TAG, "Image load complete with context: ", context);
    // TODO: may be nice to update the image view again once the newer one
    // is available on contacts database.
    CallerInfoQueryToken myCookie = (CallerInfoQueryToken) cookie;
    final String callId = myCookie.callId;
    ContactCacheEntry entry = infoMap.get(callId);

    if (entry == null) {
      Log.e(TAG, "Image Load received for empty search entry.");
      clearCallbacks(callId);
      return;
    }

    Log.d(TAG, "setting photo for entry: ", entry);

    // Conference call icons are being handled in CallCardPresenter.
    if (photo != null) {
      Log.v(TAG, "direct drawable: ", photo);
      entry.photo = photo;
      entry.photoType = ContactPhotoType.CONTACT;
    } else if (photoIcon != null) {
      Log.v(TAG, "photo icon: ", photoIcon);
      entry.photo = new BitmapDrawable(context.getResources(), photoIcon);
      entry.photoType = ContactPhotoType.CONTACT;
    } else {
      Log.v(TAG, "unknown photo");
      entry.photo = null;
      entry.photoType = ContactPhotoType.DEFAULT_PLACEHOLDER;
    }
  }

  /**
   * Implemented for ContactsAsyncHelper.OnImageLoadCompleteListener interface. make sure that the
   * call state is reflected after the image is loaded.
   */
  @MainThread
  @Override
  public void onImageLoadComplete(int token, Drawable photo, Bitmap photoIcon, Object cookie) {
    Assert.isMainThread();
    CallerInfoQueryToken myCookie = (CallerInfoQueryToken) cookie;
    final String callId = myCookie.callId;
    final int queryId = myCookie.queryId;
    if (!isWaitingForThisQuery(callId, queryId)) {
      return;
    }
    sendImageNotifications(callId, infoMap.get(callId));

    clearCallbacks(callId);
  }

  /** Blows away the stored cache values. */
  public void clearCache() {
    infoMap.clear();
    callBacks.clear();
    queryId = 0;
  }

  private ContactCacheEntry buildEntry(Context context, CallerInfo info, int presentation) {
    final ContactCacheEntry cce = new ContactCacheEntry();
    populateCacheEntry(context, info, cce, presentation);

    // This will only be true for emergency numbers
    if (info.photoResource != 0) {
      cce.photo = ContextCompat.getDrawable(context, info.photoResource);
    } else if (info.isCachedPhotoCurrent) {
      if (info.cachedPhoto != null) {
        cce.photo = info.cachedPhoto;
        cce.photoType = ContactPhotoType.CONTACT;
      } else {
        cce.photoType = ContactPhotoType.DEFAULT_PLACEHOLDER;
      }
    } else {
      cce.displayPhotoUri = info.contactDisplayPhotoUri;
      cce.photo = null;
    }

    if (info.lookupKeyOrNull != null && info.contactIdOrZero != 0) {
      cce.lookupUri = Contacts.getLookupUri(info.contactIdOrZero, info.lookupKeyOrNull);
    } else {
      Log.v(TAG, "lookup key is null or contact ID is 0 on M. Don't create a lookup uri.");
      cce.lookupUri = null;
    }

    cce.lookupKey = info.lookupKeyOrNull;
    cce.contactRingtoneUri = info.contactRingtoneUri;
    if (cce.contactRingtoneUri == null || Uri.EMPTY.equals(cce.contactRingtoneUri)) {
      cce.contactRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
    }

    return cce;
  }

  /** Sends the updated information to call the callbacks for the entry. */
  @MainThread
  private void sendInfoNotifications(String callId, ContactCacheEntry entry) {
    Trace.beginSection("ContactInfoCache.sendInfoNotifications");
    Assert.isMainThread();
    final Set<ContactInfoCacheCallback> callBacks = this.callBacks.get(callId);
    if (callBacks != null) {
      for (ContactInfoCacheCallback callBack : callBacks) {
        callBack.onContactInfoComplete(callId, entry);
      }
    }
    Trace.endSection();
  }

  @MainThread
  private void sendImageNotifications(String callId, ContactCacheEntry entry) {
    Trace.beginSection("ContactInfoCache.sendImageNotifications");
    Assert.isMainThread();
    final Set<ContactInfoCacheCallback> callBacks = this.callBacks.get(callId);
    if (callBacks != null && entry.photo != null) {
      for (ContactInfoCacheCallback callBack : callBacks) {
        callBack.onImageLoadComplete(callId, entry);
      }
    }
    Trace.endSection();
  }

  private void clearCallbacks(String callId) {
    callBacks.remove(callId);
  }

  /** Callback interface for the contact query. */
  public interface ContactInfoCacheCallback {

    void onContactInfoComplete(String callId, ContactCacheEntry entry);

    void onImageLoadComplete(String callId, ContactCacheEntry entry);
  }

  /** This is cached contact info, which should be the ONLY info used by UI. */
  public static class ContactCacheEntry {

    public String namePrimary;
    public String nameAlternative;
    public String number;
    public String location;
    public String label;
    public Drawable photo;
    @ContactPhotoType int photoType;
    boolean isSipCall;
    // Note in cache entry whether this is a pending async loading action to know whether to
    // wait for its callback or not.
    boolean hasPendingQuery;
    /** Either a display photo or a thumbnail URI. */
    Uri displayPhotoUri;

    public Uri lookupUri; // Sent to NotificationMananger
    public String lookupKey;
    public ContactLookupResult.Type contactLookupResult = ContactLookupResult.Type.NOT_FOUND;
    public long userType = ContactsUtils.USER_TYPE_CURRENT;
    Uri contactRingtoneUri;
    /** Query id to identify the query session. */
    int queryId;
    /** The phone number without any changes to display to the user (ex: cnap...) */
    String originalPhoneNumber;

    boolean shouldShowLocation;

    boolean isBusiness;
    boolean isEmergencyNumber;
    boolean isVoicemailNumber;

    public boolean isLocalContact() {
      return contactLookupResult == ContactLookupResult.Type.LOCAL_CONTACT;
    }

    @Override
    public String toString() {
      return "ContactCacheEntry{"
          + "name='"
          + MoreStrings.toSafeString(namePrimary)
          + '\''
          + ", nameAlternative='"
          + MoreStrings.toSafeString(nameAlternative)
          + '\''
          + ", number='"
          + MoreStrings.toSafeString(number)
          + '\''
          + ", location='"
          + MoreStrings.toSafeString(location)
          + '\''
          + ", label='"
          + label
          + '\''
          + ", photo="
          + photo
          + ", isSipCall="
          + isSipCall
          + ", displayPhotoUri="
          + displayPhotoUri
          + ", contactLookupResult="
          + contactLookupResult
          + ", userType="
          + userType
          + ", contactRingtoneUri="
          + contactRingtoneUri
          + ", queryId="
          + queryId
          + ", originalPhoneNumber="
          + originalPhoneNumber
          + ", shouldShowLocation="
          + shouldShowLocation
          + ", isEmergencyNumber="
          + isEmergencyNumber
          + ", isVoicemailNumber="
          + isVoicemailNumber
          + '}';
    }
  }

  private static final class DialerCallCookieWrapper {
    final String callId;
    final int numberPresentation;
    final String cnapName;

    DialerCallCookieWrapper(String callId, int numberPresentation, String cnapName) {
      this.callId = callId;
      this.numberPresentation = numberPresentation;
      this.cnapName = cnapName;
    }
  }

  private class FindInfoCallback implements OnQueryCompleteListener {

    private final boolean isIncoming;
    private final CallerInfoQueryToken queryToken;

    FindInfoCallback(boolean isIncoming, CallerInfoQueryToken queryToken) {
      this.isIncoming = isIncoming;
      this.queryToken = queryToken;
    }

    @Override
    public void onDataLoaded(int token, Object cookie, CallerInfo ci) {
      Assert.isWorkerThread();
      DialerCallCookieWrapper cw = (DialerCallCookieWrapper) cookie;
      if (!isWaitingForThisQuery(cw.callId, queryToken.queryId)) {
        return;
      }
      long start = SystemClock.uptimeMillis();
      maybeUpdateFromCequintCallerId(ci, cw.cnapName, isIncoming);
      long time = SystemClock.uptimeMillis() - start;
      Log.d(TAG, "Cequint Caller Id look up takes " + time + " ms.");
      updateCallerInfoInCacheOnAnyThread(cw.callId, cw.numberPresentation, ci, true, queryToken);
    }

    @Override
    public void onQueryComplete(int token, Object cookie, CallerInfo callerInfo) {
      Trace.beginSection("ContactInfoCache.FindInfoCallback.onQueryComplete");
      Assert.isMainThread();
      DialerCallCookieWrapper cw = (DialerCallCookieWrapper) cookie;
      String callId = cw.callId;
      if (!isWaitingForThisQuery(cw.callId, queryToken.queryId)) {
        Trace.endSection();
        return;
      }
      ContactCacheEntry cacheEntry = infoMap.get(callId);
      // This may happen only when InCallPresenter attempt to cleanup.
      if (cacheEntry == null) {
        Log.w(TAG, "Contact lookup done, but cache entry is not found.");
        clearCallbacks(callId);
        Trace.endSection();
        return;
      }
      // Before issuing a request for more data from other services, we only check that the
      // contact wasn't found in the local DB.  We don't check the if the cache entry already
      // has a name because we allow overriding cnap data with data from other services.
      if (!callerInfo.contactExists && phoneNumberService != null) {
        Log.d(TAG, "Contact lookup. Local contacts miss, checking remote");
        final PhoneNumberServiceListener listener =
            new PhoneNumberServiceListener(callId, queryToken.queryId);
        cacheEntry.hasPendingQuery = true;
        phoneNumberService.getPhoneNumberInfo(cacheEntry.number, listener);
      }
      sendInfoNotifications(callId, cacheEntry);
      if (!cacheEntry.hasPendingQuery) {
        if (callerInfo.contactExists) {
          Log.d(TAG, "Contact lookup done. Local contact found, no image.");
        } else {
          Log.d(
              TAG,
              "Contact lookup done. Local contact not found and"
                  + " no remote lookup service available.");
        }
        clearCallbacks(callId);
      }
      Trace.endSection();
    }
  }

  class PhoneNumberServiceListener implements PhoneNumberService.NumberLookupListener {

    private final String callId;
    private final int queryIdOfRemoteLookup;

    PhoneNumberServiceListener(String callId, int queryId) {
      this.callId = callId;
      queryIdOfRemoteLookup = queryId;
    }

    @Override
    public void onPhoneNumberInfoComplete(final PhoneNumberService.PhoneNumberInfo info) {
      Log.d(TAG, "PhoneNumberServiceListener.onPhoneNumberInfoComplete");
      if (!isWaitingForThisQuery(callId, queryIdOfRemoteLookup)) {
        return;
      }

      // If we got a miss, this is the end of the lookup pipeline,
      // so clear the callbacks and return.
      if (info == null) {
        Log.d(TAG, "Contact lookup done. Remote contact not found.");
        clearCallbacks(callId);
        return;
      }
      ContactCacheEntry entry = new ContactCacheEntry();
      entry.namePrimary = info.getDisplayName();
      entry.number = info.getNumber();
      entry.contactLookupResult = info.getLookupSource();
      entry.isBusiness = info.isBusiness();
      final int type = info.getPhoneType();
      final String label = info.getPhoneLabel();
      if (type == Phone.TYPE_CUSTOM) {
        entry.label = label;
      } else {
        final CharSequence typeStr = Phone.getTypeLabel(context.getResources(), type, label);
        entry.label = typeStr == null ? null : typeStr.toString();
      }
      final ContactCacheEntry oldEntry = infoMap.get(callId);
      if (oldEntry != null) {
        // Location is only obtained from local lookup so persist
        // the value for remote lookups. Once we have a name this
        // field is no longer used; it is persisted here in case
        // the UI is ever changed to use it.
        entry.location = oldEntry.location;
        entry.shouldShowLocation = oldEntry.shouldShowLocation;
        // Contact specific ringtone is obtained from local lookup.
        entry.contactRingtoneUri = oldEntry.contactRingtoneUri;
        entry.originalPhoneNumber = oldEntry.originalPhoneNumber;
      }

      // If no image and it's a business, switch to using the default business avatar.
      if (info.getImageUrl() == null && info.isBusiness()) {
        Log.d(TAG, "Business has no image. Using default.");
        entry.photoType = ContactPhotoType.BUSINESS;
      }

      Log.d(TAG, "put entry into map: " + entry);
      infoMap.put(callId, entry);
      sendInfoNotifications(callId, entry);

      entry.hasPendingQuery = info.getImageUrl() != null;

      // If there is no image then we should not expect another callback.
      if (!entry.hasPendingQuery) {
        // We're done, so clear callbacks
        clearCallbacks(callId);
      }
    }
  }

  private boolean needForceQuery(DialerCall call, ContactCacheEntry cacheEntry) {
    if (call == null || call.isConferenceCall()) {
      return false;
    }

    String newPhoneNumber = PhoneNumberUtils.stripSeparators(call.getNumber());
    if (cacheEntry == null) {
      // No info in the map yet so it is the 1st query
      Log.d(TAG, "needForceQuery: first query");
      return true;
    }
    String oldPhoneNumber = PhoneNumberUtils.stripSeparators(cacheEntry.originalPhoneNumber);

    if (!TextUtils.equals(oldPhoneNumber, newPhoneNumber)) {
      Log.d(TAG, "phone number has changed: " + oldPhoneNumber + " -> " + newPhoneNumber);
      return true;
    }

    return false;
  }

  private static final class CallerInfoQueryToken {
    final int queryId;
    final String callId;

    CallerInfoQueryToken(int queryId, String callId) {
      this.queryId = queryId;
      this.callId = callId;
    }
  }

  /** Check if the queryId in the cached map is the same as the one from query result. */
  private boolean isWaitingForThisQuery(String callId, int queryId) {
    final ContactCacheEntry existingCacheEntry = infoMap.get(callId);
    if (existingCacheEntry == null) {
      // This might happen if lookup on background thread comes back before the initial entry is
      // created.
      Log.d(TAG, "Cached entry is null.");
      return true;
    } else {
      int waitingQueryId = existingCacheEntry.queryId;
      Log.d(TAG, "waitingQueryId = " + waitingQueryId + "; queryId = " + queryId);
      return waitingQueryId == queryId;
    }
  }
}
