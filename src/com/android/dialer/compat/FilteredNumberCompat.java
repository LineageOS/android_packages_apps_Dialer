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

    package com.android.dialer.compat;

import com.google.common.base.Preconditions;

import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.telephony.PhoneNumberUtils;

import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.testing.NeededForTesting;
import com.android.dialer.database.FilteredNumberContract.FilteredNumber;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberColumns;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberSources;
import com.android.dialer.database.FilteredNumberContract.FilteredNumberTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility class to encapsulate logic to switch between call blocking using
 * {@link com.android.dialer.database.FilteredNumberContract} and using
 * {@link android.provider.BlockedNumberContract}. This class should be used rather than explicitly
 * referencing columns from either contract class in situations where both blocking solutions may be
 * used.
 */
public class FilteredNumberCompat {

    // Flag to enable feature.
    // TODO(maxwelb) remove when ready to enable new filtering.
    private static final boolean isNewFilteringEnabled = false;
    private static Boolean isEnabledForTest;

    /**
     * @return The column name for ID in the filtered number database.
     */
    public static String getIdColumnName() {
        return useNewFiltering() ? BlockedNumbersSdkCompat._ID : FilteredNumberColumns._ID;
    }

    /**
     * @return The column name for type in the filtered number database. Will be {@code null} for
     * the framework blocking implementation.
     */
    @Nullable
    public static String getTypeColumnName() {
        return useNewFiltering() ? null : FilteredNumberColumns.TYPE;
    }

    /**
     * @return The column name for source in the filtered number database. Will be {@code null} for
     * the framework blocking implementation
     */
    @Nullable
    public static String getSourceColumnName() {
        return useNewFiltering() ? null : FilteredNumberColumns.SOURCE;
    }

    /**
     * @return The column name for the original number in the filtered number database.
     */
    public static String getOriginalNumberColumnName() {
        return useNewFiltering() ? BlockedNumbersSdkCompat.COLUMN_ORIGINAL_NUMBER
                : FilteredNumberColumns.NUMBER;
    }

    /**
     * @return The column name for country iso in the filtered number database. Will be {@code null}
     * the framework blocking implementation
     */
    @Nullable
    public static String getCountryIsoColumnName() {
        return useNewFiltering() ? null : FilteredNumberColumns.COUNTRY_ISO;
    }

    /**
     * @return The column name for the e164 formatted number in the filtered number database.
     */
    public static String getE164NumberColumnName() {
        return useNewFiltering() ? BlockedNumbersSdkCompat.E164_NUMBER
                : FilteredNumberColumns.NORMALIZED_NUMBER;
    }

    /**
     * @return {@code true} if the new filtering is enabled, {@code false} otherwise.
     */
    public static boolean useNewFiltering() {
        if (isEnabledForTest != null) {
            return CompatUtils.isNCompatible() && isEnabledForTest;
        }
        return CompatUtils.isNCompatible() && isNewFilteringEnabled;
    }

    @NeededForTesting
    public static void setIsEnabledForTest(Boolean isEnabled) {
        isEnabledForTest = isEnabled;
    }

    /**
     * Gets the content {@link Uri} for number filtering.
     *
     * @param id The optional id to append with the base content uri.
     * @return The Uri for number filtering.
     */
    public static Uri getContentUri(@Nullable Integer id) {
        if (id == null) {
            return getBaseUri();
        }
        return ContentUris.withAppendedId(getBaseUri(), id);
    }


    private static Uri getBaseUri() {
        return useNewFiltering() ? BlockedNumbersSdkCompat.CONTENT_URI : FilteredNumber.CONTENT_URI;
    }

    /**
     * Removes any null column names from the given projection array. This method is intended to be
     * used to strip out any column names that aren't available in every version of number blocking.
     * Example:
     * {@literal
     *   getContext().getContentResolver().query(
     *       someUri,
     *       // Filtering ensures that no non-existant columns are queried
     *       FilteredNumberCompat.filter(new String[] {FilteredNumberCompat.getIdColumnName(),
     *           FilteredNumberCompat.getTypeColumnName()},
     *       FilteredNumberCompat.getE164NumberColumnName() + " = ?",
     *       new String[] {e164Number});
     * }
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
    public static ContentValues newBlockNumberContentValues(String number,
            @Nullable String e164Number, @Nullable String countryIso) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(getOriginalNumberColumnName(), Preconditions.checkNotNull(number));
        if (!useNewFiltering()) {
            if (e164Number == null) {
                e164Number = PhoneNumberUtils.formatNumberToE164(number, countryIso);
            }
            contentValues.put(getE164NumberColumnName(), e164Number);
            contentValues.put(getCountryIsoColumnName(), countryIso);
            contentValues.put(getTypeColumnName(), FilteredNumberTypes.BLOCKED_NUMBER);
            contentValues.put(getSourceColumnName(), FilteredNumberSources.USER);
        }
        return contentValues;
    }
}
