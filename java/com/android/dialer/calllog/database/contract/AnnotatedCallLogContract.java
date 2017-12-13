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
import android.provider.BaseColumns;
import com.android.dialer.constants.Constants;
import java.util.Arrays;

/** Contract for the AnnotatedCallLog content provider. */
public class AnnotatedCallLogContract {
  public static final String AUTHORITY = Constants.get().getAnnotatedCallLogProviderAuthority();

  public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

  /**
   * Columns shared by {@link AnnotatedCallLog} and {@link CoalescedAnnotatedCallLog}.
   *
   * <p>When adding columns be sure to update {@link #ALL_COMMON_COLUMNS}.
   */
  interface CommonColumns extends BaseColumns {

    /**
     * Timestamp of the entry, in milliseconds.
     *
     * <p>Type: INTEGER (long)
     */
    String TIMESTAMP = "timestamp";

    /**
     * The name (which may be a person's name or business name, but not a number) formatted exactly
     * as it should appear to the user. If the user's locale or name display preferences change,
     * this column should be rewritten.
     *
     * <p>Type: TEXT
     */
    String NAME = "name";

    /**
     * The phone number called or number the call came from, encoded as a {@link
     * com.android.dialer.DialerPhoneNumber} proto. The number may be empty if it was an incoming
     * call and the number was unknown.
     *
     * <p>Type: BLOB
     */
    String NUMBER = "number";

    /**
     * The number formatted as it should be displayed to the user. Note that it may not always be
     * displayed, for example if the number has a corresponding person or business name.
     *
     * <p>Type: TEXT
     */
    String FORMATTED_NUMBER = "formatted_number";

    /**
     * A photo URI for the contact to display in the call log list view.
     *
     * <p>TYPE: TEXT
     */
    String PHOTO_URI = "photo_uri";

    /**
     * A photo ID (from the contacts provider) for the contact to display in the call log list view.
     *
     * <p>Type: INTEGER (long)
     */
    String PHOTO_ID = "photo_id";

    /**
     * The contacts provider lookup URI for the contact associated with the call.
     *
     * <p>TYPE: TEXT
     */
    String LOOKUP_URI = "lookup_uri";

    // TODO(zachh): If we need to support photos other than local contacts', add a (blob?) column.

    /**
     * The number type as a string to be displayed to the user, for example "Home" or "Mobile".
     *
     * <p>This column should be updated for the appropriate language when the locale changes.
     *
     * <p>TYPE: TEXT
     */
    String NUMBER_TYPE_LABEL = "number_type_label";

    /**
     * See {@link android.provider.CallLog.Calls#IS_READ}.
     *
     * <p>TYPE: INTEGER (boolean)
     */
    String IS_READ = "is_read";

    /**
     * See {@link android.provider.CallLog.Calls#NEW}.
     *
     * <p>Type: INTEGER (boolean)
     */
    String NEW = "new";

    /**
     * See {@link android.provider.CallLog.Calls#GEOCODED_LOCATION}.
     *
     * <p>TYPE: TEXT
     */
    String GEOCODED_LOCATION = "geocoded_location";

    /**
     * See {@link android.provider.CallLog.Calls#PHONE_ACCOUNT_COMPONENT_NAME}.
     *
     * <p>TYPE: TEXT
     */
    String PHONE_ACCOUNT_COMPONENT_NAME = "phone_account_component_name";

    /**
     * See {@link android.provider.CallLog.Calls#PHONE_ACCOUNT_ID}.
     *
     * <p>TYPE: TEXT
     */
    String PHONE_ACCOUNT_ID = "phone_account_id";

    /**
     * String suitable for display which indicates the phone account used to make the call.
     *
     * <p>TYPE: TEXT
     */
    String PHONE_ACCOUNT_LABEL = "phone_account_label";

    /**
     * The color int for the phone account.
     *
     * <p>TYPE: INTEGER (int)
     */
    String PHONE_ACCOUNT_COLOR = "phone_account_color";

    /**
     * See {@link android.provider.CallLog.Calls#FEATURES}.
     *
     * <p>TYPE: INTEGER (int)
     */
    String FEATURES = "features";

    /**
     * True if a caller ID data source informed us that this is a business number. This is used to
     * determine if a generic business avatar should be shown vs. a generic person avatar.
     *
     * <p>TYPE: INTEGER (boolean)
     */
    String IS_BUSINESS = "is_business";

    /**
     * True if this was a call to voicemail. This is used to determine if the voicemail avatar
     * should be displayed.
     *
     * <p>TYPE: INTEGER (boolean)
     */
    String IS_VOICEMAIL = "is_voicemail";

    /**
     * Copied from {@link android.provider.CallLog.Calls#TYPE}.
     *
     * <p>Type: INTEGER (int)
     */
    String CALL_TYPE = "call_type";

    String[] ALL_COMMON_COLUMNS =
        new String[] {
          _ID,
          TIMESTAMP,
          NAME,
          NUMBER,
          FORMATTED_NUMBER,
          PHOTO_URI,
          PHOTO_ID,
          LOOKUP_URI,
          NUMBER_TYPE_LABEL,
          IS_READ,
          NEW,
          GEOCODED_LOCATION,
          PHONE_ACCOUNT_COMPONENT_NAME,
          PHONE_ACCOUNT_ID,
          PHONE_ACCOUNT_LABEL,
          PHONE_ACCOUNT_COLOR,
          FEATURES,
          IS_BUSINESS,
          IS_VOICEMAIL,
          CALL_TYPE
        };
  }

  /**
   * AnnotatedCallLog table.
   *
   * <p>This contains all of the non-coalesced call log entries.
   */
  public static final class AnnotatedCallLog implements CommonColumns {

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
     * See {@link android.provider.CallLog.Calls#VOICEMAIL_URI}.
     *
     * <p>TYPE: TEXT
     */
    public static final String VOICEMAIL_URI = "voicemail_uri";
  }

  /**
   * Coalesced view of the AnnotatedCallLog table.
   *
   * <p>This is an in-memory view of the {@link AnnotatedCallLog} with some adjacent entries
   * collapsed.
   *
   * <p>When adding columns be sure to update {@link #COLUMNS_ONLY_IN_COALESCED_CALL_LOG}.
   */
  public static final class CoalescedAnnotatedCallLog implements CommonColumns {

    public static final String TABLE = "CoalescedAnnotatedCallLog";

    /** The content URI for this table. */
    public static final Uri CONTENT_URI =
        Uri.withAppendedPath(AnnotatedCallLogContract.CONTENT_URI, TABLE);

    /** The MIME type of a {@link android.content.ContentProvider#getType(Uri)} single entry. */
    public static final String CONTENT_ITEM_TYPE =
        "vnd.android.cursor.item/coalesced_annotated_call_log";

    /**
     * IDs of rows in {@link AnnotatedCallLog} that are coalesced into one row in {@link
     * CoalescedAnnotatedCallLog}, encoded as a {@link com.android.dialer.CoalescedIds} proto.
     *
     * <p>Type: BLOB
     */
    public static final String COALESCED_IDS = "coalesced_ids";

    /**
     * Columns that are only in the {@link CoalescedAnnotatedCallLog} but not the {@link
     * AnnotatedCallLog}.
     */
    private static final String[] COLUMNS_ONLY_IN_COALESCED_CALL_LOG = new String[] {COALESCED_IDS};

    /** All columns in the {@link CoalescedAnnotatedCallLog}. */
    public static final String[] ALL_COLUMNS =
        concat(ALL_COMMON_COLUMNS, COLUMNS_ONLY_IN_COALESCED_CALL_LOG);
  }

  private static String[] concat(String[] first, String[] second) {
    String[] result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }
}
