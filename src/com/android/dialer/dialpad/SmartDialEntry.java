/*
 * Copyright (C) 2012 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.dialer.dialpad;

import android.net.Uri;

import java.util.ArrayList;

public class SmartDialEntry {
    /** Display name for the contact. */
    public final CharSequence displayName;
    public final Uri contactUri;
    public final CharSequence phoneNumber;

    public final ArrayList<SmartDialMatchPosition> matchPositions;
    public final SmartDialMatchPosition phoneNumberMatchPosition;

    public static final SmartDialEntry NULL = new SmartDialEntry("", Uri.EMPTY, "",
            new ArrayList<SmartDialMatchPosition>(), null);

    public SmartDialEntry(CharSequence displayName, Uri contactUri, CharSequence phoneNumber,
            ArrayList<SmartDialMatchPosition> matchPositions,
            SmartDialMatchPosition phoneNumberMatchPosition) {
        this.displayName = displayName;
        this.contactUri = contactUri;
        this.matchPositions = matchPositions;
        this.phoneNumber = phoneNumber;
        this.phoneNumberMatchPosition = phoneNumberMatchPosition;
    }
}
