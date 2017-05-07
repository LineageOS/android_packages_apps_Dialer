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

package com.android.dialer.backup;

import android.annotation.TargetApi;
import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.FullBackupDataOutput;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.os.ParcelFileDescriptor;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Voicemails;
import android.telecom.PhoneAccountHandle;
import android.util.Pair;
import com.android.dialer.common.Assert;
import com.android.dialer.common.ConfigProviderBindings;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.telecom.TelecomUtil;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * The Dialer backup agent to backup voicemails, and files under files, shared prefs and databases
 */
public class DialerBackupAgent extends BackupAgent {
  // File names suffix for backup/restore.
  private static final String VOICEMAIL_BACKUP_FILE_SUFFIX = "_voicemail_backup.proto";
  // File name formats for backup. It looks like 000000_voicemail_backup.proto, 0000001...
  private static final String VOICEMAIL_BACKUP_FILE_FORMAT = "%06d" + VOICEMAIL_BACKUP_FILE_SUFFIX;
  // Order by Date entries from database. We start backup from the newest.
  private static final String ORDER_BY_DATE = "date DESC";
  // Voicemail Uri Column
  public static final String VOICEMAIL_URI = "voicemail_uri";
  // Voicemail packages to backup
  public static final String VOICEMAIL_SOURCE_PACKAGE = "com.google.android.dialer";

  private long voicemailsBackedupSoFar = 0;
  private long sizeOfVoicemailsBackedupSoFar = 0;
  private boolean maxVoicemailBackupReached = false;

  /**
   * onBackup is used for Key/Value backup. Since we are using Dolly/Android Auto backup, we do not
   * need to implement this method and Dolly should not be calling this. Instead Dolly will be
   * calling onFullBackup.
   */
  @Override
  public void onBackup(
      ParcelFileDescriptor parcelFileDescriptor,
      BackupDataOutput backupDataOutput,
      ParcelFileDescriptor parcelFileDescriptor1)
      throws IOException {
    Logger.get(this).logImpression(DialerImpression.Type.BACKUP_ON_BACKUP);
    Assert.fail("Android Backup should not call DialerBackupAgent.onBackup");
  }

  /**
   * onRestore is used for Key/Value restore. Since we are using Dolly/Android Auto backup/restore,
   * we need to implement this method only for backwards compatibility. Dolly should be calling
   * onFileRestore during its restore.
   */
  @Override
  public void onRestore(
      BackupDataInput backupDataInput, int i, ParcelFileDescriptor parcelFileDescriptor)
      throws IOException {
    Logger.get(this).logImpression(DialerImpression.Type.BACKUP_ON_RESTORE);
  }

  @TargetApi(VERSION_CODES.M)
  @Override
  public void onFullBackup(FullBackupDataOutput data) throws IOException {
    Logger.get(this).logImpression(DialerImpression.Type.BACKUP_ON_FULL_BACKUP);
    LogUtil.i("DialerBackupAgent.onFullBackup", "performing dialer backup");
    boolean autoBackupEnabled =
        ConfigProviderBindings.get(this).getBoolean("enable_autobackup", true);
    boolean vmBackupEnabled = ConfigProviderBindings.get(this).getBoolean("enable_vm_backup", true);
    List<PhoneAccountHandle> phoneAccountsToArchive =
        DialerBackupUtils.getPhoneAccountsToArchive(this);

    if (autoBackupEnabled) {
      if (!maxVoicemailBackupReached && vmBackupEnabled && !phoneAccountsToArchive.isEmpty()) {
        voicemailsBackedupSoFar = 0;
        sizeOfVoicemailsBackedupSoFar = 0;

        LogUtil.i("DialerBackupAgent.onFullBackup", "autoBackup is enabled");
        ContentResolver contentResolver = getContentResolver();
        int limit = 1000;

        Uri uri =
            TelecomUtil.getCallLogUri(this)
                .buildUpon()
                .appendQueryParameter(Calls.LIMIT_PARAM_KEY, Integer.toString(limit))
                .build();

        LogUtil.i("DialerBackupAgent.onFullBackup", "backing up from: " + uri);

        try (Cursor cursor =
            contentResolver.query(
                uri,
                null,
                String.format(
                    "(%s = ? AND deleted = 0 AND  %s = ? AND ?)",
                    Calls.TYPE, Voicemails.SOURCE_PACKAGE),
                new String[] {
                  Integer.toString(CallLog.Calls.VOICEMAIL_TYPE),
                  VOICEMAIL_SOURCE_PACKAGE,
                  DialerBackupUtils.getPhoneAccountClause(phoneAccountsToArchive)
                },
                ORDER_BY_DATE,
                null)) {

          if (cursor == null) {
            LogUtil.i("DialerBackupAgent.onFullBackup", "cursor was null");
            return;
          }

          LogUtil.i("DialerBackupAgent.onFullBackup", "cursor count: " + cursor.getCount());
          if (cursor.moveToFirst()) {
            int fileNum = 0;
            do {
              backupRow(
                  data, cursor, String.format(Locale.US, VOICEMAIL_BACKUP_FILE_FORMAT, fileNum++));
            } while (cursor.moveToNext() && !maxVoicemailBackupReached);
          } else {
            LogUtil.i("DialerBackupAgent.onFullBackup", "cursor.moveToFirst failed");
          }
        }
      }
      LogUtil.i(
          "DialerBackupAgent.onFullBackup",
          "vm files backed up: %d, vm size backed up:%d, "
              + "max vm backup reached:%b, vm backup enabled:%b phone accounts to archive: %d",
          voicemailsBackedupSoFar,
          sizeOfVoicemailsBackedupSoFar,
          maxVoicemailBackupReached,
          vmBackupEnabled,
          phoneAccountsToArchive.size());
      super.onFullBackup(data);
      Logger.get(this).logImpression(DialerImpression.Type.BACKUP_FULL_BACKED_UP);
    } else {
      Logger.get(this).logImpression(DialerImpression.Type.BACKUP_ON_BACKUP_DISABLED);
      LogUtil.i("DialerBackupAgent.onFullBackup", "autoBackup is disabled");
    }
  }

  private void backupRow(FullBackupDataOutput data, Cursor cursor, String fileName)
      throws IOException {

    VoicemailInfo cursorRowInProto =
        DialerBackupUtils.convertVoicemailCursorRowToProto(cursor, getContentResolver());

    File file = new File(getFilesDir(), fileName);
    DialerBackupUtils.writeProtoToFile(file, cursorRowInProto);

    if (sizeOfVoicemailsBackedupSoFar + file.length()
        > DialerBackupUtils.maxVoicemailSizeToBackup) {
      Logger.get(this).logImpression(DialerImpression.Type.BACKUP_MAX_VM_BACKUP_REACHED);
      maxVoicemailBackupReached = true;
      file.delete();
      return;
    }

    backupFile(file, data);
  }

  // TODO: Write to FullBackupDataOutput directly (b/33849960)
  private void backupFile(File file, FullBackupDataOutput data) throws IOException {
    try {
      super.fullBackupFile(file, data);
      sizeOfVoicemailsBackedupSoFar = sizeOfVoicemailsBackedupSoFar + file.length();
      voicemailsBackedupSoFar++;
      Logger.get(this).logImpression(DialerImpression.Type.BACKUP_VOICEMAIL_BACKED_UP);
      LogUtil.i("DialerBackupAgent.backupFile", "file backed up:" + file.getAbsolutePath());
    } finally {
      file.delete();
    }
  }

  // Being tracked in b/33839952
  @Override
  public void onQuotaExceeded(long backupDataBytes, long quotaBytes) {
    Logger.get(this).logImpression(DialerImpression.Type.BACKUP_ON_QUOTA_EXCEEDED);
    LogUtil.i("DialerBackupAgent.onQuotaExceeded", "does nothing");
  }

  @TargetApi(VERSION_CODES.M)
  @Override
  public void onRestoreFile(
      ParcelFileDescriptor data, long size, File destination, int type, long mode, long mtime)
      throws IOException {
    LogUtil.i("DialerBackupAgent.onRestoreFile", "size:" + size + " destination: " + destination);

    String fileName = destination.getName();
    LogUtil.i("DialerBackupAgent.onRestoreFile", "file name: " + fileName);

    if (ConfigProviderBindings.get(this).getBoolean("enable_autobackup", true)) {
      if (fileName.endsWith(VOICEMAIL_BACKUP_FILE_SUFFIX)
          && ConfigProviderBindings.get(this).getBoolean("enable_vm_restore", true)) {
        if (DialerBackupUtils.canRestoreVoicemails(getContentResolver(), this)) {
          try {
            super.onRestoreFile(data, size, destination, type, mode, mtime);
            restoreVoicemail(destination);
            destination.delete();
          } catch (IOException e) {
            Logger.get(this).logImpression(DialerImpression.Type.BACKUP_ON_RESTORE_IO_EXCEPTION);
            LogUtil.e(
                "DialerBackupAgent.onRestoreFile",
                "could not restore voicemail - IOException: ",
                e);
          }
        } else {
          LogUtil.i(
              "DialerBackupAgent.onRestoreFile", "build does not support restoring voicemails");
        }

      } else {
        super.onRestoreFile(data, size, destination, type, mode, mtime);
        LogUtil.i("DialerBackupAgent.onRestoreFile", "restored: " + fileName);
        Logger.get(this).logImpression(DialerImpression.Type.BACKUP_RESTORED_FILE);
      }
    } else {
      Logger.get(this).logImpression(DialerImpression.Type.BACKUP_ON_RESTORE_DISABLED);
      LogUtil.i("DialerBackupAgent.onRestoreFile", "autoBackup is disabled");
    }
  }

  @Override
  public void onRestoreFinished() {
    Logger.get(this).logImpression(DialerImpression.Type.BACKUP_ON_RESTORE_FINISHED);
    LogUtil.i("DialerBackupAgent.onRestoreFinished", "do nothing");
  }

  @TargetApi(VERSION_CODES.M)
  private void restoreVoicemail(File file) throws IOException {
    Pair<ContentValues, byte[]> pair =
        DialerBackupUtils.convertVoicemailProtoFileToContentValueAndAudioBytes(
            file, getApplicationContext());

    if (pair == null) {
      LogUtil.i("DialerBackupAgent.restoreVoicemail", "not restoring VM due to duplicate");
      Logger.get(this)
          .logImpression(DialerImpression.Type.BACKUP_ON_RESTORE_VM_DUPLICATE_NOT_RESTORING);
      return;
    }

    // TODO: Uniquely identify backup agent as the creator of this voicemail b/34084298
    try (OutputStream restoreStream =
        getContentResolver()
            .openOutputStream(
                getContentResolver()
                    .insert(VoicemailContract.Voicemails.CONTENT_URI, pair.first))) {
      DialerBackupUtils.copyAudioBytesToContentUri(pair.second, restoreStream);
      Logger.get(this).logImpression(DialerImpression.Type.BACKUP_RESTORED_VOICEMAIL);
    }
  }
}
