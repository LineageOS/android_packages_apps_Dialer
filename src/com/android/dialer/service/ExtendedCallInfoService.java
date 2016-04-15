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

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Interface of service to get extended call information.
 */
public interface ExtendedCallInfoService {
    /**
     * All the possible locations that a user can report a number as spam or not spam.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({REPORTING_LOCATION_UNKNOWN, REPORTING_LOCATION_CALL_LOG_HISTORY,
            REPORTING_LOCATION_FEEDBACK_PROMPT})
    @interface ReportingLocation {}
    int REPORTING_LOCATION_UNKNOWN = 0;
    int REPORTING_LOCATION_CALL_LOG_HISTORY = 1;
    int REPORTING_LOCATION_FEEDBACK_PROMPT = 2;

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
     * @param countryIso The country ISO of the call.
     * @param listener The callback to be invoked after {@code Info} is fetched.
     */
    void getExtendedCallInfo(String number, String countryIso, Listener listener);

    /**
     * Reports number as spam.
     * @param number The number to be reported.
     * @param countryIso The country ISO of the number.
     * @param callType    Whether the type of call is missed, voicemail, etc. Example of this is
     *                    {@link android.provider.CallLog.Calls#VOICEMAIL_TYPE}.
     * @param from Where in the dialer this was reported from.
     *             Must be one of {@link ReportingLocation}.
     */
    void reportSpam(String number, String countryIso, int callType, @ReportingLocation int from);

    /**
     * Reports number as not spam.
     * @param number The number to be reported.
     * @param countryIso The country ISO of the number.
     * @param callType    Whether the type of call is missed, voicemail, etc. Example of this is
     *                    {@link android.provider.CallLog.Calls#VOICEMAIL_TYPE}.
     * @param from Where in the dialer this was reported from.
     *             Must be one of {@link ReportingLocation}.
     */
    void reportNotSpam(String number, String countryIso, int callType, @ReportingLocation int from);
}
