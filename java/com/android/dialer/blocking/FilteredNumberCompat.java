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
 * limitations under the License
 */

package com.android.dialer.blocking;

import android.annotation.TargetApi;
import android.app.FragmentManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.database.FilteredNumberContract.FilteredNumber;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberSources;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberTypes;
import com.android.dialer.telecom.TelecomUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compatibility class to encapsulate logic to switch between call blocking using {@link
 * com.android.dialer.database.FilteredNumberContract} and using {@link
 * android.provider.BlockedNumberContract}. This class should be used rather than explicitly
 * referencing columns from either contract class in situations where both blocking solutions may be
 * used.
 */
public class FilteredNumberCompat {

  private static Boolean canAttemptBlockOperationsForTest;

  @VisibleForTesting
  public static final String HAS_MIGRATED_TO_NEW_BLOCKING_KEY = "migratedToNewBlocking";

  /** @return The column name for ID in the filtered number database. */
  public static String getIdColumnName(Context context) {
    return useNewFiltering(context) ? BlockedNumbers.COLUMN_ID : FilteredNumberColumns._ID;
  }

  /**
   * @return The column name for type in the filtered number database. Will be {@code null} for the
   *     framework blocking implementation.
   */
  @Nullable
  public static String getTypeColumnName(Context context) {
    return useNewFiltering(context) ? null : FilteredNumberColumns.TYPE;
  }

  /**
   * @return The column name for source in the filtered number database. Will be {@code null} for
   *     the framework blocking implementation
   */
  @Nullable
  public static String getSourceColumnName(Context context) {
    return useNewFiltering(context) ? null : FilteredNumberColumns.SOURCE;
  }

  /** @return The column name for the original number in the filtered number database. */
  public static String getOriginalNumberColumnName(Context context) {
    return useNewFiltering(context)
        ? BlockedNumbers.COLUMN_ORIGINAL_NUMBER
        : FilteredNumberColumns.NUMBER;
  }

  /**
   * @return The column name for country iso in the filtered number database. Will be {@code null}
   *     the framework blocking implementation
   */
  @Nullable
  public static String getCountryIsoColumnName(Context context) {
    return useNewFiltering(context) ? null : FilteredNumberColumns.COUNTRY_ISO;
  }

  /** @return The column name for the e164 formatted number in the filtered number database. */
  public static String getE164NumberColumnName(Context context) {
    return useNewFiltering(context)
        ? BlockedNumbers.COLUMN_E164_NUMBER
        : FilteredNumberColumns.NORMALIZED_NUMBER;
  }

  /**
   * @return {@code true} if the current SDK version supports using new filtering, {@code false}
   *     otherwise.
   */
  public static boolean canUseNewFiltering() {
    return VERSION.SDK_INT >= VERSION_CODES.N;
  }

  /**
   * @return {@code true} if the new filtering should be used, i.e. it's enabled and any necessary
   *     migration has been performed, {@code false} otherwise.
   */
  public static boolean useNewFiltering(Context context) {
    return canUseNewFiltering() && hasMigratedToNewBlocking(context);
  }

  /**
   * @return {@code true} if the user has migrated to use {@link
   *     android.provider.BlockedNumberContract} blocking, {@code false} otherwise.
   */
  public static boolean hasMigratedToNewBlocking(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(HAS_MIGRATED_TO_NEW_BLOCKING_KEY, false);
  }

  /**
   * Called to inform this class whether the user has fully migrated to use {@link
   * android.provider.BlockedNumberContract} blocking or not.
   *
   * @param hasMigrated {@code true} if the user has migrated, {@code false} otherwise.
   */
  public static void setHasMigratedToNewBlocking(Context context, boolean hasMigrated) {
    PreferenceManager.getDefaultSharedPreferences(context)
        .edit()
        .putBoolean(HAS_MIGRATED_TO_NEW_BLOCKING_KEY, hasMigrated)
        .apply();
  }

  /**
   * Gets the content {@link Uri} for number filtering.
   *
   * @param id The optional id to append with the base content uri.
   * @return The Uri for number filtering.
   */
  public static Uri getContentUri(Context context, @Nullable Integer id) {
    if (id == null) {
      return getBaseUri(context);
    }
    return ContentUris.withAppendedId(getBaseUri(context), id);
  }

  private static Uri getBaseUri(Context context) {
    // Explicit version check to aid static analysis
    return useNewFiltering(context) && VERSION.SDK_INT >= VERSION_CODES.N
        ? BlockedNumbers.CONTENT_URI
        : FilteredNumber.CONTENT_URI;
  }

  /**
   * Removes any null column names from the given projection array. This method is intended to be
   * used to strip out any column names that aren't available in every version of number blocking.
   * Example: {@literal getContext().getContentResolver().query( someUri, // Filtering ensures that
   * no non-existant columns are queried FilteredNumberCompat.filter(new String[]
   * {FilteredNumberCompat.getIdColumnName(), FilteredNumberCompat.getTypeColumnName()},
   * FilteredNumberCompat.getE164NumberColumnName() + " = ?", new String[] {e164Number}); }
   *
   * @param projection The projection array.
   * @return The filtered projection array.
   */
  @Nullable
  public static String[] filter(@Nullable String[] projection) {
    if (projection == null) {
      return null;
    }
    List<String> filtered = new ArrayList<>();
    for (String column : projection) {
      if (column != null) {
        filtered.add(column);
      }
    }
    return filtered.toArray(new String[filtered.size()]);
  }

  /**
   * Creates a new {@link ContentValues} suitable for inserting in the filtered number table.
   *
   * @param number The unformatted number to insert.
   * @param e164Number (optional) The number to insert formatted to E164 standard.
   * @param countryIso (optional) The country iso to use to format the number.
   * @return The ContentValues to insert.
   * @throws NullPointerException If number is null.
   */
  public static ContentValues newBlockNumberContentValues(
      Context context, String number, @Nullable String e164Number, @Nullable String countryIso) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(getOriginalNumberColumnName(context), Objects.requireNonNull(number));
    if (!useNewFiltering(context)) {
      if (e164Number == null) {
        e164Number = PhoneNumberUtils.formatNumberToE164(number, countryIso);
      }
      contentValues.put(getE164NumberColumnName(context), e164Number);
      contentValues.put(getCountryIsoColumnName(context), countryIso);
      contentValues.put(getTypeColumnName(context), FilteredNumberTypes.BLOCKED_NUMBER);
      contentValues.put(getSourceColumnName(context), FilteredNumberSources.USER);
    }
    return contentValues;
  }

  /**
   * Shows block number migration dialog if necessary.
   *
   * @param fragmentManager The {@link FragmentManager} used to show fragments.
   * @param listener The {@link BlockedNumbersMigrator.Listener} to call when migration is complete.
   * @return boolean True if migration dialog is shown.
   */
  public static boolean maybeShowBlockNumberMigrationDialog(
      Context context, FragmentManager fragmentManager, BlockedNumbersMigrator.Listener listener) {
    if (shouldShowMigrationDialog(context)) {
      LogUtil.i(
          "FilteredNumberCompat.maybeShowBlockNumberMigrationDialog",
          "maybeShowBlockNumberMigrationDialog - showing migration dialog");
      MigrateBlockedNumbersDialogFragment.newInstance(new BlockedNumbersMigrator(context), listener)
          .show(fragmentManager, "MigrateBlockedNumbers");
      return true;
    }
    return false;
  }

  private static boolean shouldShowMigrationDialog(Context context) {
    return canUseNewFiltering() && !hasMigratedToNewBlocking(context);
  }

  /**
   * Creates the {@link Intent} which opens the blocked numbers management interface.
   *
   * @param context The {@link Context}.
   * @return The intent.
   */
  public static Intent createManageBlockedNumbersIntent(Context context) {
    // Explicit version check to aid static analysis
    if (canUseNewFiltering()
        && hasMigratedToNewBlocking(context)
        && VERSION.SDK_INT >= VERSION_CODES.N) {
      return context.getSystemService(TelecomManager.class).createManageBlockedNumbersIntent();
    }
    Intent intent = new Intent("com.android.dialer.action.BLOCKED_NUMBERS_SETTINGS");
    intent.setPackage(context.getPackageName());
    return intent;
  }

  /**
   * Method used to determine if block operations are possible.
   *
   * @param context The {@link Context}.
   * @return {@code true} if the app and user can block numbers, {@code false} otherwise.
   */
  public static boolean canAttemptBlockOperations(Context context) {
    if (canAttemptBlockOperationsForTest != null) {
      return canAttemptBlockOperationsForTest;
    }

    if (VERSION.SDK_INT < VERSION_CODES.N) {
      // Dialer blocking, must be primary user
      return context.getSystemService(UserManager.class).isSystemUser();
    }

    // Great Wall blocking, must be primary user and the default or system dialer
    // TODO: check that we're the system Dialer
    return TelecomUtil.isDefaultDialer(context)
        && safeBlockedNumbersContractCanCurrentUserBlockNumbers(context);
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public static void setCanAttemptBlockOperationsForTest(boolean canAttempt) {
    canAttemptBlockOperationsForTest = canAttempt;
  }

  /**
   * Used to determine if the call blocking settings can be opened.
   *
   * @param context The {@link Context}.
   * @return {@code true} if the current user can open the call blocking settings, {@code false}
   *     otherwise.
   */
  public static boolean canCurrentUserOpenBlockSettings(Context context) {
    if (VERSION.SDK_INT < VERSION_CODES.N) {
      // Dialer blocking, must be primary user
      return context.getSystemService(UserManager.class).isSystemUser();
    }
    // BlockedNumberContract blocking, verify through Contract API
    return TelecomUtil.isDefaultDialer(context)
        && safeBlockedNumbersContractCanCurrentUserBlockNumbers(context);
  }

  /**
   * Calls {@link BlockedNumberContract#canCurrentUserBlockNumbers(Context)} in such a way that it
   * never throws an exception. While on the CryptKeeper screen, the BlockedNumberContract isn't
   * available, using this method ensures that the Dialer doesn't crash when on that screen.
   *
   * @param context The {@link Context}.
   * @return the result of BlockedNumberContract#canCurrentUserBlockNumbers, or {@code false} if an
   *     exception was thrown.
   */
  @TargetApi(VERSION_CODES.N)
  private static boolean safeBlockedNumbersContractCanCurrentUserBlockNumbers(Context context) {
    try {
      return BlockedNumberContract.canCurrentUserBlockNumbers(context);
    } catch (Exception e) {
      LogUtil.e(
          "FilteredNumberCompat.safeBlockedNumbersContractCanCurrentUserBlockNumbers",
          "Exception while querying BlockedNumberContract",
          e);
      return false;
    }
  }
}
