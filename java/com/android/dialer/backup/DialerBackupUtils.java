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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Pair;
import com.android.dialer.common.Assert;
import com.android.dialer.common.ConfigProviderBindings;
import com.android.dialer.common.LogUtil;
import com.android.voicemail.VoicemailComponent;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Helper functions for DialerBackupAgent */
public class DialerBackupUtils {
  // Backup voicemails up to 20MB
  static long maxVoicemailSizeToBackup = 20000000L;
  static final String RESTORED_COLUMN = "restored";

  private DialerBackupUtils() {}

  public static void copyAudioBytesToContentUri(
      @NonNull byte[] audioBytesArray, @NonNull OutputStream restoreStream) throws IOException {
    LogUtil.i("DialerBackupUtils.copyStream", "audioByteArray length: " + audioBytesArray.length);

    ByteArrayInputStream decodedStream = new ByteArrayInputStream(audioBytesArray);
    LogUtil.i(
        "DialerBackupUtils.copyStream", "decodedStream.available: " + decodedStream.available());

    ByteStreams.copy(decodedStream, restoreStream);
  }

  public static @Nullable ByteString audioStreamToByteString(@NonNull InputStream stream)
      throws IOException {
    if (stream.available() > 0) {
      return ByteString.readFrom(stream);
    } else {
      LogUtil.i("DialerBackupUtils.audioStreamToByteArray", "no audio stream to backup");
    }
    return ByteString.EMPTY;
  }

  public static void writeProtoToFile(@NonNull File file, @NonNull VoicemailInfo voicemailInfo)
      throws IOException {
    LogUtil.i(
        "DialerBackupUtils.writeProtoToFile",
        "backup " + voicemailInfo + " to " + file.getAbsolutePath());

    byte[] bytes = voicemailInfo.toByteArray();
    Files.write(bytes, file);
  }

  /** Only restore voicemails that have the restored column in calllog (NMR2+ builds) */
  @TargetApi(VERSION_CODES.M)
  public static boolean canRestoreVoicemails(ContentResolver contentResolver, Context context) {
    try (Cursor cursor = contentResolver.query(Voicemails.CONTENT_URI, null, null, null, null)) {
      // Restored column only exists in NMR2 and above builds.
      if (cursor.getColumnIndex(RESTORED_COLUMN) != -1) {
        LogUtil.i("DialerBackupUtils.canRestoreVoicemails", "Build supports restore");
        return true;
      } else {
        LogUtil.i("DialerBackupUtils.canRestoreVoicemails", "Build does not support restore");
        return false;
      }
    }
  }

  public static VoicemailInfo protoFileToVoicemailInfo(@NonNull File file) throws IOException {
    byte[] byteArray = Files.toByteArray(file);
    return VoicemailInfo.parseFrom(byteArray);
  }

  @TargetApi(VERSION_CODES.M)
  public static VoicemailInfo convertVoicemailCursorRowToProto(
      @NonNull Cursor cursor, @NonNull ContentResolver contentResolver) throws IOException {

    VoicemailInfo.Builder voicemailInfo = VoicemailInfo.newBuilder();

    for (int i = 0; i < cursor.getColumnCount(); ++i) {
      String name = cursor.getColumnName(i);
      String value = cursor.getString(i);

      LogUtil.i(
          "DialerBackupUtils.convertVoicemailCursorRowToProto",
          "column index: %d, column name: %s, column value: %s",
          i,
          name,
          value);

      switch (name) {
        case Voicemails.DATE:
          voicemailInfo.setDate(value);
          break;
        case Voicemails.DELETED:
          voicemailInfo.setDeleted(value);
          break;
        case Voicemails.DIRTY:
          voicemailInfo.setDirty(value);
          break;
        case Voicemails.DIR_TYPE:
          voicemailInfo.setDirType(value);
          break;
        case Voicemails.DURATION:
          voicemailInfo.setDuration(value);
          break;
        case Voicemails.HAS_CONTENT:
          voicemailInfo.setHasContent(value);
          break;
        case Voicemails.IS_READ:
          voicemailInfo.setIsRead(value);
          break;
        case Voicemails.ITEM_TYPE:
          voicemailInfo.setItemType(value);
          break;
        case Voicemails.LAST_MODIFIED:
          voicemailInfo.setLastModified(value);
          break;
        case Voicemails.MIME_TYPE:
          voicemailInfo.setMimeType(value);
          break;
        case Voicemails.NUMBER:
          voicemailInfo.setNumber(value);
          break;
        case Voicemails.PHONE_ACCOUNT_COMPONENT_NAME:
          voicemailInfo.setPhoneAccountComponentName(value);
          break;
        case Voicemails.PHONE_ACCOUNT_ID:
          voicemailInfo.setPhoneAccountId(value);
          break;
        case Voicemails.SOURCE_DATA:
          voicemailInfo.setSourceData(value);
          break;
        case Voicemails.SOURCE_PACKAGE:
          voicemailInfo.setSourcePackage(value);
          break;
        case Voicemails.TRANSCRIPTION:
          voicemailInfo.setTranscription(value);
          break;
        case DialerBackupAgent.VOICEMAIL_URI:
          try (InputStream audioStream = contentResolver.openInputStream(Uri.parse(value))) {
            voicemailInfo.setEncodedVoicemailKey(audioStreamToByteString(audioStream));
          }
          break;
        default:
          LogUtil.i(
              "DialerBackupUtils.convertVoicemailCursorRowToProto",
              "Not backing up column: %s, with value: %s",
              name,
              value);
          break;
      }
    }
    return voicemailInfo.build();
  }

  public static Pair<ContentValues, byte[]> convertVoicemailProtoFileToContentValueAndAudioBytes(
      @NonNull File file, Context context) throws IOException {

    VoicemailInfo voicemailInfo = DialerBackupUtils.protoFileToVoicemailInfo(file);
    LogUtil.i(
        "DialerBackupUtils.convertVoicemailProtoFileToContentValueAndEncodedAudio",
        "file name: "
            + file.getName()
            + " voicemailInfo size: "
            + voicemailInfo.getSerializedSize());

    if (isDuplicate(context, voicemailInfo)) {
      LogUtil.i(
          "DialerBackupUtils.convertVoicemailProtoFileToContentValueAndEncodedAudio",
          "voicemail already exists");
      return null;
    } else {
      ContentValues contentValues = new ContentValues();

      if (voicemailInfo.hasDate()) {
        contentValues.put(Voicemails.DATE, voicemailInfo.getDate());
      }
      if (voicemailInfo.hasDeleted()) {
        contentValues.put(Voicemails.DELETED, voicemailInfo.getDeleted());
      }
      if (!voicemailInfo.hasDirty()) {
        contentValues.put(Voicemails.DIRTY, voicemailInfo.getDirty());
      }
      if (!voicemailInfo.hasDuration()) {
        contentValues.put(Voicemails.DURATION, voicemailInfo.getDuration());
      }
      if (!voicemailInfo.hasIsRead()) {
        contentValues.put(Voicemails.IS_READ, voicemailInfo.getIsRead());
      }
      if (!voicemailInfo.hasLastModified()) {
        contentValues.put(Voicemails.LAST_MODIFIED, voicemailInfo.getLastModified());
      }
      if (!voicemailInfo.hasMimeType()) {
        contentValues.put(Voicemails.MIME_TYPE, voicemailInfo.getMimeType());
      }
      if (!voicemailInfo.hasNumber()) {
        contentValues.put(Voicemails.NUMBER, voicemailInfo.getNumber());
      }
      if (!voicemailInfo.hasPhoneAccountComponentName()) {
        contentValues.put(
            Voicemails.PHONE_ACCOUNT_COMPONENT_NAME, voicemailInfo.getPhoneAccountComponentName());
      }
      if (!voicemailInfo.hasPhoneAccountId()) {
        contentValues.put(Voicemails.PHONE_ACCOUNT_ID, voicemailInfo.getPhoneAccountId());
      }
      if (!voicemailInfo.hasSourceData()) {
        contentValues.put(Voicemails.SOURCE_DATA, voicemailInfo.getSourceData());
      }
      if (!voicemailInfo.hasSourcePackage()) {
        contentValues.put(Voicemails.SOURCE_PACKAGE, voicemailInfo.getSourcePackage());
      }
      if (!voicemailInfo.hasTranscription()) {
        contentValues.put(Voicemails.TRANSCRIPTION, voicemailInfo.getTranscription());
      }
      contentValues.put(VoicemailContract.Voicemails.HAS_CONTENT, 1);
      contentValues.put(RESTORED_COLUMN, "1");
      contentValues.put(Voicemails.SOURCE_PACKAGE, getSourcePackage(context, voicemailInfo));

      LogUtil.i(
          "DialerBackupUtils.convertVoicemailProtoFileToContentValueAndEncodedAudio",
          "cv: " + contentValues);

      return Pair.create(contentValues, voicemailInfo.getEncodedVoicemailKey().toByteArray());
    }
  }

  /**
   * We should be using the system package name as the source package if there is no endless VM/VM
   * archive present on the device. This is to separate pre-O (no endless VM) and O+ (endless VM)
   * devices. This ensures that the source of truth for VMs is the VM server when endless VM is not
   * enabled, and when endless VM/archived VMs is present, the source of truth for VMs is the device
   * itself.
   */
  private static String getSourcePackage(Context context, VoicemailInfo voicemailInfo) {
    if (ConfigProviderBindings.get(context)
        .getBoolean("voicemail_restore_force_system_source_package", false)) {
      LogUtil.i("DialerBackupUtils.getSourcePackage", "forcing system source package");
      return "com.android.phone";
    }
    if (ConfigProviderBindings.get(context)
        .getBoolean("voicemail_restore_check_archive_for_source_package", true)) {
      if ("1".equals(voicemailInfo.getArchived())) {
        LogUtil.i(
            "DialerBackupUtils.getSourcePackage",
            "voicemail was archived, using app source package");
        // Using our app's source package will prevent the archived voicemail from being deleted by
        // the system when it syncs with the voicemail server. In most cases the user will not see
        // duplicate voicemails because this voicemail was archived and likely deleted from the
        // voicemail server.
        return context.getPackageName();
      } else {
        // Use the system source package. This means that if the voicemail is not present on the
        // voicemail server then the system will delete it when it syncs.
        LogUtil.i(
            "DialerBackupUtils.getSourcePackage",
            "voicemail was not archived, using system source package");
        return "com.android.phone";
      }
    }
    // Use our app's source package. This means that if the system syncs voicemail from the server
    // the user could potentially get duplicate voicemails.
    LogUtil.i("DialerBackupUtils.getSourcePackage", "defaulting to using app source package");
    return context.getPackageName();
  }

  @TargetApi(VERSION_CODES.M)
  private static boolean isDuplicate(Context context, VoicemailInfo voicemailInfo) {
    // This checks for VM that might already exist, and doesn't restore them
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                VoicemailContract.Voicemails.CONTENT_URI,
                null,
                String.format(
                    "(%s = ? AND %s = ? AND %s = ?)",
                    Voicemails.NUMBER, Voicemails.DATE, Voicemails.DURATION),
                new String[] {
                  voicemailInfo.getNumber(), voicemailInfo.getDate(), voicemailInfo.getDuration()
                },
                null,
                null)) {
      if (cursor.moveToFirst()
          && ConfigProviderBindings.get(context)
              .getBoolean("enable_vm_restore_no_duplicate", true)) {
        return true;
      }
    }
    return false;
  }

  public static String getPhoneAccountClause(List<PhoneAccountHandle> phoneAccountsToArchive) {
    Assert.checkArgument(!phoneAccountsToArchive.isEmpty());
    StringBuilder whereQuery = new StringBuilder();

    whereQuery.append("(");

    for (int i = 0; i < phoneAccountsToArchive.size(); i++) {
      whereQuery.append(
          Voicemails.PHONE_ACCOUNT_ID + " = " + phoneAccountsToArchive.get(i).getId());

      if (phoneAccountsToArchive.size() > 1 && i < phoneAccountsToArchive.size() - 1) {
        whereQuery.append(" OR ");
      }
    }
    whereQuery.append(")");
    return whereQuery.toString();
  }

  public static List<PhoneAccountHandle> getPhoneAccountsToArchive(Context context) {
    List<PhoneAccountHandle> phoneAccountsToBackUp = new ArrayList<>();

    for (PhoneAccountHandle handle :
        context.getSystemService(TelecomManager.class).getCallCapablePhoneAccounts()) {

      if (VoicemailComponent.get(context)
          .getVoicemailClient()
          .isVoicemailArchiveEnabled(context, handle)) {
        phoneAccountsToBackUp.add(handle);
        LogUtil.i(
            "DialerBackupUtils.getPhoneAccountsToArchive", "enabled for: " + handle.toString());
      } else {
        LogUtil.i(
            "DialerBackupUtils.getPhoneAccountsToArchive", "not enabled for: " + handle.toString());
      }
    }
    return phoneAccountsToBackUp;
  }
}
