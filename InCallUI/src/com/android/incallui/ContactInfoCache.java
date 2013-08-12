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

import com.google.android.collect.Lists;
import com.google.android.collect.Maps;
import com.google.common.base.Preconditions;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Looper;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;

import com.android.services.telephony.common.Call;

import java.util.List;
import java.util.Map;

/**
 * Class responsible for querying Contact Information for Call objects.
 * Can perform asynchronous requests to the Contact Provider for information as well
 * as respond synchronously for any data that it currently has cached from previous
 * queries.
 * This class always gets called from the UI thread so it does not need thread protection.
 */
public class ContactInfoCache implements CallerInfoAsyncQuery.OnQueryCompleteListener,
        ContactsAsyncHelper.OnImageLoadCompleteListener {

    private static final int TOKEN_UPDATE_PHOTO_FOR_CALL_STATE = 0;

    private final Context mContext;
    private final Map<Integer, SearchEntry> mInfoMap = Maps.newHashMap();

    public ContactInfoCache(Context context) {
        mContext = context;
    }

    /**
     * Requests contact data for the Call object passed in.
     * Returns the data through callback.  If callback is null, no response is made, however the
     * query is still performed and cached.
     *
     * @param call The call to look up.
     * @param callback The function to call back when the call is found. Can be null.
     */
    public void findInfo(Call call, ContactInfoCacheCallback callback) {
        Preconditions.checkState(Looper.getMainLooper().getThread() == Thread.currentThread());
        Preconditions.checkNotNull(callback);
        Preconditions.checkNotNull(call);

        final SearchEntry entry;

        // If the entry already exists, add callback
        if (mInfoMap.containsKey(call.getCallId())) {
            entry = mInfoMap.get(call.getCallId());

            // If this entry is still pending, the callback will also get called when it returns.
            if (!entry.finished) {
                entry.addCallback(callback);
            }
        } else {
            entry = new SearchEntry(call, callback);
            mInfoMap.put(call.getCallId(), entry);
            startQuery(entry);
        }

        // Call back with the information we have
        callback.onContactInfoComplete(entry.call.getCallId(), entry.info);
    }

    /**
     * Callback method for asynchronous caller information query.
     */
    @Override
    public void onQueryComplete(int token, Object cookie, CallerInfo ci) {
        if (cookie instanceof Call) {
            final Call call = (Call) cookie;

            if (!mInfoMap.containsKey(call.getCallId())) {
                return;
            }

            final SearchEntry entry = mInfoMap.get(call.getCallId());

            int presentationMode = call.getNumberPresentation();
            if (ci.contactExists || ci.isEmergencyNumber() || ci.isVoiceMailNumber()) {
                presentationMode = Call.PRESENTATION_ALLOWED;
            }

            // start photo query

            updateCallerInfo(entry, ci, presentationMode);
        }
    }

    /**
     * Implemented for ContactsAsyncHelper.OnImageLoadCompleteListener interface.
     * make sure that the call state is reflected after the image is loaded.
     */
    @Override
    public void onImageLoadComplete(int token, Drawable photo, Bitmap photoIcon, Object cookie) {
        Logger.d(this, "Image load complete with context: ", mContext);
        // TODO: may be nice to update the image view again once the newer one
        // is available on contacts database.
        // TODO (klp): What is this, and why does it need the write_contacts permission?
        // CallerInfoUtils.sendViewNotificationAsync(mContext, mLoadingPersonUri);

        final Call call = (Call) cookie;

        if (!mInfoMap.containsKey(call.getCallId())) {
            Logger.e(this, "Image Load received for empty search entry.");
            return;
        }

        final SearchEntry entry = mInfoMap.get(call.getCallId());

        Logger.d(this, "setting photo for entry: ", entry);

        // TODO (klp): Handle conference calls
        if (photo != null) {
            Logger.v(this, "direct drawable: ", photo);
            entry.info.photo = photo;
        } else if (photoIcon != null) {
            Logger.v(this, "photo icon: ", photoIcon);
            entry.info.photo = new BitmapDrawable(mContext.getResources(), photoIcon);
        } else {
            Logger.v(this, "unknown photo");
            entry.info.photo = mContext.getResources().getDrawable(R.drawable.picture_unknown);
        }

        sendNotification(entry);
    }

    /**
     * Performs a query for caller information.
     * Save any immediate data we get from the query. An asynchronous query may also be made
     * for any data that we do not already have. Some queries, such as those for voicemail and
     * emergency call information, will not perform an additional asynchronous query.
     */
    private void startQuery(SearchEntry entry) {
        final CallerInfo ci = CallerInfoUtils.getCallerInfoForCall(mContext, entry.call, this);

        updateCallerInfo(entry, ci, entry.call.getNumberPresentation());
    }

    private void updateCallerInfo(SearchEntry entry, CallerInfo info, int presentation) {
        // The actual strings we're going to display onscreen:
        String displayName;
        String displayNumber = null;
        String label = null;
        Uri personUri = null;
        Drawable photo = null;

        final Call call = entry.call;

        // Gather missing info unless the call is generic, in which case we wouldn't use
        // the gathered information anyway.
        if (info != null) {

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

            // Currently, infi.phoneNumber may actually be a SIP address, and
            // if so, it might sometimes include the "sip:" prefix. That
            // prefix isn't really useful to the user, though, so strip it off
            // if present. (For any other URI scheme, though, leave the
            // prefix alone.)
            // TODO: It would be cleaner for CallerInfo to explicitly support
            // SIP addresses instead of overloading the "phoneNumber" field.
            // Then we could remove this hack, and instead ask the CallerInfo
            // for a "user visible" form of the SIP address.
            String number = info.phoneNumber;
            if ((number != null) && number.startsWith("sip:")) {
                number = number.substring(4);
            }

            if (TextUtils.isEmpty(info.name)) {
                // No valid "name" in the CallerInfo, so fall back to
                // something else.
                // (Typically, we promote the phone number up to the "name" slot
                // onscreen, and possibly display a descriptive string in the
                // "number" slot.)
                if (TextUtils.isEmpty(number)) {
                    // No name *or* number! Display a generic "unknown" string
                    // (or potentially some other default based on the presentation.)
                    displayName = getPresentationString(presentation);
                    Logger.d(this, "  ==> no name *or* number! displayName = " + displayName);
                } else if (presentation != Call.PRESENTATION_ALLOWED) {
                    // This case should never happen since the network should never send a phone #
                    // AND a restricted presentation. However we leave it here in case of weird
                    // network behavior
                    displayName = getPresentationString(presentation);
                    Logger.d(this, "  ==> presentation not allowed! displayName = " + displayName);
                } else if (!TextUtils.isEmpty(info.cnapName)) {
                    // No name, but we do have a valid CNAP name, so use that.
                    displayName = info.cnapName;
                    info.name = info.cnapName;
                    displayNumber = number;
                    Logger.d(this, "  ==> cnapName available: displayName '"
                            + displayName + "', displayNumber '" + displayNumber + "'");
                } else {
                    // No name; all we have is a number. This is the typical
                    // case when an incoming call doesn't match any contact,
                    // or if you manually dial an outgoing number using the
                    // dialpad.

                    // Promote the phone number up to the "name" slot:
                    displayName = number;

                    // ...and use the "number" slot for a geographical description
                    // string if available (but only for incoming calls.)
                    if ((call != null) && (call.getState() == Call.State.INCOMING)) {
                        // TODO (CallerInfoAsyncQuery cleanup): Fix the CallerInfo
                        // query to only do the geoDescription lookup in the first
                        // place for incoming calls.
                        displayNumber = info.geoDescription; // may be null
                        Logger.d(this, "Geodescrption: " + info.geoDescription);
                    }

                    Logger.d(this, "  ==>  no name; falling back to number: displayName '"
                            + displayName + "', displayNumber '" + displayNumber + "'");
                }
            } else {
                // We do have a valid "name" in the CallerInfo. Display that
                // in the "name" slot, and the phone number in the "number" slot.
                if (presentation != Call.PRESENTATION_ALLOWED) {
                    // This case should never happen since the network should never send a name
                    // AND a restricted presentation. However we leave it here in case of weird
                    // network behavior
                    displayName = getPresentationString(presentation);
                    Logger.d(this, "  ==> valid name, but presentation not allowed!"
                            + " displayName = " + displayName);
                } else {
                    displayName = info.name;
                    displayNumber = number;
                    label = info.phoneLabel;
                    Logger.d(this, "  ==>  name is present in CallerInfo: displayName '"
                            + displayName + "', displayNumber '" + displayNumber + "'");
                }
            }
            personUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, info.person_id);
            Logger.d(this, "- got personUri: '" + personUri
                    + "', based on info.person_id: " + info.person_id);
        } else {
            displayName = getPresentationString(presentation);
        }

        // This will only be true for emergency numbers
        if (info.photoResource != 0) {
            photo = mContext.getResources().getDrawable(info.photoResource);
        } else if (info.isCachedPhotoCurrent) {
            if (info.cachedPhoto != null) {
                photo = info.cachedPhoto;
            } else {
                photo = mContext.getResources().getDrawable(R.drawable.picture_unknown);
            }
        } else {
            if (personUri == null) {
                Logger.v(this, "personUri is null. Just use unknown picture.");
                photo = mContext.getResources().getDrawable(R.drawable.picture_unknown);
            } else {
                Logger.d(this, "startObtainPhotoAsync");
                // Load the image with a callback to update the image state.
                // When the load is finished, onImageLoadComplete() will be called.
                ContactsAsyncHelper.startObtainPhotoAsync(TOKEN_UPDATE_PHOTO_FOR_CALL_STATE,
                        mContext, personUri, this, entry.call);

                // If the image load is too slow, we show a default avatar icon afterward.
                // If it is fast enough, this message will be canceled on onImageLoadComplete().
                // TODO (klp): Figure out if this handler is still needed.
                // mHandler.removeMessages(MESSAGE_SHOW_UNKNOWN_PHOTO);
                // mHandler.sendEmptyMessageDelayed(MESSAGE_SHOW_UNKNOWN_PHOTO, MESSAGE_DELAY);
            }
        }

        final ContactCacheEntry cce = entry.info;
        cce.name = displayName;
        cce.number = displayNumber;
        cce.label = label;
        cce.photo = photo;

        sendNotification(entry);
    }

    /**
     * Sends the updated information to call the callbacks for the entry.
     */
    private void sendNotification(SearchEntry entry) {
        for (int i = 0; i < entry.callbacks.size(); i++) {
            entry.callbacks.get(i).onContactInfoComplete(entry.call.getCallId(), entry.info);
        }
    }

    /**
     * Gets name strings based on some special presentation modes.
     */
    private String getPresentationString(int presentation) {
        String name = mContext.getString(R.string.unknown);
        if (presentation == Call.PRESENTATION_RESTRICTED) {
            name = mContext.getString(R.string.private_num);
        } else if (presentation == Call.PRESENTATION_PAYPHONE) {
            name = mContext.getString(R.string.payphone);
        }
        return name;
    }

    /**
     * Callback interface for the contact query.
     */
    public interface ContactInfoCacheCallback {
        public void onContactInfoComplete(int callId, ContactCacheEntry entry);
    }

    public static class ContactCacheEntry {
        public String name;
        public String number;
        public String label;
        public Drawable photo;
    }

    private static class SearchEntry {
        public Call call;
        public boolean finished;
        public final ContactCacheEntry info;
        public final List<ContactInfoCacheCallback> callbacks = Lists.newArrayList();

        public SearchEntry(Call call, ContactInfoCacheCallback callback) {
            this.call = call;

            info = new ContactCacheEntry();
            finished = false;
            callbacks.add(callback);
        }

        public void addCallback(ContactInfoCacheCallback cb) {
            if (!callbacks.contains(cb)) {
                callbacks.add(cb);
            }
        }

        public void finish() {
            callbacks.clear();
            finished = true;
        }
    }
}
