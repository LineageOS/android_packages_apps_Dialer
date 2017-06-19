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
package com.android.voicemail.impl.imap;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.util.Base64;
import com.android.voicemail.impl.OmtpConstants;
import com.android.voicemail.impl.OmtpConstants.ChangePinResult;
import com.android.voicemail.impl.OmtpEvents;
import com.android.voicemail.impl.OmtpVvmCarrierConfigHelper;
import com.android.voicemail.impl.VisualVoicemailPreferences;
import com.android.voicemail.impl.Voicemail;
import com.android.voicemail.impl.VoicemailStatus;
import com.android.voicemail.impl.VoicemailStatus.Editor;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.fetch.VoicemailFetchedCallback;
import com.android.voicemail.impl.mail.Address;
import com.android.voicemail.impl.mail.Body;
import com.android.voicemail.impl.mail.BodyPart;
import com.android.voicemail.impl.mail.FetchProfile;
import com.android.voicemail.impl.mail.Flag;
import com.android.voicemail.impl.mail.Message;
import com.android.voicemail.impl.mail.MessagingException;
import com.android.voicemail.impl.mail.Multipart;
import com.android.voicemail.impl.mail.TempDirectory;
import com.android.voicemail.impl.mail.internet.MimeMessage;
import com.android.voicemail.impl.mail.store.ImapConnection;
import com.android.voicemail.impl.mail.store.ImapFolder;
import com.android.voicemail.impl.mail.store.ImapFolder.Quota;
import com.android.voicemail.impl.mail.store.ImapStore;
import com.android.voicemail.impl.mail.store.imap.ImapConstants;
import com.android.voicemail.impl.mail.store.imap.ImapResponse;
import com.android.voicemail.impl.mail.utils.LogUtils;
import com.android.voicemail.impl.sync.OmtpVvmSyncService.TranscriptionFetchedCallback;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apache.commons.io.IOUtils;

/** A helper interface to abstract commands sent across IMAP interface for a given account. */
public class ImapHelper implements Closeable {

  private static final String TAG = "ImapHelper";

  private ImapFolder mFolder;
  private ImapStore mImapStore;

  private final Context mContext;
  private final PhoneAccountHandle mPhoneAccount;
  private final Network mNetwork;
  private final Editor mStatus;

  VisualVoicemailPreferences mPrefs;

  private final OmtpVvmCarrierConfigHelper mConfig;

  /** InitializingException */
  public static class InitializingException extends Exception {

    public InitializingException(String message) {
      super(message);
    }
  }

  public ImapHelper(
      Context context, PhoneAccountHandle phoneAccount, Network network, Editor status)
      throws InitializingException {
    this(
        context,
        new OmtpVvmCarrierConfigHelper(context, phoneAccount),
        phoneAccount,
        network,
        status);
  }

  public ImapHelper(
      Context context,
      OmtpVvmCarrierConfigHelper config,
      PhoneAccountHandle phoneAccount,
      Network network,
      Editor status)
      throws InitializingException {
    mContext = context;
    mPhoneAccount = phoneAccount;
    mNetwork = network;
    mStatus = status;
    mConfig = config;
    mPrefs = new VisualVoicemailPreferences(context, phoneAccount);

    try {
      TempDirectory.setTempDirectory(context);

      String username = mPrefs.getString(OmtpConstants.IMAP_USER_NAME, null);
      String password = mPrefs.getString(OmtpConstants.IMAP_PASSWORD, null);
      String serverName = mPrefs.getString(OmtpConstants.SERVER_ADDRESS, null);
      int port = Integer.parseInt(mPrefs.getString(OmtpConstants.IMAP_PORT, null));
      int auth = ImapStore.FLAG_NONE;

      int sslPort = mConfig.getSslPort();
      if (sslPort != 0) {
        port = sslPort;
        auth = ImapStore.FLAG_SSL;
      }

      mImapStore =
          new ImapStore(context, this, username, password, port, serverName, auth, network);
    } catch (NumberFormatException e) {
      handleEvent(OmtpEvents.DATA_INVALID_PORT);
      LogUtils.w(TAG, "Could not parse port number");
      throw new InitializingException("cannot initialize ImapHelper:" + e.toString());
    }
  }

  @Override
  public void close() {
    mImapStore.closeConnection();
  }

  public boolean isRoaming() {
    ConnectivityManager connectivityManager =
        (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo info = connectivityManager.getNetworkInfo(mNetwork);
    if (info == null) {
      return false;
    }
    return info.isRoaming();
  }

  public OmtpVvmCarrierConfigHelper getConfig() {
    return mConfig;
  }

  public ImapConnection connect() {
    return mImapStore.getConnection();
  }

  /** The caller thread will block until the method returns. */
  public boolean markMessagesAsRead(List<Voicemail> voicemails) {
    return setFlags(voicemails, Flag.SEEN);
  }

  /** The caller thread will block until the method returns. */
  public boolean markMessagesAsDeleted(List<Voicemail> voicemails) {
    return setFlags(voicemails, Flag.DELETED);
  }

  public void handleEvent(OmtpEvents event) {
    mConfig.handleEvent(mStatus, event);
  }

  /**
   * Set flags on the server for a given set of voicemails.
   *
   * @param voicemails The voicemails to set flags for.
   * @param flags The flags to set on the voicemails.
   * @return {@code true} if the operation completes successfully, {@code false} otherwise.
   */
  private boolean setFlags(List<Voicemail> voicemails, String... flags) {
    if (voicemails.size() == 0) {
      return false;
    }
    try {
      mFolder = openImapFolder(ImapFolder.MODE_READ_WRITE);
      if (mFolder != null) {
        mFolder.setFlags(convertToImapMessages(voicemails), flags, true);
        return true;
      }
      return false;
    } catch (MessagingException e) {
      LogUtils.e(TAG, e, "Messaging exception");
      return false;
    } finally {
      closeImapFolder();
    }
  }

  /**
   * Fetch a list of voicemails from the server.
   *
   * @return A list of voicemail objects containing data about voicemails stored on the server.
   */
  public List<Voicemail> fetchAllVoicemails() {
    List<Voicemail> result = new ArrayList<Voicemail>();
    Message[] messages;
    try {
      mFolder = openImapFolder(ImapFolder.MODE_READ_WRITE);
      if (mFolder == null) {
        // This means we were unable to successfully open the folder.
        return null;
      }

      // This method retrieves lightweight messages containing only the uid of the message.
      messages = mFolder.getMessages(null);

      for (Message message : messages) {
        // Get the voicemail details (message structure).
        MessageStructureWrapper messageStructureWrapper = fetchMessageStructure(message);
        if (messageStructureWrapper != null) {
          result.add(getVoicemailFromMessageStructure(messageStructureWrapper));
        }
      }
      return result;
    } catch (MessagingException e) {
      LogUtils.e(TAG, e, "Messaging Exception");
      return null;
    } finally {
      closeImapFolder();
    }
  }

  /**
   * Extract voicemail details from the message structure. Also fetch transcription if a
   * transcription exists.
   */
  private Voicemail getVoicemailFromMessageStructure(
      MessageStructureWrapper messageStructureWrapper) throws MessagingException {
    Message messageDetails = messageStructureWrapper.messageStructure;

    TranscriptionFetchedListener listener = new TranscriptionFetchedListener();
    if (messageStructureWrapper.transcriptionBodyPart != null) {
      FetchProfile fetchProfile = new FetchProfile();
      fetchProfile.add(messageStructureWrapper.transcriptionBodyPart);

      mFolder.fetch(new Message[] {messageDetails}, fetchProfile, listener);
    }

    // Found an audio attachment, this is a valid voicemail.
    long time = messageDetails.getSentDate().getTime();
    String number = getNumber(messageDetails.getFrom());
    boolean isRead = Arrays.asList(messageDetails.getFlags()).contains(Flag.SEEN);
    Long duration = messageDetails.getDuration();
    Voicemail.Builder builder =
        Voicemail.createForInsertion(time, number)
            .setPhoneAccount(mPhoneAccount)
            .setSourcePackage(mContext.getPackageName())
            .setSourceData(messageDetails.getUid())
            .setIsRead(isRead)
            .setTranscription(listener.getVoicemailTranscription());
    if (duration != null) {
      builder.setDuration(duration);
    }
    return builder.build();
  }

  /**
   * The "from" field of a visual voicemail IMAP message is the number of the caller who left the
   * message. Extract this number from the list of "from" addresses.
   *
   * @param fromAddresses A list of addresses that comprise the "from" line.
   * @return The number of the voicemail sender.
   */
  private String getNumber(Address[] fromAddresses) {
    if (fromAddresses != null && fromAddresses.length > 0) {
      if (fromAddresses.length != 1) {
        LogUtils.w(TAG, "More than one from addresses found. Using the first one.");
      }
      String sender = fromAddresses[0].getAddress();
      int atPos = sender.indexOf('@');
      if (atPos != -1) {
        // Strip domain part of the address.
        sender = sender.substring(0, atPos);
      }
      return sender;
    }
    return null;
  }

  /**
   * Fetches the structure of the given message and returns a wrapper containing the message
   * structure and the transcription structure (if applicable).
   *
   * @throws MessagingException if fetching the structure of the message fails
   */
  private MessageStructureWrapper fetchMessageStructure(Message message) throws MessagingException {
    LogUtils.d(TAG, "Fetching message structure for " + message.getUid());

    MessageStructureFetchedListener listener = new MessageStructureFetchedListener();

    FetchProfile fetchProfile = new FetchProfile();
    fetchProfile.addAll(
        Arrays.asList(
            FetchProfile.Item.FLAGS, FetchProfile.Item.ENVELOPE, FetchProfile.Item.STRUCTURE));

    // The IMAP folder fetch method will call "messageRetrieved" on the listener when the
    // message is successfully retrieved.
    mFolder.fetch(new Message[] {message}, fetchProfile, listener);
    return listener.getMessageStructure();
  }

  public boolean fetchVoicemailPayload(VoicemailFetchedCallback callback, final String uid) {
    try {
      mFolder = openImapFolder(ImapFolder.MODE_READ_WRITE);
      if (mFolder == null) {
        // This means we were unable to successfully open the folder.
        return false;
      }
      Message message = mFolder.getMessage(uid);
      if (message == null) {
        return false;
      }
      VoicemailPayload voicemailPayload = fetchVoicemailPayload(message);
      callback.setVoicemailContent(voicemailPayload);
      return true;
    } catch (MessagingException e) {
    } finally {
      closeImapFolder();
    }
    return false;
  }

  /**
   * Fetches the body of the given message and returns the parsed voicemail payload.
   *
   * @throws MessagingException if fetching the body of the message fails
   */
  private VoicemailPayload fetchVoicemailPayload(Message message) throws MessagingException {
    LogUtils.d(TAG, "Fetching message body for " + message.getUid());

    MessageBodyFetchedListener listener = new MessageBodyFetchedListener();

    FetchProfile fetchProfile = new FetchProfile();
    fetchProfile.add(FetchProfile.Item.BODY);

    mFolder.fetch(new Message[] {message}, fetchProfile, listener);
    return listener.getVoicemailPayload();
  }

  public boolean fetchTranscription(TranscriptionFetchedCallback callback, String uid) {
    try {
      mFolder = openImapFolder(ImapFolder.MODE_READ_WRITE);
      if (mFolder == null) {
        // This means we were unable to successfully open the folder.
        return false;
      }

      Message message = mFolder.getMessage(uid);
      if (message == null) {
        return false;
      }

      MessageStructureWrapper messageStructureWrapper = fetchMessageStructure(message);
      if (messageStructureWrapper != null) {
        TranscriptionFetchedListener listener = new TranscriptionFetchedListener();
        if (messageStructureWrapper.transcriptionBodyPart != null) {
          FetchProfile fetchProfile = new FetchProfile();
          fetchProfile.add(messageStructureWrapper.transcriptionBodyPart);

          // This method is called synchronously so the transcription will be populated
          // in the listener once the next method is called.
          mFolder.fetch(new Message[] {message}, fetchProfile, listener);
          callback.setVoicemailTranscription(listener.getVoicemailTranscription());
        }
      }
      return true;
    } catch (MessagingException e) {
      LogUtils.e(TAG, e, "Messaging Exception");
      return false;
    } finally {
      closeImapFolder();
    }
  }

  @ChangePinResult
  public int changePin(String oldPin, String newPin) throws MessagingException {
    ImapConnection connection = mImapStore.getConnection();
    try {
      String command =
          getConfig().getProtocol().getCommand(OmtpConstants.IMAP_CHANGE_TUI_PWD_FORMAT);
      connection.sendCommand(String.format(Locale.US, command, newPin, oldPin), true);
      return getChangePinResultFromImapResponse(connection.readResponse());
    } catch (IOException ioe) {
      VvmLog.e(TAG, "changePin: ", ioe);
      return OmtpConstants.CHANGE_PIN_SYSTEM_ERROR;
    } finally {
      connection.destroyResponses();
    }
  }

  public void changeVoicemailTuiLanguage(String languageCode) throws MessagingException {
    ImapConnection connection = mImapStore.getConnection();
    try {
      String command =
          getConfig().getProtocol().getCommand(OmtpConstants.IMAP_CHANGE_VM_LANG_FORMAT);
      connection.sendCommand(String.format(Locale.US, command, languageCode), true);
    } catch (IOException ioe) {
      LogUtils.e(TAG, ioe.toString());
    } finally {
      connection.destroyResponses();
    }
  }

  public void closeNewUserTutorial() throws MessagingException {
    ImapConnection connection = mImapStore.getConnection();
    try {
      String command = getConfig().getProtocol().getCommand(OmtpConstants.IMAP_CLOSE_NUT);
      connection.executeSimpleCommand(command, false);
    } catch (IOException ioe) {
      throw new MessagingException(MessagingException.SERVER_ERROR, ioe.toString());
    } finally {
      connection.destroyResponses();
    }
  }

  @ChangePinResult
  private static int getChangePinResultFromImapResponse(ImapResponse response)
      throws MessagingException {
    if (!response.isTagged()) {
      throw new MessagingException(MessagingException.SERVER_ERROR, "tagged response expected");
    }
    if (!response.isOk()) {
      String message = response.getStringOrEmpty(1).getString();
      LogUtils.d(TAG, "change PIN failed: " + message);
      if (OmtpConstants.RESPONSE_CHANGE_PIN_TOO_SHORT.equals(message)) {
        return OmtpConstants.CHANGE_PIN_TOO_SHORT;
      }
      if (OmtpConstants.RESPONSE_CHANGE_PIN_TOO_LONG.equals(message)) {
        return OmtpConstants.CHANGE_PIN_TOO_LONG;
      }
      if (OmtpConstants.RESPONSE_CHANGE_PIN_TOO_WEAK.equals(message)) {
        return OmtpConstants.CHANGE_PIN_TOO_WEAK;
      }
      if (OmtpConstants.RESPONSE_CHANGE_PIN_MISMATCH.equals(message)) {
        return OmtpConstants.CHANGE_PIN_MISMATCH;
      }
      if (OmtpConstants.RESPONSE_CHANGE_PIN_INVALID_CHARACTER.equals(message)) {
        return OmtpConstants.CHANGE_PIN_INVALID_CHARACTER;
      }
      return OmtpConstants.CHANGE_PIN_SYSTEM_ERROR;
    }
    LogUtils.d(TAG, "change PIN succeeded");
    return OmtpConstants.CHANGE_PIN_SUCCESS;
  }

  public void updateQuota() {
    try {
      mFolder = openImapFolder(ImapFolder.MODE_READ_WRITE);
      if (mFolder == null) {
        // This means we were unable to successfully open the folder.
        return;
      }
      updateQuota(mFolder);
    } catch (MessagingException e) {
      LogUtils.e(TAG, e, "Messaging Exception");
    } finally {
      closeImapFolder();
    }
  }

  @Nullable
  public Quota getQuota() {
    try {
      mFolder = openImapFolder(ImapFolder.MODE_READ_ONLY);
      if (mFolder == null) {
        // This means we were unable to successfully open the folder.
        LogUtils.e(TAG, "Unable to open folder");
        return null;
      }
      return mFolder.getQuota();
    } catch (MessagingException e) {
      LogUtils.e(TAG, e, "Messaging Exception");
      return null;
    } finally {
      closeImapFolder();
    }
  }

  private void updateQuota(ImapFolder folder) throws MessagingException {
    setQuota(folder.getQuota());
  }

  private void setQuota(ImapFolder.Quota quota) {
    if (quota == null) {
      LogUtils.i(TAG, "quota was null");
      return;
    }

    LogUtils.i(
        TAG,
        "Updating Voicemail status table with"
            + " quota occupied: "
            + quota.occupied
            + " new quota total:"
            + quota.total);
    VoicemailStatus.edit(mContext, mPhoneAccount).setQuota(quota.occupied, quota.total).apply();
    LogUtils.i(TAG, "Updated quota occupied and total");
  }

  /**
   * A wrapper to hold a message with its header details and the structure for transcriptions (so
   * they can be fetched in the future).
   */
  public static class MessageStructureWrapper {

    public Message messageStructure;
    public BodyPart transcriptionBodyPart;

    public MessageStructureWrapper() {}
  }

  /** Listener for the message structure being fetched. */
  private final class MessageStructureFetchedListener
      implements ImapFolder.MessageRetrievalListener {

    private MessageStructureWrapper mMessageStructure;

    public MessageStructureFetchedListener() {}

    public MessageStructureWrapper getMessageStructure() {
      return mMessageStructure;
    }

    @Override
    public void messageRetrieved(Message message) {
      LogUtils.d(TAG, "Fetched message structure for " + message.getUid());
      LogUtils.d(TAG, "Message retrieved: " + message);
      try {
        mMessageStructure = getMessageOrNull(message);
        if (mMessageStructure == null) {
          LogUtils.d(TAG, "This voicemail does not have an attachment...");
          return;
        }
      } catch (MessagingException e) {
        LogUtils.e(TAG, e, "Messaging Exception");
        closeImapFolder();
      }
    }

    /**
     * Check if this IMAP message is a valid voicemail and whether it contains a transcription.
     *
     * @param message The IMAP message.
     * @return The MessageStructureWrapper object corresponding to an IMAP message and
     *     transcription.
     */
    private MessageStructureWrapper getMessageOrNull(Message message) throws MessagingException {
      if (!message.getMimeType().startsWith("multipart/")) {
        LogUtils.w(TAG, "Ignored non multi-part message");
        return null;
      }

      MessageStructureWrapper messageStructureWrapper = new MessageStructureWrapper();

      Multipart multipart = (Multipart) message.getBody();
      for (int i = 0; i < multipart.getCount(); ++i) {
        BodyPart bodyPart = multipart.getBodyPart(i);
        String bodyPartMimeType = bodyPart.getMimeType().toLowerCase();
        LogUtils.d(TAG, "bodyPart mime type: " + bodyPartMimeType);

        if (bodyPartMimeType.startsWith("audio/")) {
          messageStructureWrapper.messageStructure = message;
        } else if (bodyPartMimeType.startsWith("text/")) {
          messageStructureWrapper.transcriptionBodyPart = bodyPart;
        } else {
          VvmLog.v(TAG, "Unknown bodyPart MIME: " + bodyPartMimeType);
        }
      }

      if (messageStructureWrapper.messageStructure != null) {
        return messageStructureWrapper;
      }

      // No attachment found, this is not a voicemail.
      return null;
    }
  }

  /** Listener for the message body being fetched. */
  private final class MessageBodyFetchedListener implements ImapFolder.MessageRetrievalListener {

    private VoicemailPayload mVoicemailPayload;

    /** Returns the fetch voicemail payload. */
    public VoicemailPayload getVoicemailPayload() {
      return mVoicemailPayload;
    }

    @Override
    public void messageRetrieved(Message message) {
      LogUtils.d(TAG, "Fetched message body for " + message.getUid());
      LogUtils.d(TAG, "Message retrieved: " + message);
      try {
        mVoicemailPayload = getVoicemailPayloadFromMessage(message);
      } catch (MessagingException e) {
        LogUtils.e(TAG, "Messaging Exception:", e);
      } catch (IOException e) {
        LogUtils.e(TAG, "IO Exception:", e);
      }
    }

    private VoicemailPayload getVoicemailPayloadFromMessage(Message message)
        throws MessagingException, IOException {
      Multipart multipart = (Multipart) message.getBody();
      List<String> mimeTypes = new ArrayList<>();
      for (int i = 0; i < multipart.getCount(); ++i) {
        BodyPart bodyPart = multipart.getBodyPart(i);
        String bodyPartMimeType = bodyPart.getMimeType().toLowerCase();
        mimeTypes.add(bodyPartMimeType);
        if (bodyPartMimeType.startsWith("audio/")) {
          byte[] bytes = getDataFromBody(bodyPart.getBody());
          LogUtils.d(TAG, String.format("Fetched %s bytes of data", bytes.length));
          return new VoicemailPayload(bodyPartMimeType, bytes);
        }
      }
      LogUtils.e(TAG, "No audio attachment found on this voicemail, mimeTypes:" + mimeTypes);
      return null;
    }
  }

  /** Listener for the transcription being fetched. */
  private final class TranscriptionFetchedListener implements ImapFolder.MessageRetrievalListener {

    private String mVoicemailTranscription;

    /** Returns the fetched voicemail transcription. */
    public String getVoicemailTranscription() {
      return mVoicemailTranscription;
    }

    @Override
    public void messageRetrieved(Message message) {
      LogUtils.d(TAG, "Fetched transcription for " + message.getUid());
      try {
        mVoicemailTranscription = new String(getDataFromBody(message.getBody()));
      } catch (MessagingException e) {
        LogUtils.e(TAG, "Messaging Exception:", e);
      } catch (IOException e) {
        LogUtils.e(TAG, "IO Exception:", e);
      }
    }
  }

  private ImapFolder openImapFolder(String modeReadWrite) {
    try {
      if (mImapStore == null) {
        return null;
      }
      ImapFolder folder = new ImapFolder(mImapStore, ImapConstants.INBOX);
      folder.open(modeReadWrite);
      return folder;
    } catch (MessagingException e) {
      LogUtils.e(TAG, e, "Messaging Exception");
    }
    return null;
  }

  private Message[] convertToImapMessages(List<Voicemail> voicemails) {
    Message[] messages = new Message[voicemails.size()];
    for (int i = 0; i < voicemails.size(); ++i) {
      messages[i] = new MimeMessage();
      messages[i].setUid(voicemails.get(i).getSourceData());
    }
    return messages;
  }

  private void closeImapFolder() {
    if (mFolder != null) {
      mFolder.close(true);
    }
  }

  private byte[] getDataFromBody(Body body) throws IOException, MessagingException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BufferedOutputStream bufferedOut = new BufferedOutputStream(out);
    try {
      body.writeTo(bufferedOut);
      return Base64.decode(out.toByteArray(), Base64.DEFAULT);
    } finally {
      IOUtils.closeQuietly(bufferedOut);
      IOUtils.closeQuietly(out);
    }
  }
}
