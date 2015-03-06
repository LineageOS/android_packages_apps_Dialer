/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.telecom.PhoneAccountHandle;
import android.test.InstrumentationTestCase;

import com.android.incallui.InCallPresenter.InCallState;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class InCallPresenterTest extends InstrumentationTestCase {
    private Call mIncomingCall;
    private Call mActiveCall;
    private Call mConnectingCall;
    private Call mWaitingForAccountsCall;
    private Call mDisconnectedCall;

    @Mock private InCallActivity mInCallActivity;
    @Mock private AudioModeProvider mAudioModeProvider;
    @Mock private CallList mCallList;
    @Mock private StatusBarNotifier mStatusBarNotifier;
    @Mock private ContactInfoCache mContactInfoCache;
    @Mock private ProximitySensor mProximitySensor;

    InCallPresenter mInCallPresenter;
    @Mock private Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        System.setProperty("dexmaker.dexcache",
                getInstrumentation().getTargetContext().getCacheDir().getPath());
        MockitoAnnotations.initMocks(this);

        mIncomingCall = getMockCall(Call.State.INCOMING);
        mActiveCall = getMockCall(Call.State.ACTIVE);
        mConnectingCall = getMockCall(Call.State.CONNECTING);
        mWaitingForAccountsCall = getMockCall(Call.State.PRE_DIAL_WAIT, false);
        mDisconnectedCall = getMockCall(Call.State.DISCONNECTED);

        mInCallPresenter = InCallPresenter.getInstance();
        mInCallPresenter.setUp(mContext, mCallList, mAudioModeProvider, mStatusBarNotifier,
                mContactInfoCache, mProximitySensor);
    }

    @Override
    protected void tearDown() throws Exception {
        // The tear down method needs to run in the main thread since there is an explicit check
        // inside TelecomAdapter.getInstance().
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mInCallPresenter.unsetActivity(mInCallActivity);
                mInCallPresenter.tearDown();
                InCallPresenter.setInstance(null);
            }
        });
    }

    public void testOnActivitySet_finishesActivityWhenNoCalls() {
        mInCallPresenter.setActivity(mInCallActivity);

        verify(mInCallActivity).finish();
    }

    public void testOnCallListChange_sendsNotificationWhenInCall() {
        when(mCallList.getIncomingCall()).thenReturn(mIncomingCall);

        mInCallPresenter.onCallListChange(mCallList);

        verify(mStatusBarNotifier).updateNotification(InCallState.INCOMING, mCallList);
        verifyInCallActivityNotStarted();
    }

    /**
     * This behavior is required to ensure that the screen is turned on again by the restarting
     * activity.
     */
    public void testOnCallListChange_handlesCallWaitingWhenScreenOffShouldRestartActivity() {
        when(mCallList.getActiveCall()).thenReturn(mActiveCall);

        mInCallPresenter.onCallListChange(mCallList);
        mInCallPresenter.setActivity(mInCallActivity);

        // Pretend that there is a call waiting and the screen is off
        when(mInCallActivity.isDestroyed()).thenReturn(false);
        when(mInCallActivity.isFinishing()).thenReturn(false);
        when(mProximitySensor.isScreenReallyOff()).thenReturn(true);
        when(mCallList.getIncomingCall()).thenReturn(mIncomingCall);

        mInCallPresenter.onCallListChange(mCallList);
        verify(mInCallActivity).finish();
    }

    /**
     * Verifies that the PENDING_OUTGOING -> IN_CALL transition brings up InCallActivity so
     * that it can display an error dialog.
     */
    public void testOnCallListChange_pendingOutgoingToInCallTransitionShowsUiForErrorDialog() {
        when(mCallList.getPendingOutgoingCall()).thenReturn(mConnectingCall);

        mInCallPresenter.onCallListChange(mCallList);

        when(mCallList.getPendingOutgoingCall()).thenReturn(null);
        when(mCallList.getActiveCall()).thenReturn(mActiveCall);

        mInCallPresenter.onCallListChange(mCallList);

        verify(mContext).startActivity(InCallPresenter.getInstance().getInCallIntent(false, false));
        verifyIncomingCallNotificationNotSent();
    }

    /**
     * Verifies that if there is a call in the PRE_DIAL_WAIT state, InCallActivity is displayed
     * to display the account picker.
     */
    public void testOnCallListChange_noAccountProvidedForCallShowsUiForAccountPicker() {
        when(mCallList.getWaitingForAccountCall()).thenReturn(mWaitingForAccountsCall);
        mInCallPresenter.onCallListChange(mCallList);

        verify(mContext).startActivity(InCallPresenter.getInstance().getInCallIntent(false, false));
        verifyIncomingCallNotificationNotSent();
    }

    /**
     * Verifies that for an expected call state change (e.g. NO_CALLS -> PENDING_OUTGOING),
     * InCallActivity is not displayed.
     */
    public void testOnCallListChange_noCallsToPendingOutgoingDoesNotShowUi() {
        when(mCallList.getPendingOutgoingCall()).thenReturn(mConnectingCall);
        mInCallPresenter.onCallListChange(mCallList);

        verifyInCallActivityNotStarted();
        verifyIncomingCallNotificationNotSent();
    }

    public void testOnCallListChange_LastCallDisconnectedNoCallsLeftFinishesUi() {
        when(mCallList.getDisconnectedCall()).thenReturn(mDisconnectedCall);
        mInCallPresenter.onCallListChange(mCallList);

        mInCallPresenter.setActivity(mInCallActivity);

        verify(mInCallActivity, never()).finish();

        // Last remaining disconnected call is removed from CallList, activity should shut down.
        when(mCallList.getDisconnectedCall()).thenReturn(null);
        mInCallPresenter.onCallListChange(mCallList);
        verify(mInCallActivity).finish();
    }


    //TODO
    public void testCircularReveal_startsCircularRevealForOutgoingCalls() {

    }

    public void testCircularReveal_waitTillCircularRevealSentBeforeShowingCallCard() {
    }

    public void testHangupOngoingCall_disconnectsCallCorrectly() {
    }

    public void testAnswerIncomingCall() {
    }

    public void testDeclineIncomingCall() {
    }

    private void verifyInCallActivityNotStarted() {
        verify(mContext, never()).startActivity(Mockito.any(Intent.class));
    }

    private void verifyIncomingCallNotificationNotSent() {
        verify(mStatusBarNotifier, never()).updateNotification(Mockito.any(InCallState.class),
                Mockito.any(CallList.class));
    }

    private static Call getMockCall(int state) {
        return getMockCall(state, true);
    }

    private static Call getMockCall(int state, boolean hasAccountHandle) {
        final Call call = Mockito.mock(Call.class);
        when(call.getState()).thenReturn(Integer.valueOf(state));
        if (hasAccountHandle) {
            when(call.getAccountHandle()).thenReturn(new PhoneAccountHandle(null, null));
        }
        return call;
    }
}
