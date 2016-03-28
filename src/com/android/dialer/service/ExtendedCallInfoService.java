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
 * limitations under the License.
 */

package com.android.dialer.service;

/**
 * Interface of service to get extended call information.
 */
public interface ExtendedCallInfoService {

    /**
     * Interface for a callback to be invoked when data is fetched.
     */
    interface Listener {
        /**
         * Called when data is fetched.
         * @param isSpam True if the call is spam.
         */
        void onComplete(boolean isSpam);
    }

    /**
     * Gets extended call information.
     * @param number The phone number of the call.
     * @param listener The callback to be invoked after {@code Info} is fetched.
     */
    void getExtendedCallInfo(String number, Listener listener);
}
