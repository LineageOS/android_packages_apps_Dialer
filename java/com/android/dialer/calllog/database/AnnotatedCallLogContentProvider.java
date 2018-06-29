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

package com.android.dialer.calllog.database;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.calllog.database.AnnotatedCallLogConstraints.Operation;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import java.util.ArrayList;
import java.util.Arrays;

/** {@link ContentProvider} for the annotated call log. */
public class AnnotatedCallLogContentProvider extends ContentProvider {

  private static final int ANNOTATED_CALL_LOG_TABLE_CODE = 1;
  private static final int ANNOTATED_CALL_LOG_TABLE_ID_CODE = 2;
  private static final int ANNOTATED_CALL_LOG_TABLE_DISTINCT_NUMBER_CODE = 3;

  private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

  static {
    uriMatcher.addURI(
        AnnotatedCallLogContract.AUTHORITY, AnnotatedCallLog.TABLE, ANNOTATED_CALL_LOG_TABLE_CODE);
    uriMatcher.addURI(
        AnnotatedCallLogContract.AUTHORITY,
        AnnotatedCallLog.TABLE + "/#",
        ANNOTATED_CALL_LOG_TABLE_ID_CODE);
    uriMatcher.addURI(
        AnnotatedCallLogContract.AUTHORITY,
        AnnotatedCallLog.DISTINCT_PHONE_NUMBERS,
        ANNOTATED_CALL_LOG_TABLE_DISTINCT_NUMBER_CODE);
  }

  private AnnotatedCallLogDatabaseHelper databaseHelper;

  private final ThreadLocal<Boolean> applyingBatch = new ThreadLocal<>();

  /** Ensures that only a single notification is generated from {@link #applyBatch(ArrayList)}. */
  private boolean isApplyingBatch() {
    return applyingBatch.get() != null && applyingBatch.get();
  }

  @Override
  public boolean onCreate() {
    databaseHelper = CallLogDatabaseComponent.get(getContext()).annotatedCallLogDatabaseHelper();

    // Note: As this method is called before Application#onCreate, we must *not* initialize objects
    // that require preparation work done in Application#onCreate.
    // One example is to avoid obtaining an instance that depends on Google's proprietary config,
    // which is initialized in Application#onCreate.

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
    queryBuilder.setTables(AnnotatedCallLog.TABLE);
    int match = uriMatcher.match(uri);
    switch (match) {
      case ANNOTATED_CALL_LOG_TABLE_ID_CODE:
        queryBuilder.appendWhere(AnnotatedCallLog._ID + "=" + ContentUris.parseId(uri));
        Cursor cursor =
            queryBuilder.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        if (cursor != null) {
          cursor.setNotificationUri(
              getContext().getContentResolver(), AnnotatedCallLog.CONTENT_URI);
        } else {
          LogUtil.w("AnnotatedCallLogContentProvider.query", "cursor was null");
        }
        return cursor;
      case ANNOTATED_CALL_LOG_TABLE_CODE:
        cursor =
            queryBuilder.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        if (cursor != null) {
          cursor.setNotificationUri(
              getContext().getContentResolver(), AnnotatedCallLog.CONTENT_URI);
        } else {
          LogUtil.w("AnnotatedCallLogContentProvider.query", "cursor was null");
        }
        return cursor;
      case ANNOTATED_CALL_LOG_TABLE_DISTINCT_NUMBER_CODE:
        Assert.checkArgument(
            Arrays.equals(projection, new String[] {AnnotatedCallLog.NUMBER}),
            "only NUMBER supported for projection for distinct phone number query, got: %s",
            Arrays.toString(projection));
        queryBuilder.setDistinct(true);
        cursor =
            queryBuilder.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        if (cursor != null) {
          cursor.setNotificationUri(
              getContext().getContentResolver(), AnnotatedCallLog.CONTENT_URI);
        } else {
          LogUtil.w("AnnotatedCallLogContentProvider.query", "cursor was null");
        }
        return cursor;
      default:
        throw new IllegalArgumentException("Unknown uri: " + uri);
    }
  }

  @Nullable
  @Override
  public String getType(@NonNull Uri uri) {
    return AnnotatedCallLog.CONTENT_ITEM_TYPE;
  }

  @Nullable
  @Override
  public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
    // Javadoc states values is not nullable, even though it is annotated as such (a bug)!
    Assert.checkArgument(values != null);

    AnnotatedCallLogConstraints.check(values, Operation.INSERT);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    int match = uriMatcher.match(uri);
    switch (match) {
      case ANNOTATED_CALL_LOG_TABLE_CODE:
        Assert.checkArgument(
            values.get(AnnotatedCallLog._ID) != null, "You must specify an _ID when inserting");
        break;
      case ANNOTATED_CALL_LOG_TABLE_ID_CODE:
        Long idFromUri = ContentUris.parseId(uri);
        Long idFromValues = values.getAsLong(AnnotatedCallLog._ID);
        Assert.checkArgument(
            idFromValues == null || idFromValues.equals(idFromUri),
            "_ID from values %d does not match ID from URI: %s",
            idFromValues,
            uri);
        if (idFromValues == null) {
          values.put(AnnotatedCallLog._ID, idFromUri);
        }
        break;
      case ANNOTATED_CALL_LOG_TABLE_DISTINCT_NUMBER_CODE:
        throw new UnsupportedOperationException();
      default:
        throw new IllegalArgumentException("Unknown uri: " + uri);
    }
    long id = database.insert(AnnotatedCallLog.TABLE, null, values);
    if (id < 0) {
      LogUtil.w(
          "AnnotatedCallLogContentProvider.insert",
          "error inserting row with id: %d",
          values.get(AnnotatedCallLog._ID));
      return null;
    }
    Uri insertedUri = ContentUris.withAppendedId(AnnotatedCallLog.CONTENT_URI, id);
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
      case ANNOTATED_CALL_LOG_TABLE_CODE:
        break;
      case ANNOTATED_CALL_LOG_TABLE_ID_CODE:
        Assert.checkArgument(selection == null, "Do not specify selection when deleting by ID");
        Assert.checkArgument(
            selectionArgs == null, "Do not specify selection args when deleting by ID");
        long id = ContentUris.parseId(uri);
        Assert.checkArgument(id != -1, "error parsing id from uri %s", uri);
        selection = getSelectionWithId(id);
        break;
      case ANNOTATED_CALL_LOG_TABLE_DISTINCT_NUMBER_CODE:
        throw new UnsupportedOperationException();
      default:
        throw new IllegalArgumentException("Unknown uri: " + uri);
    }
    int rows = database.delete(AnnotatedCallLog.TABLE, selection, selectionArgs);
    if (rows == 0) {
      LogUtil.w("AnnotatedCallLogContentProvider.delete", "no rows deleted");
      return rows;
    }
    if (!isApplyingBatch()) {
      notifyChange(uri);
    }
    return rows;
  }

  @Override
  public int update(
      @NonNull Uri uri,
      @Nullable ContentValues values,
      @Nullable String selection,
      @Nullable String[] selectionArgs) {
    // Javadoc states values is not nullable, even though it is annotated as such (a bug)!
    Assert.checkArgument(values != null);

    AnnotatedCallLogConstraints.check(values, Operation.UPDATE);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    int match = uriMatcher.match(uri);
    switch (match) {
      case ANNOTATED_CALL_LOG_TABLE_CODE:
        int rows = database.update(AnnotatedCallLog.TABLE, values, selection, selectionArgs);
        if (rows == 0) {
          LogUtil.w("AnnotatedCallLogContentProvider.update", "no rows updated");
          return rows;
        }
        if (!isApplyingBatch()) {
          notifyChange(uri);
        }
        return rows;
      case ANNOTATED_CALL_LOG_TABLE_ID_CODE:
        Assert.checkArgument(
            !values.containsKey(AnnotatedCallLog._ID), "Do not specify _ID when updating by ID");
        Assert.checkArgument(selection == null, "Do not specify selection when updating by ID");
        Assert.checkArgument(
            selectionArgs == null, "Do not specify selection args when updating by ID");
        selection = getSelectionWithId(ContentUris.parseId(uri));
        rows = database.update(AnnotatedCallLog.TABLE, values, selection, selectionArgs);
        if (rows == 0) {
          LogUtil.w("AnnotatedCallLogContentProvider.update", "no rows updated");
          return rows;
        }
        if (!isApplyingBatch()) {
          notifyChange(uri);
        }
        return rows;
      case ANNOTATED_CALL_LOG_TABLE_DISTINCT_NUMBER_CODE:
        throw new UnsupportedOperationException();
      default:
        throw new IllegalArgumentException("Unknown uri: " + uri);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Note: When applyBatch is used with the AnnotatedCallLog, only a single notification for the
   * content URI is generated, not individual notifications for each affected URI.
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
          case ANNOTATED_CALL_LOG_TABLE_CODE:
          case ANNOTATED_CALL_LOG_TABLE_ID_CODE:
            // These are allowed values, continue.
            break;
          case ANNOTATED_CALL_LOG_TABLE_DISTINCT_NUMBER_CODE:
            throw new UnsupportedOperationException();
          default:
            throw new IllegalArgumentException("Unknown uri: " + operation.getUri());
        }
        ContentProviderResult result = operation.apply(this, results, i);
        if (operations.get(i).isInsert()) {
          if (result.uri == null) {
            throw new OperationApplicationException("error inserting row");
          }
        } else if (result.count == 0) {
          /*
           * The batches built by MutationApplier happen to contain operations in order of:
           *
           * 1. Inserts
           * 2. Updates
           * 3. Deletes
           *
           * Let's say the last row in the table is row Z, and MutationApplier wishes to update it,
           * as well as insert row A. When row A gets inserted, row Z will be deleted via the
           * trigger if the table is full. Then later, when we try to process the update for row Z,
           * it won't exist.
           */
          LogUtil.w(
              "AnnotatedCallLogContentProvider.applyBatch",
              "update or delete failed, possibly because row got cleaned up");
        }
        results[i] = result;
      }
      database.setTransactionSuccessful();
    } finally {
      applyingBatch.set(false);
      database.endTransaction();
    }
    notifyChange(AnnotatedCallLog.CONTENT_URI);
    return results;
  }

  private String getSelectionWithId(long id) {
    return AnnotatedCallLog._ID + "=" + id;
  }

  private void notifyChange(Uri uri) {
    getContext().getContentResolver().notifyChange(uri, /* observer = */ null);
  }
}
