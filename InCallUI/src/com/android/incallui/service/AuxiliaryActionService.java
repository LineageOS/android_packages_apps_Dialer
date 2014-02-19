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
 * limitations under the License
 */

package com.android.incallui.service;

import android.graphics.drawable.Drawable;

/**
 * Generic service that allows the user to perform an action from within the in call UI.
 * If the service is implemented then a button is added to the InCallUI. The button is visible if
 * AuxiliaryActionService.isActionEnabled() returns true and hidden otherwise. If this service
 * is not implemented then the button is always hidden.
 */
public interface AuxiliaryActionService {
    /**
     * Client of the service.
     */
    public interface Client {
        /**
         * Called when the action's enabled state may have changed.
         */
        public void onAuxiliaryActionStateChanged();
    }

    /**
     * Sets the client.
     */
    public void setClient(Client client);

    /**
     * Sets the remote phone number.
     */
    public void setRemotePhoneNumber(String remotePhoneNumber);

    /**
     * Gets the action's description.
     *
     * @return the description.
     */
    public String getActionDescription();

    /**
     * Gets the action's drawable.
     *
     * @return the drawable.
     */
    public Drawable getActionDrawable();

    /**
     * Checks if the auxiliary action is enabled.
     *
     * @return true if the action is enabled, otherwise false.
     */
    public boolean isActionEnabled();

    /**
     * Triggers the action for the auxiliary service.
     */
    public void performAction();
}
