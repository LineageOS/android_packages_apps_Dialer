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
package com.android.incallui.ringtone;

import android.net.Uri;
import android.support.annotation.Nullable;

import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.incallui.Call.State;

/**
 * Class that determines when ringtones should be played and can play the call waiting tone when
 * necessary.
 */
public class DialerRingtoneManager {

    /*
     * Flag used to determine if the Dialer is responsible for playing ringtones for incoming calls.
     */
    private static final boolean IS_DIALER_RINGING_ENABLED = false;
    private boolean mForceDialerRingingEnabled = false;

    /**
     * Determines if a ringtone should be played for the given call state (see {@link State}) and
     * {@link Uri}.
     *
     * @param callState the call state for the call being checked.
     * @param ringtoneUri the ringtone to potentially play.
     * @return {@code true} if the ringtone should be played, {@code false} otherwise.
     */
    public boolean shouldPlayRingtone(int callState, @Nullable Uri ringtoneUri) {
        return CompatUtils.isNCompatible()
                && isDialerRingingEnabled()
                && callState == State.INCOMING
                && ringtoneUri != null;
    }

    private boolean isDialerRingingEnabled() {
        return mForceDialerRingingEnabled || IS_DIALER_RINGING_ENABLED;
    }

    @NeededForTesting
    void forceDialerRingingEnabled() {
        mForceDialerRingingEnabled = true;
    }
}
