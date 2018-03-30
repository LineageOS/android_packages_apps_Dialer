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

import android.content.ContentValues;
import android.content.Context;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.support.annotation.Nullable;
import android.telephony.PhoneNumberUtils;
import com.android.dialer.common.database.Selection;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

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
   * Block a number.
   *
   * @param countryIso the current location used to guess the country code of the number if not
   *     available. If {@code null} and {@code number} does not have a country code, only the
   *     original number will be blocked.
   * @throws BlockingFailedException in the returned future if the operation failed.
   */
  public static ListenableFuture<Void> block(
      Context context,
      ListeningExecutorService executorService,
      String number,
      @Nullable String countryIso) {
    return executorService.submit(
        () -> {
          ContentValues values = new ContentValues();
          values.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number);
          String e164Number = PhoneNumberUtils.formatNumberToE164(number, countryIso);
          if (e164Number != null) {
            values.put(BlockedNumbers.COLUMN_E164_NUMBER, e164Number);
          }
          try {
            context.getContentResolver().insert(BlockedNumbers.CONTENT_URI, values);
          } catch (SecurityException e) {
            throw new BlockingFailedException(e);
          }
          return null;
        });
  }

  /**
   * Unblock a number.
   *
   * @param countryIso the current location used to guess the country code of the number if not
   *     available. If {@code null} and {@code number} does not have a country code, only the
   *     original number will be unblocked.
   * @throws BlockingFailedException in the returned future if the operation failed.
   */
  public static ListenableFuture<Void> unblock(
      Context context,
      ListeningExecutorService executorService,
      String number,
      @Nullable String countryIso) {
    return executorService.submit(
        () -> {
          Selection selection =
              Selection.column(BlockedNumbers.COLUMN_ORIGINAL_NUMBER).is("=", number);
          String e164Number = PhoneNumberUtils.formatNumberToE164(number, countryIso);
          if (e164Number != null) {
            selection =
                selection
                    .buildUpon()
                    .or(Selection.column(BlockedNumbers.COLUMN_E164_NUMBER).is("=", e164Number))
                    .build();
          }
          try {
            context
                .getContentResolver()
                .delete(
                    BlockedNumbers.CONTENT_URI,
                    selection.getSelection(),
                    selection.getSelectionArgs());
          } catch (SecurityException e) {
            throw new BlockingFailedException(e);
          }
          return null;
        });
  }
}
