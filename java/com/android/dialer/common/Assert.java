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

import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import javax.annotation.CheckReturnValue;

/** Assertions which will result in program termination unless disabled by flags. */
public class Assert {

  private static boolean areThreadAssertsEnabled = true;

  public static void setAreThreadAssertsEnabled(boolean areThreadAssertsEnabled) {
    Assert.areThreadAssertsEnabled = areThreadAssertsEnabled;
  }

  /**
   * Called when a truly exceptional case occurs.
   *
   * @throws AssertionError
   * @deprecated Use throw Assert.create*FailException() instead.
   */
  @Deprecated
  public static void fail() {
    throw new AssertionError("Fail");
  }

  /**
   * Called when a truly exceptional case occurs.
   *
   * @param reason the optional reason to supply as the exception message
   * @throws AssertionError
   * @deprecated Use throw Assert.create*FailException() instead.
   */
  @Deprecated
  public static void fail(String reason) {
    throw new AssertionError(reason);
  }

  @CheckReturnValue
  public static AssertionError createAssertionFailException(String msg) {
    return new AssertionError(msg);
  }

  @CheckReturnValue
  public static AssertionError createAssertionFailException(String msg, Throwable reason) {
    return new AssertionError(msg, reason);
  }

  @CheckReturnValue
  public static UnsupportedOperationException createUnsupportedOperationFailException() {
    return new UnsupportedOperationException();
  }

  @CheckReturnValue
  public static UnsupportedOperationException createUnsupportedOperationFailException(String msg) {
    return new UnsupportedOperationException(msg);
  }

  @CheckReturnValue
  public static IllegalStateException createIllegalStateFailException() {
    return new IllegalStateException();
  }

  @CheckReturnValue
  public static IllegalStateException createIllegalStateFailException(String msg) {
    return new IllegalStateException(msg);
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @throws IllegalArgumentException if {@code expression} is false
   */
  public static void checkArgument(boolean expression) {
    checkArgument(expression, null);
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param messageTemplate the message to log, possible with format arguments.
   * @param args optional arguments to be used in the formatted string.
   * @throws IllegalArgumentException if {@code expression} is false
   */
  public static void checkArgument(
      boolean expression, @Nullable String messageTemplate, Object... args) {
    if (!expression) {
      throw new IllegalArgumentException(format(messageTemplate, args));
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @param expression a boolean expression
   * @throws IllegalStateException if {@code expression} is false
   */
  public static void checkState(boolean expression) {
    checkState(expression, null);
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param messageTemplate the message to log, possible with format arguments.
   * @param args optional arguments to be used in the formatted string.
   * @throws IllegalStateException if {@code expression} is false
   */
  public static void checkState(
      boolean expression, @Nullable String messageTemplate, Object... args) {
    if (!expression) {
      throw new IllegalStateException(format(messageTemplate, args));
    }
  }

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not null.
   *
   * @param reference an object reference
   * @return the non-null reference that was validated
   * @throws NullPointerException if {@code reference} is null
   */
  @NonNull
  public static <T> T isNotNull(@Nullable T reference) {
    return isNotNull(reference, null);
  }

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not null.
   *
   * @param reference an object reference
   * @param messageTemplate the message to log, possible with format arguments.
   * @param args optional arguments to be used in the formatted string.
   * @return the non-null reference that was validated
   * @throws NullPointerException if {@code reference} is null
   */
  @NonNull
  public static <T> T isNotNull(
      @Nullable T reference, @Nullable String messageTemplate, Object... args) {
    if (reference == null) {
      throw new NullPointerException(format(messageTemplate, args));
    }
    return reference;
  }

  /**
   * Ensures that the current thread is the main thread.
   *
   * @throws IllegalStateException if called on a background thread
   */
  public static void isMainThread() {
    isMainThread(null);
  }

  /**
   * Ensures that the current thread is the main thread.
   *
   * @param messageTemplate the message to log, possible with format arguments.
   * @param args optional arguments to be used in the formatted string.
   * @throws IllegalStateException if called on a background thread
   */
  public static void isMainThread(@Nullable String messageTemplate, Object... args) {
    if (!areThreadAssertsEnabled) {
      return;
    }
    checkState(Looper.getMainLooper().equals(Looper.myLooper()), messageTemplate, args);
  }

  /**
   * Ensures that the current thread is a worker thread.
   *
   * @throws IllegalStateException if called on the main thread
   */
  public static void isWorkerThread() {
    isWorkerThread(null);
  }

  /**
   * Ensures that the current thread is a worker thread.
   *
   * @param messageTemplate the message to log, possible with format arguments.
   * @param args optional arguments to be used in the formatted string.
   * @throws IllegalStateException if called on the main thread
   */
  public static void isWorkerThread(@Nullable String messageTemplate, Object... args) {
    if (!areThreadAssertsEnabled) {
      return;
    }
    checkState(!Looper.getMainLooper().equals(Looper.myLooper()), messageTemplate, args);
  }

  private static String format(@Nullable String messageTemplate, Object... args) {
    if (messageTemplate == null) {
      return null;
    }
    return String.format(messageTemplate, args);
  }
}
