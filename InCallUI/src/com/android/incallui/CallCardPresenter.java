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

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;

import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;

import com.android.services.telephony.common.Call;

/**
 * Presenter for the Call Card Fragment.
 * This class listens for changes to InCallState and passes it along to the fragment.
 */
public class CallCardPresenter extends Presenter<CallCardPresenter.CallCardUi> implements
        InCallStateListener, CallerInfoAsyncQuery.OnQueryCompleteListener,
        ContactsAsyncHelper.OnImageLoadCompleteListener {

    private static final int TOKEN_UPDATE_PHOTO_FOR_CALL_STATE = 0;

    private Context mContext;

    /**
     * Uri being used to load contact photo for mPhoto. Will be null when nothing is being loaded,
     * or a photo is already loaded.
     */
    private Uri mLoadingPersonUri;

    // Track the state for the photo.
    private ContactsAsyncHelper.ImageTracker mPhotoTracker;

    public CallCardPresenter() {
        mPhotoTracker = new ContactsAsyncHelper.ImageTracker();
    }

    @Override
    public void onUiReady(CallCardUi ui) {
        super.onUiReady(ui);
    }

    public void setContext(Context context) {
        mContext = context;
    }

    @Override
    public void onStateChange(InCallState state, CallList callList) {
        final CallCardUi ui = getUi();

        Call primary = null;
        Call secondary = null;

        if (state == InCallState.INCOMING) {
            primary = callList.getIncomingCall();
        } else if (state == InCallState.OUTGOING) {
            primary = callList.getOutgoingCall();

            // getCallToDisplay doesn't go through outgoing or incoming calls. It will return the
            // highest priority call to display as the secondary call.
            secondary = getCallToDisplay(callList, null);
        } else if (state == InCallState.INCALL) {
            primary = getCallToDisplay(callList, null);
            secondary = getCallToDisplay(callList, primary);
        }

        Logger.d(this, "Primary call: " + primary);
        Logger.d(this, "Secondary call: " + secondary);


        if (primary != null) {
            // Set primary call data
            final CallerInfo primaryCallInfo = CallerInfoUtils.getCallerInfoForCall(mContext,
                    primary, null, this);
            updateDisplayByCallerInfo(primary, primaryCallInfo, primary.getNumberPresentation(),
                    true);

            ui.setNumber(primary.getNumber());
            ui.setCallState(primary.getState(), primary.getDisconnectCause());
        } else {
            ui.setNumber("");
            ui.setCallState(Call.State.INVALID, Call.DisconnectCause.UNKNOWN);
        }

        // Set secondary call data
        if (secondary != null) {
            ui.setSecondaryCallInfo(true, secondary.getNumber());
        } else {
            ui.setSecondaryCallInfo(false, null);
        }
    }

    /**
     * Get the highest priority call to display.
     * Goes through the calls and chooses which to return based on priority of which type of call
     * to display to the user. Callers can use the "ignore" feature to get the second best call
     * by passing a previously found primary call as ignore.
     *
     * @param ignore A call to ignore if found.
     */
    private Call getCallToDisplay(CallList callList, Call ignore) {

        // Disconnected calls get primary position to let user know quickly
        // what call has disconnected. Disconnected calls are very short lived.
        Call retval = callList.getDisconnectedCall();
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Active calls come second.  An active call always gets precedent.
        retval = callList.getActiveCall();
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Then we go to background call (calls on hold)
        retval = callList.getBackgroundCall();
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Lastly, we go to a second background call.
        retval = callList.getSecondBackgroundCall();

        return retval;
    }

    public interface CallCardUi extends Ui {
        // TODO(klp): Consider passing in the Call object directly in these methods.
        void setVisible(boolean on);
        void setNumber(String number);
        void setNumberLabel(String label);
        void setName(String name);
        void setName(String name, boolean isNumber);
        void setImage(int resource);
        void setImage(Drawable drawable);
        void setImage(Bitmap bitmap);
        void setSecondaryCallInfo(boolean show, String number);
        void setCallState(int state, Call.DisconnectCause cause);
    }

    @Override
    public void onQueryComplete(int token, Object cookie, CallerInfo ci) {
        if (cookie instanceof Call) {
            final Call call = (Call) cookie;
            if (ci.contactExists || ci.isEmergencyNumber() || ci.isVoiceMailNumber()) {
                updateDisplayByCallerInfo(call, ci, Call.PRESENTATION_ALLOWED, true);
            } else {
                // If the contact doesn't exist, we can still use information from the
                // returned caller info (geodescription, etc).
                updateDisplayByCallerInfo(call, ci, call.getNumberPresentation(), true);
            }

            // Todo (klp): updatePhotoForCallState(call);
        }
    }

    /**
     * Based on the given caller info, determine a suitable name, phone number and label
     * to be passed to the CallCardUI.
     *
     * If the current call is a conference call, use
     * updateDisplayForConference() instead.
     */
    private void updateDisplayByCallerInfo(Call call, CallerInfo info, int presentation,
            boolean isPrimary) {

        // Inform the state machine that we are displaying a photo.
        mPhotoTracker.setPhotoRequest(info);
        mPhotoTracker.setPhotoState(ContactsAsyncHelper.ImageTracker.DISPLAY_IMAGE);

        // The actual strings we're going to display onscreen:
        String displayName;
        String displayNumber = null;
        String label = null;
        Uri personUri = null;

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

        // TODO (klp): Update secondary user call info as well.
        if (isPrimary) {
            updateInfoUiForPrimary(displayName, displayNumber, label);
        }

        // If the photoResource is filled in for the CallerInfo, (like with the
        // Emergency Number case), then we can just set the photo image without
        // requesting for an image load. Please refer to CallerInfoAsyncQuery.java
        // for cases where CallerInfo.photoResource may be set. We can also avoid
        // the image load step if the image data is cached.
        final CallCardUi ui = getUi();
        if (info == null) return;

        // This will only be true for emergency numbers
        if (info.photoResource != 0) {
            ui.setImage(info.photoResource);
        } else if (info.isCachedPhotoCurrent) {
            if (info.cachedPhoto != null) {
                ui.setImage(info.cachedPhoto);
            } else {
                ui.setImage(R.drawable.picture_unknown);
            }
        } else {
            if (personUri == null) {
                Logger.v(this, "personUri is null. Just use unknown picture.");
                ui.setImage(R.drawable.picture_unknown);
            } else if (personUri.equals(mLoadingPersonUri)) {
                Logger.v(this, "The requested Uri (" + personUri + ") is being loaded already."
                        + " Ignore the duplicate load request.");
            } else {
                // Remember which person's photo is being loaded right now so that we won't issue
                // unnecessary load request multiple times, which will mess up animation around
                // the contact photo.
                mLoadingPersonUri = personUri;

                // Load the image with a callback to update the image state.
                // When the load is finished, onImageLoadComplete() will be called.
                ContactsAsyncHelper.startObtainPhotoAsync(TOKEN_UPDATE_PHOTO_FOR_CALL_STATE,
                        mContext, personUri, this, call);

                // If the image load is too slow, we show a default avatar icon afterward.
                // If it is fast enough, this message will be canceled on onImageLoadComplete().
                // TODO (klp): Figure out if this handler is still needed.
                // mHandler.removeMessages(MESSAGE_SHOW_UNKNOWN_PHOTO);
                // mHandler.sendEmptyMessageDelayed(MESSAGE_SHOW_UNKNOWN_PHOTO, MESSAGE_DELAY);
            }
        }
        // TODO (klp): Update other fields - photo, sip label, etc.
    }

    /**
     * Implemented for ContactsAsyncHelper.OnImageLoadCompleteListener interface.
     * make sure that the call state is reflected after the image is loaded.
     */
    @Override
    public void onImageLoadComplete(int token, Drawable photo, Bitmap photoIcon, Object cookie) {
        // mHandler.removeMessages(MESSAGE_SHOW_UNKNOWN_PHOTO);
        if (mLoadingPersonUri != null) {
            // Start sending view notification after the current request being done.
            // New image may possibly be available from the next phone calls.
            //
            // TODO: may be nice to update the image view again once the newer one
            // is available on contacts database.
            // TODO (klp): What is this, and why does it need the write_contacts permission?
            // CallerInfoUtils.sendViewNotificationAsync(mContext, mLoadingPersonUri);
        } else {
            // This should not happen while we need some verbose info if it happens..
            Logger.v(this, "Person Uri isn't available while Image is successfully loaded.");
        }
        mLoadingPersonUri = null;

        Call call = (Call) cookie;

        // TODO (klp): Handle conference calls

        final CallCardUi ui = getUi();
        if (photo != null) {
            ui.setImage(photo);
        } else if (photoIcon != null) {
            ui.setImage(photoIcon);
        } else {
            ui.setImage(R.drawable.picture_unknown);
        }
    }

    /**
     * Updates the info portion of the call card with passed in values for the primary user.
     */
    private void updateInfoUiForPrimary(String displayName, String displayNumber, String label) {
        final CallCardUi ui = getUi();
        ui.setName(displayName);
        ui.setNumber(displayNumber);
        ui.setNumberLabel(label);
    }

    public String getPresentationString(int presentation) {
        String name = mContext.getString(R.string.unknown);
        if (presentation == Call.PRESENTATION_RESTRICTED) {
            name = mContext.getString(R.string.private_num);
        } else if (presentation == Call.PRESENTATION_PAYPHONE) {
            name = mContext.getString(R.string.payphone);
        }
        return name;
    }
}
