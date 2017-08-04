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

package com.android.dialer.searchfragment.cp2;

import android.content.ContentResolver;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.dialer.searchfragment.common.Projections;
import com.android.dialer.searchfragment.common.QueryFilteringUtil;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for a cursor returned by {@link SearchContactsCursorLoader}.
 *
 * <p>This cursor removes duplicate phone numbers associated with the same contact and can filter
 * contacts based on a query by calling {@link #filter(String)}.
 */
public final class SearchContactCursor implements Cursor {

  private final Cursor cursor;
  // List of cursor ids that are valid for displaying after filtering.
  private final List<Integer> queryFilteredPositions = new ArrayList<>();

  private int currentPosition = 0;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    Qualification.NUMBERS_ARE_NOT_DUPLICATES,
    Qualification.NEW_NUMBER_IS_MORE_QUALIFIED,
    Qualification.CURRENT_MORE_QUALIFIED
  })
  private @interface Qualification {
    /** Numbers are not duplicates (i.e. neither is more qualified than the other). */
    int NUMBERS_ARE_NOT_DUPLICATES = 0;
    /** Number are duplicates and new number is more qualified than the existing number. */
    int NEW_NUMBER_IS_MORE_QUALIFIED = 1;
    /** Numbers are duplicates but current/existing number is more qualified than new number. */
    int CURRENT_MORE_QUALIFIED = 2;
  }

  /**
   * @param cursor with projection {@link Projections#PHONE_PROJECTION}.
   * @param query to filter cursor results.
   */
  public SearchContactCursor(Cursor cursor, @Nullable String query) {
    // TODO investigate copying this into a MatrixCursor and holding in memory
    this.cursor = cursor;
    filter(query);
  }

  /**
   * Filters out contacts that do not match the query.
   *
   * <p>The query can have at least 1 of 3 forms:
   *
   * <ul>
   *   <li>A phone number
   *   <li>A T9 representation of a name (matches {@link QueryFilteringUtil#T9_PATTERN}).
   *   <li>A name
   * </ul>
   *
   * <p>A contact is considered a match if:
   *
   * <ul>
   *   <li>Its phone number contains the phone number query
   *   <li>Its name represented in T9 contains the T9 query
   *   <li>Its name contains the query
   * </ul>
   */
  public void filter(@Nullable String query) {
    if (query == null) {
      query = "";
    }
    queryFilteredPositions.clear();

    // On some devices, contacts have multiple rows with identical phone numbers. These numbers are
    // considered duplicates. Since the order might not be guaranteed, we compare all of the numbers
    // and hold onto the most qualified one as the one we want to display to the user.
    // See #getQualification for details on how qualification is determined.
    int previousMostQualifiedPosition = 0;
    String previousName = "";
    String previousMostQualifiedNumber = "";

    query = query.toLowerCase();
    cursor.moveToPosition(-1);

    while (cursor.moveToNext()) {
      int position = cursor.getPosition();
      String currentNumber = cursor.getString(Projections.PHONE_NUMBER);
      String currentName = cursor.getString(Projections.PHONE_DISPLAY_NAME);

      if (!previousName.equals(currentName)) {
        previousName = currentName;
        previousMostQualifiedNumber = currentNumber;
        previousMostQualifiedPosition = position;
      } else {
        // Since the contact name is the same, check if this number is a duplicate
        switch (getQualification(currentNumber, previousMostQualifiedNumber)) {
          case Qualification.CURRENT_MORE_QUALIFIED:
            // Number is a less qualified duplicate, ignore it.
            continue;
          case Qualification.NEW_NUMBER_IS_MORE_QUALIFIED:
            // If number wasn't filtered out before, remove it and add it's more qualified version.
            if (queryFilteredPositions.contains(previousMostQualifiedPosition)) {
              queryFilteredPositions.remove(previousMostQualifiedPosition);
              queryFilteredPositions.add(position);
            }
            previousMostQualifiedNumber = currentNumber;
            previousMostQualifiedPosition = position;
            continue;
          case Qualification.NUMBERS_ARE_NOT_DUPLICATES:
          default:
            previousMostQualifiedNumber = currentNumber;
            previousMostQualifiedPosition = position;
        }
      }

      if (TextUtils.isEmpty(query)
          || QueryFilteringUtil.nameMatchesT9Query(query, previousName)
          || QueryFilteringUtil.numberMatchesNumberQuery(query, previousMostQualifiedNumber)
          || previousName.contains(query)) {
        queryFilteredPositions.add(previousMostQualifiedPosition);
      }
    }
    currentPosition = 0;
    cursor.moveToFirst();
  }

  /**
   * @param number that may or may not be more qualified than the existing most qualified number
   * @param mostQualifiedNumber currently most qualified number associated with same contact
   * @return {@link Qualification} where the more qualified number is the number with the most
   *     digits. If the digits are the same, the number with the most formatting is more qualified.
   */
  private @Qualification int getQualification(String number, String mostQualifiedNumber) {
    // Ignore formatting
    String numberDigits = QueryFilteringUtil.digitsOnly(number);
    String qualifiedNumberDigits = QueryFilteringUtil.digitsOnly(mostQualifiedNumber);

    // If the numbers are identical, return version with more formatting
    if (qualifiedNumberDigits.equals(numberDigits)) {
      if (mostQualifiedNumber.length() >= number.length()) {
        return Qualification.CURRENT_MORE_QUALIFIED;
      } else {
        return Qualification.NEW_NUMBER_IS_MORE_QUALIFIED;
      }
    }

    // If one number is a suffix of another, then return the longer one.
    // If they are equal, then return the current most qualified number.
    if (qualifiedNumberDigits.endsWith(numberDigits)) {
      return Qualification.CURRENT_MORE_QUALIFIED;
    }
    if (numberDigits.endsWith(qualifiedNumberDigits)) {
      return Qualification.NEW_NUMBER_IS_MORE_QUALIFIED;
    }
    return Qualification.NUMBERS_ARE_NOT_DUPLICATES;
  }

  @Override
  public boolean moveToPosition(int position) {
    currentPosition = position;
    return currentPosition < getCount()
        && cursor.moveToPosition(queryFilteredPositions.get(currentPosition));
  }

  @Override
  public boolean move(int offset) {
    currentPosition += offset;
    return moveToPosition(currentPosition);
  }

  @Override
  public int getCount() {
    return queryFilteredPositions.size();
  }

  @Override
  public boolean isFirst() {
    return currentPosition == 0;
  }

  @Override
  public boolean isLast() {
    return currentPosition == getCount() - 1;
  }

  @Override
  public int getPosition() {
    return currentPosition;
  }

  @Override
  public boolean moveToFirst() {
    return moveToPosition(0);
  }

  @Override
  public boolean moveToLast() {
    return moveToPosition(getCount() - 1);
  }

  @Override
  public boolean moveToNext() {
    return moveToPosition(++currentPosition);
  }

  @Override
  public boolean moveToPrevious() {
    return moveToPosition(--currentPosition);
  }

  // Methods below simply call the corresponding method in cursor.
  @Override
  public boolean isBeforeFirst() {
    return cursor.isBeforeFirst();
  }

  @Override
  public boolean isAfterLast() {
    return cursor.isAfterLast();
  }

  @Override
  public int getColumnIndex(String columnName) {
    return cursor.getColumnIndex(columnName);
  }

  @Override
  public int getColumnIndexOrThrow(String columnName) {
    return cursor.getColumnIndexOrThrow(columnName);
  }

  @Override
  public String getColumnName(int columnIndex) {
    return cursor.getColumnName(columnIndex);
  }

  @Override
  public String[] getColumnNames() {
    return cursor.getColumnNames();
  }

  @Override
  public int getColumnCount() {
    return cursor.getColumnCount();
  }

  @Override
  public byte[] getBlob(int columnIndex) {
    return cursor.getBlob(columnIndex);
  }

  @Override
  public String getString(int columnIndex) {
    return cursor.getString(columnIndex);
  }

  @Override
  public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
    cursor.copyStringToBuffer(columnIndex, buffer);
  }

  @Override
  public short getShort(int columnIndex) {
    return cursor.getShort(columnIndex);
  }

  @Override
  public int getInt(int columnIndex) {
    return cursor.getInt(columnIndex);
  }

  @Override
  public long getLong(int columnIndex) {
    return cursor.getLong(columnIndex);
  }

  @Override
  public float getFloat(int columnIndex) {
    return cursor.getFloat(columnIndex);
  }

  @Override
  public double getDouble(int columnIndex) {
    return cursor.getDouble(columnIndex);
  }

  @Override
  public int getType(int columnIndex) {
    return cursor.getType(columnIndex);
  }

  @Override
  public boolean isNull(int columnIndex) {
    return cursor.isNull(columnIndex);
  }

  @Override
  public void deactivate() {
    cursor.deactivate();
  }

  @Override
  public boolean requery() {
    return cursor.requery();
  }

  @Override
  public void close() {
    cursor.close();
  }

  @Override
  public boolean isClosed() {
    return cursor.isClosed();
  }

  @Override
  public void registerContentObserver(ContentObserver observer) {
    cursor.registerContentObserver(observer);
  }

  @Override
  public void unregisterContentObserver(ContentObserver observer) {
    cursor.unregisterContentObserver(observer);
  }

  @Override
  public void registerDataSetObserver(DataSetObserver observer) {
    cursor.registerDataSetObserver(observer);
  }

  @Override
  public void unregisterDataSetObserver(DataSetObserver observer) {
    cursor.unregisterDataSetObserver(observer);
  }

  @Override
  public void setNotificationUri(ContentResolver cr, Uri uri) {
    cursor.setNotificationUri(cr, uri);
  }

  @Override
  public Uri getNotificationUri() {
    return cursor.getNotificationUri();
  }

  @Override
  public boolean getWantsAllOnMoveCalls() {
    return cursor.getWantsAllOnMoveCalls();
  }

  @Override
  public void setExtras(Bundle extras) {
    cursor.setExtras(extras);
  }

  @Override
  public Bundle getExtras() {
    return cursor.getExtras();
  }

  @Override
  public Bundle respond(Bundle extras) {
    return cursor.respond(extras);
  }
}
