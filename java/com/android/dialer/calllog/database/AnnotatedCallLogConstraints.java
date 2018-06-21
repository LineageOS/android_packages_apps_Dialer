/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.calllog.database;

import android.content.ContentValues;
import android.provider.CallLog.Calls;
import android.support.annotation.IntDef;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.common.Assert;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.function.Predicate;

/** Constraints for columns in the {@link AnnotatedCallLog}. */
final class AnnotatedCallLogConstraints {

  /** Type of operation the {@link ContentValues} to be checked is used for. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({Operation.INSERT, Operation.UPDATE})
  @interface Operation {
    int INSERT = 1;
    int UPDATE = 2;
  }

  private AnnotatedCallLogConstraints() {}

  /**
   * Checks if the given {@link ContentValues} meets the constraints defined in this class. An
   * {@link IllegalArgumentException} will be thrown if it doesn't.
   */
  public static void check(ContentValues contentValues, @Operation int operationType) {
    checkBooleanColumn(AnnotatedCallLog.IS_READ, contentValues, operationType);
    checkBooleanColumn(AnnotatedCallLog.NEW, contentValues, operationType);
    checkBooleanColumn(AnnotatedCallLog.IS_VOICEMAIL_CALL, contentValues, operationType);
    checkCallTypeColumn(contentValues, operationType);
  }

  /**
   * Checks a boolean column.
   *
   * <p>Constraints: the value must be either 0 or 1 (SQLite database has no boolean type, so the
   * value has to be an integer).
   */
  private static void checkBooleanColumn(
      String columnName, ContentValues contentValues, @Operation int operationType) {
    checkColumn(
        columnName,
        contentValues,
        operationType,
        contentValuesToCheck -> {
          Integer value = contentValuesToCheck.getAsInteger(columnName);
          return value != null && (value == 0 || value == 1);
        });
  }

  /**
   * Checks column {@link AnnotatedCallLog#CALL_TYPE}.
   *
   * <p>Constraints: the value must be one of {@link android.provider.CallLog.Calls#TYPE}.
   */
  private static void checkCallTypeColumn(
      ContentValues contentValues, @Operation int operationType) {
    checkColumn(
        AnnotatedCallLog.CALL_TYPE,
        contentValues,
        operationType,
        contentValuesToCheck -> {
          Integer callType = contentValuesToCheck.getAsInteger(AnnotatedCallLog.CALL_TYPE);
          return callType != null
              && (callType == Calls.INCOMING_TYPE
                  || callType == Calls.OUTGOING_TYPE
                  || callType == Calls.MISSED_TYPE
                  || callType == Calls.VOICEMAIL_TYPE
                  || callType == Calls.REJECTED_TYPE
                  || callType == Calls.BLOCKED_TYPE
                  || callType == Calls.ANSWERED_EXTERNALLY_TYPE);
        });
  }

  private static void checkColumn(
      String columnName,
      ContentValues contentValues,
      @Operation int operationType,
      Predicate<ContentValues> predicate) {
    switch (operationType) {
      case Operation.UPDATE:
        if (!contentValues.containsKey(columnName)) {
          return;
        }
        // fall through
      case Operation.INSERT:
        Assert.checkArgument(
            predicate.test(contentValues),
            "Column %s contains invalid value: %s",
            columnName,
            contentValues.get(columnName));
        return;
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported operation: %s", operationType));
    }
  }
}
