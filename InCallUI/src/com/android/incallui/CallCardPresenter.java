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

import android.graphics.drawable.Drawable;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.android.incallui.AudioModeProvider.AudioModeListener;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.service.PhoneNumberService;
import com.android.incallui.util.HttpFetcher;
import com.android.incalluibind.ServiceFactory;
import com.android.services.telephony.common.AudioMode;
import com.android.services.telephony.common.Call;
import com.android.services.telephony.common.Call.DisconnectCause;

import java.io.IOException;

/**
 * Presenter for the Call Card Fragment.
 * <p>
 * This class listens for changes to InCallState and passes it along to the fragment.
 */
public class CallCardPresenter extends Presenter<CallCardPresenter.CallCardUi>
        implements InCallStateListener, AudioModeListener, ContactInfoCacheCallback {

    private static final String TAG = CallCardPresenter.class.getSimpleName();
    private static final long CALL_TIME_UPDATE_INTERVAL = 1000; // in milliseconds

    private PhoneNumberService mPhoneNumberService;
    private AudioModeProvider mAudioModeProvider;
    private ContactInfoCache mContactInfoCache;
    private Call mPrimary;
    private Call mSecondary;
    private ContactCacheEntry mPrimaryContactInfo;
    private ContactCacheEntry mSecondaryContactInfo;
    private CallTimer mCallTimer;

    public CallCardPresenter() {

        // create the call timer
        mCallTimer = new CallTimer(new Runnable() {
            @Override
            public void run() {
                updateCallTime();
            }
        });
    }

    public void init(PhoneNumberService phoneNumberService) {
        mPhoneNumberService = phoneNumberService;
    }

    @Override
    public void onUiReady(CallCardUi ui) {
        super.onUiReady(ui);

        if (mAudioModeProvider != null) {
            mAudioModeProvider.addListener(this);
        }
    }

    @Override
    public void onUiUnready(CallCardUi ui) {
        super.onUiUnready(ui);

        if (mAudioModeProvider != null) {
            mAudioModeProvider.removeListener(this);
        }
        mPrimary = null;
        mPrimaryContactInfo = null;
        mSecondaryContactInfo = null;
    }

    @Override
    public void onStateChange(InCallState state, CallList callList) {
        Logger.d(TAG, "onStateChange()");
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        Call primary = null;
        Call secondary = null;

        if (state == InCallState.INCOMING) {
            primary = callList.getIncomingCall();
        } else if (state == InCallState.OUTGOING) {
            primary = callList.getOutgoingCall();

            // getCallToDisplay doesn't go through outgoing or incoming calls. It will return the
            // highest priority call to display as the secondary call.
            secondary = getCallToDisplay(callList, null, true);
        } else if (state == InCallState.INCALL) {
            primary = getCallToDisplay(callList, null, false);
            secondary = getCallToDisplay(callList, primary, true);
        }

        Logger.d(this, "Primary call: " + primary);
        Logger.d(this, "Secondary call: " + secondary);

        mPrimary = primary;
        mSecondary = secondary;

        // Query for contact data. This will call back on onContactInfoComplete at least once
        // synchronously, and potentially a second time asynchronously if it needs to make
        // a full query for the data.
        // It is in that callback that we set the values into the Ui.
        startContactInfoSearch();

        // Start/Stop the call time update timer
        if (mPrimary != null && mPrimary.getState() == Call.State.ACTIVE) {
            Logger.d(this, "Starting the calltime timer");
            mCallTimer.start(CALL_TIME_UPDATE_INTERVAL);
        } else {
            Logger.d(this, "Canceling the calltime timer");
            mCallTimer.cancel();
            ui.setPrimaryCallElapsedTime(false, null);
        }

        // Set the call state
        if (mPrimary != null) {
            final boolean bluetoothOn = mAudioModeProvider != null &&
                    mAudioModeProvider.getAudioMode() == AudioMode.BLUETOOTH;
            ui.setCallState(mPrimary.getState(), mPrimary.getDisconnectCause(), bluetoothOn);
        } else {
            ui.setCallState(Call.State.IDLE, Call.DisconnectCause.UNKNOWN, false);
        }
    }

    @Override
    public void onAudioMode(int mode) {
        if (mPrimary != null && getUi() != null) {
            final boolean bluetoothOn = (AudioMode.BLUETOOTH == mode);

            getUi().setCallState(mPrimary.getState(), mPrimary.getDisconnectCause(), bluetoothOn);
        }
    }

    @Override
    public void onSupportedAudioMode(int mask) {
    }

    public void updateCallTime() {
        final CallCardUi ui = getUi();

        if (ui == null || mPrimary == null || mPrimary.getState() != Call.State.ACTIVE) {
            if (ui != null) {
                ui.setPrimaryCallElapsedTime(false, null);
            }
            mCallTimer.cancel();
        } else {
            final long callStart = mPrimary.getConnectTime();
            final long duration = System.currentTimeMillis() - callStart;
            ui.setPrimaryCallElapsedTime(true, DateUtils.formatElapsedTime(duration / 1000));
        }
    }


    public void setContactInfoCache(ContactInfoCache cache) {
        mContactInfoCache = cache;
        startContactInfoSearch();
    }

    /**
     * Starts a query for more contact data for the save primary and secondary calls.
     */
    private void startContactInfoSearch() {
        if (mPrimary != null && mContactInfoCache != null) {
            mContactInfoCache.findInfo(mPrimary, this);
        } else {
            mPrimaryContactInfo = null;
            updatePrimaryDisplayInfo();
        }

        if (mSecondary != null && mContactInfoCache != null) {
            mContactInfoCache.findInfo(mSecondary, this);
        } else {
            mSecondaryContactInfo = null;
            updateSecondaryDisplayInfo();
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
    private Call getCallToDisplay(CallList callList, Call ignore, boolean skipDisconnected) {

        // Active calls come second.  An active call always gets precedent.
        Call retval = callList.getActiveCall();
        if (retval != null && retval != ignore) {
            return retval;
        }

        // Disconnected calls get primary position if there are no active calls
        // to let user know quickly what call has disconnected. Disconnected
        // calls are very short lived.
        if (!skipDisconnected) {
            retval = callList.getDisconnectedCall();
            if (retval != null && retval != ignore) {
                return retval;
            }
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

    /**
     * Callback received when Contact info data query completes.
     */
    @Override
    public void onContactInfoComplete(int callId, ContactCacheEntry entry) {
        if (mPrimary != null && mPrimary.getCallId() == callId) {
            mPrimaryContactInfo = entry;
            updatePrimaryDisplayInfo();
            lookupPhoneNumber(mPrimary.getNumber());
        }
        if (mSecondary != null && mSecondary.getCallId() == callId) {
            mSecondaryContactInfo = entry;
            updateSecondaryDisplayInfo();
            // TODO(klp): investigate reverse lookup for secondary call.
        }

    }

    private void updatePrimaryDisplayInfo() {
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        if (mPrimaryContactInfo != null) {
            final String name = getNameForCall(mPrimaryContactInfo);
            final String number = getNumberForCall(mPrimaryContactInfo);
            final boolean nameIsNumber = name != null && name.equals(mPrimaryContactInfo.number);
            ui.setPrimary(number, name, nameIsNumber, mPrimaryContactInfo.label,
                    mPrimaryContactInfo.photo, mPrimary.isConferenceCall());
        } else {
            // reset to nothing (like at end of call)
            ui.setPrimary(null, null, false, null, null, false);
        }

    }

    public void lookupPhoneNumber(String phoneNumber) {
        if (mPhoneNumberService != null) {
            mPhoneNumberService.getPhoneNumberInfo(phoneNumber,
                    new PhoneNumberService.PhoneNumberServiceListener() {
                        @Override
                        public void onPhoneNumberInfoComplete(
                                final PhoneNumberService.PhoneNumberInfo info) {
                            if (info == null) {
                                return;
                            }
                            // TODO(klp): Ui is sometimes null due to something being shutdown.
                            if (getUi() != null) {
                                if (info.getName() != null) {
                                    getUi().setName(info.getName());
                                }

                                if (info.getImageUrl() != null) {
                                    fetchImage(info.getImageUrl());
                                }
                            }
                        }
                    });
        }
    }

    private void fetchImage(final String url) {
        if (url != null) {
            new AsyncTask<Void, Void, Bitmap>() {

                @Override
                protected Bitmap doInBackground(Void... params) {
                    // Fetch the image
                    try {
                        final byte[] image = HttpFetcher.getRequestAsByteArray(url);
                        return BitmapFactory.decodeByteArray(image, 0, image.length);
                    } catch (IOException e) {
                        Logger.e(TAG, "Unable to download/decode photo.", e);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Bitmap bitmap) {
                    // TODO(klp): same as above, figure out why it's null.
                    if (getUi() != null) {
                        getUi().setImage(bitmap);
                    }
                }

            }.execute();
        }
    }

    /**
     * Gets the name to display for the call.
     */
    private static String getNameForCall(ContactCacheEntry contactInfo) {
        if (TextUtils.isEmpty(contactInfo.name)) {
            return contactInfo.number;
        }
        return contactInfo.name;
    }

    /**
     * Gets the number to display for a call.
     */
    private static String getNumberForCall(ContactCacheEntry contactInfo) {
        // If the name is empty, we use the number for the name...so dont show a second
        // number in the number field
        if (TextUtils.isEmpty(contactInfo.name)) {
            return null;
        }
        return contactInfo.number;
    }

    private void updateSecondaryDisplayInfo() {
        final CallCardUi ui = getUi();
        if (ui == null) {
            return;
        }

        if (mSecondaryContactInfo != null) {
            final String name = getNameForCall(mSecondaryContactInfo);
            ui.setSecondary(true, getNameForCall(mSecondaryContactInfo),
                    mSecondaryContactInfo.label, mSecondaryContactInfo.photo);
        } else {
            // reset to nothing so that it starts off blank next time we use it.
            ui.setSecondary(false, null, null, null);
        }
    }

    public void setAudioModeProvider(AudioModeProvider audioModeProvider) {
        mAudioModeProvider = audioModeProvider;
        mAudioModeProvider.addListener(this);
    }

    public interface CallCardUi extends Ui {
        void setVisible(boolean on);
        void setPrimary(String number, String name, boolean nameIsNumber, String label,
                Drawable photo, boolean isConference);
        void setSecondary(boolean show, String name, String label, Drawable photo);
        void setCallState(int state, Call.DisconnectCause cause, boolean bluetoothOn);
        void setPrimaryCallElapsedTime(boolean show, String duration);
        void setName(String name);
        void setImage(Bitmap bitmap);
    }
}
