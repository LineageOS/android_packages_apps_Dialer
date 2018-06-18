/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.calllog.database.contract;

import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;
import com.android.dialer.compat.android.provider.VoicemailCompat;
import com.android.dialer.constants.Constants;

/** Contract for the AnnotatedCallLog content provider. */
public class AnnotatedCallLogContract {
  public static final String AUTHORITY = Constants.get().getAnnotatedCallLogProviderAuthority();

  public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

  /** AnnotatedCallLog table. */
  public static final class AnnotatedCallLog implements BaseColumns {

    public static final String TABLE = "AnnotatedCallLog";
    public static final String DISTINCT_PHONE_NUMBERS = "DistinctPhoneNumbers";

    /** The content URI for this table. */
    public static final Uri CONTENT_URI =
        Uri.withAppendedPath(AnnotatedCallLogContract.CONTENT_URI, TABLE);

    /** Content URI for selecting the distinct phone numbers from the AnnotatedCallLog. */
    public static final Uri DISTINCT_NUMBERS_CONTENT_URI =
        Uri.withAppendedPath(AnnotatedCallLogContract.CONTENT_URI, DISTINCT_PHONE_NUMBERS);

    /** The MIME type of a {@link android.content.ContentProvider#getType(Uri)} single entry. */
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/annotated_call_log";

    /**
     * Timestamp of the entry, in milliseconds.
     *
     * <p>Type: INTEGER (long)
     */
    public static final String TIMESTAMP = "timestamp";

    /**
     * The phone number called or number the call came from, encoded as a {@link
     * com.android.dialer.DialerPhoneNumber} proto. The number may be empty if it was an incoming
     * call and the number was unknown.
     *
     * <p>Type: BLOB
     */
    public static final String NUMBER = "number";

    /**
     * The number formatted as it should be displayed to the user. Note that it may not always be
     * displayed, for example if the number has a corresponding person or business name.
     *
     * <p>Type: TEXT
     */
    public static final String FORMATTED_NUMBER = "formatted_number";

    /**
     * See {@link android.provider.CallLog.Calls#NUMBER_PRESENTATION}.
     *
     * <p>Type: INTEGER (int)
     */
    public static final String NUMBER_PRESENTATION = "presentation";

    /**
     * See {@link android.provider.CallLog.Calls#IS_READ}.
     *
     * <p>TYPE: INTEGER (boolean)
     */
    public static final String IS_READ = "is_read";

    /**
     * See {@link android.provider.CallLog.Calls#NEW}.
     *
     * <p>Type: INTEGER (boolean)
     */
    public static final String NEW = "new";

    /**
     * See {@link android.provider.CallLog.Calls#GEOCODED_LOCATION}.
     *
     * <p>TYPE: TEXT
     */
    public static final String GEOCODED_LOCATION = "geocoded_location";

    /**
     * See {@link android.provider.CallLog.Calls#PHONE_ACCOUNT_COMPONENT_NAME}.
     *
     * <p>TYPE: TEXT
     */
    public static final String PHONE_ACCOUNT_COMPONENT_NAME = "phone_account_component_name";

    /**
     * See {@link android.provider.CallLog.Calls#PHONE_ACCOUNT_ID}.
     *
     * <p>TYPE: TEXT
     */
    public static final String PHONE_ACCOUNT_ID = "phone_account_id";

    /**
     * See {@link android.provider.CallLog.Calls#FEATURES}.
     *
     * <p>TYPE: INTEGER (int)
     */
    public static final String FEATURES = "features";

    /**
     * Additional attributes about the number.
     *
     * <p>TYPE: BLOB
     *
     * @see com.android.dialer.NumberAttributes
     */
    public static final String NUMBER_ATTRIBUTES = "number_attributes";

    /**
     * Whether the call is to the voicemail inbox.
     *
     * <p>TYPE: INTEGER (boolean)
     *
     * @see android.telecom.TelecomManager#isVoiceMailNumber(android.telecom.PhoneAccountHandle,
     *     String)
     */
    public static final String IS_VOICEMAIL_CALL = "is_voicemail_call";

    /**
     * The "name" of the voicemail inbox. This is provided by the SIM to show as the caller ID
     *
     * <p>TYPE: TEXT
     *
     * @see android.telephony.TelephonyManager#getVoiceMailAlphaTag()
     */
    public static final String VOICEMAIL_CALL_TAG = "voicemail_call_tag";

    /**
     * Copied from {@link android.provider.CallLog.Calls#TYPE}.
     *
     * <p>Type: INTEGER (int)
     */
    public static final String CALL_TYPE = "call_type";

    /**
     * See {@link android.provider.CallLog.Calls#DATA_USAGE}.
     *
     * <p>Type: INTEGER (long)
     */
    public static final String DATA_USAGE = "data_usage";

    /**
     * See {@link android.provider.CallLog.Calls#DURATION}.
     *
     * <p>TYPE: INTEGER (long)
     */
    public static final String DURATION = "duration";

    /**
     * See {@link android.provider.CallLog.Calls#TRANSCRIPTION}.
     *
     * <p>TYPE: TEXT
     */
    public static final String TRANSCRIPTION = "transcription";

    /**
     * See {@link VoicemailCompat#TRANSCRIPTION_STATE}
     *
     * <p>Only populated in {@link Build.VERSION_CODES#O} and above
     *
     * <p>TYPE: INTEGER
     */
    public static final String TRANSCRIPTION_STATE = "transcription_state";

    /**
     * See {@link android.provider.CallLog.Calls#VOICEMAIL_URI}.
     *
     * <p>TYPE: TEXT
     */
    public static final String VOICEMAIL_URI = "voicemail_uri";

    /**
     * An unique id to associate this call log row to a {@link android.telecom.Call}.
     *
     * <p>For pre-Q device, this is same as {@link #TIMESTAMP}.
     *
     * <p>For Q+ device, this will be copied from {@link android.provider.CallLog.Calls}.
     *
     * <p>Type: TEXT
     */
    public static final String CALL_MAPPING_ID = "call_mapping_id";
  }
}
