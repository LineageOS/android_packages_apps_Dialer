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

package com.android.dialer.blocking;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.support.annotation.Nullable;
import android.telephony.PhoneNumberUtils;
import android.util.ArrayMap;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.database.Selection;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Blocks and unblocks number. */
public final class Blocking {

  private Blocking() {}

  /**
   * Thrown when blocking cannot be performed because dialer is not the default dialer, or the
   * current user is not a primary user.
   *
   * <p>Blocking is only allowed on the primary user (the first user added). Primary user cannot be
   * easily checked because {@link
   * android.provider.BlockedNumberContract#canCurrentUserBlockNumbers(Context)} is a slow IPC, and
   * UserManager.isPrimaryUser() is a system API. Since secondary users are rare cases this class
   * choose to ignore the check and let callers handle the failure later.
   */
  public static final class BlockingFailedException extends Exception {
    BlockingFailedException(Throwable cause) {
      super(cause);
    }
  }

  /**
   * Block a list of numbers.
   *
   * @param countryIso the current location used to guess the country code of the number if not
   *     available. If {@code null} and {@code number} does not have a country code, only the
   *     original number will be blocked.
   * @throws BlockingFailedException in the returned future if the operation failed.
   */
  public static ListenableFuture<Void> block(
      Context context, ImmutableCollection<String> numbers, @Nullable String countryIso) {
    return DialerExecutorComponent.get(context)
        .backgroundExecutor()
        .submit(
            () -> {
              ArrayList<ContentProviderOperation> operations = new ArrayList<>();
              for (String number : numbers) {
                ContentValues values = new ContentValues();
                values.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number);
                String e164Number = PhoneNumberUtils.formatNumberToE164(number, countryIso);
                if (e164Number != null) {
                  values.put(BlockedNumbers.COLUMN_E164_NUMBER, e164Number);
                }
                operations.add(
                    ContentProviderOperation.newInsert(BlockedNumbers.CONTENT_URI)
                        .withValues(values)
                        .build());
              }
              applyBatchOps(context.getContentResolver(), operations);
              return null;
            });
  }

  /**
   * Unblock a list of number.
   *
   * @param countryIso the current location used to guess the country code of the number if not
   *     available. If {@code null} and {@code number} does not have a country code, only the
   *     original number will be unblocked.
   * @throws BlockingFailedException in the returned future if the operation failed.
   */
  public static ListenableFuture<Void> unblock(
      Context context, ImmutableCollection<String> numbers, @Nullable String countryIso) {
    return DialerExecutorComponent.get(context)
        .backgroundExecutor()
        .submit(
            () -> {
              ArrayList<ContentProviderOperation> operations = new ArrayList<>();
              for (String number : numbers) {
                Selection selection =
                    Selection.column(BlockedNumbers.COLUMN_ORIGINAL_NUMBER).is("=", number);
                String e164Number = PhoneNumberUtils.formatNumberToE164(number, countryIso);
                if (e164Number != null) {
                  selection =
                      selection
                          .buildUpon()
                          .or(
                              Selection.column(BlockedNumbers.COLUMN_E164_NUMBER)
                                  .is("=", e164Number))
                          .build();
                }
                operations.add(
                    ContentProviderOperation.newDelete(BlockedNumbers.CONTENT_URI)
                        .withSelection(selection.getSelection(), selection.getSelectionArgs())
                        .build());
              }
              applyBatchOps(context.getContentResolver(), operations);
              return null;
            });
  }

  /**
   * Get blocked numbers from a list of number.
   *
   * @param countryIso the current location used to guess the country code of the number if not
   *     available. If {@code null} and {@code number} does not have a country code, only the
   *     original number will be used to check blocked status.
   * @throws BlockingFailedException in the returned future if the operation failed.
   */
  public static ListenableFuture<ImmutableMap<String, Boolean>> isBlocked(
      Context context, ImmutableCollection<String> numbers, @Nullable String countryIso) {
    return DialerExecutorComponent.get(context)
        .backgroundExecutor()
        .submit(
            () -> {
              Map<String, Boolean> blockedStatus = new ArrayMap<>();
              List<String> e164Numbers = new ArrayList<>();

              for (String number : numbers) {
                // Initialize as unblocked
                blockedStatus.put(number, false);
                String e164Number = PhoneNumberUtils.formatNumberToE164(number, countryIso);
                if (e164Number != null) {
                  e164Numbers.add(e164Number);
                }
              }

              Selection selection =
                  Selection.builder()
                      .or(Selection.column(BlockedNumbers.COLUMN_ORIGINAL_NUMBER).in(numbers))
                      .or(Selection.column(BlockedNumbers.COLUMN_E164_NUMBER).in(e164Numbers))
                      .build();

              try (Cursor cursor =
                  context
                      .getContentResolver()
                      .query(
                          BlockedNumbers.CONTENT_URI,
                          new String[] {BlockedNumbers.COLUMN_ORIGINAL_NUMBER},
                          selection.getSelection(),
                          selection.getSelectionArgs(),
                          null)) {
                if (cursor == null) {
                  return ImmutableMap.copyOf(blockedStatus);
                }
                while (cursor.moveToNext()) {
                  // Update blocked status
                  blockedStatus.put(cursor.getString(0), true);
                }
              }
              return ImmutableMap.copyOf(blockedStatus);
            });
  }

  private static ContentProviderResult[] applyBatchOps(
      ContentResolver resolver, ArrayList<ContentProviderOperation> ops)
      throws BlockingFailedException {
    try {
      return resolver.applyBatch(BlockedNumberContract.AUTHORITY, ops);
    } catch (RemoteException | OperationApplicationException | SecurityException e) {
      throw new BlockingFailedException(e);
    }
  }
}
