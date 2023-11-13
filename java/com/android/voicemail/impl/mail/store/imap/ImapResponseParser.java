/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.voicemail.impl.mail.store.imap;

import android.text.TextUtils;

import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.mail.FixedLengthInputStream;
import com.android.voicemail.impl.mail.MessagingException;
import com.android.voicemail.impl.mail.PeekableInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/** IMAP response parser. */
public class ImapResponseParser {
  private static final String TAG = "ImapResponseParser";

  /** Literal larger than this will be stored in temp file. */
  public static final int LITERAL_KEEP_IN_MEMORY_THRESHOLD = 2 * 1024 * 1024;

  /** Input stream */
  private final PeekableInputStream in;

  private final int literalKeepInMemoryThreshold;

  /** StringBuilder used by readUntil() */
  private final StringBuilder bufferReadUntil = new StringBuilder();

  /** StringBuilder used by parseBareString() */
  private final StringBuilder parseBareString = new StringBuilder();

  /**
   * We store all {@link ImapResponse} in it. {@link #destroyResponses()} must be called from time
   * to time to destroy them and clear it.
   */
  private final ArrayList<ImapResponse> responsesToDestroy = new ArrayList<>();

  /**
   * Exception thrown when we receive BYE. It derives from IOException, so it'll be treated in the
   * same way EOF does.
   */
  public static class ByeException extends IOException {
    public static final String MESSAGE = "Received BYE";

    public ByeException() {
      super(MESSAGE);
    }
  }

  /** Public constructor for normal use. */
  public ImapResponseParser(InputStream in) {
    this(in, LITERAL_KEEP_IN_MEMORY_THRESHOLD);
  }

  /** Constructor for testing to override the literal size threshold. */
  /* package for test */ ImapResponseParser(InputStream in, int literalKeepInMemoryThreshold) {
    this.in = new PeekableInputStream(in);
    this.literalKeepInMemoryThreshold = literalKeepInMemoryThreshold;
  }

  private static IOException newEOSException() {
    final String message = "End of stream reached";
    VvmLog.d(TAG, message);
    return new IOException(message);
  }

  /**
   * Peek next one byte.
   *
   * <p>Throws IOException() if reaches EOF. As long as logical response lines end with \r\n, we
   * shouldn't see EOF during parsing.
   */
  private int peek() throws IOException {
    final int next = in.peek();
    if (next == -1) {
      throw newEOSException();
    }
    return next;
  }

  /**
   * Read and return one byte from {@link #in}, and put it in {@link #mDiscourseLogger}.
   *
   * <p>Throws IOException() if reaches EOF. As long as logical response lines end with \r\n, we
   * shouldn't see EOF during parsing.
   */
  private int readByte() throws IOException {
    int next = in.read();
    if (next == -1) {
      throw newEOSException();
    }
    return next;
  }

  /**
   * Destroy all the {@link ImapResponse}s stored in the internal storage and clear it.
   *
   * @see #readResponse()
   */
  public void destroyResponses() {
    for (ImapResponse r : responsesToDestroy) {
      r.destroy();
    }
    responsesToDestroy.clear();
  }

  /**
   * Reads the next response available on the stream and returns an {@link ImapResponse} object that
   * represents it.
   *
   * <p>When this method successfully returns an {@link ImapResponse}, the {@link ImapResponse} is
   * stored in the internal storage. When the {@link ImapResponse} is no longer used {@link
   * #destroyResponses} should be called to destroy all the responses in the array.
   *
   * @param byeExpected is a untagged BYE response expected? If not proper cleanup will be done and
   *     {@link ByeException} will be thrown.
   * @return the parsed {@link ImapResponse} object.
   * @exception ByeException when detects BYE and <code>byeExpected</code> is false.
   */
  public ImapResponse readResponse(boolean byeExpected) throws IOException, MessagingException {
    ImapResponse response = null;
    try {
      response = parseResponse();
    } catch (RuntimeException e) {
      // Parser crash -- log network activities.
      onParseError(e);
      throw e;
    } catch (IOException e) {
      // Network error, or received an unexpected char.
      onParseError(e);
      throw e;
    }

    // Handle this outside of try-catch.  We don't have to dump protocol log when getting BYE.
    if (!byeExpected && response.is(0, ImapConstants.BYE)) {
      VvmLog.w(TAG, ByeException.MESSAGE);
      response.destroy();
      throw new ByeException();
    }
    responsesToDestroy.add(response);
    return response;
  }

  private void onParseError(Exception e) {
    // Read a few more bytes, so that the log will contain some more context, even if the parser
    // crashes in the middle of a response.
    // This also makes sure the byte in question will be logged, no matter where it crashes.
    // e.g. when parseAtom() peeks and finds at an unexpected char, it throws an exception
    // before actually reading it.
    // However, we don't want to read too much, because then it may get into an email message.
    try {
      for (int i = 0; i < 4; i++) {
        int b = readByte();
        if (b == -1 || b == '\n') {
          break;
        }
      }
    } catch (IOException ignore) {
    }
    VvmLog.w(TAG, "Exception detected: " + e.getMessage());
  }

  /**
   * Read next byte from stream and throw it away. If the byte is different from {@code expected}
   * throw {@link MessagingException}.
   */
  /* package for test */ void expect(char expected) throws IOException {
    final int next = readByte();
    if (expected != next) {
      throw new IOException(
          String.format(
              "Expected %04x (%c) but got %04x (%c)", (int) expected, expected, next, (char) next));
    }
  }

  /**
   * Read bytes until we find {@code end}, and return all as string. The {@code end} will be read
   * (rather than peeked) and won't be included in the result.
   */
  /* package for test */ String readUntil(char end) throws IOException {
    bufferReadUntil.setLength(0);
    for (; ; ) {
      final int ch = readByte();
      if (ch != end) {
        bufferReadUntil.append((char) ch);
      } else {
        return bufferReadUntil.toString();
      }
    }
  }

  /** Read all bytes until \r\n. */
  /* package */ String readUntilEol() throws IOException {
    String ret = readUntil('\r');
    expect('\n'); // TODO Should this really be error?
    return ret;
  }

  /** Parse and return the response line. */
  private ImapResponse parseResponse() throws IOException, MessagingException {
    // We need to destroy the response if we get an exception.
    // So, we first store the response that's being built in responseToDestroy, until it's
    // completely built, at which point we copy it into responseToReturn and null out
    // responseToDestroyt.
    // If responseToDestroy is not null in finally, we destroy it because that means
    // we got an exception somewhere.
    ImapResponse responseToDestroy = null;
    final ImapResponse responseToReturn;

    try {
      final int ch = peek();
      if (ch == '+') { // Continuation request
        readByte(); // skip +
        expect(' ');
        responseToDestroy = new ImapResponse(null, true);

        // If it's continuation request, we don't really care what's in it.
        responseToDestroy.add(new ImapSimpleString(readUntilEol()));

        // Response has successfully been built.  Let's return it.
        responseToReturn = responseToDestroy;
        responseToDestroy = null;
      } else {
        // Status response or response data
        final String tag;
        if (ch == '*') {
          tag = null;
          readByte(); // skip *
          expect(' ');
        } else {
          tag = readUntil(' ');
        }
        responseToDestroy = new ImapResponse(tag, false);

        final ImapString firstString = parseBareString();
        responseToDestroy.add(firstString);

        // parseBareString won't eat a space after the string, so we need to skip it,
        // if exists.
        // If the next char is not ' ', it should be EOL.
        if (peek() == ' ') {
          readByte(); // skip ' '

          if (responseToDestroy.isStatusResponse()) { // It's a status response

            // Is there a response code?
            final int next = peek();
            if (next == '[') {
              responseToDestroy.add(parseList('[', ']'));
              if (peek() == ' ') { // Skip following space
                readByte();
              }
            }

            String rest = readUntilEol();
            if (!TextUtils.isEmpty(rest)) {
              // The rest is free-form text.
              responseToDestroy.add(new ImapSimpleString(rest));
            }
          } else { // It's a response data.
            parseElements(responseToDestroy, '\0');
          }
        } else {
          expect('\r');
          expect('\n');
        }

        // Response has successfully been built.  Let's return it.
        responseToReturn = responseToDestroy;
        responseToDestroy = null;
      }
    } finally {
      if (responseToDestroy != null) {
        // We get an exception.
        responseToDestroy.destroy();
      }
    }

    return responseToReturn;
  }

  private ImapElement parseElement() throws IOException, MessagingException {
    final int next = peek();
    switch (next) {
      case '(':
        return parseList('(', ')');
      case '[':
        return parseList('[', ']');
      case '"':
        readByte(); // Skip "
        return new ImapSimpleString(readUntil('"'));
      case '{':
        return parseLiteral();
      case '\r': // CR
        readByte(); // Consume \r
        expect('\n'); // Should be followed by LF.
        return null;
      case '\n': // LF // There shouldn't be a bare LF, but just in case.
        readByte(); // Consume \n
        return null;
      default:
        return parseBareString();
    }
  }

  /**
   * Parses an atom.
   *
   * <p>Special case: If an atom contains '[', everything until the next ']' will be considered a
   * part of the atom. (e.g. "BODY[HEADER.FIELDS ("DATE" ...)]" will become a single ImapString)
   *
   * <p>If the value is "NIL", returns an empty string.
   */
  private ImapString parseBareString() throws IOException, MessagingException {
    parseBareString.setLength(0);
    for (; ; ) {
      final int ch = peek();

      // TODO Can we clean this up?  (This condition is from the old parser.)
      if (ch == '('
          || ch == ')'
          || ch == '{'
          || ch == ' '
          ||
          // ']' is not part of atom (it's in resp-specials)
          ch == ']'
          ||
          // docs claim that flags are \ atom but atom isn't supposed to
          // contain
          // * and some flags contain *
          // ch == '%' || ch == '*' ||
          ch == '%'
          ||
          // TODO probably should not allow \ and should recognize
          // it as a flag instead
          // ch == '"' || ch == '\' ||
          ch == '"'
          || (0x00 <= ch && ch <= 0x1f)
          || ch == 0x7f) {
        if (parseBareString.length() == 0) {
          throw new MessagingException("Expected string, none found.");
        }
        String s = parseBareString.toString();

        // NIL will be always converted into the empty string.
        if (ImapConstants.NIL.equalsIgnoreCase(s)) {
          return ImapString.EMPTY;
        }
        return new ImapSimpleString(s);
      } else if (ch == '[') {
        // Eat all until next ']'
        parseBareString.append((char) readByte());
        parseBareString.append(readUntil(']'));
        parseBareString.append(']'); // readUntil won't include the end char.
      } else {
        parseBareString.append((char) readByte());
      }
    }
  }

  private void parseElements(ImapList list, char end) throws IOException, MessagingException {
    for (; ; ) {
      for (; ; ) {
        final int next = peek();
        if (next == end) {
          return;
        }
        if (next != ' ') {
          break;
        }
        // Skip space
        readByte();
      }
      final ImapElement el = parseElement();
      if (el == null) { // EOL
        return;
      }
      list.add(el);
    }
  }

  private ImapList parseList(char opening, char closing) throws IOException, MessagingException {
    expect(opening);
    final ImapList list = new ImapList();
    parseElements(list, closing);
    expect(closing);
    return list;
  }

  private ImapString parseLiteral() throws IOException, MessagingException {
    expect('{');
    final int size;
    try {
      size = Integer.parseInt(readUntil('}'));
    } catch (NumberFormatException nfe) {
      throw new MessagingException("Invalid length in literal");
    }
    if (size < 0) {
      throw new MessagingException("Invalid negative length in literal");
    }
    expect('\r');
    expect('\n');
    FixedLengthInputStream in = new FixedLengthInputStream(this.in, size);
    if (size > literalKeepInMemoryThreshold) {
      return new ImapTempFileLiteral(in);
    } else {
      return new ImapMemoryLiteral(in);
    }
  }
}
