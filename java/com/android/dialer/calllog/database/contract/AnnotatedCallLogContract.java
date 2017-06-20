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
     * Name to display for the entry.
     *
     * <p>Type: TEXT
     */
    String CONTACT_NAME = "contact_name";

    String[] ALL_COMMON_COLUMNS = new String[] {_ID, TIMESTAMP, CONTACT_NAME};
  }

  /**
   * AnnotatedCallLog table.
   *
   * <p>This contains all of the non-coalesced call log entries.
   */
  public static final class AnnotatedCallLog implements CommonColumns {

    public static final String TABLE = "AnnotatedCallLog";

    /** The content URI for this table. */
    public static final Uri CONTENT_URI =
        Uri.withAppendedPath(AnnotatedCallLogContract.CONTENT_URI, TABLE);

    /** The MIME type of a {@link android.content.ContentProvider#getType(Uri)} single entry. */
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/annotated_call_log";

    /**
     * The phone number called or number the call came from, encoded as a {@link
     * com.android.dialer.DialerPhoneNumber} proto. The number may be empty if it was an incoming
     * call and the number was unknown.
     *
     * <p>This column is only present in the annotated call log, and not the coalesced annotated
     * call log. The coalesced version uses a formatted number string rather than proto bytes.
     *
     * <p>Type: BLOB
     */
    public static final String NUMBER = "number";
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
     * Number of AnnotatedCallLog rows represented by this CoalescedAnnotatedCallLog row.
     *
     * <p>Type: INTEGER
     */
    public static final String NUMBER_CALLS = "number_calls";

    /**
     * The phone number formatted in a way suitable for display to the user. This value is generated
     * on the fly when the {@link CoalescedAnnotatedCallLog} is generated.
     *
     * <p>Type: TEXT
     */
    public static final String FORMATTED_NUMBER = "formatted_number";

    /**
     * Columns that are only in the {@link CoalescedAnnotatedCallLog} but not the {@link
     * AnnotatedCallLog}.
     */
    private static final String[] COLUMNS_ONLY_IN_COALESCED_CALL_LOG =
        new String[] {NUMBER_CALLS, FORMATTED_NUMBER};

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
