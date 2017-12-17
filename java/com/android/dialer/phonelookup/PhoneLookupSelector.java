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
import android.support.annotation.Nullable;
import com.android.dialer.phonelookup.PhoneLookupInfo.Cp2Info.Cp2ContactInfo;

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
    Cp2ContactInfo firstLocalContact = firstLocalContact(phoneLookupInfo);
    if (firstLocalContact != null) {
      String name = firstLocalContact.getName();
      if (!name.isEmpty()) {
        return firstLocalContact.getName();
      }
    }
    if (phoneLookupInfo.hasPeopleApiInfo()) {
      return phoneLookupInfo.getPeopleApiInfo().getDisplayName();
    }
    return "";
  }

  /** Select the photo URI associated with this number. */
  @NonNull
  public static String selectPhotoUri(PhoneLookupInfo phoneLookupInfo) {
    Cp2ContactInfo firstLocalContact = firstLocalContact(phoneLookupInfo);
    if (firstLocalContact != null) {
      String photoUri = firstLocalContact.getPhotoUri();
      if (!photoUri.isEmpty()) {
        return photoUri;
      }
    }
    return "";
  }

  /** Select the photo ID associated with this number, or 0 if there is none. */
  public static long selectPhotoId(PhoneLookupInfo phoneLookupInfo) {
    Cp2ContactInfo firstLocalContact = firstLocalContact(phoneLookupInfo);
    if (firstLocalContact != null) {
      long photoId = firstLocalContact.getPhotoId();
      if (photoId > 0) {
        return photoId;
      }
    }
    return 0;
  }

  /** Select the lookup URI associated with this number. */
  @NonNull
  public static String selectLookupUri(PhoneLookupInfo phoneLookupInfo) {
    Cp2ContactInfo firstLocalContact = firstLocalContact(phoneLookupInfo);
    if (firstLocalContact != null) {
      String lookupUri = firstLocalContact.getLookupUri();
      if (!lookupUri.isEmpty()) {
        return lookupUri;
      }
    }
    return "";
  }

  /**
   * A localized string representing the number type such as "Home" or "Mobile", or a custom value
   * set by the user.
   */
  @NonNull
  public static String selectNumberLabel(PhoneLookupInfo phoneLookupInfo) {
    Cp2ContactInfo firstLocalContact = firstLocalContact(phoneLookupInfo);
    if (firstLocalContact != null) {
      String label = firstLocalContact.getLabel();
      if (!label.isEmpty()) {
        return label;
      }
    }
    return "";
  }

  /**
   * Arbitrarily select the first contact. In the future, it may make sense to display contact
   * information from all contacts with the same number (for example show the name as "Mom, Dad" or
   * show a synthesized photo containing photos of both "Mom" and "Dad").
   */
  @Nullable
  private static Cp2ContactInfo firstLocalContact(PhoneLookupInfo phoneLookupInfo) {
    if (phoneLookupInfo.getCp2Info().getCp2ContactInfoCount() > 0) {
      return phoneLookupInfo.getCp2Info().getCp2ContactInfo(0);
    }
    return null;
  }
}
