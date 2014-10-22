/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.incallui;

import android.telecom.Phone;

/**
 * Interface implemented by In-Call components that maintain a reference to the Telecomm API
 * {@code Phone} object. Clarifies the expectations associated with the relevant method calls.
 */
public interface InCallPhoneListener {

    /**
     * Called once at {@code InCallService} startup time with a valid {@code Phone}. At
     * that time, there will be no existing {@code Call}s.
     *
     * @param phone The {@code Phone} object.
     */
    void setPhone(Phone phone);

    /**
     * Called once at {@code InCallService} shutdown time. At that time, any {@code Call}s
     * will have transitioned through the disconnected state and will no longer exist.
     */
    void clearPhone();
}
