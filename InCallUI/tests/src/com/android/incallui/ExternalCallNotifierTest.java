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

package com.android.incallui;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.contacts.common.preference.ContactsPreferences;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.telecom.*;
import android.telecom.Call;
import android.telephony.TelephonyManager;
import android.test.AndroidTestCase;
import android.test.mock.MockContext;

/**
 * Unit tests for {@link ExternalCallNotifier}.
 */
public class ExternalCallNotifierTest extends AndroidTestCase {
    private static final int TIMEOUT_MILLIS = 5000;
    private static final String NAME_PRIMARY = "Full Name";
    private static final String NAME_ALTERNATIVE = "Name, Full";
    private static final String LOCATION = "US";
    private static final String NUMBER = "6505551212";

    @Mock private ContactsPreferences mContactsPreferences;
    @Mock private NotificationManager mNotificationManager;
    @Mock private MockContext mMockContext;
    @Mock private Resources mResources;
    @Mock private StatusBarNotifier mStatusBarNotifier;
    @Mock private ContactInfoCache mContactInfoCache;
    @Mock private TelecomManager mTelecomManager;
    @Mock private TelephonyManager mTelephonyManager;
    @Mock private ProximitySensor mProximitySensor;
    @Mock private CallList mCallList;
    private InCallPresenter mInCallPresenter;
    private ExternalCallNotifier mExternalCallNotifier;
    private ContactInfoCache.ContactCacheEntry mContactInfo;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);

        when(mContactsPreferences.getDisplayOrder())
                .thenReturn(ContactsPreferences.DISPLAY_ORDER_PRIMARY);

        // Setup the mock context to return mocks for some of the needed services; the notification
        // service is especially important as we want to be able to intercept calls into it and
        // validate the notifcations.
        when(mMockContext.getSystemService(eq(Context.NOTIFICATION_SERVICE)))
                .thenReturn(mNotificationManager);
        when(mMockContext.getSystemService(eq(Context.TELECOM_SERVICE)))
                .thenReturn(mTelecomManager);
        when(mMockContext.getSystemService(eq(Context.TELEPHONY_SERVICE)))
                .thenReturn(mTelephonyManager);

        // These aspects of the context are used by the notification builder to build the actual
        // notification; we will rely on the actual implementations of these.
        when(mMockContext.getPackageManager()).thenReturn(mContext.getPackageManager());
        when(mMockContext.getResources()).thenReturn(mContext.getResources());
        when(mMockContext.getApplicationInfo()).thenReturn(mContext.getApplicationInfo());
        when(mMockContext.getContentResolver()).thenReturn(mContext.getContentResolver());
        when(mMockContext.getPackageName()).thenReturn(mContext.getPackageName());

        ContactsPreferencesFactory.setTestInstance(null);
        mExternalCallNotifier = new ExternalCallNotifier(mMockContext, mContactInfoCache);

        // We don't directly use the InCallPresenter in the test, or even in ExternalCallNotifier
        // itself.  However, ExternalCallNotifier needs to make instances of
        // com.android.incallui.Call for the purpose of performing contact cache lookups.  The
        // Call class depends on the static InCallPresenter for a number of things, so we need to
        // set it up here to prevent crashes.
        mInCallPresenter = InCallPresenter.getInstance();
        mInCallPresenter.setUp(mMockContext, mCallList, new ExternalCallList(),
                null, mStatusBarNotifier, mExternalCallNotifier, mContactInfoCache,
                mProximitySensor);

        // Unlocked all contact info is available
        mContactInfo = new ContactInfoCache.ContactCacheEntry();
        mContactInfo.namePrimary = NAME_PRIMARY;
        mContactInfo.nameAlternative = NAME_ALTERNATIVE;
        mContactInfo.location = LOCATION;
        mContactInfo.number = NUMBER;

        // Given the mock ContactInfoCache cache, we need to mock out what happens when the
        // ExternalCallNotifier calls into the contact info cache to do a lookup.  We will always
        // return mock info stored in mContactInfo.
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                com.android.incallui.Call call = (com.android.incallui.Call) args[0];
                ContactInfoCache.ContactInfoCacheCallback callback
                        = (ContactInfoCache.ContactInfoCacheCallback) args[2];
                callback.onContactInfoComplete(call.getId(), mContactInfo);
                return null;
            }
        }).when(mContactInfoCache).findInfo(any(com.android.incallui.Call.class), anyBoolean(),
                any(ContactInfoCache.ContactInfoCacheCallback.class));
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        ContactsPreferencesFactory.setTestInstance(null);
        mInCallPresenter.tearDown();
    }

    public void testPostNonPullable() {
        TestTelecomCall call = getTestCall(false);
        mExternalCallNotifier.onExternalCallAdded(call.getCall());
        Notification notification = verifyNotificationPosted();
        assertNull(notification.actions);
    }

    public void testPostPullable() {
        TestTelecomCall call = getTestCall(true);
        mExternalCallNotifier.onExternalCallAdded(call.getCall());
        Notification notification = verifyNotificationPosted();
        assertEquals(1, notification.actions.length);
    }

    public void testNotificationDismissed() {
        TestTelecomCall call = getTestCall(false);
        mExternalCallNotifier.onExternalCallAdded(call.getCall());
        verifyNotificationPosted();

        mExternalCallNotifier.onExternalCallRemoved(call.getCall());
        verify(mNotificationManager, timeout(TIMEOUT_MILLIS)).cancel(eq("EXTERNAL_CALL"), eq(0));
    }

    public void testNotificationUpdated() {
        TestTelecomCall call = getTestCall(false);
        mExternalCallNotifier.onExternalCallAdded(call.getCall());
        verifyNotificationPosted();

        call.setCapabilities(android.telecom.Call.Details.CAPABILITY_CAN_PULL_CALL);
        mExternalCallNotifier.onExternalCallUpdated(call.getCall());

        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);
        verify(mNotificationManager, timeout(TIMEOUT_MILLIS).times(2))
                .notify(eq("EXTERNAL_CALL"), eq(0), notificationCaptor.capture());
        Notification notification1 = notificationCaptor.getAllValues().get(0);
        assertNull(notification1.actions);
        Notification notification2 = notificationCaptor.getAllValues().get(1);
        assertEquals(1, notification2.actions.length);
    }

    private Notification verifyNotificationPosted() {
        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);
        verify(mNotificationManager, timeout(TIMEOUT_MILLIS))
                .notify(eq("EXTERNAL_CALL"), eq(0), notificationCaptor.capture());
        return notificationCaptor.getValue();
    }

    private TestTelecomCall getTestCall(boolean canPull) {
        TestTelecomCall testCall = TestTelecomCall.createInstance(
                "1",
                Uri.parse("tel:650-555-1212"), /* handle */
                TelecomManager.PRESENTATION_ALLOWED, /* handlePresentation */
                "Joe", /* callerDisplayName */
                TelecomManager.PRESENTATION_ALLOWED, /* callerDisplayNamePresentation */
                new PhoneAccountHandle(new ComponentName("test", "class"),
                        "handle"), /* accountHandle */
                canPull ? android.telecom.Call.Details.CAPABILITY_CAN_PULL_CALL : 0, /* capabilities */
                Call.Details.PROPERTY_IS_EXTERNAL_CALL, /* properties */
                null, /* disconnectCause */
                0, /* connectTimeMillis */
                null, /* GatewayInfo */
                VideoProfile.STATE_AUDIO_ONLY, /* videoState */
                null, /* statusHints */
                null, /* extras */
                null /* intentExtras */);
        return testCall;
    }
}
