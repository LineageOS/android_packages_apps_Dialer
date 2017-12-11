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

package com.android.dialer.phonelookup.database;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract.PhoneLookupHistory;
import java.util.ArrayList;

/**
 * {@link ContentProvider} for the PhoneLookupHistory.
 *
 * <p>Operations may run against the entire table using the URI:
 *
 * <pre>
 *   content://com.android.dialer.phonelookuphistory/PhoneLookupHistory
 * </pre>
 *
 * <p>Or against an individual row keyed by normalized number where the number is the last component
 * in the URI path, for example:
 *
 * <pre>
 *     content://com.android.dialer.phonelookuphistory/PhoneLookupHistory/+11234567890
 * </pre>
 */
public class PhoneLookupHistoryContentProvider extends ContentProvider {

  // For operations against: content://com.android.dialer.phonelookuphistory/PhoneLookupHistory
  private static final int PHONE_LOOKUP_HISTORY_TABLE_CODE = 1;
  // For operations against: content://com.android.dialer.phonelookuphistory/PhoneLookupHistory/+123
  private static final int PHONE_LOOKUP_HISTORY_TABLE_ID_CODE = 2;

  private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

  static {
    uriMatcher.addURI(
        PhoneLookupHistoryContract.AUTHORITY,
        PhoneLookupHistory.TABLE,
        PHONE_LOOKUP_HISTORY_TABLE_CODE);
    uriMatcher.addURI(
        PhoneLookupHistoryContract.AUTHORITY,
        PhoneLookupHistory.TABLE + "/*", // The last path component should be a normalized number
        PHONE_LOOKUP_HISTORY_TABLE_ID_CODE);
  }

  private PhoneLookupHistoryDatabaseHelper databaseHelper;

  private final ThreadLocal<Boolean> applyingBatch = new ThreadLocal<>();

  /** Ensures that only a single notification is generated from {@link #applyBatch(ArrayList)}. */
  private boolean isApplyingBatch() {
    return applyingBatch.get() != null && applyingBatch.get();
  }

  @Override
  public boolean onCreate() {
    databaseHelper = new PhoneLookupHistoryDatabaseHelper(getContext());
    return true;
  }

  @Nullable
  @Override
  public Cursor query(
      @NonNull Uri uri,
      @Nullable String[] projection,
      @Nullable String selection,
      @Nullable String[] selectionArgs,
      @Nullable String sortOrder) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
    queryBuilder.setTables(PhoneLookupHistory.TABLE);
    int match = uriMatcher.match(uri);
    switch (match) {
      case PHONE_LOOKUP_HISTORY_TABLE_ID_CODE:
        queryBuilder.appendWhere(
            PhoneLookupHistory.NORMALIZED_NUMBER
                + "="
                + DatabaseUtils.sqlEscapeString(uri.getLastPathSegment()));
        // fall through
      case PHONE_LOOKUP_HISTORY_TABLE_CODE:
        Cursor cursor =
            queryBuilder.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        if (cursor == null) {
          LogUtil.w("PhoneLookupHistoryContentProvider.query", "cursor was null");
          return null;
        }
        cursor.setNotificationUri(
            getContext().getContentResolver(), PhoneLookupHistory.CONTENT_URI);
        return cursor;
      default:
        throw new IllegalArgumentException("Unknown uri: " + uri);
    }
  }

  @Nullable
  @Override
  public String getType(@NonNull Uri uri) {
    return PhoneLookupHistory.CONTENT_ITEM_TYPE;
  }

  @Nullable
  @Override
  public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
    // Javadoc states values is not nullable, even though it is annotated as such (a bug)!
    Assert.checkArgument(values != null);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    int match = uriMatcher.match(uri);
    switch (match) {
      case PHONE_LOOKUP_HISTORY_TABLE_CODE:
        Assert.checkArgument(
            !TextUtils.isEmpty(values.getAsString(PhoneLookupHistory.NORMALIZED_NUMBER)),
            "You must specify a normalized number when inserting");
        break;
      case PHONE_LOOKUP_HISTORY_TABLE_ID_CODE:
        String normalizedNumberFromUri = uri.getLastPathSegment();
        String normalizedNumberFromValues =
            values.getAsString(PhoneLookupHistory.NORMALIZED_NUMBER);
        Assert.checkArgument(
            normalizedNumberFromValues == null
                || normalizedNumberFromValues.equals(normalizedNumberFromUri),
            "NORMALIZED_NUMBER from values %s does not match normalized number from URI: %s",
            LogUtil.sanitizePhoneNumber(normalizedNumberFromValues),
            LogUtil.sanitizePhoneNumber(normalizedNumberFromUri));
        if (normalizedNumberFromValues == null) {
          values.put(PhoneLookupHistory.NORMALIZED_NUMBER, normalizedNumberFromUri);
        }
        break;
      default:
        throw new IllegalArgumentException("Unknown uri: " + uri);
    }
    // Note: The id returned for a successful insert isn't actually part of the table.
    long id = database.insert(PhoneLookupHistory.TABLE, null, values);
    if (id < 0) {
      LogUtil.w(
          "PhoneLookupHistoryContentProvider.insert",
          "error inserting row with number: %s",
          LogUtil.sanitizePhoneNumber(values.getAsString(PhoneLookupHistory.NORMALIZED_NUMBER)));
      return null;
    }
    Uri insertedUri =
        PhoneLookupHistory.CONTENT_URI
            .buildUpon()
            .appendEncodedPath(values.getAsString(PhoneLookupHistory.NORMALIZED_NUMBER))
            .build();
    if (!isApplyingBatch()) {
      notifyChange(insertedUri);
    }
    return insertedUri;
  }

  @Override
  public int delete(
      @NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    final int match = uriMatcher.match(uri);
    switch (match) {
      case PHONE_LOOKUP_HISTORY_TABLE_CODE:
        break;
      case PHONE_LOOKUP_HISTORY_TABLE_ID_CODE:
        Assert.checkArgument(selection == null, "Do not specify selection when deleting by number");
        Assert.checkArgument(
            selectionArgs == null, "Do not specify selection args when deleting by number");
        String number = uri.getLastPathSegment();
        Assert.checkArgument(!TextUtils.isEmpty(number), "error parsing number from uri: %s", uri);
        selection = PhoneLookupHistory.NORMALIZED_NUMBER + "= ?";
        selectionArgs = new String[] {number};
        break;
      default:
        throw new IllegalArgumentException("Unknown uri: " + uri);
    }
    int rows = database.delete(PhoneLookupHistory.TABLE, selection, selectionArgs);
    if (rows == 0) {
      LogUtil.w("PhoneLookupHistoryContentProvider.delete", "no rows deleted");
      return rows;
    }
    if (!isApplyingBatch()) {
      notifyChange(uri);
    }
    return rows;
  }

  /**
   * Note: If the normalized number is included as part of the URI (for example,
   * "content://com.android.dialer.phonelookuphistory/PhoneLookupHistory/+123") then the update
   * operation will actually be a "replace" operation, inserting a new row if one does not already
   * exist.
   *
   * <p>All columns in an existing row will be replaced which means you must specify all required
   * columns in {@code values} when using this method.
   */
  @Override
  public int update(
      @NonNull Uri uri,
      @Nullable ContentValues values,
      @Nullable String selection,
      @Nullable String[] selectionArgs) {
    // Javadoc states values is not nullable, even though it is annotated as such (a bug)!
    Assert.checkArgument(values != null);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    int match = uriMatcher.match(uri);
    switch (match) {
      case PHONE_LOOKUP_HISTORY_TABLE_CODE:
        int rows = database.update(PhoneLookupHistory.TABLE, values, selection, selectionArgs);
        if (rows == 0) {
          LogUtil.w("PhoneLookupHistoryContentProvider.update", "no rows updated");
          return rows;
        }
        if (!isApplyingBatch()) {
          notifyChange(uri);
        }
        return rows;
      case PHONE_LOOKUP_HISTORY_TABLE_ID_CODE:
        Assert.checkArgument(
            !values.containsKey(PhoneLookupHistory.NORMALIZED_NUMBER),
            "Do not specify number in values when updating by number");
        Assert.checkArgument(selection == null, "Do not specify selection when updating by ID");
        Assert.checkArgument(
            selectionArgs == null, "Do not specify selection args when updating by ID");

        String normalizedNumber = uri.getLastPathSegment();
        values.put(PhoneLookupHistory.NORMALIZED_NUMBER, normalizedNumber);
        long result = database.replace(PhoneLookupHistory.TABLE, null, values);
        Assert.checkArgument(result != -1, "replacing PhoneLookupHistory row failed");
        if (!isApplyingBatch()) {
          notifyChange(uri);
        }
        return 1;
      default:
        throw new IllegalArgumentException("Unknown uri: " + uri);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Note: When applyBatch is used with the PhoneLookupHistory, only a single notification for
   * the content URI is generated, not individual notifications for each affected URI.
   */
  @NonNull
  @Override
  public ContentProviderResult[] applyBatch(@NonNull ArrayList<ContentProviderOperation> operations)
      throws OperationApplicationException {
    ContentProviderResult[] results = new ContentProviderResult[operations.size()];
    if (operations.isEmpty()) {
      return results;
    }

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    try {
      applyingBatch.set(true);
      database.beginTransaction();
      for (int i = 0; i < operations.size(); i++) {
        ContentProviderOperation operation = operations.get(i);
        int match = uriMatcher.match(operation.getUri());
        switch (match) {
          case PHONE_LOOKUP_HISTORY_TABLE_CODE:
          case PHONE_LOOKUP_HISTORY_TABLE_ID_CODE:
            ContentProviderResult result = operation.apply(this, results, i);
            if (operation.isInsert()) {
              if (result.uri == null) {
                throw new OperationApplicationException("error inserting row");
              }
            } else if (result.count == 0) {
              throw new OperationApplicationException("error applying operation");
            }
            results[i] = result;
            break;
          default:
            throw new IllegalArgumentException("Unknown uri: " + operation.getUri());
        }
      }
      database.setTransactionSuccessful();
    } finally {
      applyingBatch.set(false);
      database.endTransaction();
    }
    notifyChange(PhoneLookupHistory.CONTENT_URI);
    return results;
  }

  private void notifyChange(Uri uri) {
    getContext().getContentResolver().notifyChange(uri, null);
  }
}
