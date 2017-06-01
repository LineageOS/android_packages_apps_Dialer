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
import android.content.Context;
import android.net.Network;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.support.v4.os.BuildCompat;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.util.ArrayMap;
import com.android.dialer.logging.DialerImpression;
import com.android.voicemail.VoicemailComponent;
import com.android.voicemail.impl.ActivationTask;
import com.android.voicemail.impl.Assert;
import com.android.voicemail.impl.OmtpEvents;
import com.android.voicemail.impl.OmtpVvmCarrierConfigHelper;
import com.android.voicemail.impl.Voicemail;
import com.android.voicemail.impl.VoicemailStatus;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.fetch.VoicemailFetchedCallback;
import com.android.voicemail.impl.imap.ImapHelper;
import com.android.voicemail.impl.imap.ImapHelper.InitializingException;
import com.android.voicemail.impl.mail.store.ImapFolder.Quota;
import com.android.voicemail.impl.scheduling.BaseTask;
import com.android.voicemail.impl.settings.VisualVoicemailSettingsUtil;
import com.android.voicemail.impl.sync.VvmNetworkRequest.NetworkWrapper;
import com.android.voicemail.impl.sync.VvmNetworkRequest.RequestFailedException;
import com.android.voicemail.impl.utils.LoggerUtils;
import com.android.voicemail.impl.utils.VoicemailDatabaseUtil;
import java.util.List;
import java.util.Map;

/** Sync OMTP visual voicemail. */
@TargetApi(VERSION_CODES.O)
public class OmtpVvmSyncService {

  private static final String TAG = OmtpVvmSyncService.class.getSimpleName();

  /** Signifies a sync with both uploading to the server and downloading from the server. */
  public static final String SYNC_FULL_SYNC = "full_sync";
  /** Only upload to the server. */
  public static final String SYNC_UPLOAD_ONLY = "upload_only";
  /** Only download from the server. */
  public static final String SYNC_DOWNLOAD_ONLY = "download_only";
  /** Only download single voicemail transcription. */
  public static final String SYNC_DOWNLOAD_ONE_TRANSCRIPTION = "download_one_transcription";
  /** Threshold for whether we should archive and delete voicemails from the remote VM server. */
  private static final float AUTO_DELETE_ARCHIVE_VM_THRESHOLD = 0.75f;

  private final Context mContext;

  private VoicemailsQueryHelper mQueryHelper;

  public OmtpVvmSyncService(Context context) {
    mContext = context;
    mQueryHelper = new VoicemailsQueryHelper(mContext);
  }

  public void sync(
      BaseTask task,
      String action,
      PhoneAccountHandle phoneAccount,
      Voicemail voicemail,
      VoicemailStatus.Editor status) {
    Assert.isTrue(phoneAccount != null);
    VvmLog.v(TAG, "Sync requested: " + action + " - for account: " + phoneAccount);
    setupAndSendRequest(task, phoneAccount, voicemail, action, status);
  }

  private void setupAndSendRequest(
      BaseTask task,
      PhoneAccountHandle phoneAccount,
      Voicemail voicemail,
      String action,
      VoicemailStatus.Editor status) {
    if (!VisualVoicemailSettingsUtil.isEnabled(mContext, phoneAccount)) {
      VvmLog.v(TAG, "Sync requested for disabled account");
      return;
    }
    if (!VvmAccountManager.isAccountActivated(mContext, phoneAccount)) {
      ActivationTask.start(mContext, phoneAccount, null);
      return;
    }

    OmtpVvmCarrierConfigHelper config = new OmtpVvmCarrierConfigHelper(mContext, phoneAccount);
    LoggerUtils.logImpressionOnMainThread(mContext, DialerImpression.Type.VVM_SYNC_STARTED);
    // DATA_IMAP_OPERATION_STARTED posting should not be deferred. This event clears all data
    // channel errors, which should happen when the task starts, not when it ends. It is the
    // "Sync in progress..." status.
    config.handleEvent(
        VoicemailStatus.edit(mContext, phoneAccount), OmtpEvents.DATA_IMAP_OPERATION_STARTED);
    try (NetworkWrapper network = VvmNetworkRequest.getNetwork(config, phoneAccount, status)) {
      if (network == null) {
        VvmLog.e(TAG, "unable to acquire network");
        task.fail();
        return;
      }
      doSync(task, network.get(), phoneAccount, voicemail, action, status);
    } catch (RequestFailedException e) {
      config.handleEvent(status, OmtpEvents.DATA_NO_CONNECTION_CELLULAR_REQUIRED);
      task.fail();
    }
  }

  private void doSync(
      BaseTask task,
      Network network,
      PhoneAccountHandle phoneAccount,
      Voicemail voicemail,
      String action,
      VoicemailStatus.Editor status) {
    try (ImapHelper imapHelper = new ImapHelper(mContext, phoneAccount, network, status)) {
      boolean success;
      if (voicemail == null) {
        success = syncAll(action, imapHelper, phoneAccount);
      } else {
        success = syncOne(imapHelper, voicemail, phoneAccount);
      }
      if (success) {
        // TODO: b/30569269 failure should interrupt all subsequent task via exceptions
        imapHelper.updateQuota();
        autoDeleteAndArchiveVM(imapHelper, phoneAccount);
        imapHelper.handleEvent(OmtpEvents.DATA_IMAP_OPERATION_COMPLETED);
        LoggerUtils.logImpressionOnMainThread(mContext, DialerImpression.Type.VVM_SYNC_COMPLETED);
      } else {
        task.fail();
      }
    } catch (InitializingException e) {
      VvmLog.w(TAG, "Can't retrieve Imap credentials.", e);
      return;
    }
  }

  /**
   * If the VM quota exceeds {@value AUTO_DELETE_ARCHIVE_VM_THRESHOLD}, we should archive the VMs
   * and delete them from the server to ensure new VMs can be received.
   */
  private void autoDeleteAndArchiveVM(
      ImapHelper imapHelper, PhoneAccountHandle phoneAccountHandle) {
    if (!isArchiveAllowedAndEnabled(mContext, phoneAccountHandle)) {
      VvmLog.i(TAG, "autoDeleteAndArchiveVM is turned off");
      LoggerUtils.logImpressionOnMainThread(
          mContext, DialerImpression.Type.VVM_ARCHIVE_AUTO_DELETE_TURNED_OFF);
      return;
    }
    Quota quotaOnServer = imapHelper.getQuota();
    if (quotaOnServer == null) {
      LoggerUtils.logImpressionOnMainThread(
          mContext, DialerImpression.Type.VVM_ARCHIVE_AUTO_DELETE_FAILED_DUE_TO_FAILED_QUOTA_CHECK);
      VvmLog.e(TAG, "autoDeleteAndArchiveVM failed - Can't retrieve Imap quota.");
      return;
    }

    if ((float) quotaOnServer.occupied / (float) quotaOnServer.total
        > AUTO_DELETE_ARCHIVE_VM_THRESHOLD) {
      deleteAndArchiveVM(imapHelper, quotaOnServer);
      imapHelper.updateQuota();
      LoggerUtils.logImpressionOnMainThread(
          mContext, DialerImpression.Type.VVM_ARCHIVE_AUTO_DELETED_VM_FROM_SERVER);
    } else {
      VvmLog.i(TAG, "no need to archive and auto delete VM, quota below threshold");
    }
  }

  private static boolean isArchiveAllowedAndEnabled(
      Context context, PhoneAccountHandle phoneAccountHandle) {

    if (!VoicemailComponent.get(context)
        .getVoicemailClient()
        .isVoicemailArchiveAvailable(context)) {
      VvmLog.i("isArchiveAllowedAndEnabled", "voicemail archive is not available");
      return false;
    }
    if (!VisualVoicemailSettingsUtil.isArchiveEnabled(context, phoneAccountHandle)) {
      VvmLog.i("isArchiveAllowedAndEnabled", "voicemail archive is turned off");
      return false;
    }
    if (!VisualVoicemailSettingsUtil.isEnabled(context, phoneAccountHandle)) {
      VvmLog.i("isArchiveAllowedAndEnabled", "voicemail is turned off");
      return false;
    }
    return true;
  }

  private void deleteAndArchiveVM(ImapHelper imapHelper, Quota quotaOnServer) {
    // Archive column should only be used for 0 and above
    Assert.isTrue(BuildCompat.isAtLeastO());

    // The number of voicemails that exceed our threshold and should be deleted from the server
    int numVoicemails =
        quotaOnServer.occupied - (int) (AUTO_DELETE_ARCHIVE_VM_THRESHOLD * quotaOnServer.total);
    List<Voicemail> oldestVoicemails = mQueryHelper.oldestVoicemailsOnServer(numVoicemails);
    VvmLog.w(TAG, "number of voicemails to delete " + numVoicemails);
    if (!oldestVoicemails.isEmpty()) {
      mQueryHelper.markArchivedInDatabase(oldestVoicemails);
      imapHelper.markMessagesAsDeleted(oldestVoicemails);
      VvmLog.i(
          TAG,
          String.format(
              "successfully archived and deleted %d voicemails", oldestVoicemails.size()));
    } else {
      VvmLog.w(TAG, "remote voicemail server is empty");
    }
  }

  private boolean syncAll(String action, ImapHelper imapHelper, PhoneAccountHandle account) {
    boolean uploadSuccess = true;
    boolean downloadSuccess = true;

    if (SYNC_FULL_SYNC.equals(action) || SYNC_UPLOAD_ONLY.equals(action)) {
      uploadSuccess = upload(account, imapHelper);
    }
    if (SYNC_FULL_SYNC.equals(action) || SYNC_DOWNLOAD_ONLY.equals(action)) {
      downloadSuccess = download(imapHelper, account);
    }

    VvmLog.v(
        TAG,
        "upload succeeded: ["
            + String.valueOf(uploadSuccess)
            + "] download succeeded: ["
            + String.valueOf(downloadSuccess)
            + "]");

    return uploadSuccess && downloadSuccess;
  }

  private boolean syncOne(ImapHelper imapHelper, Voicemail voicemail, PhoneAccountHandle account) {
    if (shouldPerformPrefetch(account, imapHelper)) {
      VoicemailFetchedCallback callback =
          new VoicemailFetchedCallback(mContext, voicemail.getUri(), account);
      imapHelper.fetchVoicemailPayload(callback, voicemail.getSourceData());
    }

    return imapHelper.fetchTranscription(
        new TranscriptionFetchedCallback(mContext, voicemail), voicemail.getSourceData());
  }

  private boolean upload(PhoneAccountHandle phoneAccountHandle, ImapHelper imapHelper) {
    List<Voicemail> readVoicemails = mQueryHelper.getReadVoicemails(phoneAccountHandle);
    List<Voicemail> deletedVoicemails = mQueryHelper.getDeletedVoicemails(phoneAccountHandle);

    boolean success = true;

    if (deletedVoicemails.size() > 0) {
      if (imapHelper.markMessagesAsDeleted(deletedVoicemails)) {
        // We want to delete selectively instead of all the voicemails for this provider
        // in case the state changed since the IMAP query was completed.
        mQueryHelper.deleteFromDatabase(deletedVoicemails);
      } else {
        success = false;
      }
    }

    if (readVoicemails.size() > 0) {
      VvmLog.i(TAG, "Marking voicemails as read");
      if (imapHelper.markMessagesAsRead(readVoicemails)) {
        VvmLog.i(TAG, "Marking voicemails as clean");
        mQueryHelper.markCleanInDatabase(readVoicemails);
      } else {
        success = false;
      }
    }

    return success;
  }

  private boolean download(ImapHelper imapHelper, PhoneAccountHandle account) {
    List<Voicemail> serverVoicemails = imapHelper.fetchAllVoicemails();
    List<Voicemail> localVoicemails = mQueryHelper.getAllVoicemails(account);

    if (localVoicemails == null || serverVoicemails == null) {
      // Null value means the query failed.
      return false;
    }

    Map<String, Voicemail> remoteMap = buildMap(serverVoicemails);

    // Go through all the local voicemails and check if they are on the server.
    // They may be read or deleted on the server but not locally. Perform the
    // appropriate local operation if the status differs from the server. Remove
    // the messages that exist both locally and on the server to know which server
    // messages to insert locally.
    // Voicemails that were removed automatically from the server, are marked as
    // archived and are stored locally. We do not delete them, as they were removed from the server
    // by design (to make space).
    for (int i = 0; i < localVoicemails.size(); i++) {
      Voicemail localVoicemail = localVoicemails.get(i);
      Voicemail remoteVoicemail = remoteMap.remove(localVoicemail.getSourceData());

      // Do not delete voicemails that are archived marked as archived.
      if (remoteVoicemail == null) {
        mQueryHelper.deleteNonArchivedFromDatabase(localVoicemail);
      } else {
        if (remoteVoicemail.isRead() && !localVoicemail.isRead()) {
          mQueryHelper.markReadInDatabase(localVoicemail);
        }

        if (!TextUtils.isEmpty(remoteVoicemail.getTranscription())
            && TextUtils.isEmpty(localVoicemail.getTranscription())) {
          LoggerUtils.logImpressionOnMainThread(
              mContext, DialerImpression.Type.VVM_TRANSCRIPTION_DOWNLOADED);
          mQueryHelper.updateWithTranscription(localVoicemail, remoteVoicemail.getTranscription());
        }
      }
    }

    // The leftover messages are messages that exist on the server but not locally.
    boolean prefetchEnabled = shouldPerformPrefetch(account, imapHelper);
    for (Voicemail remoteVoicemail : remoteMap.values()) {
      if (!TextUtils.isEmpty(remoteVoicemail.getTranscription())) {
        LoggerUtils.logImpressionOnMainThread(
            mContext, DialerImpression.Type.VVM_TRANSCRIPTION_DOWNLOADED);
      }
      Uri uri = VoicemailDatabaseUtil.insert(mContext, remoteVoicemail);
      if (prefetchEnabled) {
        VoicemailFetchedCallback fetchedCallback =
            new VoicemailFetchedCallback(mContext, uri, account);
        imapHelper.fetchVoicemailPayload(fetchedCallback, remoteVoicemail.getSourceData());
      }
    }

    return true;
  }

  private boolean shouldPerformPrefetch(PhoneAccountHandle account, ImapHelper imapHelper) {
    OmtpVvmCarrierConfigHelper carrierConfigHelper =
        new OmtpVvmCarrierConfigHelper(mContext, account);
    return carrierConfigHelper.isPrefetchEnabled() && !imapHelper.isRoaming();
  }

  /** Builds a map from provider data to message for the given collection of voicemails. */
  private Map<String, Voicemail> buildMap(List<Voicemail> messages) {
    Map<String, Voicemail> map = new ArrayMap<String, Voicemail>();
    for (Voicemail message : messages) {
      map.put(message.getSourceData(), message);
    }
    return map;
  }

  /** Callback for {@link ImapHelper#fetchTranscription(TranscriptionFetchedCallback, String)} */
  public static class TranscriptionFetchedCallback {

    private Context mContext;
    private Voicemail mVoicemail;

    public TranscriptionFetchedCallback(Context context, Voicemail voicemail) {
      mContext = context;
      mVoicemail = voicemail;
    }

    public void setVoicemailTranscription(String transcription) {
      VoicemailsQueryHelper queryHelper = new VoicemailsQueryHelper(mContext);
      queryHelper.updateWithTranscription(mVoicemail, transcription);
    }
  }
}
