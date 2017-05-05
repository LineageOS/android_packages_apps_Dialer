/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.voicemail.impl.sync;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.NonNull;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.common.Assert;
import com.android.voicemail.impl.Voicemail;
import java.util.ArrayList;
import java.util.List;

/** Construct queries to interact with the voicemails table. */
public class VoicemailsQueryHelper {
  static final String[] PROJECTION =
      new String[] {
        Voicemails._ID, // 0
        Voicemails.SOURCE_DATA, // 1
        Voicemails.IS_READ, // 2
        Voicemails.DELETED, // 3
        Voicemails.TRANSCRIPTION // 4
      };

  public static final int _ID = 0;
  public static final int SOURCE_DATA = 1;
  public static final int IS_READ = 2;
  public static final int DELETED = 3;
  public static final int TRANSCRIPTION = 4;

  static final String READ_SELECTION =
      Voicemails.DIRTY + "=1 AND " + Voicemails.DELETED + "!=1 AND " + Voicemails.IS_READ + "=1";
  static final String DELETED_SELECTION = Voicemails.DELETED + "=1";
  static final String ARCHIVED_SELECTION = Voicemails.ARCHIVED + "=0";

  private Context mContext;
  private ContentResolver mContentResolver;
  private Uri mSourceUri;

  public VoicemailsQueryHelper(Context context) {
    mContext = context;
    mContentResolver = context.getContentResolver();
    mSourceUri = VoicemailContract.Voicemails.buildSourceUri(mContext.getPackageName());
  }

  /**
   * Get all the local read voicemails that have not been synced to the server.
   *
   * @return A list of read voicemails.
   */
  public List<Voicemail> getReadVoicemails(@NonNull PhoneAccountHandle phoneAccountHandle) {
    return getLocalVoicemails(phoneAccountHandle, READ_SELECTION);
  }

  /**
   * Get all the locally deleted voicemails that have not been synced to the server.
   *
   * @return A list of deleted voicemails.
   */
  public List<Voicemail> getDeletedVoicemails(@NonNull PhoneAccountHandle phoneAccountHandle) {
    return getLocalVoicemails(phoneAccountHandle, DELETED_SELECTION);
  }

  /**
   * Get all voicemails locally stored.
   *
   * @return A list of all locally stored voicemails.
   */
  public List<Voicemail> getAllVoicemails(@NonNull PhoneAccountHandle phoneAccountHandle) {
    return getLocalVoicemails(phoneAccountHandle, null);
  }

  /**
   * Utility method to make queries to the voicemail database.
   *
   * <p>TODO(b/36588206) add PhoneAccountHandle filtering back
   *
   * @param selection A filter declaring which rows to return. {@code null} returns all rows.
   * @return A list of voicemails according to the selection statement.
   */
  private List<Voicemail> getLocalVoicemails(
      @NonNull PhoneAccountHandle unusedPhoneAccountHandle, String selection) {
    Cursor cursor = mContentResolver.query(mSourceUri, PROJECTION, selection, null, null);
    if (cursor == null) {
      return null;
    }
    try {
      List<Voicemail> voicemails = new ArrayList<Voicemail>();
      while (cursor.moveToNext()) {
        final long id = cursor.getLong(_ID);
        final String sourceData = cursor.getString(SOURCE_DATA);
        final boolean isRead = cursor.getInt(IS_READ) == 1;
        final String transcription = cursor.getString(TRANSCRIPTION);
        Voicemail voicemail =
            Voicemail.createForUpdate(id, sourceData)
                .setIsRead(isRead)
                .setTranscription(transcription)
                .build();
        voicemails.add(voicemail);
      }
      return voicemails;
    } finally {
      cursor.close();
    }
  }

  /**
   * Deletes a list of voicemails from the voicemail content provider.
   *
   * @param voicemails The list of voicemails to delete
   * @return The number of voicemails deleted
   */
  public int deleteFromDatabase(List<Voicemail> voicemails) {
    int count = voicemails.size();
    if (count == 0) {
      return 0;
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append(voicemails.get(i).getId());
    }

    String selectionStatement = String.format(Voicemails._ID + " IN (%s)", sb.toString());
    return mContentResolver.delete(Voicemails.CONTENT_URI, selectionStatement, null);
  }

  /** Utility method to delete a single voicemail that is not archived. */
  public void deleteNonArchivedFromDatabase(Voicemail voicemail) {
    mContentResolver.delete(
        Voicemails.CONTENT_URI,
        Voicemails._ID + "=? AND " + Voicemails.ARCHIVED + "= 0",
        new String[] {Long.toString(voicemail.getId())});
  }

  public int markReadInDatabase(List<Voicemail> voicemails) {
    int count = voicemails.size();
    for (int i = 0; i < count; i++) {
      markReadInDatabase(voicemails.get(i));
    }
    return count;
  }

  /** Utility method to mark single message as read. */
  public void markReadInDatabase(Voicemail voicemail) {
    Uri uri = ContentUris.withAppendedId(mSourceUri, voicemail.getId());
    ContentValues contentValues = new ContentValues();
    contentValues.put(Voicemails.IS_READ, "1");
    mContentResolver.update(uri, contentValues, null, null);
  }

  /**
   * Sends an update command to the voicemail content provider for a list of voicemails. From the
   * view of the provider, since the updater is the owner of the entry, a blank "update" means that
   * the voicemail source is indicating that the server has up-to-date information on the voicemail.
   * This flips the "dirty" bit to "0".
   *
   * @param voicemails The list of voicemails to update
   * @return The number of voicemails updated
   */
  public int markCleanInDatabase(List<Voicemail> voicemails) {
    int count = voicemails.size();
    for (int i = 0; i < count; i++) {
      markCleanInDatabase(voicemails.get(i));
    }
    return count;
  }

  /** Utility method to mark single message as clean. */
  public void markCleanInDatabase(Voicemail voicemail) {
    Uri uri = ContentUris.withAppendedId(mSourceUri, voicemail.getId());
    ContentValues contentValues = new ContentValues();
    mContentResolver.update(uri, contentValues, null, null);
  }

  /** Utility method to add a transcription to the voicemail. */
  public void updateWithTranscription(Voicemail voicemail, String transcription) {
    Uri uri = ContentUris.withAppendedId(mSourceUri, voicemail.getId());
    ContentValues contentValues = new ContentValues();
    contentValues.put(Voicemails.TRANSCRIPTION, transcription);
    mContentResolver.update(uri, contentValues, null, null);
  }

  /**
   * Voicemail is unique if the tuple of (phone account component name, phone account id, source
   * data) is unique. If the phone account is missing, we also consider this unique since it's
   * simply an "unknown" account.
   *
   * @param voicemail The voicemail to check if it is unique.
   * @return {@code true} if the voicemail is unique, {@code false} otherwise.
   */
  public boolean isVoicemailUnique(Voicemail voicemail) {
    Cursor cursor = null;
    PhoneAccountHandle phoneAccount = voicemail.getPhoneAccount();
    if (phoneAccount != null) {
      String phoneAccountComponentName = phoneAccount.getComponentName().flattenToString();
      String phoneAccountId = phoneAccount.getId();
      String sourceData = voicemail.getSourceData();
      if (phoneAccountComponentName == null || phoneAccountId == null || sourceData == null) {
        return true;
      }
      try {
        String whereClause =
            Voicemails.PHONE_ACCOUNT_COMPONENT_NAME
                + "=? AND "
                + Voicemails.PHONE_ACCOUNT_ID
                + "=? AND "
                + Voicemails.SOURCE_DATA
                + "=?";
        String[] whereArgs = {phoneAccountComponentName, phoneAccountId, sourceData};
        cursor = mContentResolver.query(mSourceUri, PROJECTION, whereClause, whereArgs, null);
        if (cursor.getCount() == 0) {
          return true;
        } else {
          return false;
        }
      } finally {
        if (cursor != null) {
          cursor.close();
        }
      }
    }
    return true;
  }

  /**
   * Marks voicemails in the local database as archived. This indicates that the voicemails from the
   * server were removed automatically to make space for new voicemails, and are stored locally on
   * the users devices, without a corresponding server copy.
   */
  public void markArchivedInDatabase(List<Voicemail> voicemails) {
    for (Voicemail voicemail : voicemails) {
      markArchiveInDatabase(voicemail);
    }
  }

  /** Utility method to mark single voicemail as archived. */
  public void markArchiveInDatabase(Voicemail voicemail) {
    Uri uri = ContentUris.withAppendedId(mSourceUri, voicemail.getId());
    ContentValues contentValues = new ContentValues();
    contentValues.put(Voicemails.ARCHIVED, "1");
    mContentResolver.update(uri, contentValues, null, null);
  }

  /** Find the oldest voicemails that are on the device, and also on the server. */
  @TargetApi(VERSION_CODES.M) // used for try with resources
  public List<Voicemail> oldestVoicemailsOnServer(int numVoicemails) {
    if (numVoicemails <= 0) {
      Assert.fail("Query for remote voicemails cannot be <= 0");
    }

    String sortAndLimit = "date ASC limit " + numVoicemails;

    try (Cursor cursor =
        mContentResolver.query(mSourceUri, PROJECTION, ARCHIVED_SELECTION, null, sortAndLimit)) {

      Assert.isNotNull(cursor);

      List<Voicemail> voicemails = new ArrayList<>();
      while (cursor.moveToNext()) {
        final long id = cursor.getLong(_ID);
        final String sourceData = cursor.getString(SOURCE_DATA);
        Voicemail voicemail = Voicemail.createForUpdate(id, sourceData).build();
        voicemails.add(voicemail);
      }

      if (voicemails.size() != numVoicemails) {
        Assert.fail(
            String.format(
                "voicemail count (%d) doesn't matched expected (%d)",
                voicemails.size(), numVoicemails));
      }
      return voicemails;
    }
  }
}
