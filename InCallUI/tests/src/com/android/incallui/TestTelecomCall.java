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

import com.google.common.base.Preconditions;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.telecom.DisconnectCause;
import android.telecom.GatewayInfo;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Wrapper class which uses reflection to create instances of {@link android.telecom.Call} for use
 * with unit testing.  Since {@link android.telecom.Call} is final, it cannot be mocked using
 * mockito, and since all setter methods are hidden, it is necessary to use reflection.  In the
 * future, it would be desirable to replace this if a different mocking solution is used.
 */
public class TestTelecomCall {

    private android.telecom.Call mCall;

    public static @Nullable TestTelecomCall createInstance(String callId,
            Uri handle,
            int handlePresentation,
            String callerDisplayName,
            int callerDisplayNamePresentation,
            PhoneAccountHandle accountHandle,
            int capabilities,
            int properties,
            DisconnectCause disconnectCause,
            long connectTimeMillis,
            GatewayInfo gatewayInfo,
            int videoState,
            StatusHints statusHints,
            Bundle extras,
            Bundle intentExtras) {

        try {
            // Phone and InCall adapter are @hide, so we cannot refer to them directly.
            Class<?> phoneClass = Class.forName("android.telecom.Phone");
            Class<?> incallAdapterClass = Class.forName("android.telecom.InCallAdapter");
            Class<?> callClass = android.telecom.Call.class;
            Constructor<?> cons = callClass
                    .getDeclaredConstructor(phoneClass, String.class, incallAdapterClass);
            cons.setAccessible(true);

            android.telecom.Call call = (android.telecom.Call) cons.newInstance(null, callId, null);

            // Create an instance of the call details.
            Class<?> callDetailsClass = android.telecom.Call.Details.class;
            Constructor<?> detailsCons = callDetailsClass.getDeclaredConstructor(
                    String.class, /* telecomCallId */
                    Uri.class, /* handle */
                    int.class, /* handlePresentation */
                    String.class, /* callerDisplayName */
                    int.class, /* callerDisplayNamePresentation */
                    PhoneAccountHandle.class, /* accountHandle */
                    int.class, /* capabilities */
                    int.class, /* properties */
                    DisconnectCause.class, /* disconnectCause */
                    long.class, /* connectTimeMillis */
                    GatewayInfo.class, /* gatewayInfo */
                    int.class, /* videoState */
                    StatusHints.class, /* statusHints */
                    Bundle.class, /* extras */
                    Bundle.class /* intentExtras */);
            detailsCons.setAccessible(true);

            android.telecom.Call.Details details = (android.telecom.Call.Details)
                    detailsCons.newInstance(callId, handle, handlePresentation, callerDisplayName,
                            callerDisplayNamePresentation, accountHandle, capabilities, properties,
                            disconnectCause, connectTimeMillis, gatewayInfo, videoState,
                            statusHints,
                            extras, intentExtras);

            // Finally, set this as the details of the call.
            Field detailsField = call.getClass().getDeclaredField("mDetails");
            detailsField.setAccessible(true);
            detailsField.set(call, details);

            return new TestTelecomCall(call);
        } catch (NoSuchMethodException nsm) {
            return null;
        } catch (ClassNotFoundException cnf) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        } catch (InstantiationException e) {
            return null;
        } catch (InvocationTargetException e) {
            return null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private TestTelecomCall(android.telecom.Call call) {
        mCall = call;
    }

    public android.telecom.Call getCall() {
        return mCall;
    }

    public void forceDetailsUpdate() {
        Preconditions.checkNotNull(mCall);

        try {
            Method method = mCall.getClass().getDeclaredMethod("fireDetailsChanged",
                    android.telecom.Call.Details.class);
            method.setAccessible(true);
            method.invoke(mCall, mCall.getDetails());
        } catch (NoSuchMethodException e) {
            Log.e(this, "forceDetailsUpdate", e);
        } catch (InvocationTargetException e) {
            Log.e(this, "forceDetailsUpdate", e);
        } catch (IllegalAccessException e) {
            Log.e(this, "forceDetailsUpdate", e);
        }
    }

    public void setCapabilities(int capabilities) {
        Preconditions.checkNotNull(mCall);
        try {
            Field field = mCall.getDetails().getClass().getDeclaredField("mCallCapabilities");
            field.setAccessible(true);
            field.set(mCall.getDetails(), capabilities);
        } catch (IllegalAccessException e) {
            Log.e(this, "setProperties", e);
        } catch (NoSuchFieldException e) {
            Log.e(this, "setProperties", e);
        }
    }

    public void setCall(android.telecom.Call call) {
        mCall = call;
    }
}
