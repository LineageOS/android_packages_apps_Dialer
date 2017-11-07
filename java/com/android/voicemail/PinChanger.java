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
 * limitations under the License
 */

package com.android.voicemail;

import android.support.annotation.IntDef;
import android.support.annotation.WorkerThread;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Interface to change the PIN used to access the mailbox by calling. */
public interface PinChanger {

  /** Results from {@link #changePin(String, String)} */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
    value = {
      CHANGE_PIN_SUCCESS,
      CHANGE_PIN_TOO_SHORT,
      CHANGE_PIN_TOO_LONG,
      CHANGE_PIN_TOO_WEAK,
      CHANGE_PIN_MISMATCH,
      CHANGE_PIN_INVALID_CHARACTER,
      CHANGE_PIN_SYSTEM_ERROR
    }
  )
  @interface ChangePinResult {}

  int CHANGE_PIN_SUCCESS = 0;
  int CHANGE_PIN_TOO_SHORT = 1;
  int CHANGE_PIN_TOO_LONG = 2;
  int CHANGE_PIN_TOO_WEAK = 3;
  int CHANGE_PIN_MISMATCH = 4;
  int CHANGE_PIN_INVALID_CHARACTER = 5;
  int CHANGE_PIN_SYSTEM_ERROR = 6;

  @WorkerThread
  @ChangePinResult
  int changePin(String oldPin, String newPin);

  /**
   * Set the scrambled PIN if it is auto generated during provisioning. Set to {@code null} to
   * clear.
   */
  void setScrambledPin(String pin);

  String getScrambledPin();

  /** Format requirements for the PIN. */
  class PinSpecification {
    public int minLength;
    public int maxLength;
  }

  PinSpecification getPinSpecification();
}
