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

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.telecom.PhoneAccountHandle;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashSet;

/**
 * Provides an instance of a mock CallList, and provides utility methods to put the CallList into
 * various states (e.g. incoming call, active call, call waiting).
 */
public class MockCallListWrapper {
    private CallList mCallList;
    private HashSet<Integer> mCallSet = new HashSet<>();

    public MockCallListWrapper() {
        mCallList = Mockito.mock(CallList.class);
        mCallList = spy(new CallList());
        when(mCallList.getFirstCallWithState(anyInt())).thenAnswer(new Answer<Call>() {
            @Override
            public Call answer(InvocationOnMock i) throws Throwable {
                Object[] args = i.getArguments();
                final int state = (int) args[0];
                if (mCallSet.contains(state)) {
                    return getMockCall(state);
                } else {
                    return null;
                }
            }
        });
    }

    public CallList getCallList() {
        return mCallList;
    }

    public void setHasCall(int state, boolean hasCall) {
        if (hasCall) {
            mCallSet.add(state);
        } else {
            mCallSet.remove(state);
        }
    }

    private static Call getMockCall(int state) {
        return getMockCall(state, state != Call.State.SELECT_PHONE_ACCOUNT);
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
