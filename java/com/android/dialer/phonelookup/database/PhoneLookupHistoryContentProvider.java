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
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract.PhoneLookupHistory;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

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

  /**
   * Can't use {@link UriMatcher} because it doesn't support empty values, and numbers can be empty.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({UriType.PHONE_LOOKUP_HISTORY_TABLE_CODE, UriType.PHONE_LOOKUP_HISTORY_TABLE_ID_CODE})
  private @interface UriType {
    // For operations against: content://com.android.dialer.phonelookuphistory/PhoneLookupHistory
    int PHONE_LOOKUP_HISTORY_TABLE_CODE = 1;
    // For operations against:
    // content://com.android.dialer.phonelookuphistory/PhoneLookupHistory?number=123
    int PHONE_LOOKUP_HISTORY_TABLE_ID_CODE = 2;
  }

  private PhoneLookupHistoryDatabaseHelper databaseHelper;

  private final ThreadLocal<Boolean> applyingBatch = new ThreadLocal<>();

  /** Ensures that only a single notification is generated from {@link #applyBatch(ArrayList)}. */
  private boolean isApplyingBatch() {
    return applyingBatch.get() != null && applyingBatch.get();
  }

  @Override
  public boolean onCreate() {
    databaseHelper =
        PhoneLookupDatabaseComponent.get(getContext()).phoneLookupHistoryDatabaseHelper();
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
    @UriType int uriType = uriType(uri);
    switch (uriType) {
      case UriType.PHONE_LOOKUP_HISTORY_TABLE_ID_CODE:
        queryBuilder.appendWhere(
            PhoneLookupHistory.NORMALIZED_NUMBER
                + "="
                + DatabaseUtils.sqlEscapeString(
                    Uri.decode(uri.getQueryParameter(PhoneLookupHistory.NUMBER_QUERY_PARAM))));
        // fall through
      case UriType.PHONE_LOOKUP_HISTORY_TABLE_CODE:
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
    @UriType int uriType = uriType(uri);
    switch (uriType) {
      case UriType.PHONE_LOOKUP_HISTORY_TABLE_CODE:
        Assert.checkArgument(
            values.getAsString(PhoneLookupHistory.NORMALIZED_NUMBER) != null,
            "You must specify a normalized number when inserting");
        break;
      case UriType.PHONE_LOOKUP_HISTORY_TABLE_ID_CODE:
        String normalizedNumberFromUri =
            Uri.decode(uri.getQueryParameter(PhoneLookupHistory.NUMBER_QUERY_PARAM));
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
        PhoneLookupHistory.contentUriForNumber(
            values.getAsString(PhoneLookupHistory.NORMALIZED_NUMBER));
    if (!isApplyingBatch()) {
      notifyChange(insertedUri);
    }
    return insertedUri;
  }

  @Override
  public int delete(
      @NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    @UriType int uriType = uriType(uri);
    switch (uriType) {
      case UriType.PHONE_LOOKUP_HISTORY_TABLE_CODE:
        break;
      case UriType.PHONE_LOOKUP_HISTORY_TABLE_ID_CODE:
        Assert.checkArgument(selection == null, "Do not specify selection when deleting by number");
        Assert.checkArgument(
            selectionArgs == null, "Do not specify selection args when deleting by number");
        String number = Uri.decode(uri.getQueryParameter(PhoneLookupHistory.NUMBER_QUERY_PARAM));
        Assert.checkArgument(
            number != null, "error parsing number from uri: %s", LogUtil.sanitizePii(uri));
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
    @UriType int uriType = uriType(uri);
    switch (uriType) {
      case UriType.PHONE_LOOKUP_HISTORY_TABLE_CODE:
        int rows = database.update(PhoneLookupHistory.TABLE, values, selection, selectionArgs);
        if (rows == 0) {
          LogUtil.w("PhoneLookupHistoryContentProvider.update", "no rows updated");
          return rows;
        }
        if (!isApplyingBatch()) {
          notifyChange(uri);
        }
        return rows;
      case UriType.PHONE_LOOKUP_HISTORY_TABLE_ID_CODE:
        Assert.checkArgument(
            !values.containsKey(PhoneLookupHistory.NORMALIZED_NUMBER),
            "Do not specify number in values when updating by number");
        Assert.checkArgument(selection == null, "Do not specify selection when updating by ID");
        Assert.checkArgument(
            selectionArgs == null, "Do not specify selection args when updating by ID");

        String normalizedNumber =
            Uri.decode(uri.getQueryParameter(PhoneLookupHistory.NUMBER_QUERY_PARAM));
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
        @UriType int uriType = uriType(operation.getUri());
        switch (uriType) {
          case UriType.PHONE_LOOKUP_HISTORY_TABLE_CODE:
          case UriType.PHONE_LOOKUP_HISTORY_TABLE_ID_CODE:
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

  @UriType
  private int uriType(Uri uri) {
    Assert.checkArgument(uri.getAuthority().equals(PhoneLookupHistoryContract.AUTHORITY));
    List<String> pathSegments = uri.getPathSegments();
    Assert.checkArgument(pathSegments.size() == 1);
    Assert.checkArgument(pathSegments.get(0).equals(PhoneLookupHistory.TABLE));
    return uri.getQueryParameter(PhoneLookupHistory.NUMBER_QUERY_PARAM) == null
        ? UriType.PHONE_LOOKUP_HISTORY_TABLE_CODE
        : UriType.PHONE_LOOKUP_HISTORY_TABLE_ID_CODE;
  }
}
