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

package com.android.dialer.blocking;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler.OnHasBlockedNumbersListener;
import com.android.dialer.common.LogUtil;
import java.util.Objects;

/**
 * Class responsible for checking if the user can be auto-migrated to {@link
 * android.provider.BlockedNumberContract} blocking. In order for this to happen, the user cannot
 * have any numbers that are blocked in the Dialer solution.
 */
public class BlockedNumbersAutoMigrator {

  static final String HAS_CHECKED_AUTO_MIGRATE_KEY = "checkedAutoMigrate";

  @NonNull private final Context context;
  @NonNull private final SharedPreferences sharedPreferences;
  @NonNull private final FilteredNumberAsyncQueryHandler queryHandler;

  /**
   * Constructs the BlockedNumbersAutoMigrator with the given {@link SharedPreferences} and {@link
   * FilteredNumberAsyncQueryHandler}.
   *
   * @param sharedPreferences The SharedPreferences used to persist information.
   * @param queryHandler The FilteredNumberAsyncQueryHandler used to determine if there are blocked
   *     numbers.
   * @throws NullPointerException if sharedPreferences or queryHandler are null.
   */
  public BlockedNumbersAutoMigrator(
      @NonNull Context context,
      @NonNull SharedPreferences sharedPreferences,
      @NonNull FilteredNumberAsyncQueryHandler queryHandler) {
    this.context = Objects.requireNonNull(context);
    this.sharedPreferences = Objects.requireNonNull(sharedPreferences);
    this.queryHandler = Objects.requireNonNull(queryHandler);
  }

  /**
   * Attempts to perform the auto-migration. Auto-migration will only be attempted once and can be
   * performed only when the user has no blocked numbers. As a result of this method, the user will
   * be migrated to the framework blocking solution, as determined by {@link
   * FilteredNumberCompat#hasMigratedToNewBlocking()}.
   */
  public void autoMigrate() {
    if (!shouldAttemptAutoMigrate()) {
      return;
    }

    LogUtil.i("BlockedNumbersAutoMigrator", "attempting to auto-migrate.");
    queryHandler.hasBlockedNumbers(
        new OnHasBlockedNumbersListener() {
          @Override
          public void onHasBlockedNumbers(boolean hasBlockedNumbers) {
            if (hasBlockedNumbers) {
              LogUtil.i("BlockedNumbersAutoMigrator", "not auto-migrating: blocked numbers exist.");
              return;
            }
            LogUtil.i("BlockedNumbersAutoMigrator", "auto-migrating: no blocked numbers.");
            FilteredNumberCompat.setHasMigratedToNewBlocking(context, true);
          }
        });
  }

  private boolean shouldAttemptAutoMigrate() {
    if (sharedPreferences.contains(HAS_CHECKED_AUTO_MIGRATE_KEY)) {
      LogUtil.v("BlockedNumbersAutoMigrator", "not attempting auto-migrate: already checked once.");
      return false;
    }

    if (!FilteredNumberCompat.canAttemptBlockOperations(context)) {
      // This may be the case where the user is on the lock screen, so we shouldn't record that the
      // migration status was checked.
      LogUtil.i(
          "BlockedNumbersAutoMigrator", "not attempting auto-migrate: current user can't block");
      return false;
    }
    LogUtil.i("BlockedNumbersAutoMigrator", "updating state as already checked for auto-migrate.");
    sharedPreferences.edit().putBoolean(HAS_CHECKED_AUTO_MIGRATE_KEY, true).apply();

    if (!FilteredNumberCompat.canUseNewFiltering()) {
      LogUtil.i("BlockedNumbersAutoMigrator", "not attempting auto-migrate: not available.");
      return false;
    }

    if (FilteredNumberCompat.hasMigratedToNewBlocking(context)) {
      LogUtil.i("BlockedNumbersAutoMigrator", "not attempting auto-migrate: already migrated.");
      return false;
    }
    return true;
  }
}
