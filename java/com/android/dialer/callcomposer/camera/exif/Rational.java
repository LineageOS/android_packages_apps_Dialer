/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.dialer.callcomposer.camera.exif;

import java.util.Objects;

/**
 * The rational data type of EXIF tag. Contains a pair of longs representing the numerator and
 * denominator of a Rational number.
 */
public class Rational {

  private final long numerator;
  private final long denominator;

  /** Create a Rational with a given numerator and denominator. */
  Rational(long nominator, long denominator) {
    numerator = nominator;
    this.denominator = denominator;
  }

  /** Gets the numerator of the rational. */
  long getNumerator() {
    return numerator;
  }

  /** Gets the denominator of the rational */
  long getDenominator() {
    return denominator;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    if (obj instanceof Rational) {
      Rational data = (Rational) obj;
      return numerator == data.numerator && denominator == data.denominator;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(numerator, denominator);
  }

  @Override
  public String toString() {
    return numerator + "/" + denominator;
  }
}
