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

package com.android.dialer.service;

import android.support.annotation.Nullable;

/**
 * Manager of extended blocking events. It notifies all listeners of all blocking-related events.
 */
public interface ExtendedBlockingManager {

    interface ButtonRendererListener {
        void onBlockedNumber(String number, @Nullable String countryIso);
        void onUnblockedNumber(String number, @Nullable String countryIso);
    }

    void addButtonRendererListener(@Nullable ButtonRendererListener listener);

    void removeButtonRendererListener(@Nullable ButtonRendererListener listener);

    void notifyOnBlockedNumber(String number, @Nullable String countryIso);

    void notifyOnUnblockedNumber(String number, @Nullable String countryIso);
}
