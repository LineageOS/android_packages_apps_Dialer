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

package com.android.voicemail.impl.mail.store.imap;

import android.annotation.TargetApi;
import android.os.Build.VERSION_CODES;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.ArrayMap;
import android.util.Base64;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.mail.MailTransport;
import com.android.voicemail.impl.mail.MessagingException;
import com.android.voicemail.impl.mail.store.ImapStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;

@SuppressWarnings("AndroidApiChecker") // Map.getOrDefault() is java8
@TargetApi(VERSION_CODES.O)
public class DigestMd5Utils {

  private static final String TAG = "DigestMd5Utils";

  private static final String DIGEST_CHARSET = "CHARSET";
  private static final String DIGEST_USERNAME = "username";
  private static final String DIGEST_REALM = "realm";
  private static final String DIGEST_NONCE = "nonce";
  private static final String DIGEST_NC = "nc";
  private static final String DIGEST_CNONCE = "cnonce";
  private static final String DIGEST_URI = "digest-uri";
  private static final String DIGEST_RESPONSE = "response";
  private static final String DIGEST_QOP = "qop";

  private static final String RESPONSE_AUTH_HEADER = "rspauth=";
  private static final String HEX_CHARS = "0123456789abcdef";

  /** Represents the set of data we need to generate the DIGEST-MD5 response. */
  public static class Data {

    private static final String CHARSET = "utf-8";

    public String username;
    public String password;
    public String realm;
    public String nonce;
    public String nc;
    public String cnonce;
    public String digestUri;
    public String qop;

    @VisibleForTesting
    Data() {
      // Do nothing
    }

    public Data(ImapStore imapStore, MailTransport transport, Map<String, String> challenge) {
      username = imapStore.getUsername();
      password = imapStore.getPassword();
      realm = challenge.getOrDefault(DIGEST_REALM, "");
      nonce = challenge.get(DIGEST_NONCE);
      cnonce = createCnonce();
      nc = "00000001"; // Subsequent Authentication not supported, nounce count always 1.
      qop = "auth"; // Other config not supported
      digestUri = "imap/" + transport.getHost();
    }

    private static String createCnonce() {
      SecureRandom generator = new SecureRandom();

      // At least 64 bits of entropy is required
      byte[] rawBytes = new byte[8];
      generator.nextBytes(rawBytes);

      return Base64.encodeToString(rawBytes, Base64.NO_WRAP);
    }

    /** Verify the response-auth returned by the server is correct. */
    public void verifyResponseAuth(String response) throws MessagingException {
      if (!response.startsWith(RESPONSE_AUTH_HEADER)) {
        throw new MessagingException("response-auth expected");
      }
      if (!response
          .substring(RESPONSE_AUTH_HEADER.length())
          .equals(DigestMd5Utils.getResponse(this, true))) {
        throw new MessagingException("invalid response-auth return from the server.");
      }
    }

    public String createResponse() {
      String response = getResponse(this, false);
      ResponseBuilder builder = new ResponseBuilder();
      builder
          .append(DIGEST_CHARSET, CHARSET)
          .appendQuoted(DIGEST_USERNAME, username)
          .appendQuoted(DIGEST_REALM, realm)
          .appendQuoted(DIGEST_NONCE, nonce)
          .append(DIGEST_NC, nc)
          .appendQuoted(DIGEST_CNONCE, cnonce)
          .appendQuoted(DIGEST_URI, digestUri)
          .append(DIGEST_RESPONSE, response)
          .append(DIGEST_QOP, qop);
      return builder.toString();
    }

    private static class ResponseBuilder {

      private StringBuilder mBuilder = new StringBuilder();

      public ResponseBuilder appendQuoted(String key, String value) {
        if (mBuilder.length() != 0) {
          mBuilder.append(",");
        }
        mBuilder.append(key).append("=\"").append(value).append("\"");
        return this;
      }

      public ResponseBuilder append(String key, String value) {
        if (mBuilder.length() != 0) {
          mBuilder.append(",");
        }
        mBuilder.append(key).append("=").append(value);
        return this;
      }

      @Override
      public String toString() {
        return mBuilder.toString();
      }
    }
  }

  /*
     response-value  =
         toHex( getKeyDigest ( toHex(getMd5(a1)),
         { nonce-value, ":" nc-value, ":",
           cnonce-value, ":", qop-value, ":", toHex(getMd5(a2)) }))
  * @param isResponseAuth is the response the one the server is returning us. response-auth has
  * different a2 format.
  */
  @VisibleForTesting
  static String getResponse(Data data, boolean isResponseAuth) {
    StringBuilder a1 = new StringBuilder();
    a1.append(
        new String(
            getMd5(data.username + ":" + data.realm + ":" + data.password),
            StandardCharsets.ISO_8859_1));
    a1.append(":").append(data.nonce).append(":").append(data.cnonce);

    StringBuilder a2 = new StringBuilder();
    if (!isResponseAuth) {
      a2.append("AUTHENTICATE");
    }
    a2.append(":").append(data.digestUri);

    return toHex(
        getKeyDigest(
            toHex(getMd5(a1.toString())),
            data.nonce
                + ":"
                + data.nc
                + ":"
                + data.cnonce
                + ":"
                + data.qop
                + ":"
                + toHex(getMd5(a2.toString()))));
  }

  /** Let getMd5(s) be the 16 octet MD5 hash [RFC 1321] of the octet string s. */
  private static byte[] getMd5(String s) {
    try {
      MessageDigest digester = MessageDigest.getInstance("MD5");
      digester.update(s.getBytes(StandardCharsets.ISO_8859_1));
      return digester.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Let getKeyDigest(k, s) be getMd5({k, ":", s}), i.e., the 16 octet hash of the string k, a colon
   * and the string s.
   */
  private static byte[] getKeyDigest(String k, String s) {
    StringBuilder builder = new StringBuilder(k).append(":").append(s);
    return getMd5(builder.toString());
  }

  /**
   * Let toHex(n) be the representation of the 16 octet MD5 hash n as a string of 32 hex digits
   * (with alphabetic characters always in lower case, since MD5 is case sensitive).
   */
  private static String toHex(byte[] n) {
    StringBuilder result = new StringBuilder();
    for (byte b : n) {
      int unsignedByte = b & 0xFF;
      result
          .append(HEX_CHARS.charAt(unsignedByte / 16))
          .append(HEX_CHARS.charAt(unsignedByte % 16));
    }
    return result.toString();
  }

  public static Map<String, String> parseDigestMessage(String message) throws MessagingException {
    Map<String, String> result = new DigestMessageParser(message).parse();
    if (!result.containsKey(DIGEST_NONCE)) {
      throw new MessagingException("nonce missing from server DIGEST-MD5 challenge");
    }
    return result;
  }

  /** Parse the key-value pair returned by the server. */
  private static class DigestMessageParser {

    private final String mMessage;
    private int mPosition = 0;
    private Map<String, String> mResult = new ArrayMap<>();

    public DigestMessageParser(String message) {
      mMessage = message;
    }

    @Nullable
    public Map<String, String> parse() {
      try {
        while (mPosition < mMessage.length()) {
          parsePair();
          if (mPosition != mMessage.length()) {
            expect(',');
          }
        }
      } catch (IndexOutOfBoundsException e) {
        VvmLog.e(TAG, e.toString());
        return null;
      }
      return mResult;
    }

    private void parsePair() {
      String key = parseKey();
      expect('=');
      String value = parseValue();
      mResult.put(key, value);
    }

    private void expect(char c) {
      if (pop() != c) {
        throw new IllegalStateException("unexpected character " + mMessage.charAt(mPosition));
      }
    }

    private char pop() {
      char result = peek();
      mPosition++;
      return result;
    }

    private char peek() {
      return mMessage.charAt(mPosition);
    }

    private void goToNext(char c) {
      while (peek() != c) {
        mPosition++;
      }
    }

    private String parseKey() {
      int start = mPosition;
      goToNext('=');
      return mMessage.substring(start, mPosition);
    }

    private String parseValue() {
      if (peek() == '"') {
        return parseQuotedValue();
      } else {
        return parseUnquotedValue();
      }
    }

    private String parseQuotedValue() {
      expect('"');
      StringBuilder result = new StringBuilder();
      while (true) {
        char c = pop();
        if (c == '\\') {
          result.append(pop());
        } else if (c == '"') {
          break;
        } else {
          result.append(c);
        }
      }
      return result.toString();
    }

    private String parseUnquotedValue() {
      StringBuilder result = new StringBuilder();
      while (true) {
        char c = pop();
        if (c == '\\') {
          result.append(pop());
        } else if (c == ',') {
          mPosition--;
          break;
        } else {
          result.append(c);
        }

        if (mPosition == mMessage.length()) {
          break;
        }
      }
      return result.toString();
    }
  }
}
