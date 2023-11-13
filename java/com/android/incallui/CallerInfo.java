/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.incallui;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.ContactsUtils.UserType;
import com.android.contacts.common.util.TelephonyManagerUtils;
import com.android.dialer.logging.ContactLookupResult;
import com.android.dialer.phonenumbercache.ContactInfoHelper;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;

/**
 * Looks up caller information for the given phone number. This is intermediate data and should NOT
 * be used by any UI.
 */
public class CallerInfo {

  private static final String TAG = "CallerInfo";

  private static final String[] DEFAULT_PHONELOOKUP_PROJECTION =
      new String[] {
        PhoneLookup.CONTACT_ID,
        PhoneLookup.DISPLAY_NAME,
        PhoneLookup.LOOKUP_KEY,
        PhoneLookup.NUMBER,
        PhoneLookup.NORMALIZED_NUMBER,
        PhoneLookup.LABEL,
        PhoneLookup.TYPE,
        PhoneLookup.PHOTO_URI,
        PhoneLookup.CUSTOM_RINGTONE,
        PhoneLookup.SEND_TO_VOICEMAIL
      };

  /**
   * Please note that, any one of these member variables can be null, and any accesses to them
   * should be prepared to handle such a case.
   *
   * <p>Also, it is implied that phoneNumber is more often populated than name is, (think of calls
   * being dialed/received using numbers where names are not known to the device), so phoneNumber
   * should serve as a dependable fallback when name is unavailable.
   *
   * <p>One other detail here is that this CallerInfo object reflects information found on a
   * connection, it is an OUTPUT that serves mainly to display information to the user. In no way is
   * this object used as input to make a connection, so we can choose to display whatever
   * human-readable text makes sense to the user for a connection. This is especially relevant for
   * the phone number field, since it is the one field that is most likely exposed to the user.
   *
   * <p>As an example: 1. User dials "911" 2. Device recognizes that this is an emergency number 3.
   * We use the "Emergency Number" string instead of "911" in the phoneNumber field.
   *
   * <p>What we're really doing here is treating phoneNumber as an essential field here, NOT name.
   * We're NOT always guaranteed to have a name for a connection, but the number should be
   * displayable.
   */
  public String name;

  public String nameAlternative;
  public String phoneNumber;
  public String normalizedNumber;
  public String forwardingNumber;
  public String geoDescription;
  boolean shouldShowGeoDescription;
  public String cnapName;
  public int numberPresentation;
  public int namePresentation;
  public boolean contactExists;
  public ContactLookupResult.Type contactLookupResultType = ContactLookupResult.Type.NOT_FOUND;
  public String phoneLabel;
  /* Split up the phoneLabel into number type and label name */
  public int numberType;
  public String numberLabel;
  public int photoResource;
  // Contact ID, which will be 0 if a contact comes from the corp CP2.
  public long contactIdOrZero;
  public String lookupKeyOrNull;
  public boolean needUpdate;
  public Uri contactRefUri;
  public @UserType long userType;
  /**
   * Contact display photo URI. If a contact has no display photo but a thumbnail, it'll be the
   * thumbnail URI instead.
   */
  public Uri contactDisplayPhotoUri;
  // fields to hold individual contact preference data,
  // including the send to voicemail flag and the ringtone
  // uri reference.
  public Uri contactRingtoneUri;
  public boolean shouldSendToVoicemail;
  /**
   * Drawable representing the caller image. This is essentially a cache for the image data tied
   * into the connection / callerinfo object.
   *
   * <p>This might be a high resolution picture which is more suitable for full-screen image view
   * than for smaller icons used in some kinds of notifications.
   *
   * <p>The {@link #isCachedPhotoCurrent} flag indicates if the image data needs to be reloaded.
   */
  public Drawable cachedPhoto;
  /**
   * Bitmap representing the caller image which has possibly lower resolution than {@link
   * #cachedPhoto} and thus more suitable for icons (like notification icons).
   *
   * <p>In usual cases this is just down-scaled image of {@link #cachedPhoto}. If the down-scaling
   * fails, this will just become null.
   *
   * <p>The {@link #isCachedPhotoCurrent} flag indicates if the image data needs to be reloaded.
   */
  public Bitmap cachedPhotoIcon;
  /**
   * Boolean which indicates if {@link #cachedPhoto} and {@link #cachedPhotoIcon} is fresh enough.
   * If it is false, those images aren't pointing to valid objects.
   */
  public boolean isCachedPhotoCurrent;
  /**
   * String which holds the call subject sent as extra from the lower layers for this call. This is
   * used to display the no-caller ID reason for restricted/unknown number presentation.
   */
  public String callSubject;

  public String countryIso;

  private boolean isEmergency;
  private boolean isVoiceMail;

  public CallerInfo() {
    // TODO: Move all the basic initialization here?
    isEmergency = false;
    isVoiceMail = false;
    userType = ContactsUtils.USER_TYPE_CURRENT;
  }

  static String[] getDefaultPhoneLookupProjection() {
    return DEFAULT_PHONELOOKUP_PROJECTION;
  }

  /**
   * getCallerInfo given a Cursor.
   *
   * @param context the context used to retrieve string constants
   * @param contactRef the URI to attach to this CallerInfo object
   * @param cursor the first object in the cursor is used to build the CallerInfo object.
   * @return the CallerInfo which contains the caller id for the given number. The returned
   *     CallerInfo is null if no number is supplied.
   */
  public static CallerInfo getCallerInfo(Context context, Uri contactRef, Cursor cursor) {
    CallerInfo info = new CallerInfo();
    info.cachedPhoto = null;
    info.contactExists = false;
    info.contactRefUri = contactRef;
    info.isCachedPhotoCurrent = false;
    info.name = null;
    info.needUpdate = false;
    info.numberLabel = null;
    info.numberType = 0;
    info.phoneLabel = null;
    info.photoResource = 0;
    info.userType = ContactsUtils.USER_TYPE_CURRENT;

    Log.v(TAG, "getCallerInfo() based on cursor...");

    if (cursor == null || !cursor.moveToFirst()) {
      return info;
    }

    // TODO: photo_id is always available but not taken
    // care of here. Maybe we should store it in the
    // CallerInfo object as well.

    long contactId = 0L;
    int columnIndex;

    // Look for the number
    columnIndex = cursor.getColumnIndex(PhoneLookup.NUMBER);
    if (columnIndex != -1) {
      // The Contacts provider ignores special characters in phone numbers when searching for a
      // contact. For example, number "123" is considered a match with a contact with number "#123".
      // We need to check whether the result contains a number that truly matches the query and move
      // the cursor to that position before filling in the fields in CallerInfo.
      boolean hasNumberMatch =
          PhoneNumberHelper.updateCursorToMatchContactLookupUri(cursor, columnIndex, contactRef);
      if (hasNumberMatch) {
        info.phoneNumber = cursor.getString(columnIndex);
      } else {
        return info;
      }
    }

    // Look for the name
    columnIndex = cursor.getColumnIndex(PhoneLookup.DISPLAY_NAME);
    if (columnIndex != -1) {
      info.name = normalize(cursor.getString(columnIndex));
    }

    // Look for the normalized number
    columnIndex = cursor.getColumnIndex(PhoneLookup.NORMALIZED_NUMBER);
    if (columnIndex != -1) {
      info.normalizedNumber = cursor.getString(columnIndex);
    }

    // Look for the label/type combo
    columnIndex = cursor.getColumnIndex(PhoneLookup.LABEL);
    if (columnIndex != -1) {
      int typeColumnIndex = cursor.getColumnIndex(PhoneLookup.TYPE);
      if (typeColumnIndex != -1) {
        info.numberType = cursor.getInt(typeColumnIndex);
        info.numberLabel = cursor.getString(columnIndex);
        info.phoneLabel =
            Phone.getTypeLabel(context.getResources(), info.numberType, info.numberLabel)
                .toString();
      }
    }

    // cache the lookup key for later use to create lookup URIs
    columnIndex = cursor.getColumnIndex(PhoneLookup.LOOKUP_KEY);
    if (columnIndex != -1) {
      info.lookupKeyOrNull = cursor.getString(columnIndex);
    }

    // Look for the person_id.
    columnIndex = getColumnIndexForPersonId(contactRef, cursor);
    if (columnIndex != -1) {
      contactId = cursor.getLong(columnIndex);
      if (contactId != 0 && !Contacts.isEnterpriseContactId(contactId)) {
        info.contactIdOrZero = contactId;
        Log.v(TAG, "==> got info.contactIdOrZero: " + info.contactIdOrZero);
      }
    } else {
      // No valid columnIndex, so we can't look up person_id.
      Log.v(TAG, "Couldn't find contactId column for " + contactRef);
      // Watch out: this means that anything that depends on
      // person_id will be broken (like contact photo lookups in
      // the in-call UI, for example.)
    }

    // Display photo URI.
    columnIndex = cursor.getColumnIndex(PhoneLookup.PHOTO_URI);
    if ((columnIndex != -1) && (cursor.getString(columnIndex) != null)) {
      info.contactDisplayPhotoUri = Uri.parse(cursor.getString(columnIndex));
    } else {
      info.contactDisplayPhotoUri = null;
    }

    // look for the custom ringtone, create from the string stored
    // in the database.
    columnIndex = cursor.getColumnIndex(PhoneLookup.CUSTOM_RINGTONE);
    if ((columnIndex != -1) && (cursor.getString(columnIndex) != null)) {
      if (TextUtils.isEmpty(cursor.getString(columnIndex))) {
        // make it consistent with frameworks/base/.../CallerInfo.java
        info.contactRingtoneUri = Uri.EMPTY;
      } else {
        info.contactRingtoneUri = Uri.parse(cursor.getString(columnIndex));
      }
    } else {
      info.contactRingtoneUri = null;
    }

    // look for the send to voicemail flag, set it to true only
    // under certain circumstances.
    columnIndex = cursor.getColumnIndex(PhoneLookup.SEND_TO_VOICEMAIL);
    info.shouldSendToVoicemail = (columnIndex != -1) && ((cursor.getInt(columnIndex)) == 1);
    info.contactExists = true;
    info.contactLookupResultType = ContactLookupResult.Type.LOCAL_CONTACT;

    // Determine userType by directoryId and contactId
    final String directory =
        contactRef == null
            ? null
            : contactRef.getQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY);
    Long directoryId = null;
    if (directory != null) {
      try {
        directoryId = Long.parseLong(directory);
      } catch (NumberFormatException e) {
        // do nothing
      }
    }
    info.userType = ContactsUtils.determineUserType(directoryId, contactId);

    info.nameAlternative =
        ContactInfoHelper.lookUpDisplayNameAlternative(
            context, info.lookupKeyOrNull, info.userType, directoryId);
    cursor.close();

    return info;
  }

  /**
   * getCallerInfo given a URI, look up in the call-log database for the uri unique key.
   *
   * @param context the context used to get the ContentResolver
   * @param contactRef the URI used to lookup caller id
   * @return the CallerInfo which contains the caller id for the given number. The returned
   *     CallerInfo is null if no number is supplied.
   */
  private static CallerInfo getCallerInfo(Context context, Uri contactRef) {

    return getCallerInfo(
        context,
        contactRef,
        context.getContentResolver().query(contactRef, null, null, null, null));
  }

  /**
   * Performs another lookup if previous lookup fails and it's a SIP call and the peer's username is
   * all numeric. Look up the username as it could be a PSTN number in the contact database.
   *
   * @param context the query context
   * @param number the original phone number, could be a SIP URI
   * @param previousResult the result of previous lookup
   * @return previousResult if it's not the case
   */
  static CallerInfo doSecondaryLookupIfNecessary(
      Context context, String number, CallerInfo previousResult) {
    if (!previousResult.contactExists && PhoneNumberHelper.isUriNumber(number)) {
      String username = PhoneNumberHelper.getUsernameFromUriNumber(number);
      if (PhoneNumberUtils.isGlobalPhoneNumber(username)) {
        previousResult =
            getCallerInfo(
                context,
                Uri.withAppendedPath(
                    PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI, Uri.encode(username)));
      }
    }
    return previousResult;
  }

  // Accessors

  private static String normalize(String s) {
    if (s == null || s.length() > 0) {
      return s;
    } else {
      return null;
    }
  }

  /**
   * Returns the column index to use to find the "person_id" field in the specified cursor, based on
   * the contact URI that was originally queried.
   *
   * <p>This is a helper function for the getCallerInfo() method that takes a Cursor. Looking up the
   * person_id is nontrivial (compared to all the other CallerInfo fields) since the column we need
   * to use depends on what query we originally ran.
   *
   * <p>Watch out: be sure to not do any database access in this method, since it's run from the UI
   * thread (see comments below for more info.)
   *
   * @return the columnIndex to use (with cursor.getLong()) to get the person_id, or -1 if we
   *     couldn't figure out what colum to use.
   *     <p>TODO: Add a unittest for this method. (This is a little tricky to test, since we'll need
   *     a live contacts database to test against, preloaded with at least some phone numbers and
   *     SIP addresses. And we'll probably have to hardcode the column indexes we expect, so the
   *     test might break whenever the contacts schema changes. But we can at least make sure we
   *     handle all the URI patterns we claim to, and that the mime types match what we expect...)
   */
  private static int getColumnIndexForPersonId(Uri contactRef, Cursor cursor) {
    // TODO: This is pretty ugly now, see bug 2269240 for
    // more details. The column to use depends upon the type of URL:
    // - content://com.android.contacts/data/phones ==> use the "contact_id" column
    // - content://com.android.contacts/phone_lookup ==> use the "_ID" column
    // - content://com.android.contacts/data ==> use the "contact_id" column
    // If it's none of the above, we leave columnIndex=-1 which means
    // that the person_id field will be left unset.
    //
    // The logic here *used* to be based on the mime type of contactRef
    // (for example Phone.CONTENT_ITEM_TYPE would tell us to use the
    // RawContacts.CONTACT_ID column).  But looking up the mime type requires
    // a call to context.getContentResolver().getType(contactRef), which
    // isn't safe to do from the UI thread since it can cause an ANR if
    // the contacts provider is slow or blocked (like during a sync.)
    //
    // So instead, figure out the column to use for person_id by just
    // looking at the URI itself.

    Log.v(TAG, "- getColumnIndexForPersonId: contactRef URI = '" + contactRef + "'...");
    // Warning: Do not enable the following logging (due to ANR risk.)
    // if (VDBG) Rlog.v(TAG, "- MIME type: "
    //                 + context.getContentResolver().getType(contactRef));

    String url = contactRef.toString();
    String columnName = null;
    if (url.startsWith("content://com.android.contacts/data/phones")) {
      // Direct lookup in the Phone table.
      // MIME type: Phone.CONTENT_ITEM_TYPE (= "vnd.android.cursor.item/phone_v2")
      Log.v(TAG, "'data/phones' URI; using RawContacts.CONTACT_ID");
      columnName = RawContacts.CONTACT_ID;
    } else if (url.startsWith("content://com.android.contacts/data")) {
      // Direct lookup in the Data table.
      // MIME type: Data.CONTENT_TYPE (= "vnd.android.cursor.dir/data")
      Log.v(TAG, "'data' URI; using Data.CONTACT_ID");
      // (Note Data.CONTACT_ID and RawContacts.CONTACT_ID are equivalent.)
      columnName = Data.CONTACT_ID;
    } else if (url.startsWith("content://com.android.contacts/phone_lookup")) {
      // Lookup in the PhoneLookup table, which provides "fuzzy matching"
      // for phone numbers.
      // MIME type: PhoneLookup.CONTENT_TYPE (= "vnd.android.cursor.dir/phone_lookup")
      Log.v(TAG, "'phone_lookup' URI; using PhoneLookup._ID");
      columnName = PhoneLookup.CONTACT_ID;
    } else {
      Log.v(TAG, "Unexpected prefix for contactRef '" + url + "'");
    }
    int columnIndex = (columnName != null) ? cursor.getColumnIndex(columnName) : -1;
    Log.v(
        TAG,
        "==> Using column '"
            + columnName
            + "' (columnIndex = "
            + columnIndex
            + ") for person_id lookup...");
    return columnIndex;
  }

  /** @return true if the caller info is an emergency number. */
  public boolean isEmergencyNumber() {
    return isEmergency;
  }

  /** @return true if the caller info is a voicemail number. */
  public boolean isVoiceMailNumber() {
    return isVoiceMail;
  }

  /**
   * Mark this CallerInfo as an emergency call.
   *
   * @param context To lookup the localized 'Emergency Number' string.
   * @return this instance.
   */
  /* package */ CallerInfo markAsEmergency(Context context) {
    name = context.getString(R.string.emergency_number);
    phoneNumber = null;

    isEmergency = true;
    return this;
  }

  /**
   * Mark this CallerInfo as a voicemail call. The voicemail label is obtained from the telephony
   * manager. Caller must hold the READ_PHONE_STATE permission otherwise the phoneNumber will be set
   * to null.
   *
   * @return this instance.
   */
  /* package */ CallerInfo markAsVoiceMail(Context context) {
    isVoiceMail = true;

    try {
      // For voicemail calls, we display the voice mail tag
      // instead of the real phone number in the "number"
      // field.
      name = TelephonyManagerUtils.getVoiceMailAlphaTag(context);
      phoneNumber = null;
    } catch (SecurityException se) {
      // Should never happen: if this process does not have
      // permission to retrieve VM tag, it should not have
      // permission to retrieve VM number and would not call
      // this method.
      // Leave phoneNumber untouched.
      Log.e(TAG, "Cannot access VoiceMail.", se);
    }
    // TODO: There is no voicemail picture?
    // photoResource = android.R.drawable.badge_voicemail;
    return this;
  }

  /**
   * Updates this CallerInfo's geoDescription field, based on the raw phone number in the
   * phoneNumber field.
   *
   * <p>(Note that the various getCallerInfo() methods do *not* set the geoDescription
   * automatically; you need to call this method explicitly to get it.)
   *
   * @param context the context used to look up the current locale / country
   * @param fallbackNumber if this CallerInfo's phoneNumber field is empty, this specifies a
   *     fallback number to use instead.
   */
  public void updateGeoDescription(Context context, String fallbackNumber) {
    String number = TextUtils.isEmpty(phoneNumber) ? fallbackNumber : phoneNumber;
    geoDescription = PhoneNumberHelper.getGeoDescription(context, number, countryIso);
  }

  /** @return a string debug representation of this instance. */
  @Override
  public String toString() {
    // Warning: never check in this file with VERBOSE_DEBUG = true
    // because that will result in PII in the system log.
    final boolean VERBOSE_DEBUG = false;

    if (VERBOSE_DEBUG) {
      return new StringBuilder(384)
          .append(super.toString() + " { ")
          .append("\nname: " + name)
          .append("\nphoneNumber: " + phoneNumber)
          .append("\nnormalizedNumber: " + normalizedNumber)
          .append("\forwardingNumber: " + forwardingNumber)
          .append("\ngeoDescription: " + geoDescription)
          .append("\ncnapName: " + cnapName)
          .append("\nnumberPresentation: " + numberPresentation)
          .append("\nnamePresentation: " + namePresentation)
          .append("\ncontactExists: " + contactExists)
          .append("\nphoneLabel: " + phoneLabel)
          .append("\nnumberType: " + numberType)
          .append("\nnumberLabel: " + numberLabel)
          .append("\nphotoResource: " + photoResource)
          .append("\ncontactIdOrZero: " + contactIdOrZero)
          .append("\nneedUpdate: " + needUpdate)
          .append("\ncontactRefUri: " + contactRefUri)
          .append("\ncontactRingtoneUri: " + contactRingtoneUri)
          .append("\ncontactDisplayPhotoUri: " + contactDisplayPhotoUri)
          .append("\nshouldSendToVoicemail: " + shouldSendToVoicemail)
          .append("\ncachedPhoto: " + cachedPhoto)
          .append("\nisCachedPhotoCurrent: " + isCachedPhotoCurrent)
          .append("\nemergency: " + isEmergency)
          .append("\nvoicemail: " + isVoiceMail)
          .append("\nuserType: " + userType)
          .append(" }")
          .toString();
    } else {
      return new StringBuilder(128)
          .append(super.toString() + " { ")
          .append("name " + ((name == null) ? "null" : "non-null"))
          .append(", phoneNumber " + ((phoneNumber == null) ? "null" : "non-null"))
          .append(" }")
          .toString();
    }
  }
}
