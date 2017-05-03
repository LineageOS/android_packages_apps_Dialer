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
 * limitations under the License.
 */
package com.android.voicemail.impl.mail.store;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Base64DataException;
import com.android.voicemail.impl.OmtpEvents;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.mail.AuthenticationFailedException;
import com.android.voicemail.impl.mail.Body;
import com.android.voicemail.impl.mail.FetchProfile;
import com.android.voicemail.impl.mail.Flag;
import com.android.voicemail.impl.mail.Message;
import com.android.voicemail.impl.mail.MessagingException;
import com.android.voicemail.impl.mail.Part;
import com.android.voicemail.impl.mail.internet.BinaryTempFileBody;
import com.android.voicemail.impl.mail.internet.MimeBodyPart;
import com.android.voicemail.impl.mail.internet.MimeHeader;
import com.android.voicemail.impl.mail.internet.MimeMultipart;
import com.android.voicemail.impl.mail.internet.MimeUtility;
import com.android.voicemail.impl.mail.store.ImapStore.ImapException;
import com.android.voicemail.impl.mail.store.ImapStore.ImapMessage;
import com.android.voicemail.impl.mail.store.imap.ImapConstants;
import com.android.voicemail.impl.mail.store.imap.ImapElement;
import com.android.voicemail.impl.mail.store.imap.ImapList;
import com.android.voicemail.impl.mail.store.imap.ImapResponse;
import com.android.voicemail.impl.mail.store.imap.ImapString;
import com.android.voicemail.impl.mail.utils.Utility;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class ImapFolder {
  private static final String TAG = "ImapFolder";
  private static final String[] PERMANENT_FLAGS = {
    Flag.DELETED, Flag.SEEN, Flag.FLAGGED, Flag.ANSWERED
  };
  private static final int COPY_BUFFER_SIZE = 16 * 1024;

  private final ImapStore mStore;
  private final String mName;
  private int mMessageCount = -1;
  private ImapConnection mConnection;
  private String mMode;
  private boolean mExists;
  /** A set of hashes that can be used to track dirtiness */
  Object[] mHash;

  public static final String MODE_READ_ONLY = "mode_read_only";
  public static final String MODE_READ_WRITE = "mode_read_write";

  public ImapFolder(ImapStore store, String name) {
    mStore = store;
    mName = name;
  }

  /** Callback for each message retrieval. */
  public interface MessageRetrievalListener {
    public void messageRetrieved(Message message);
  }

  private void destroyResponses() {
    if (mConnection != null) {
      mConnection.destroyResponses();
    }
  }

  public void open(String mode) throws MessagingException {
    try {
      if (isOpen()) {
        throw new AssertionError("Duplicated open on ImapFolder");
      }
      synchronized (this) {
        mConnection = mStore.getConnection();
      }
      // * FLAGS (\Answered \Flagged \Deleted \Seen \Draft NonJunk
      // $MDNSent)
      // * OK [PERMANENTFLAGS (\Answered \Flagged \Deleted \Seen \Draft
      // NonJunk $MDNSent \*)] Flags permitted.
      // * 23 EXISTS
      // * 0 RECENT
      // * OK [UIDVALIDITY 1125022061] UIDs valid
      // * OK [UIDNEXT 57576] Predicted next UID
      // 2 OK [READ-WRITE] Select completed.
      try {
        doSelect();
      } catch (IOException ioe) {
        throw ioExceptionHandler(mConnection, ioe);
      } finally {
        destroyResponses();
      }
    } catch (AuthenticationFailedException e) {
      // Don't cache this connection, so we're forced to try connecting/login again
      mConnection = null;
      close(false);
      throw e;
    } catch (MessagingException e) {
      mExists = false;
      close(false);
      throw e;
    }
  }

  public boolean isOpen() {
    return mExists && mConnection != null;
  }

  public String getMode() {
    return mMode;
  }

  public void close(boolean expunge) {
    if (expunge) {
      try {
        expunge();
      } catch (MessagingException e) {
        VvmLog.e(TAG, "Messaging Exception", e);
      }
    }
    mMessageCount = -1;
    synchronized (this) {
      mConnection = null;
    }
  }

  public int getMessageCount() {
    return mMessageCount;
  }

  String[] getSearchUids(List<ImapResponse> responses) {
    // S: * SEARCH 2 3 6
    final ArrayList<String> uids = new ArrayList<String>();
    for (ImapResponse response : responses) {
      if (!response.isDataResponse(0, ImapConstants.SEARCH)) {
        continue;
      }
      // Found SEARCH response data
      for (int i = 1; i < response.size(); i++) {
        ImapString s = response.getStringOrEmpty(i);
        if (s.isString()) {
          uids.add(s.getString());
        }
      }
    }
    return uids.toArray(Utility.EMPTY_STRINGS);
  }

  @VisibleForTesting
  String[] searchForUids(String searchCriteria) throws MessagingException {
    checkOpen();
    try {
      try {
        final String command = ImapConstants.UID_SEARCH + " " + searchCriteria;
        final String[] result = getSearchUids(mConnection.executeSimpleCommand(command));
        VvmLog.d(TAG, "searchForUids '" + searchCriteria + "' results: " + result.length);
        return result;
      } catch (ImapException me) {
        VvmLog.d(TAG, "ImapException in search: " + searchCriteria, me);
        return Utility.EMPTY_STRINGS; // Not found
      } catch (IOException ioe) {
        VvmLog.d(TAG, "IOException in search: " + searchCriteria, ioe);
        mStore.getImapHelper().handleEvent(OmtpEvents.DATA_GENERIC_IMAP_IOE);
        throw ioExceptionHandler(mConnection, ioe);
      }
    } finally {
      destroyResponses();
    }
  }

  @Nullable
  public Message getMessage(String uid) throws MessagingException {
    checkOpen();

    final String[] uids = searchForUids(ImapConstants.UID + " " + uid);
    for (int i = 0; i < uids.length; i++) {
      if (uids[i].equals(uid)) {
        return new ImapMessage(uid, this);
      }
    }
    VvmLog.e(TAG, "UID " + uid + " not found on server");
    return null;
  }

  @VisibleForTesting
  protected static boolean isAsciiString(String str) {
    int len = str.length();
    for (int i = 0; i < len; i++) {
      char c = str.charAt(i);
      if (c >= 128) return false;
    }
    return true;
  }

  public Message[] getMessages(String[] uids) throws MessagingException {
    if (uids == null) {
      uids = searchForUids("1:* NOT DELETED");
    }
    return getMessagesInternal(uids);
  }

  public Message[] getMessagesInternal(String[] uids) {
    final ArrayList<Message> messages = new ArrayList<Message>(uids.length);
    for (int i = 0; i < uids.length; i++) {
      final String uid = uids[i];
      final ImapMessage message = new ImapMessage(uid, this);
      messages.add(message);
    }
    return messages.toArray(Message.EMPTY_ARRAY);
  }

  public void fetch(Message[] messages, FetchProfile fp, MessageRetrievalListener listener)
      throws MessagingException {
    try {
      fetchInternal(messages, fp, listener);
    } catch (RuntimeException e) { // Probably a parser error.
      VvmLog.w(TAG, "Exception detected: " + e.getMessage());
      throw e;
    }
  }

  public void fetchInternal(Message[] messages, FetchProfile fp, MessageRetrievalListener listener)
      throws MessagingException {
    if (messages.length == 0) {
      return;
    }
    checkOpen();
    ArrayMap<String, Message> messageMap = new ArrayMap<String, Message>();
    for (Message m : messages) {
      messageMap.put(m.getUid(), m);
    }

    /*
     * Figure out what command we are going to run:
     * FLAGS     - UID FETCH (FLAGS)
     * ENVELOPE  - UID FETCH (INTERNALDATE UID RFC822.SIZE FLAGS BODY.PEEK[
     *                            HEADER.FIELDS (date subject from content-type to cc)])
     * STRUCTURE - UID FETCH (BODYSTRUCTURE)
     * BODY_SANE - UID FETCH (BODY.PEEK[]<0.N>) where N = max bytes returned
     * BODY      - UID FETCH (BODY.PEEK[])
     * Part      - UID FETCH (BODY.PEEK[ID]) where ID = mime part ID
     */

    final LinkedHashSet<String> fetchFields = new LinkedHashSet<String>();

    fetchFields.add(ImapConstants.UID);
    if (fp.contains(FetchProfile.Item.FLAGS)) {
      fetchFields.add(ImapConstants.FLAGS);
    }
    if (fp.contains(FetchProfile.Item.ENVELOPE)) {
      fetchFields.add(ImapConstants.INTERNALDATE);
      fetchFields.add(ImapConstants.RFC822_SIZE);
      fetchFields.add(ImapConstants.FETCH_FIELD_HEADERS);
    }
    if (fp.contains(FetchProfile.Item.STRUCTURE)) {
      fetchFields.add(ImapConstants.BODYSTRUCTURE);
    }

    if (fp.contains(FetchProfile.Item.BODY_SANE)) {
      fetchFields.add(ImapConstants.FETCH_FIELD_BODY_PEEK_SANE);
    }
    if (fp.contains(FetchProfile.Item.BODY)) {
      fetchFields.add(ImapConstants.FETCH_FIELD_BODY_PEEK);
    }

    // TODO Why are we only fetching the first part given?
    final Part fetchPart = fp.getFirstPart();
    if (fetchPart != null) {
      final String[] partIds = fetchPart.getHeader(MimeHeader.HEADER_ANDROID_ATTACHMENT_STORE_DATA);
      // TODO Why can a single part have more than one Id? And why should we only fetch
      // the first id if there are more than one?
      if (partIds != null) {
        fetchFields.add(ImapConstants.FETCH_FIELD_BODY_PEEK_BARE + "[" + partIds[0] + "]");
      }
    }

    try {
      mConnection.sendCommand(
          String.format(
              Locale.US,
              ImapConstants.UID_FETCH + " %s (%s)",
              ImapStore.joinMessageUids(messages),
              Utility.combine(fetchFields.toArray(new String[fetchFields.size()]), ' ')),
          false);
      ImapResponse response;
      do {
        response = null;
        try {
          response = mConnection.readResponse();

          if (!response.isDataResponse(1, ImapConstants.FETCH)) {
            continue; // Ignore
          }
          final ImapList fetchList = response.getListOrEmpty(2);
          final String uid = fetchList.getKeyedStringOrEmpty(ImapConstants.UID).getString();
          if (TextUtils.isEmpty(uid)) continue;

          ImapMessage message = (ImapMessage) messageMap.get(uid);
          if (message == null) continue;

          if (fp.contains(FetchProfile.Item.FLAGS)) {
            final ImapList flags = fetchList.getKeyedListOrEmpty(ImapConstants.FLAGS);
            for (int i = 0, count = flags.size(); i < count; i++) {
              final ImapString flag = flags.getStringOrEmpty(i);
              if (flag.is(ImapConstants.FLAG_DELETED)) {
                message.setFlagInternal(Flag.DELETED, true);
              } else if (flag.is(ImapConstants.FLAG_ANSWERED)) {
                message.setFlagInternal(Flag.ANSWERED, true);
              } else if (flag.is(ImapConstants.FLAG_SEEN)) {
                message.setFlagInternal(Flag.SEEN, true);
              } else if (flag.is(ImapConstants.FLAG_FLAGGED)) {
                message.setFlagInternal(Flag.FLAGGED, true);
              }
            }
          }
          if (fp.contains(FetchProfile.Item.ENVELOPE)) {
            final Date internalDate =
                fetchList.getKeyedStringOrEmpty(ImapConstants.INTERNALDATE).getDateOrNull();
            final int size =
                fetchList.getKeyedStringOrEmpty(ImapConstants.RFC822_SIZE).getNumberOrZero();
            final String header =
                fetchList
                    .getKeyedStringOrEmpty(ImapConstants.BODY_BRACKET_HEADER, true)
                    .getString();

            message.setInternalDate(internalDate);
            message.setSize(size);
            try {
              message.parse(Utility.streamFromAsciiString(header));
            } catch (Exception e) {
              VvmLog.e(TAG, "Error parsing header %s", e);
            }
          }
          if (fp.contains(FetchProfile.Item.STRUCTURE)) {
            ImapList bs = fetchList.getKeyedListOrEmpty(ImapConstants.BODYSTRUCTURE);
            if (!bs.isEmpty()) {
              try {
                parseBodyStructure(bs, message, ImapConstants.TEXT);
              } catch (MessagingException e) {
                VvmLog.v(TAG, "Error handling message", e);
                message.setBody(null);
              }
            }
          }
          if (fp.contains(FetchProfile.Item.BODY) || fp.contains(FetchProfile.Item.BODY_SANE)) {
            // Body is keyed by "BODY[]...".
            // Previously used "BODY[..." but this can be confused with "BODY[HEADER..."
            // TODO Should we accept "RFC822" as well??
            ImapString body = fetchList.getKeyedStringOrEmpty("BODY[]", true);
            InputStream bodyStream = body.getAsStream();
            try {
              message.parse(bodyStream);
            } catch (Exception e) {
              VvmLog.e(TAG, "Error parsing body %s", e);
            }
          }
          if (fetchPart != null) {
            InputStream bodyStream = fetchList.getKeyedStringOrEmpty("BODY[", true).getAsStream();
            String[] encodings = fetchPart.getHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING);

            String contentTransferEncoding = null;
            if (encodings != null && encodings.length > 0) {
              contentTransferEncoding = encodings[0];
            } else {
              // According to http://tools.ietf.org/html/rfc2045#section-6.1
              // "7bit" is the default.
              contentTransferEncoding = "7bit";
            }

            try {
              // TODO Don't create 2 temp files.
              // decodeBody creates BinaryTempFileBody, but we could avoid this
              // if we implement ImapStringBody.
              // (We'll need to share a temp file.  Protect it with a ref-count.)
              message.setBody(
                  decodeBody(
                      mStore.getContext(),
                      bodyStream,
                      contentTransferEncoding,
                      fetchPart.getSize(),
                      listener));
            } catch (Exception e) {
              // TODO: Figure out what kinds of exceptions might actually be thrown
              // from here. This blanket catch-all is because we're not sure what to
              // do if we don't have a contentTransferEncoding, and we don't have
              // time to figure out what exceptions might be thrown.
              VvmLog.e(TAG, "Error fetching body %s", e);
            }
          }

          if (listener != null) {
            listener.messageRetrieved(message);
          }
        } finally {
          destroyResponses();
        }
      } while (!response.isTagged());
    } catch (IOException ioe) {
      mStore.getImapHelper().handleEvent(OmtpEvents.DATA_GENERIC_IMAP_IOE);
      throw ioExceptionHandler(mConnection, ioe);
    }
  }

  /**
   * Removes any content transfer encoding from the stream and returns a Body. This code is
   * taken/condensed from MimeUtility.decodeBody
   */
  private static Body decodeBody(
      Context context,
      InputStream in,
      String contentTransferEncoding,
      int size,
      MessageRetrievalListener listener)
      throws IOException {
    // Get a properly wrapped input stream
    in = MimeUtility.getInputStreamForContentTransferEncoding(in, contentTransferEncoding);
    BinaryTempFileBody tempBody = new BinaryTempFileBody();
    OutputStream out = tempBody.getOutputStream();
    try {
      byte[] buffer = new byte[COPY_BUFFER_SIZE];
      int n = 0;
      int count = 0;
      while (-1 != (n = in.read(buffer))) {
        out.write(buffer, 0, n);
        count += n;
      }
    } catch (Base64DataException bde) {
      String warning = "\n\nThere was an error while decoding the message.";
      out.write(warning.getBytes());
    } finally {
      out.close();
    }
    return tempBody;
  }

  public String[] getPermanentFlags() {
    return PERMANENT_FLAGS;
  }

  /**
   * Handle any untagged responses that the caller doesn't care to handle themselves.
   *
   * @param responses
   */
  private void handleUntaggedResponses(List<ImapResponse> responses) {
    for (ImapResponse response : responses) {
      handleUntaggedResponse(response);
    }
  }

  /**
   * Handle an untagged response that the caller doesn't care to handle themselves.
   *
   * @param response
   */
  private void handleUntaggedResponse(ImapResponse response) {
    if (response.isDataResponse(1, ImapConstants.EXISTS)) {
      mMessageCount = response.getStringOrEmpty(0).getNumberOrZero();
    }
  }

  private static void parseBodyStructure(ImapList bs, Part part, String id)
      throws MessagingException {
    if (bs.getElementOrNone(0).isList()) {
      /*
       * This is a multipart/*
       */
      MimeMultipart mp = new MimeMultipart();
      for (int i = 0, count = bs.size(); i < count; i++) {
        ImapElement e = bs.getElementOrNone(i);
        if (e.isList()) {
          /*
           * For each part in the message we're going to add a new BodyPart and parse
           * into it.
           */
          MimeBodyPart bp = new MimeBodyPart();
          if (id.equals(ImapConstants.TEXT)) {
            parseBodyStructure(bs.getListOrEmpty(i), bp, Integer.toString(i + 1));

          } else {
            parseBodyStructure(bs.getListOrEmpty(i), bp, id + "." + (i + 1));
          }
          mp.addBodyPart(bp);

        } else {
          if (e.isString()) {
            mp.setSubType(bs.getStringOrEmpty(i).getString().toLowerCase(Locale.US));
          }
          break; // Ignore the rest of the list.
        }
      }
      part.setBody(mp);
    } else {
      /*
       * This is a body. We need to add as much information as we can find out about
       * it to the Part.
       */

      /*
      body type
      body subtype
      body parameter parenthesized list
      body id
      body description
      body encoding
      body size
      */

      final ImapString type = bs.getStringOrEmpty(0);
      final ImapString subType = bs.getStringOrEmpty(1);
      final String mimeType = (type.getString() + "/" + subType.getString()).toLowerCase(Locale.US);

      final ImapList bodyParams = bs.getListOrEmpty(2);
      final ImapString cid = bs.getStringOrEmpty(3);
      final ImapString encoding = bs.getStringOrEmpty(5);
      final int size = bs.getStringOrEmpty(6).getNumberOrZero();

      if (MimeUtility.mimeTypeMatches(mimeType, MimeUtility.MIME_TYPE_RFC822)) {
        // A body type of type MESSAGE and subtype RFC822
        // contains, immediately after the basic fields, the
        // envelope structure, body structure, and size in
        // text lines of the encapsulated message.
        // [MESSAGE, RFC822, [NAME, filename.eml], NIL, NIL, 7BIT, 5974, NIL,
        //     [INLINE, [FILENAME*0, Fwd: Xxx..., FILENAME*1, filename.eml]], NIL]
        /*
         * This will be caught by fetch and handled appropriately.
         */
        throw new MessagingException(
            "BODYSTRUCTURE " + MimeUtility.MIME_TYPE_RFC822 + " not yet supported.");
      }

      /*
       * Set the content type with as much information as we know right now.
       */
      final StringBuilder contentType = new StringBuilder(mimeType);

      /*
       * If there are body params we might be able to get some more information out
       * of them.
       */
      for (int i = 1, count = bodyParams.size(); i < count; i += 2) {

        // TODO We need to convert " into %22, but
        // because MimeUtility.getHeaderParameter doesn't recognize it,
        // we can't fix it for now.
        contentType.append(
            String.format(
                ";\n %s=\"%s\"",
                bodyParams.getStringOrEmpty(i - 1).getString(),
                bodyParams.getStringOrEmpty(i).getString()));
      }

      part.setHeader(MimeHeader.HEADER_CONTENT_TYPE, contentType.toString());

      // Extension items
      final ImapList bodyDisposition;

      if (type.is(ImapConstants.TEXT) && bs.getElementOrNone(9).isList()) {
        // If media-type is TEXT, 9th element might be: [body-fld-lines] := number
        // So, if it's not a list, use 10th element.
        // (Couldn't find evidence in the RFC if it's ALWAYS 10th element.)
        bodyDisposition = bs.getListOrEmpty(9);
      } else {
        bodyDisposition = bs.getListOrEmpty(8);
      }

      final StringBuilder contentDisposition = new StringBuilder();

      if (bodyDisposition.size() > 0) {
        final String bodyDisposition0Str =
            bodyDisposition.getStringOrEmpty(0).getString().toLowerCase(Locale.US);
        if (!TextUtils.isEmpty(bodyDisposition0Str)) {
          contentDisposition.append(bodyDisposition0Str);
        }

        final ImapList bodyDispositionParams = bodyDisposition.getListOrEmpty(1);
        if (!bodyDispositionParams.isEmpty()) {
          /*
           * If there is body disposition information we can pull some more
           * information about the attachment out.
           */
          for (int i = 1, count = bodyDispositionParams.size(); i < count; i += 2) {

            // TODO We need to convert " into %22.  See above.
            contentDisposition.append(
                String.format(
                    Locale.US,
                    ";\n %s=\"%s\"",
                    bodyDispositionParams
                        .getStringOrEmpty(i - 1)
                        .getString()
                        .toLowerCase(Locale.US),
                    bodyDispositionParams.getStringOrEmpty(i).getString()));
          }
        }
      }

      if ((size > 0)
          && (MimeUtility.getHeaderParameter(contentDisposition.toString(), "size") == null)) {
        contentDisposition.append(String.format(Locale.US, ";\n size=%d", size));
      }

      if (contentDisposition.length() > 0) {
        /*
         * Set the content disposition containing at least the size. Attachment
         * handling code will use this down the road.
         */
        part.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, contentDisposition.toString());
      }

      /*
       * Set the Content-Transfer-Encoding header. Attachment code will use this
       * to parse the body.
       */
      if (!encoding.isEmpty()) {
        part.setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING, encoding.getString());
      }

      /*
       * Set the Content-ID header.
       */
      if (!cid.isEmpty()) {
        part.setHeader(MimeHeader.HEADER_CONTENT_ID, cid.getString());
      }

      if (size > 0) {
        if (part instanceof ImapMessage) {
          ((ImapMessage) part).setSize(size);
        } else if (part instanceof MimeBodyPart) {
          ((MimeBodyPart) part).setSize(size);
        } else {
          throw new MessagingException("Unknown part type " + part.toString());
        }
      }
      part.setHeader(MimeHeader.HEADER_ANDROID_ATTACHMENT_STORE_DATA, id);
    }
  }

  public Message[] expunge() throws MessagingException {
    checkOpen();
    try {
      handleUntaggedResponses(mConnection.executeSimpleCommand(ImapConstants.EXPUNGE));
    } catch (IOException ioe) {
      mStore.getImapHelper().handleEvent(OmtpEvents.DATA_GENERIC_IMAP_IOE);
      throw ioExceptionHandler(mConnection, ioe);
    } finally {
      destroyResponses();
    }
    return null;
  }

  public void setFlags(Message[] messages, String[] flags, boolean value)
      throws MessagingException {
    checkOpen();

    String allFlags = "";
    if (flags.length > 0) {
      StringBuilder flagList = new StringBuilder();
      for (int i = 0, count = flags.length; i < count; i++) {
        String flag = flags[i];
        if (flag == Flag.SEEN) {
          flagList.append(" " + ImapConstants.FLAG_SEEN);
        } else if (flag == Flag.DELETED) {
          flagList.append(" " + ImapConstants.FLAG_DELETED);
        } else if (flag == Flag.FLAGGED) {
          flagList.append(" " + ImapConstants.FLAG_FLAGGED);
        } else if (flag == Flag.ANSWERED) {
          flagList.append(" " + ImapConstants.FLAG_ANSWERED);
        }
      }
      allFlags = flagList.substring(1);
    }
    try {
      mConnection.executeSimpleCommand(
          String.format(
              Locale.US,
              ImapConstants.UID_STORE + " %s %s" + ImapConstants.FLAGS_SILENT + " (%s)",
              ImapStore.joinMessageUids(messages),
              value ? "+" : "-",
              allFlags));

    } catch (IOException ioe) {
      mStore.getImapHelper().handleEvent(OmtpEvents.DATA_GENERIC_IMAP_IOE);
      throw ioExceptionHandler(mConnection, ioe);
    } finally {
      destroyResponses();
    }
  }

  /**
   * Selects the folder for use. Before performing any operations on this folder, it must be
   * selected.
   */
  private void doSelect() throws IOException, MessagingException {
    final List<ImapResponse> responses =
        mConnection.executeSimpleCommand(
            String.format(Locale.US, ImapConstants.SELECT + " \"%s\"", mName));

    // Assume the folder is opened read-write; unless we are notified otherwise
    mMode = MODE_READ_WRITE;
    int messageCount = -1;
    for (ImapResponse response : responses) {
      if (response.isDataResponse(1, ImapConstants.EXISTS)) {
        messageCount = response.getStringOrEmpty(0).getNumberOrZero();
      } else if (response.isOk()) {
        final ImapString responseCode = response.getResponseCodeOrEmpty();
        if (responseCode.is(ImapConstants.READ_ONLY)) {
          mMode = MODE_READ_ONLY;
        } else if (responseCode.is(ImapConstants.READ_WRITE)) {
          mMode = MODE_READ_WRITE;
        }
      } else if (response.isTagged()) { // Not OK
        mStore.getImapHelper().handleEvent(OmtpEvents.DATA_MAILBOX_OPEN_FAILED);
        throw new MessagingException(
            "Can't open mailbox: " + response.getStatusResponseTextOrEmpty());
      }
    }
    if (messageCount == -1) {
      throw new MessagingException("Did not find message count during select");
    }
    mMessageCount = messageCount;
    mExists = true;
  }

  public class Quota {

    public final int occupied;
    public final int total;

    public Quota(int occupied, int total) {
      this.occupied = occupied;
      this.total = total;
    }
  }

  public Quota getQuota() throws MessagingException {
    try {
      final List<ImapResponse> responses =
          mConnection.executeSimpleCommand(
              String.format(Locale.US, ImapConstants.GETQUOTAROOT + " \"%s\"", mName));

      for (ImapResponse response : responses) {
        if (!response.isDataResponse(0, ImapConstants.QUOTA)) {
          continue;
        }
        ImapList list = response.getListOrEmpty(2);
        for (int i = 0; i < list.size(); i += 3) {
          if (!list.getStringOrEmpty(i).is("voice")) {
            continue;
          }
          return new Quota(
              list.getStringOrEmpty(i + 1).getNumber(-1),
              list.getStringOrEmpty(i + 2).getNumber(-1));
        }
      }
    } catch (IOException ioe) {
      mStore.getImapHelper().handleEvent(OmtpEvents.DATA_GENERIC_IMAP_IOE);
      throw ioExceptionHandler(mConnection, ioe);
    } finally {
      destroyResponses();
    }
    return null;
  }

  private void checkOpen() throws MessagingException {
    if (!isOpen()) {
      throw new MessagingException("Folder " + mName + " is not open.");
    }
  }

  private MessagingException ioExceptionHandler(ImapConnection connection, IOException ioe) {
    VvmLog.d(TAG, "IO Exception detected: ", ioe);
    connection.close();
    if (connection == mConnection) {
      mConnection = null; // To prevent close() from returning the connection to the pool.
      close(false);
    }
    return new MessagingException(MessagingException.IOERROR, "IO Error", ioe);
  }

  public Message createMessage(String uid) {
    return new ImapMessage(uid, this);
  }
}
