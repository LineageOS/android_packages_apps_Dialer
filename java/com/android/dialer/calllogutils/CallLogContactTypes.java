/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.calllogutils;

import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.lettertile.LetterTileDrawable;

/** Determines the {@link LetterTileDrawable.ContactType} for a {@link CoalescedRow}. */
public class CallLogContactTypes {

  /** Determines the {@link LetterTileDrawable.ContactType} for a {@link CoalescedRow}. */
  @LetterTileDrawable.ContactType
  public static int getContactType(CoalescedRow row) {
    // TODO(zachh): Set these fields correctly.
    boolean isVoicemail = false;
    boolean isSpam = false;
    boolean isBusiness = false;
    int numberPresentation = 0;
    boolean isConference = false;

    return LetterTileDrawable.getContactTypeFromPrimitives(
        isVoicemail, isSpam, isBusiness, numberPresentation, isConference);
  }
}
