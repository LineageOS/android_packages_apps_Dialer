/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.voicemail.impl.mail.internet;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Base64DataException;
import android.util.Base64InputStream;

import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.mail.Body;
import com.android.voicemail.impl.mail.BodyPart;
import com.android.voicemail.impl.mail.Message;
import com.android.voicemail.impl.mail.MessagingException;
import com.android.voicemail.impl.mail.Multipart;
import com.android.voicemail.impl.mail.Part;

import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.codec.EncoderUtil;
import org.apache.james.mime4j.codec.QuotedPrintableInputStream;
import org.apache.james.mime4j.util.CharsetUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MimeUtility {
  private static final String LOG_TAG = "Email";

  public static final String MIME_TYPE_RFC822 = "message/rfc822";
  private static final Pattern PATTERN_CR_OR_LF = Pattern.compile("\r|\n");

  /**
   * Replace sequences of CRLF+WSP with WSP. Tries to preserve original string object whenever
   * possible.
   */
  public static String unfold(String s) {
    if (s == null) {
      return null;
    }
    Matcher patternMatcher = PATTERN_CR_OR_LF.matcher(s);
    if (patternMatcher.find()) {
      patternMatcher.reset();
      s = patternMatcher.replaceAll("");
    }
    return s;
  }

  public static String decode(String s) {
    if (s == null) {
      return null;
    }
    return DecoderUtil.decodeEncodedWords(s, DecodeMonitor.STRICT);
  }

  public static String unfoldAndDecode(String s) {
    return decode(unfold(s));
  }

  // TODO implement proper foldAndEncode
  // NOTE: When this really works, we *must* remove all calls to foldAndEncode2() to prevent
  // duplication of encoding.
  public static String foldAndEncode(String s) {
    return s;
  }

  /**
   * INTERIM version of foldAndEncode that will be used only by Subject: headers. This is safer than
   * implementing foldAndEncode() (see above) and risking unknown damage to other headers.
   *
   * <p>TODO: Copy this code to foldAndEncode(), get rid of this function, confirm all working OK.
   *
   * @param s original string to encode and fold
   * @param usedCharacters number of characters already used up by header name
   * @return the String ready to be transmitted
   */
  public static String foldAndEncode2(String s, int usedCharacters) {
    // james.mime4j.codec.EncoderUtil.java
    // encode:  encodeIfNecessary(text, usage, numUsedInHeaderName)
    // Usage.TEXT_TOKENlooks like the right thing for subjects
    // use WORD_ENTITY for address/names

    String encoded = EncoderUtil.encodeIfNecessary(s, EncoderUtil.Usage.TEXT_TOKEN, usedCharacters);

    return fold(encoded, usedCharacters);
  }

  /**
   * INTERIM: From newer version of org.apache.james (but we don't want to import the entire
   * MimeUtil class).
   *
   * <p>Splits the specified string into a multiple-line representation with lines no longer than 76
   * characters (because the line might contain encoded words; see <a
   * href='http://www.faqs.org/rfcs/rfc2047.html'>RFC 2047</a> section 2). If the string contains
   * non-whitespace sequences longer than 76 characters a line break is inserted at the whitespace
   * character following the sequence resulting in a line longer than 76 characters.
   *
   * @param s string to split.
   * @param usedCharacters number of characters already used up. Usually the number of characters
   *     for header field name plus colon and one space.
   * @return a multiple-line representation of the given string.
   */
  public static String fold(String s, int usedCharacters) {
    final int maxCharacters = 76;

    final int length = s.length();
    if (usedCharacters + length <= maxCharacters) {
      return s;
    }

    StringBuilder sb = new StringBuilder();

    int lastLineBreak = -usedCharacters;
    int wspIdx = indexOfWsp(s, 0);
    while (true) {
      if (wspIdx == length) {
        sb.append(s.substring(Math.max(0, lastLineBreak)));
        return sb.toString();
      }

      int nextWspIdx = indexOfWsp(s, wspIdx + 1);

      if (nextWspIdx - lastLineBreak > maxCharacters) {
        sb.append(s.substring(Math.max(0, lastLineBreak), wspIdx));
        sb.append("\r\n");
        lastLineBreak = wspIdx;
      }

      wspIdx = nextWspIdx;
    }
  }

  /**
   * INTERIM: From newer version of org.apache.james (but we don't want to import the entire
   * MimeUtil class).
   *
   * <p>Search for whitespace.
   */
  private static int indexOfWsp(String s, int fromIndex) {
    final int len = s.length();
    for (int index = fromIndex; index < len; index++) {
      char c = s.charAt(index);
      if (c == ' ' || c == '\t') {
        return index;
      }
    }
    return len;
  }

  /**
   * Returns the named parameter of a header field. If name is null the first parameter is returned,
   * or if there are no additional parameters in the field the entire field is returned. Otherwise
   * the named parameter is searched for in a case insensitive fashion and returned. If the
   * parameter cannot be found the method returns null.
   *
   * <p>TODO: quite inefficient with the inner trimming & splitting. TODO: Also has a latent bug:
   * uses "startsWith" to match the name, which can false-positive. TODO: The doc says that for a
   * null name you get the first param, but you get the header. Should probably just fix the doc,
   * but if other code assumes that behavior, fix the code. TODO: Need to decode %-escaped strings,
   * as in: filename="ab%22d". ('+' -> ' ' conversion too? check RFC)
   *
   * @param header
   * @param name
   * @return the entire header (if name=null), the found parameter, or null
   */
  public static String getHeaderParameter(String header, String name) {
    if (header == null) {
      return null;
    }
    String[] parts = unfold(header).split(";");
    if (name == null) {
      return parts[0].trim();
    }
    String lowerCaseName = name.toLowerCase();
    for (String part : parts) {
      if (part.trim().toLowerCase().startsWith(lowerCaseName)) {
        String[] parameterParts = part.split("=", 2);
        if (parameterParts.length < 2) {
          return null;
        }
        String parameter = parameterParts[1].trim();
        if (parameter.startsWith("\"") && parameter.endsWith("\"")) {
          return parameter.substring(1, parameter.length() - 1);
        } else {
          return parameter;
        }
      }
    }
    return null;
  }

  /**
   * Reads the Part's body and returns a String based on any charset conversion that needed to be
   * done.
   *
   * @param part The part containing a body
   * @return a String containing the converted text in the body, or null if there was no text or an
   *     error during conversion.
   */
  public static String getTextFromPart(Part part) {
    try {
      if (part != null && part.getBody() != null) {
        InputStream in = part.getBody().getInputStream();
        String mimeType = part.getMimeType();
        if (mimeType != null && MimeUtility.mimeTypeMatches(mimeType, "text/*")) {
          /*
           * Now we read the part into a buffer for further processing. Because
           * the stream is now wrapped we'll remove any transfer encoding at this point.
           */
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          IOUtils.copy(in, out);
          in.close();
          in = null; // we want all of our memory back, and close might not release

          /*
           * We've got a text part, so let's see if it needs to be processed further.
           */
          String charset = getHeaderParameter(part.getContentType(), "charset");
          if (charset != null) {
            /*
             * See if there is conversion from the MIME charset to the Java one.
             */
            charset = CharsetUtil.lookup(charset).name();
          }
          /*
           * No encoding, so use us-ascii, which is the standard.
           */
          if (charset == null) {
            charset = "ASCII";
          }
          /*
           * Convert and return as new String
           */
          String result = out.toString(charset);
          out.close();
          return result;
        }
      }

    } catch (OutOfMemoryError | Exception oom) {
      /*
       * If we are not able to process the body there's nothing we can do about it. Return
       * null and let the upper layers handle the missing content.
       */
      VvmLog.e(LOG_TAG, "Unable to getTextFromPart " + oom);
    }
    return null;
  }

  /**
   * Returns true if the given mimeType matches the matchAgainst specification. The comparison
   * ignores case and the matchAgainst string may include "*" for a wildcard (e.g. "image/*").
   *
   * @param mimeType A MIME type to check.
   * @param matchAgainst A MIME type to check against. May include wildcards.
   * @return true if the mimeType matches
   */
  public static boolean mimeTypeMatches(String mimeType, String matchAgainst) {
    Pattern p = Pattern.compile(matchAgainst.replaceAll("\\*", "\\.\\*"), Pattern.CASE_INSENSITIVE);
    return p.matcher(mimeType).matches();
  }

  /**
   * Returns true if the given mimeType matches any of the matchAgainst specifications. The
   * comparison ignores case and the matchAgainst strings may include "*" for a wildcard (e.g.
   * "image/*").
   *
   * @param mimeType A MIME type to check.
   * @param matchAgainst An array of MIME types to check against. May include wildcards.
   * @return true if the mimeType matches any of the matchAgainst strings
   */
  public static boolean mimeTypeMatches(String mimeType, String[] matchAgainst) {
    for (String matchType : matchAgainst) {
      if (mimeTypeMatches(mimeType, matchType)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Given an input stream and a transfer encoding, return a wrapped input stream for that encoding
   * (or the original if none is required)
   *
   * @param in the input stream
   * @param contentTransferEncoding the content transfer encoding
   * @return a properly wrapped stream
   */
  public static InputStream getInputStreamForContentTransferEncoding(
      InputStream in, String contentTransferEncoding) {
    if (contentTransferEncoding != null) {
      contentTransferEncoding = MimeUtility.getHeaderParameter(contentTransferEncoding, null);
      if ("quoted-printable".equalsIgnoreCase(contentTransferEncoding)) {
        in = new QuotedPrintableInputStream(in);
      } else if ("base64".equalsIgnoreCase(contentTransferEncoding)) {
        in = new Base64InputStream(in, Base64.DEFAULT);
      }
    }
    return in;
  }

  /** Removes any content transfer encoding from the stream and returns a Body. */
  public static Body decodeBody(InputStream in, String contentTransferEncoding) throws IOException {
    /*
     * We'll remove any transfer encoding by wrapping the stream.
     */
    in = getInputStreamForContentTransferEncoding(in, contentTransferEncoding);
    BinaryTempFileBody tempBody = new BinaryTempFileBody();
    OutputStream out = tempBody.getOutputStream();
    try {
      IOUtils.copy(in, out);
    } catch (Base64DataException bde) {
      // TODO Need to fix this somehow
      //String warning = "\n\n" + Email.getMessageDecodeErrorString();
      //out.write(warning.getBytes());
    } finally {
      out.close();
    }
    return tempBody;
  }

  /**
   * Recursively scan a Part (usually a Message) and sort out which of its children will be
   * "viewable" and which will be attachments.
   *
   * @param part The part to be broken down
   * @param viewables This arraylist will be populated with all parts that appear to be the
   *     "message" (e.g. text/plain & text/html)
   * @param attachments This arraylist will be populated with all parts that appear to be
   *     attachments (including inlines)
   * @throws MessagingException
   */
  public static void collectParts(Part part, ArrayList<Part> viewables, ArrayList<Part> attachments)
      throws MessagingException {
    String disposition = part.getDisposition();
    String dispositionType = MimeUtility.getHeaderParameter(disposition, null);
    // If a disposition is not specified, default to "inline"
    boolean inline =
        TextUtils.isEmpty(dispositionType) || "inline".equalsIgnoreCase(dispositionType);
    // The lower-case mime type
    String mimeType = part.getMimeType().toLowerCase();

    if (part.getBody() instanceof Multipart) {
      // If the part is Multipart but not alternative it's either mixed or
      // something we don't know about, which means we treat it as mixed
      // per the spec. We just process its pieces recursively.
      MimeMultipart mp = (MimeMultipart) part.getBody();
      boolean foundHtml = false;
      if (mp.getSubTypeForTest().equals("alternative")) {
        for (int i = 0; i < mp.getCount(); i++) {
          if (mp.getBodyPart(i).isMimeType("text/html")) {
            foundHtml = true;
            break;
          }
        }
      }
      for (int i = 0; i < mp.getCount(); i++) {
        // See if we have text and html
        BodyPart bp = mp.getBodyPart(i);
        // If there's html, don't bother loading text
        if (foundHtml && bp.isMimeType("text/plain")) {
          continue;
        }
        collectParts(bp, viewables, attachments);
      }
    } else if (part.getBody() instanceof Message) {
      // If the part is an embedded message we just continue to process
      // it, pulling any viewables or attachments into the running list.
      Message message = (Message) part.getBody();
      collectParts(message, viewables, attachments);
    } else if (inline && (mimeType.startsWith("text") || (mimeType.startsWith("image")))) {
      // We'll treat text and images as viewables
      viewables.add(part);
    } else {
      // Everything else is an attachment.
      attachments.add(part);
    }
  }
}
