/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.common.backoff;

import com.android.dialer.common.Assert;

/**
 * Given an initial backoff delay, D, a base multiplier, B, and a total number of backoffs, N, this
 * class returns values in the exponential sequence, D, D*B, D*B^2, ... D*B^(N-1), ...
 *
 * <p>Example usage:
 *
 * <pre>
 *   long initialDelayMillis = 1000;
 *   double multiplier = 1.2;
 *   int backoffs = 10;
 *   ExponentialBackoff backoff = new ExponentialBackoff(initialDelayMillis, multiplier, backoffs);
 *   while (backoff.isInRange()) {
 *     ...
 *     sleep(backoff.getNextBackoff());
 *   }
 * </pre>
 *
 * <p>Note: the base multiplier can be calculated using {@code ExponentialBaseCalculator}
 */
public final class ExponentialBackoff {
  public final long initialDelayMillis;
  public final double baseMultiplier;
  public final int maximumBackoffs;
  private double nextBackoff;
  private int backoffCount;

  /**
   * Setup an exponential backoff with an initial delay, a base multiplier and a maximum number of
   * backoff steps.
   *
   * @throws IllegalArgumentException for negative argument values
   */
  public ExponentialBackoff(long initialDelayMillis, double baseMultiplier, int maximumBackoffs) {
    Assert.checkArgument(initialDelayMillis > 0);
    Assert.checkArgument(baseMultiplier > 0);
    Assert.checkArgument(maximumBackoffs > 0);
    this.initialDelayMillis = initialDelayMillis;
    this.baseMultiplier = baseMultiplier;
    this.maximumBackoffs = maximumBackoffs;
    reset();
  }

  /**
   * @return the next backoff time in the exponential sequence. Specifically, if D is the initial
   *     delay, B is the base multiplier and N is the total number of backoffs, then the return
   *     values will be: D, D*B, D*B^2, ... D*B^(N-1), ...
   */
  public long getNextBackoff() {
    long backoff = Math.round(nextBackoff);
    backoffCount++;
    nextBackoff *= baseMultiplier;
    return backoff;
  }

  /** @return the number of times getNextBackoff() has been called */
  public int getBackoffCount() {
    return backoffCount;
  }

  /**
   * @return {@code true} if getNextBackoff() has been called less than the maximumBackoffs value
   *     specified in the constructor.
   */
  public boolean isInRange() {
    return backoffCount < maximumBackoffs;
  }

  /**
   * Reset the sequence of backoff values so the next call to getNextBackoff() will return the
   * initial delay and getBackoffCount() will return 0
   */
  public void reset() {
    nextBackoff = initialDelayMillis;
    backoffCount = 0;
  }
}
