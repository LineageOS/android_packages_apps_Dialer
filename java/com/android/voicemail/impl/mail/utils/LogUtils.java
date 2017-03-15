/**
 * Copyright (c) 2015 The Android Open Source Project
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.voicemail.impl.mail.utils;

import android.net.Uri;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.Log;
import com.android.voicemail.impl.VvmLog;
import java.util.List;

public class LogUtils {
  public static final String TAG = "Email Log";

  private static final String ACCOUNT_PREFIX = "account:";

  /** Priority constant for the println method; use LogUtils.v. */
  public static final int VERBOSE = Log.VERBOSE;

  /** Priority constant for the println method; use LogUtils.d. */
  public static final int DEBUG = Log.DEBUG;

  /** Priority constant for the println method; use LogUtils.i. */
  public static final int INFO = Log.INFO;

  /** Priority constant for the println method; use LogUtils.w. */
  public static final int WARN = Log.WARN;

  /** Priority constant for the println method; use LogUtils.e. */
  public static final int ERROR = Log.ERROR;

  /**
   * Used to enable/disable logging that we don't want included in production releases. This should
   * be set to DEBUG for production releases, and VERBOSE for internal builds.
   */
  private static final int MAX_ENABLED_LOG_LEVEL = DEBUG;

  private static Boolean sDebugLoggingEnabledForTests = null;

  /** Enable debug logging for unit tests. */
  @VisibleForTesting
  public static void setDebugLoggingEnabledForTests(boolean enabled) {
    setDebugLoggingEnabledForTestsInternal(enabled);
  }

  protected static void setDebugLoggingEnabledForTestsInternal(boolean enabled) {
    sDebugLoggingEnabledForTests = Boolean.valueOf(enabled);
  }

  /** Returns true if the build configuration prevents debug logging. */
  @VisibleForTesting
  public static boolean buildPreventsDebugLogging() {
    return MAX_ENABLED_LOG_LEVEL > VERBOSE;
  }

  /** Returns a boolean indicating whether debug logging is enabled. */
  protected static boolean isDebugLoggingEnabled(String tag) {
    if (buildPreventsDebugLogging()) {
      return false;
    }
    if (sDebugLoggingEnabledForTests != null) {
      return sDebugLoggingEnabledForTests.booleanValue();
    }
    return Log.isLoggable(tag, Log.DEBUG) || Log.isLoggable(TAG, Log.DEBUG);
  }

  /**
   * Returns a String for the specified content provider uri. This will do sanitation of the uri to
   * remove PII if debug logging is not enabled.
   */
  public static String contentUriToString(final Uri uri) {
    return contentUriToString(TAG, uri);
  }

  /**
   * Returns a String for the specified content provider uri. This will do sanitation of the uri to
   * remove PII if debug logging is not enabled.
   */
  public static String contentUriToString(String tag, Uri uri) {
    if (isDebugLoggingEnabled(tag)) {
      // Debug logging has been enabled, so log the uri as is
      return uri.toString();
    } else {
      // Debug logging is not enabled, we want to remove the email address from the uri.
      List<String> pathSegments = uri.getPathSegments();

      Uri.Builder builder =
          new Uri.Builder()
              .scheme(uri.getScheme())
              .authority(uri.getAuthority())
              .query(uri.getQuery())
              .fragment(uri.getFragment());

      // This assumes that the first path segment is the account
      final String account = pathSegments.get(0);

      builder = builder.appendPath(sanitizeAccountName(account));
      for (int i = 1; i < pathSegments.size(); i++) {
        builder.appendPath(pathSegments.get(i));
      }
      return builder.toString();
    }
  }

  /** Sanitizes an account name. If debug logging is not enabled, a sanitized name is returned. */
  public static String sanitizeAccountName(String accountName) {
    if (TextUtils.isEmpty(accountName)) {
      return "";
    }

    return ACCOUNT_PREFIX + sanitizeName(TAG, accountName);
  }

  public static String sanitizeName(final String tag, final String name) {
    if (TextUtils.isEmpty(name)) {
      return "";
    }

    if (isDebugLoggingEnabled(tag)) {
      return name;
    }

    return String.valueOf(name.hashCode());
  }

  /**
   * Checks to see whether or not a log for the specified tag is loggable at the specified level.
   */
  public static boolean isLoggable(String tag, int level) {
    if (MAX_ENABLED_LOG_LEVEL > level) {
      return false;
    }
    return Log.isLoggable(tag, level) || Log.isLoggable(TAG, level);
  }

  /**
   * Send a {@link #VERBOSE} log message.
   *
   * @param tag Used to identify the source of a log message. It usually identifies the class or
   *     activity where the log call occurs.
   * @param format the format string (see {@link java.util.Formatter#format})
   * @param args the list of arguments passed to the formatter. If there are more arguments than
   *     required by {@code format}, additional arguments are ignored.
   */
  public static void v(String tag, String format, Object... args) {
    if (isLoggable(tag, VERBOSE)) {
      VvmLog.v(tag, String.format(format, args));
    }
  }

  /**
   * Send a {@link #VERBOSE} log message.
   *
   * @param tag Used to identify the source of a log message. It usually identifies the class or
   *     activity where the log call occurs.
   * @param tr An exception to log
   * @param format the format string (see {@link java.util.Formatter#format})
   * @param args the list of arguments passed to the formatter. If there are more arguments than
   *     required by {@code format}, additional arguments are ignored.
   */
  public static void v(String tag, Throwable tr, String format, Object... args) {
    if (isLoggable(tag, VERBOSE)) {
      VvmLog.v(tag, String.format(format, args), tr);
    }
  }

  /**
   * Send a {@link #DEBUG} log message.
   *
   * @param tag Used to identify the source of a log message. It usually identifies the class or
   *     activity where the log call occurs.
   * @param format the format string (see {@link java.util.Formatter#format})
   * @param args the list of arguments passed to the formatter. If there are more arguments than
   *     required by {@code format}, additional arguments are ignored.
   */
  public static void d(String tag, String format, Object... args) {
    if (isLoggable(tag, DEBUG)) {
      VvmLog.d(tag, String.format(format, args));
    }
  }

  /**
   * Send a {@link #DEBUG} log message.
   *
   * @param tag Used to identify the source of a log message. It usually identifies the class or
   *     activity where the log call occurs.
   * @param tr An exception to log
   * @param format the format string (see {@link java.util.Formatter#format})
   * @param args the list of arguments passed to the formatter. If there are more arguments than
   *     required by {@code format}, additional arguments are ignored.
   */
  public static void d(String tag, Throwable tr, String format, Object... args) {
    if (isLoggable(tag, DEBUG)) {
      VvmLog.d(tag, String.format(format, args), tr);
    }
  }

  /**
   * Send a {@link #INFO} log message.
   *
   * @param tag Used to identify the source of a log message. It usually identifies the class or
   *     activity where the log call occurs.
   * @param format the format string (see {@link java.util.Formatter#format})
   * @param args the list of arguments passed to the formatter. If there are more arguments than
   *     required by {@code format}, additional arguments are ignored.
   */
  public static void i(String tag, String format, Object... args) {
    if (isLoggable(tag, INFO)) {
      VvmLog.i(tag, String.format(format, args));
    }
  }

  /**
   * Send a {@link #INFO} log message.
   *
   * @param tag Used to identify the source of a log message. It usually identifies the class or
   *     activity where the log call occurs.
   * @param tr An exception to log
   * @param format the format string (see {@link java.util.Formatter#format})
   * @param args the list of arguments passed to the formatter. If there are more arguments than
   *     required by {@code format}, additional arguments are ignored.
   */
  public static void i(String tag, Throwable tr, String format, Object... args) {
    if (isLoggable(tag, INFO)) {
      VvmLog.i(tag, String.format(format, args), tr);
    }
  }

  /**
   * Send a {@link #WARN} log message.
   *
   * @param tag Used to identify the source of a log message. It usually identifies the class or
   *     activity where the log call occurs.
   * @param format the format string (see {@link java.util.Formatter#format})
   * @param args the list of arguments passed to the formatter. If there are more arguments than
   *     required by {@code format}, additional arguments are ignored.
   */
  public static void w(String tag, String format, Object... args) {
    if (isLoggable(tag, WARN)) {
      VvmLog.w(tag, String.format(format, args));
    }
  }

  /**
   * Send a {@link #WARN} log message.
   *
   * @param tag Used to identify the source of a log message. It usually identifies the class or
   *     activity where the log call occurs.
   * @param tr An exception to log
   * @param format the format string (see {@link java.util.Formatter#format})
   * @param args the list of arguments passed to the formatter. If there are more arguments than
   *     required by {@code format}, additional arguments are ignored.
   */
  public static void w(String tag, Throwable tr, String format, Object... args) {
    if (isLoggable(tag, WARN)) {
      VvmLog.w(tag, String.format(format, args), tr);
    }
  }

  /**
   * Send a {@link #ERROR} log message.
   *
   * @param tag Used to identify the source of a log message. It usually identifies the class or
   *     activity where the log call occurs.
   * @param format the format string (see {@link java.util.Formatter#format})
   * @param args the list of arguments passed to the formatter. If there are more arguments than
   *     required by {@code format}, additional arguments are ignored.
   */
  public static void e(String tag, String format, Object... args) {
    if (isLoggable(tag, ERROR)) {
      VvmLog.e(tag, String.format(format, args));
    }
  }

  /**
   * Send a {@link #ERROR} log message.
   *
   * @param tag Used to identify the source of a log message. It usually identifies the class or
   *     activity where the log call occurs.
   * @param tr An exception to log
   * @param format the format string (see {@link java.util.Formatter#format})
   * @param args the list of arguments passed to the formatter. If there are more arguments than
   *     required by {@code format}, additional arguments are ignored.
   */
  public static void e(String tag, Throwable tr, String format, Object... args) {
    if (isLoggable(tag, ERROR)) {
      VvmLog.e(tag, String.format(format, args), tr);
    }
  }

  /**
   * What a Terrible Failure: Report a condition that should never happen. The error will always be
   * logged at level ASSERT with the call stack. Depending on system configuration, a report may be
   * added to the {@link android.os.DropBoxManager} and/or the process may be terminated immediately
   * with an error dialog.
   *
   * @param tag Used to identify the source of a log message. It usually identifies the class or
   *     activity where the log call occurs.
   * @param format the format string (see {@link java.util.Formatter#format})
   * @param args the list of arguments passed to the formatter. If there are more arguments than
   *     required by {@code format}, additional arguments are ignored.
   */
  public static void wtf(String tag, String format, Object... args) {
    VvmLog.wtf(tag, String.format(format, args), new Error());
  }

  /**
   * What a Terrible Failure: Report a condition that should never happen. The error will always be
   * logged at level ASSERT with the call stack. Depending on system configuration, a report may be
   * added to the {@link android.os.DropBoxManager} and/or the process may be terminated immediately
   * with an error dialog.
   *
   * @param tag Used to identify the source of a log message. It usually identifies the class or
   *     activity where the log call occurs.
   * @param tr An exception to log
   * @param format the format string (see {@link java.util.Formatter#format})
   * @param args the list of arguments passed to the formatter. If there are more arguments than
   *     required by {@code format}, additional arguments are ignored.
   */
  public static void wtf(String tag, Throwable tr, String format, Object... args) {
    VvmLog.wtf(tag, String.format(format, args), tr);
  }

  public static String byteToHex(int b) {
    return byteToHex(new StringBuilder(), b).toString();
  }

  public static StringBuilder byteToHex(StringBuilder sb, int b) {
    b &= 0xFF;
    sb.append("0123456789ABCDEF".charAt(b >> 4));
    sb.append("0123456789ABCDEF".charAt(b & 0xF));
    return sb;
  }
}
