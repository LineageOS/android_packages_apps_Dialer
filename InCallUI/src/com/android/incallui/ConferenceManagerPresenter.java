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
import android.net.Uri;
import android.telecom.PhoneCapabilities;
import android.text.TextUtils;

import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;

import com.google.common.base.Preconditions;

/**
 * Logic for call buttons.
 */
public class ConferenceManagerPresenter
        extends Presenter<ConferenceManagerPresenter.ConferenceManagerUi>
        implements InCallStateListener {

    private static final int MAX_CALLERS_IN_CONFERENCE = 5;

    private String[] mCallerIds;
    private Context mContext;

    @Override
    public void onUiReady(ConferenceManagerUi ui) {
        super.onUiReady(ui);

        // register for call state changes last
        InCallPresenter.getInstance().addListener(this);
    }

    @Override
    public void onUiUnready(ConferenceManagerUi ui) {
        super.onUiUnready(ui);

        InCallPresenter.getInstance().removeListener(this);
    }

    @Override
    public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
        if (getUi().isFragmentVisible()) {
            Log.v(this, "onStateChange" + newState);
            if (newState == InCallState.INCALL) {
                final Call call = callList.getActiveOrBackgroundCall();
                if (call != null && call.isConferenceCall()) {
                    Log.v(this, "Number of existing calls is " +
                            String.valueOf(call.getChildCallIds().size()));
                    update(callList);
                } else {
                    getUi().setVisible(false);
                }
            } else {
                getUi().setVisible(false);
            }
        }
    }

    public void init(Context context, CallList callList) {
        mContext = Preconditions.checkNotNull(context);
        mContext = context;
        update(callList);
    }

    private void update(CallList callList) {
        // callList is non null, but getActiveOrBackgroundCall() may return null
        final Call currentCall = callList.getActiveOrBackgroundCall();
        if (currentCall != null) {
            // getChildCallIds() always returns a valid Set
            mCallerIds = currentCall.getChildCallIds().toArray(new String[0]);
        } else {
            mCallerIds = new String[0];
        }
        Log.d(this, "Number of calls is " + String.valueOf(mCallerIds.length));

        // Users can split out a call from the conference call if there either the active call
        // or the holding call is empty. If both are filled at the moment, users can not split out
        // another call.
        final boolean hasActiveCall = (callList.getActiveCall() != null);
        final boolean hasHoldingCall = (callList.getBackgroundCall() != null);
        boolean canSeparate = !(hasActiveCall && hasHoldingCall);

        for (int i = 0; i < MAX_CALLERS_IN_CONFERENCE; i++) {
            if (i < mCallerIds.length) {
                int callCapabilities =
                        callList.getCallById(currentCall.getChildCallIds().get(i))
                        .getTelecommCall().getDetails().getCallCapabilities();
                boolean thisRowCanSeparate = canSeparate &&
                        ((callCapabilities & PhoneCapabilities.SEPARATE_FROM_CONFERENCE) != 0);
                boolean thisRowCanDisconnect =
                        ((callCapabilities & PhoneCapabilities.DISCONNECT_FROM_CONFERENCE) != 0);
                // Fill in the row in the UI for this caller.
                final ContactCacheEntry contactCache = ContactInfoCache.getInstance(mContext).
                        getInfo(mCallerIds[i]);
                updateManageConferenceRow(
                        i,
                        contactCache,
                        thisRowCanSeparate,
                        thisRowCanDisconnect);
            } else {
                // Blank out this row in the UI
                updateManageConferenceRow(i, null, false, false);
            }
        }
    }

    /**
      * Updates a single row of the "Manage conference" UI.  (One row in this
      * UI represents a single caller in the conference.)
      *
      * @param i the row to update
      * @param contactCacheEntry the contact details corresponding to this caller.
      *        If null, that means this is an "empty slot" in the conference,
      *        so hide this row in the UI.
      * @param canSeparate if true, show a "Separate" (i.e. "Private") button
      *        on this row in the UI.
      * @param canDisconnect if true, show a "Disconnect" button on this row in the UI.
      */
    public void updateManageConferenceRow(final int i,
                                          final ContactCacheEntry contactCacheEntry,
                                          boolean canSeparate,
                                          boolean canDisconnect) {

        if (contactCacheEntry != null) {
            // Activate this row of the Manage conference panel:
            getUi().setRowVisible(i, true);

            String name = contactCacheEntry.name;
            String number = contactCacheEntry.number;

            if (TextUtils.isEmpty(name)) {
                name = number;
                number = null;
            }

            getUi().setupSeparateButtonForRow(i, canSeparate);
            getUi().setupEndButtonForRow(i, canDisconnect);
            getUi().displayCallerInfoForConferenceRow(i, name, number, contactCacheEntry.label,
                    contactCacheEntry.lookupKey, contactCacheEntry.displayPhotoUri);
        } else {
            // Disable this row of the Manage conference panel:
            getUi().setRowVisible(i, false);
        }
    }

    public int getMaxCallersInConference() {
        return MAX_CALLERS_IN_CONFERENCE;
    }

    public void separateConferenceConnection(int rowId) {
        if (rowId < mCallerIds.length) {
            TelecomAdapter.getInstance().separateCall(mCallerIds[rowId]);
        }
    }

    public void endConferenceConnection(int rowId) {
        if (rowId < mCallerIds.length) {
            TelecomAdapter.getInstance().disconnectCall(mCallerIds[rowId]);
        }
    }

    public interface ConferenceManagerUi extends Ui {
        void setVisible(boolean on);
        boolean isFragmentVisible();
        void setRowVisible(int rowId, boolean on);
        void displayCallerInfoForConferenceRow(int rowId, String callerName, String callerNumber,
                String callerNumberType, String lookupKey, Uri photoUri);
        void setupSeparateButtonForRow(int rowId, boolean canSeparate);
        void setupEndButtonForRow(int rowId, boolean canDisconnect);
    }
}
