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

package com.android.dialer.compat;

import android.content.Context;
import android.net.Uri;

public class BlockedNumbersSdkCompat {

    public static final Uri CONTENT_URI = null;

    public static final String _ID = null;

    public static final String COLUMN_ORIGINAL_NUMBER = null;

    public static final String E164_NUMBER = null;

    public static boolean canCurrentUserBlockNumbers(Context context) {
        return false;
    }
}
