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
import android.content.Context;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.util.ArraySet;
import android.text.TextUtils;
import android.util.ArrayMap;
import com.android.dialer.searchfragment.common.Projections;
import com.android.dialer.searchfragment.common.QueryFilteringUtil;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Wrapper for a cursor containing all on device contacts.
 *
 * <p>This cursor removes duplicate phone numbers associated with the same contact and can filter
 * contacts based on a query by calling {@link #filter(String, Context)}.
 */
final class ContactFilterCursor implements Cursor {

  private final Cursor cursor;
  // List of cursor ids that are valid for displaying after filtering.
  private final List<Integer> queryFilteredPositions = new ArrayList<>();
  private final ContactTernarySearchTree contactTree;

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
   * @param cursor with projection {@link Projections#CP2_PROJECTION}.
   * @param query to filter cursor results.
   * @param context of the app.
   */
  ContactFilterCursor(Cursor cursor, @Nullable String query, Context context) {
    this.cursor = createCursor(cursor);
    contactTree = buildContactSearchTree(context, this.cursor);
    filter(query, context);
  }

  /**
   * Returns a new cursor with contact information coalesced.
   *
   * <p>Here are some sample rows and columns that might exist in cp2 database:
   *
   * <ul>
   *   <li>display Name (William), contactID (202), mimeType (name), data1 (A Pixel)
   *   <li>display Name (William), contactID (202), mimeType (phone), data1 (+1 650-200-3333)
   *   <li>display Name (William), contactID (202), mimeType (phone), data1 (+1 540-555-6666)
   *   <li>display Name (William), contactID (202), mimeType (organization), data1 (Walmart)
   *   <li>display Name (William), contactID (202), mimeType (nickname), data1 (Will)
   * </ul>
   *
   * <p>These rows would be coalesced into new rows like so:
   *
   * <ul>
   *   <li>display Name (William), phoneNumber (+1 650-200-3333), organization (Walmart), nickname
   *       (Will)
   *   <li>display Name (William), phoneNumber (+1 540-555-6666), organization (Walmart), nickname
   *       (Will)
   * </ul>
   */
  private static Cursor createCursor(Cursor cursor) {
    // Convert cursor rows into Cp2Contacts
    List<Cp2Contact> cp2Contacts = new ArrayList<>();
    Map<Integer, Integer> contactIdsToPosition = new ArrayMap<>();
    cursor.moveToPosition(-1);
    while (cursor.moveToNext()) {
      Cp2Contact contact = Cp2Contact.fromCursor(cursor);
      cp2Contacts.add(contact);
      contactIdsToPosition.put(contact.contactId(), cursor.getPosition());
    }
    cursor.close();

    // Group then combine contact data
    List<Cp2Contact> coalescedContacts = new ArrayList<>();
    for (Integer contactId : contactIdsToPosition.keySet()) {
      List<Cp2Contact> duplicateContacts = getAllContactsWithContactId(contactId, cp2Contacts);
      coalescedContacts.addAll(coalesceContacts(duplicateContacts));
    }

    // Sort the contacts back into the exact same order they were inside of {@code cursor}
    Collections.sort(coalescedContacts, (o1, o2) -> compare(contactIdsToPosition, o1, o2));
    MatrixCursor newCursor = new MatrixCursor(Projections.CP2_PROJECTION, coalescedContacts.size());
    for (Cp2Contact contact : coalescedContacts) {
      newCursor.addRow(contact.toCursorRow());
    }
    return newCursor;
  }

  private static List<Cp2Contact> coalesceContacts(List<Cp2Contact> contactsWithSameContactId) {
    StringBuilder companyName = new StringBuilder();
    StringBuilder nickName = new StringBuilder();
    List<Cp2Contact> phoneContacts = new ArrayList<>();
    for (Cp2Contact contact : contactsWithSameContactId) {
      if (contact.mimeType().equals(Phone.CONTENT_ITEM_TYPE)) {
        phoneContacts.add(contact);
      } else if (contact.mimeType().equals(Organization.CONTENT_ITEM_TYPE)) {
        // Since a contact can have more than one company name but they aren't visible to the user
        // in our search UI, we can lazily concatenate them together to make them all searchable.
        companyName.append(" ").append(contact.companyName());
      } else if (contact.mimeType().equals(Nickname.CONTENT_ITEM_TYPE)) {
        // Since a contact can have more than one nickname but they aren't visible to the user
        // in our search UI, we can lazily concatenate them together to make them all searchable.
        nickName.append(" ").append(contact.nickName());
      }
    }

    removeDuplicatePhoneNumbers(phoneContacts);

    List<Cp2Contact> coalescedContacts = new ArrayList<>();
    for (Cp2Contact phoneContact : phoneContacts) {
      coalescedContacts.add(
          phoneContact
              .toBuilder()
              .setCompanyName(companyName.length() == 0 ? null : companyName.toString())
              .setNickName(nickName.length() == 0 ? null : nickName.toString())
              .build());
    }
    return coalescedContacts;
  }

  private static int compare(
      Map<Integer, Integer> contactIdsToPosition, Cp2Contact o1, Cp2Contact o2) {
    int position1 = contactIdsToPosition.get(o1.contactId());
    int position2 = contactIdsToPosition.get(o2.contactId());
    return Integer.compare(position1, position2);
  }

  private static void removeDuplicatePhoneNumbers(List<Cp2Contact> phoneContacts) {
    for (int i = 0; i < phoneContacts.size(); i++) {
      Cp2Contact contact1 = phoneContacts.get(i);
      for (int j = i + 1; j < phoneContacts.size(); /* don't iterate by default */ ) {
        Cp2Contact contact2 = phoneContacts.get(j);
        int qualification = getQualification(contact2.phoneNumber(), contact1.phoneNumber());
        if (qualification == Qualification.CURRENT_MORE_QUALIFIED) {
          phoneContacts.remove(contact2);
        } else if (qualification == Qualification.NEW_NUMBER_IS_MORE_QUALIFIED) {
          phoneContacts.remove(contact1);
          break;
        } else if (qualification == Qualification.NUMBERS_ARE_NOT_DUPLICATES) {
          // Keep both contacts
          j++;
        }
      }
    }
  }

  /**
   * @param number that may or may not be more qualified than the existing most qualified number
   * @param mostQualifiedNumber currently most qualified number associated with same contact
   * @return {@link Qualification} where the more qualified number is the number with the most
   *     digits. If the digits are the same, the number with the most formatting is more qualified.
   */
  private static @Qualification int getQualification(String number, String mostQualifiedNumber) {
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

  private static List<Cp2Contact> getAllContactsWithContactId(
      int contactId, List<Cp2Contact> contacts) {
    List<Cp2Contact> contactIdContacts = new ArrayList<>();
    for (Cp2Contact contact : contacts) {
      if (contact.contactId() == contactId) {
        contactIdContacts.add(contact);
      }
    }
    return contactIdContacts;
  }

  /**
   * Returns a ternary search trie based on the contact at the cursor's current position with the
   * following terms inserted:
   *
   * <ul>
   *   <li>Contact's whole display name, company name and nickname.
   *   <li>The T9 representations of those values
   *   <li>The T9 initials of those values
   *   <li>All possible substrings a contact's phone number
   * </ul>
   */
  private static ContactTernarySearchTree buildContactSearchTree(Context context, Cursor cursor) {
    ContactTernarySearchTree tree = new ContactTernarySearchTree();
    cursor.moveToPosition(-1);
    while (cursor.moveToNext()) {
      int position = cursor.getPosition();
      Set<String> queryMatches = new ArraySet<>();
      addMatches(context, queryMatches, cursor.getString(Projections.DISPLAY_NAME));
      addMatches(context, queryMatches, cursor.getString(Projections.COMPANY_NAME));
      addMatches(context, queryMatches, cursor.getString(Projections.NICKNAME));
      for (String query : queryMatches) {
        tree.put(query, position);
      }
      String number = QueryFilteringUtil.digitsOnly(cursor.getString(Projections.PHONE_NUMBER));
      Set<String> numberSubstrings = new ArraySet<>();
      numberSubstrings.add(number);
      for (int start = 0; start < number.length(); start++) {
        numberSubstrings.add(number.substring(start, number.length()));
      }
      for (String substring : numberSubstrings) {
        tree.put(substring, position);
      }
    }
    return tree;
  }

  /**
   * Returns a set containing:
   *
   * <ul>
   *   <li>The white space divided parts of phrase
   *   <li>The T9 representation of the white space divided parts of phrase
   *   <li>The T9 representation of the initials (i.e. first character of each part) of phrase
   * </ul>
   */
  private static void addMatches(Context context, Set<String> existingMatches, String phrase) {
    if (TextUtils.isEmpty(phrase)) {
      return;
    }
    String initials = "";
    phrase = phrase.toLowerCase(Locale.getDefault());
    existingMatches.add(phrase);
    for (String name : phrase.split("\\s")) {
      if (TextUtils.isEmpty(name)) {
        continue;
      }
      existingMatches.add(name);
      existingMatches.add(QueryFilteringUtil.getT9Representation(name, context));
      initials += name.charAt(0);
    }
    existingMatches.add(QueryFilteringUtil.getT9Representation(initials, context));
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
   *   <li>Its company contains the query
   * </ul>
   */
  public void filter(@Nullable String query, Context context) {
    if (query == null) {
      query = "";
    }
    queryFilteredPositions.clear();
    if (TextUtils.isEmpty(query)) {
      for (int i = 0; i < cursor.getCount(); i++) {
        queryFilteredPositions.add(i);
      }
    } else {
      queryFilteredPositions.addAll(contactTree.get(query.toLowerCase(Locale.getDefault())));
    }
    Collections.sort(queryFilteredPositions);
    currentPosition = 0;
    cursor.moveToFirst();
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
