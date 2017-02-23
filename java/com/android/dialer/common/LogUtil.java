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
 * limitations under the License
 */

package com.android.dialer.common;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

/** Provides logging functions. */
public class LogUtil {

  public static final String TAG = "Dialer";
  private static final String SEPARATOR = " - ";

  private LogUtil() {}

  /**
   * Log at a verbose level. Verbose logs should generally be filtered out, but may be useful when
   * additional information is needed (e.g. to see how a particular flow evolved). These logs will
   * not generally be available on production builds.
   *
   * @param tag An identifier to allow searching for related logs. Generally of the form
   *     'Class.method'.
   * @param msg The message you would like logged, possibly with format arguments.
   * @param args Optional arguments to be used in the formatted string.
   * @see {@link String#format(String, Object...)}
   * @see {@link android.util.Log#v(String, String)}
   */
  public static void v(@NonNull String tag, @Nullable String msg, @Nullable Object... args) {
    println(android.util.Log.VERBOSE, TAG, tag, msg, args);
  }

  /**
   * Log at a debug level. Debug logs should provide known-useful information to aid in
   * troubleshooting or evaluating flow. These logs will not generally be available on production
   * builds.
   *
   * @param tag An identifier to allow searching for related logs. Generally of the form
   *     'Class.method'
   * @param msg The message you would like logged, possibly with format arguments
   * @param args Optional arguments to be used in the formatted string
   * @see {@link String#format(String, Object...)}
   * @see {@link android.util.Log#d(String, String)}
   */
  public static void d(@NonNull String tag, @Nullable String msg, @Nullable Object... args) {
    println(android.util.Log.DEBUG, TAG, tag, msg, args);
  }

  /**
   * Log at an info level. Info logs provide information that would be useful to have on production
   * builds for troubleshooting.
   *
   * @param tag An identifier to allow searching for related logs. Generally of the form
   *     'Class.method'.
   * @param msg The message you would like logged, possibly with format arguments.
   * @param args Optional arguments to be used in the formatted string.
   * @see {@link String#format(String, Object...)}
   * @see {@link android.util.Log#i(String, String)}
   */
  public static void i(@NonNull String tag, @Nullable String msg, @Nullable Object... args) {
    println(android.util.Log.INFO, TAG, tag, msg, args);
  }

  /**
   * Log entry into a method at the info level.
   *
   * @param tag An identifier to allow searching for related logs. Generally of the form
   *     'Class.method'.
   */
  public static void enterBlock(String tag) {
    println(android.util.Log.INFO, TAG, tag, "enter");
  }

  /**
   * Log at a warn level. Warn logs indicate a possible error (e.g. a default switch branch was hit,
   * or a null object was expected to be non-null), but recovery is possible. This may be used when
   * it is not guaranteed that an indeterminate or bad state was entered, just that something may
   * have gone wrong.
   *
   * @param tag An identifier to allow searching for related logs. Generally of the form
   *     'Class.method'.
   * @param msg The message you would like logged, possibly with format arguments.
   * @param args Optional arguments to be used in the formatted string.
   * @see {@link String#format(String, Object...)}
   * @see {@link android.util.Log#w(String, String)}
   */
  public static void w(@NonNull String tag, @Nullable String msg, @Nullable Object... args) {
    println(android.util.Log.WARN, TAG, tag, msg, args);
  }

  /**
   * Log at an error level. Error logs are used when it is known that an error occurred and is
   * possibly fatal. This is used to log information that will be useful for troubleshooting a crash
   * or other severe condition (e.g. error codes, state values, etc.).
   *
   * @param tag An identifier to allow searching for related logs. Generally of the form
   *     'Class.method'.
   * @param msg The message you would like logged, possibly with format arguments.
   * @param args Optional arguments to be used in the formatted string.
   * @see {@link String#format(String, Object...)}
   * @see {@link android.util.Log#e(String, String)}
   */
  public static void e(@NonNull String tag, @Nullable String msg, @Nullable Object... args) {
    println(android.util.Log.ERROR, TAG, tag, msg, args);
  }

  /**
   * Log an exception at an error level. Error logs are used when it is known that an error occurred
   * and is possibly fatal. This is used to log information that will be useful for troubleshooting
   * a crash or other severe condition (e.g. error codes, state values, etc.).
   *
   * @param tag An identifier to allow searching for related logs. Generally of the form
   *     'Class.method'.
   * @param msg The message you would like logged.
   * @param throwable The exception to log.
   * @see {@link String#format(String, Object...)}
   * @see {@link android.util.Log#e(String, String)}
   */
  public static void e(@NonNull String tag, @Nullable String msg, @NonNull Throwable throwable) {
    if (!TextUtils.isEmpty(msg)) {
      println(android.util.Log.ERROR, TAG, tag, msg);
    }
    println(android.util.Log.ERROR, TAG, tag, android.util.Log.getStackTraceString(throwable));
  }

  /**
   * Used for log statements where we don't want to log various strings (e.g., usernames) with
   * default logging to avoid leaking PII in logcat.
   *
   * @return text as is if {@value #TAG}'s log level is set to DEBUG or VERBOSE or on non-release
   *     builds; returns a redacted version otherwise.
   */
  public static String sanitizePii(@Nullable Object object) {
    if (object == null) {
      return "null";
    }
    if (isDebugEnabled()) {
      return object.toString();
    }
    return "Redacted-" + object.toString().length() + "-chars";
  }

  /** Anonymizes char to prevent logging personally identifiable information. */
  public static char sanitizeDialPadChar(char ch) {
    if (isDebugEnabled()) {
      return ch;
    }
    if (is12Key(ch)) {
      return '*';
    }
    return ch;
  }

  /** Anonymizes the phone number to prevent logging personally identifiable information. */
  public static String sanitizePhoneNumber(@Nullable String phoneNumber) {
    if (isDebugEnabled()) {
      return phoneNumber;
    }
    if (phoneNumber == null) {
      return null;
    }
    StringBuilder stringBuilder = new StringBuilder(phoneNumber.length());
    for (char c : phoneNumber.toCharArray()) {
      stringBuilder.append(sanitizeDialPadChar(c));
    }
    return stringBuilder.toString();
  }

  public static boolean isVerboseEnabled() {
    return android.util.Log.isLoggable(TAG, android.util.Log.VERBOSE);
  }

  public static boolean isDebugEnabled() {
    return android.util.Log.isLoggable(TAG, android.util.Log.DEBUG);
  }

  private static boolean is12Key(char ch) {
    return PhoneNumberUtils.is12Key(ch);
  }

  private static void println(
      int level,
      @NonNull String tag,
      @NonNull String localTag,
      @Nullable String msg,
      @Nullable Object... args) {
    // Formatted message is computed lazily if required.
    String formattedMsg;
    // Either null is passed as a single argument or more than one argument is passed.
    boolean hasArgs = args == null || args.length > 0;
    if ((level >= android.util.Log.INFO) || android.util.Log.isLoggable(tag, level)) {
      formattedMsg = localTag;
      if (!TextUtils.isEmpty(msg)) {
        formattedMsg += SEPARATOR + (hasArgs ? String.format(msg, args) : msg);
      }
      android.util.Log.println(level, tag, formattedMsg);
    }
  }
}
