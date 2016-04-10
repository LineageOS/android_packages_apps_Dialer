/*
 * Copyright (C) 2016 The Android Open Source Project
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


package com.android.incallui.spam;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.any;

import android.content.ContentValues;
import android.content.Context;
import android.provider.CallLog;
import android.telecom.DisconnectCause;
import android.test.InstrumentationTestCase;

import com.android.contacts.common.test.mocks.ContactsMockContext;
import com.android.contacts.common.test.mocks.MockContentProvider;
import com.android.dialer.calllog.CallLogAsyncTaskUtil;
import com.android.dialer.util.AsyncTaskExecutors;
import com.android.dialer.util.FakeAsyncTaskExecutor;
import com.android.dialer.util.TelecomUtil;
import com.android.incallui.Call;
import com.android.incallui.Call.CallHistoryStatus;
import com.android.incallui.Call.LogState;

public class SpamCallListListenerTest extends InstrumentationTestCase {
    private static final String NUMBER = "+18005657862";
    private static final int DURATION = 100;

    private TestSpamCallListListener mListener;
    private FakeAsyncTaskExecutor mFakeAsyncTaskExecutor;
    private ContactsMockContext mContext;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mContext = new ContactsMockContext(getInstrumentation().getContext(), CallLog.AUTHORITY);
        mListener = new TestSpamCallListListener(mContext);
        mFakeAsyncTaskExecutor = new FakeAsyncTaskExecutor(getInstrumentation());
        AsyncTaskExecutors.setFactoryForTest(mFakeAsyncTaskExecutor.getFactory());
    }

    @Override
    public void tearDown() throws Exception {
        AsyncTaskExecutors.setFactoryForTest(null);
        CallLogAsyncTaskUtil.resetForTest();
        super.tearDown();
    }

    public void testOutgoingCall() {
        Call call = getMockCall(NUMBER, false, 0, Call.CALL_HISTORY_STATUS_NOT_PRESENT,
                LogState.LOOKUP_UNKNOWN, DisconnectCause.REMOTE);
        mListener.onDisconnect(call);
        assertFalse(mListener.mShowNotificationCalled);
    }

    public void testIncomingCall_UnknownNumber() {
        Call call = getMockCall(null, true, DURATION, Call.CALL_HISTORY_STATUS_NOT_PRESENT,
                LogState.LOOKUP_UNKNOWN, DisconnectCause.REMOTE);
        mListener.onDisconnect(call);
        assertFalse(mListener.mShowNotificationCalled);
    }

    public void testIncomingCall_Rejected() {
        Call call = getMockCall(NUMBER, true, 0, Call.CALL_HISTORY_STATUS_NOT_PRESENT,
                LogState.LOOKUP_UNKNOWN, DisconnectCause.REJECTED);
        mListener.onDisconnect(call);
        assertFalse(mListener.mShowNotificationCalled);
    }
    public void testIncomingCall_HangUpLocal() {
        Call call = getMockCall(NUMBER, true, DURATION, Call.CALL_HISTORY_STATUS_NOT_PRESENT,
                LogState.LOOKUP_UNKNOWN, DisconnectCause.LOCAL);
        mListener.onDisconnect(call);
        assertTrue(mListener.mShowNotificationCalled);
    }

    public void testIncomingCall_HangUpRemote() {
        Call call = getMockCall(NUMBER, true, DURATION, Call.CALL_HISTORY_STATUS_NOT_PRESENT,
                LogState.LOOKUP_UNKNOWN, DisconnectCause.REMOTE);
        mListener.onDisconnect(call);
        assertTrue(mListener.mShowNotificationCalled);
    }

    public void testIncomingCall_ValidNumber_NotInCallHistory_InContacts() {
        Call call = getMockCall(NUMBER, true, 0, Call.CALL_HISTORY_STATUS_NOT_PRESENT,
                LogState.LOOKUP_LOCAL_CONTACT, DisconnectCause.REJECTED);
        mListener.onDisconnect(call);
        assertFalse(mListener.mShowNotificationCalled);
    }

    public void testIncomingCall_ValidNumber_InCallHistory_InContacts() {
        Call call = getMockCall(NUMBER, true, 0, Call.CALL_HISTORY_STATUS_PRESENT,
                LogState.LOOKUP_LOCAL_CONTACT, DisconnectCause.REJECTED);
        mListener.onDisconnect(call);
        assertFalse(mListener.mShowNotificationCalled);
    }

    public void testIncomingCall_ValidNumber_InCallHistory_NotInContacts() {
        Call call = getMockCall(NUMBER, true, 0, Call.CALL_HISTORY_STATUS_PRESENT,
                LogState.LOOKUP_UNKNOWN, DisconnectCause.REJECTED);
        mListener.onDisconnect(call);
        assertFalse(mListener.mShowNotificationCalled);
    }

    public void testIncomingCall_ValidNumber_NotInCallHistory_NotInContacts() throws Throwable {
        Call call = getMockCall(NUMBER, true, DURATION, Call.CALL_HISTORY_STATUS_NOT_PRESENT,
                LogState.LOOKUP_UNKNOWN, DisconnectCause.LOCAL);
        mListener.onDisconnect(call);
        assertTrue(mListener.mShowNotificationCalled);
    }

    public void testIncomingCall_CheckCallHistory_NumberExists() throws Throwable {
        final Call call = getMockCall(NUMBER, true, DURATION, Call.CALL_HISTORY_STATUS_UNKNOWN,
                LogState.LOOKUP_UNKNOWN, DisconnectCause.LOCAL);
        expectCallLogQuery(NUMBER, true);
        incomingCall(call);
        verify(call).setCallHistoryStatus(eq(Call.CALL_HISTORY_STATUS_PRESENT));
        assertFalse(mListener.mShowNotificationCalled);
    }

    public void testIncomingCall_CheckCallHistory_NumberNotExists() throws Throwable {
        final Call call = getMockCall(NUMBER, true, DURATION, Call.CALL_HISTORY_STATUS_UNKNOWN,
                LogState.LOOKUP_UNKNOWN, DisconnectCause.LOCAL);
        expectCallLogQuery(NUMBER, false);
        incomingCall(call);
        verify(call).setCallHistoryStatus(eq(Call.CALL_HISTORY_STATUS_NOT_PRESENT));
        assertTrue(mListener.mShowNotificationCalled);
    }

    private void incomingCall(final Call call) throws Throwable {
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mListener.onIncomingCall(call);
            }
        });
        getInstrumentation().waitForIdleSync();
        mFakeAsyncTaskExecutor.runTask(CallLogAsyncTaskUtil.Tasks.GET_NUMBER_IN_CALL_HISTORY);
        mListener.onDisconnect(call);
    }

    private void expectCallLogQuery(String number, boolean inCallHistory) {
        MockContentProvider.Query query = mContext.getContactsProvider()
                .expectQuery(TelecomUtil.getCallLogUri(mContext))
                .withSelection(CallLog.Calls.NUMBER + " = ?", number)
                .withProjection(CallLog.Calls._ID)
                .withAnySortOrder();
        ContentValues values = new ContentValues();
        values.put(CallLog.Calls.NUMBER, number);
        if (inCallHistory) {
            query.returnRow(values);
        } else {
            query.returnEmptyCursor();
        }
    }

    private static Call getMockCall(String number,
                                    boolean isIncoming,
                                    int duration,
                                    @CallHistoryStatus int callHistoryStatus,
                                    int contactLookupResult,
                                    int disconnectCause) {
        Call call = mock(Call.class);
        LogState logState = new LogState();
        logState.isIncoming = isIncoming;
        logState.duration = duration;
        logState.contactLookupResult = contactLookupResult;
        when(call.getDisconnectCause()).thenReturn(new DisconnectCause(disconnectCause));
        when(call.getLogState()).thenReturn(logState);
        when(call.getNumber()).thenReturn(number);
        doCallRealMethod().when(call).setCallHistoryStatus(anyInt());
        when(call.getCallHistoryStatus()).thenCallRealMethod();
        call.setCallHistoryStatus(callHistoryStatus);
        return call;
    }

    private static class TestSpamCallListListener extends SpamCallListListener {
        private boolean mShowNotificationCalled;

        public TestSpamCallListListener(Context context) {
            super(context);
        }

        void showNotification(String number) {
            mShowNotificationCalled = true;
        }
    }
}