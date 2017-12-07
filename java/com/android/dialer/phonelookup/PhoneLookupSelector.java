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
package com.android.dialer.phonelookup;

import android.support.annotation.NonNull;

/**
 * Prioritizes information from a {@link PhoneLookupInfo}.
 *
 * <p>For example, a single {@link PhoneLookupInfo} may contain different name information from many
 * different {@link PhoneLookup} sources. This class defines the rules for deciding which name
 * should be selected for display to the user, by prioritizing the data from some {@link PhoneLookup
 * PhoneLookups} over others.
 *
 * <p>Note that the logic in this class may be highly coupled with the logic in {@code
 * CompositePhoneLookup}, because {@code CompositePhoneLookup} may also include prioritization logic
 * for short-circuiting low-priority {@link PhoneLookup PhoneLookups}.
 */
public final class PhoneLookupSelector {

  /**
   * Select the name associated with this number. Examples of this are a local contact's name or a
   * business name received from caller ID.
   */
  @NonNull
  public static String selectName(PhoneLookupInfo phoneLookupInfo) {
    if (phoneLookupInfo.getCp2Info().getCp2ContactInfoCount() > 0) {
      // Arbitrarily select the first contact's name. In the future, it may make sense to join the
      // names such as "Mom, Dad" in the case that multiple contacts share the same number.
      return phoneLookupInfo.getCp2Info().getCp2ContactInfo(0).getName();
    }
    return "";
  }
}
