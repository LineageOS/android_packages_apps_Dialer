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
package com.android.dialer.phonelookup.consolidator;

import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.android.dialer.logging.ContactSource;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.BlockedState;
import com.android.dialer.phonelookup.PhoneLookupInfo.Cp2Info.Cp2ContactInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.PeopleApiInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.PeopleApiInfo.InfoType;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Consolidates information from a {@link PhoneLookupInfo}.
 *
 * <p>For example, a single {@link PhoneLookupInfo} may contain different name information from many
 * different {@link PhoneLookup} implementations. This class defines the rules for deciding which
 * name should be selected for display to the user, by prioritizing the data from some {@link
 * PhoneLookup PhoneLookups} over others.
 */
public final class PhoneLookupInfoConsolidator {

  /** Integers representing {@link PhoneLookup} implementations that can provide a contact's name */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    NameSource.NONE,
    NameSource.CP2_DEFAULT_DIRECTORY,
    NameSource.CP2_EXTENDED_DIRECTORY,
    NameSource.PEOPLE_API,
    NameSource.CEQUINT,
    NameSource.CNAP,
    NameSource.PHONE_NUMBER_CACHE
  })
  @interface NameSource {
    int NONE = 0; // used when none of the other sources can provide the name
    int CP2_DEFAULT_DIRECTORY = 1;
    int CP2_EXTENDED_DIRECTORY = 2;
    int PEOPLE_API = 3;
    int CEQUINT = 4;
    int CNAP = 5;
    int PHONE_NUMBER_CACHE = 6;
  }

  /**
   * Sources that can provide information about a contact's name.
   *
   * <p>Each source is one of the values in NameSource, as defined above.
   *
   * <p>Sources are sorted in the order of priority. For example, if source CP2_DEFAULT_DIRECTORY
   * can provide the name, we will use that name in the UI and ignore all the other sources. If
   * source CP2_DEFAULT_DIRECTORY can't provide the name, source CP2_EXTENDED_DIRECTORY will be
   * consulted.
   *
   * <p>The reason for defining a name source is to avoid mixing info from different sub-messages in
   * PhoneLookupInfo proto when we are supposed to stick with only one sub-message. For example, if
   * a PhoneLookupInfo proto has both default_cp2_info and extended_cp2_info but only
   * extended_cp2_info has a photo URI, PhoneLookupInfoConsolidator should provide an empty photo
   * URI as CP2_DEFAULT_DIRECTORY has higher priority and we should not use extended_cp2_info's
   * photo URI to display the contact's photo.
   */
  private static final ImmutableList<Integer> NAME_SOURCES_IN_PRIORITY_ORDER =
      ImmutableList.of(
          NameSource.CP2_DEFAULT_DIRECTORY,
          NameSource.CP2_EXTENDED_DIRECTORY,
          NameSource.PEOPLE_API,
          NameSource.CEQUINT,
          NameSource.CNAP,
          NameSource.PHONE_NUMBER_CACHE);

  private final @NameSource int nameSource;
  private final PhoneLookupInfo phoneLookupInfo;

  @Nullable private final Cp2ContactInfo firstDefaultCp2Contact;
  @Nullable private final Cp2ContactInfo firstExtendedCp2Contact;

  public PhoneLookupInfoConsolidator(PhoneLookupInfo phoneLookupInfo) {
    this.phoneLookupInfo = phoneLookupInfo;

    this.firstDefaultCp2Contact = getFirstContactInDefaultDirectory();
    this.firstExtendedCp2Contact = getFirstContactInExtendedDirectories();
    this.nameSource = selectNameSource();
  }

  /**
   * Returns a {@link com.android.dialer.logging.ContactSource.Type} representing the source from
   * which info is used to display contact info in the UI.
   */
  public ContactSource.Type getContactSource() {
    switch (nameSource) {
      case NameSource.CP2_DEFAULT_DIRECTORY:
        return ContactSource.Type.SOURCE_TYPE_DIRECTORY;
      case NameSource.CP2_EXTENDED_DIRECTORY:
        return ContactSource.Type.SOURCE_TYPE_EXTENDED;
      case NameSource.PEOPLE_API:
        return getRefinedPeopleApiSource();
      case NameSource.CEQUINT:
        return ContactSource.Type.SOURCE_TYPE_CEQUINT_CALLER_ID;
      case NameSource.CNAP:
        return ContactSource.Type.SOURCE_TYPE_CNAP;
      case NameSource.PHONE_NUMBER_CACHE:
        ContactSource.Type sourceType =
            ContactSource.Type.forNumber(phoneLookupInfo.getMigratedInfo().getSourceType());
        if (sourceType == null) {
          sourceType = ContactSource.Type.UNKNOWN_SOURCE_TYPE;
        }
        return sourceType;
      case NameSource.NONE:
        return ContactSource.Type.UNKNOWN_SOURCE_TYPE;
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  private ContactSource.Type getRefinedPeopleApiSource() {
    Assert.checkState(nameSource == NameSource.PEOPLE_API);

    switch (phoneLookupInfo.getPeopleApiInfo().getInfoType()) {
      case CONTACT:
        return ContactSource.Type.SOURCE_TYPE_PROFILE;
      case NEARBY_BUSINESS:
        return ContactSource.Type.SOURCE_TYPE_PLACES;
      default:
        return ContactSource.Type.SOURCE_TYPE_REMOTE_OTHER;
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns the name associated with that number.
   *
   * <p>Examples of this are a local contact's name or a business name received from caller ID.
   *
   * <p>If no name can be obtained from the {@link PhoneLookupInfo}, an empty string will be
   * returned.
   */
  public String getName() {
    switch (nameSource) {
      case NameSource.CP2_DEFAULT_DIRECTORY:
        return Assert.isNotNull(firstDefaultCp2Contact).getName();
      case NameSource.CP2_EXTENDED_DIRECTORY:
        return Assert.isNotNull(firstExtendedCp2Contact).getName();
      case NameSource.PEOPLE_API:
        return phoneLookupInfo.getPeopleApiInfo().getDisplayName();
      case NameSource.CEQUINT:
        return phoneLookupInfo.getCequintInfo().getName();
      case NameSource.CNAP:
        return phoneLookupInfo.getCnapInfo().getName();
      case NameSource.PHONE_NUMBER_CACHE:
        return phoneLookupInfo.getMigratedInfo().getName();
      case NameSource.NONE:
        return "";
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns the photo thumbnail URI associated with that number.
   *
   * <p>If no photo thumbnail URI can be obtained from the {@link PhoneLookupInfo}, an empty string
   * will be returned.
   */
  public String getPhotoThumbnailUri() {
    switch (nameSource) {
      case NameSource.CP2_DEFAULT_DIRECTORY:
        return Assert.isNotNull(firstDefaultCp2Contact).getPhotoThumbnailUri();
      case NameSource.CP2_EXTENDED_DIRECTORY:
        return Assert.isNotNull(firstExtendedCp2Contact).getPhotoThumbnailUri();
      case NameSource.PHONE_NUMBER_CACHE:
        return phoneLookupInfo.getMigratedInfo().getPhotoUri();
      case NameSource.PEOPLE_API:
      case NameSource.CEQUINT:
      case NameSource.CNAP:
      case NameSource.NONE:
        return "";
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns the photo URI associated with that number.
   *
   * <p>If no photo URI can be obtained from the {@link PhoneLookupInfo}, an empty string will be
   * returned.
   */
  public String getPhotoUri() {
    switch (nameSource) {
      case NameSource.CP2_DEFAULT_DIRECTORY:
        return Assert.isNotNull(firstDefaultCp2Contact).getPhotoUri();
      case NameSource.CP2_EXTENDED_DIRECTORY:
        return Assert.isNotNull(firstExtendedCp2Contact).getPhotoUri();
      case NameSource.CEQUINT:
        return phoneLookupInfo.getCequintInfo().getPhotoUri();
      case NameSource.PHONE_NUMBER_CACHE:
        return phoneLookupInfo.getMigratedInfo().getPhotoUri();
      case NameSource.PEOPLE_API:
      case NameSource.CNAP:
      case NameSource.NONE:
        return "";
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns the photo ID associated with that number, or 0 if there is none.
   */
  public long getPhotoId() {
    switch (nameSource) {
      case NameSource.CP2_DEFAULT_DIRECTORY:
        return Math.max(Assert.isNotNull(firstDefaultCp2Contact).getPhotoId(), 0);
      case NameSource.CP2_EXTENDED_DIRECTORY:
        return Math.max(Assert.isNotNull(firstExtendedCp2Contact).getPhotoId(), 0);
      case NameSource.PHONE_NUMBER_CACHE:
      case NameSource.PEOPLE_API:
      case NameSource.CEQUINT:
      case NameSource.CNAP:
      case NameSource.NONE:
        return 0;
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns the lookup URI associated with that number, or an empty string if no lookup URI can be
   * obtained.
   */
  public String getLookupUri() {
    switch (nameSource) {
      case NameSource.CP2_DEFAULT_DIRECTORY:
        return Assert.isNotNull(firstDefaultCp2Contact).getLookupUri();
      case NameSource.CP2_EXTENDED_DIRECTORY:
        return Assert.isNotNull(firstExtendedCp2Contact).getLookupUri();
      case NameSource.PEOPLE_API:
        return Assert.isNotNull(phoneLookupInfo.getPeopleApiInfo().getLookupUri());
      case NameSource.PHONE_NUMBER_CACHE:
      case NameSource.CEQUINT:
      case NameSource.CNAP:
      case NameSource.NONE:
        return "";
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns a localized string representing the number type such as "Home" or "Mobile", or a custom
   * value set by the user.
   *
   * <p>If no label can be obtained from the {@link PhoneLookupInfo}, an empty string will be
   * returned.
   */
  public String getNumberLabel() {
    switch (nameSource) {
      case NameSource.CP2_DEFAULT_DIRECTORY:
        return Assert.isNotNull(firstDefaultCp2Contact).getLabel();
      case NameSource.CP2_EXTENDED_DIRECTORY:
        return Assert.isNotNull(firstExtendedCp2Contact).getLabel();
      case NameSource.PHONE_NUMBER_CACHE:
        return phoneLookupInfo.getMigratedInfo().getLabel();
      case NameSource.PEOPLE_API:
      case NameSource.CEQUINT:
      case NameSource.CNAP:
      case NameSource.NONE:
        return "";
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns the number's geolocation (which is for display purpose only).
   *
   * <p>If no geolocation can be obtained from the {@link PhoneLookupInfo}, an empty string will be
   * returned.
   */
  public String getGeolocation() {
    switch (nameSource) {
      case NameSource.CEQUINT:
        return phoneLookupInfo.getCequintInfo().getGeolocation();
      case NameSource.CP2_DEFAULT_DIRECTORY:
      case NameSource.CP2_EXTENDED_DIRECTORY:
      case NameSource.PEOPLE_API:
      case NameSource.CNAP:
      case NameSource.PHONE_NUMBER_CACHE:
      case NameSource.NONE:
        return "";
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns whether the number belongs to a business place.
   */
  public boolean isBusiness() {
    switch (nameSource) {
      case NameSource.PEOPLE_API:
        return phoneLookupInfo.getPeopleApiInfo().getInfoType() == InfoType.NEARBY_BUSINESS;
      case NameSource.PHONE_NUMBER_CACHE:
        return phoneLookupInfo.getMigratedInfo().getIsBusiness();
      case NameSource.CP2_DEFAULT_DIRECTORY:
      case NameSource.CP2_EXTENDED_DIRECTORY:
      case NameSource.CEQUINT:
      case NameSource.CNAP:
      case NameSource.NONE:
        return false;
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns whether the number is blocked.
   */
  public boolean isBlocked() {
    // If system blocking reported blocked state it always takes priority over the dialer blocking.
    // It will be absent if dialer blocking should be used.
    if (phoneLookupInfo.getSystemBlockedNumberInfo().hasBlockedState()) {
      return phoneLookupInfo
          .getSystemBlockedNumberInfo()
          .getBlockedState()
          .equals(BlockedState.BLOCKED);
    }
    return false;
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns whether the number is spam.
   */
  public boolean isSpam() {
    return phoneLookupInfo.getSpamInfo().getIsSpam();
  }

  /**
   * Returns true if the {@link PhoneLookupInfo} passed to the constructor has incomplete default
   * CP2 info (info from the default directory).
   */
  public boolean isDefaultCp2InfoIncomplete() {
    return phoneLookupInfo.getDefaultCp2Info().getIsIncomplete();
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns whether the number is an emergency number (e.g., 911 in the U.S.).
   */
  public boolean isEmergencyNumber() {
    return phoneLookupInfo.getEmergencyInfo().getIsEmergencyNumber();
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns whether the number can be reported as invalid.
   *
   * <p>As we currently report invalid numbers via the People API, only numbers from the People API
   * can be reported as invalid.
   */
  public boolean canReportAsInvalidNumber() {
    switch (nameSource) {
      case NameSource.CP2_DEFAULT_DIRECTORY:
      case NameSource.CP2_EXTENDED_DIRECTORY:
      case NameSource.CEQUINT:
      case NameSource.CNAP:
      case NameSource.PHONE_NUMBER_CACHE:
      case NameSource.NONE:
        return false;
      case NameSource.PEOPLE_API:
        PeopleApiInfo peopleApiInfo = phoneLookupInfo.getPeopleApiInfo();
        return peopleApiInfo.getInfoType() != InfoType.UNKNOWN
            && !peopleApiInfo.getPersonId().isEmpty();
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns whether the number can be reached via carrier video calls.
   */
  public boolean canSupportCarrierVideoCall() {
    switch (nameSource) {
      case NameSource.CP2_DEFAULT_DIRECTORY:
        return Assert.isNotNull(firstDefaultCp2Contact).getCanSupportCarrierVideoCall();
      case NameSource.CP2_EXTENDED_DIRECTORY:
      case NameSource.PEOPLE_API:
      case NameSource.CEQUINT:
      case NameSource.CNAP:
      case NameSource.PHONE_NUMBER_CACHE:
      case NameSource.NONE:
        return false;
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * Arbitrarily select the first CP2 contact in the default directory. In the future, it may make
   * sense to display contact information from all contacts with the same number (for example show
   * the name as "Mom, Dad" or show a synthesized photo containing photos of both "Mom" and "Dad").
   */
  @Nullable
  private Cp2ContactInfo getFirstContactInDefaultDirectory() {
    return phoneLookupInfo.getDefaultCp2Info().getCp2ContactInfoCount() > 0
        ? phoneLookupInfo.getDefaultCp2Info().getCp2ContactInfo(0)
        : null;
  }

  /**
   * Arbitrarily select the first CP2 contact in extended directories. In the future, it may make
   * sense to display contact information from all contacts with the same number (for example show
   * the name as "Mom, Dad" or show a synthesized photo containing photos of both "Mom" and "Dad").
   */
  @Nullable
  private Cp2ContactInfo getFirstContactInExtendedDirectories() {
    return phoneLookupInfo.getExtendedCp2Info().getCp2ContactInfoCount() > 0
        ? phoneLookupInfo.getExtendedCp2Info().getCp2ContactInfo(0)
        : null;
  }

  /** Select the {@link PhoneLookup} source providing a contact's name. */
  private @NameSource int selectNameSource() {
    for (int nameSource : NAME_SOURCES_IN_PRIORITY_ORDER) {
      switch (nameSource) {
        case NameSource.CP2_DEFAULT_DIRECTORY:
          if (firstDefaultCp2Contact != null && !firstDefaultCp2Contact.getName().isEmpty()) {
            return NameSource.CP2_DEFAULT_DIRECTORY;
          }
          break;
        case NameSource.CP2_EXTENDED_DIRECTORY:
          if (firstExtendedCp2Contact != null && !firstExtendedCp2Contact.getName().isEmpty()) {
            return NameSource.CP2_EXTENDED_DIRECTORY;
          }
          break;
        case NameSource.PEOPLE_API:
          if (phoneLookupInfo.hasPeopleApiInfo()
              && !phoneLookupInfo.getPeopleApiInfo().getDisplayName().isEmpty()) {
            return NameSource.PEOPLE_API;
          }
          break;
        case NameSource.CEQUINT:
          if (!phoneLookupInfo.getCequintInfo().getName().isEmpty()) {
            return NameSource.CEQUINT;
          }
          break;
        case NameSource.CNAP:
          if (!phoneLookupInfo.getCnapInfo().getName().isEmpty()) {
            return NameSource.CNAP;
          }
          break;
        case NameSource.PHONE_NUMBER_CACHE:
          if (!phoneLookupInfo.getMigratedInfo().getName().isEmpty()) {
            return NameSource.PHONE_NUMBER_CACHE;
          }
          break;
        default:
          throw Assert.createUnsupportedOperationFailException(
              String.format("Unsupported name source: %s", nameSource));
      }
    }

    return NameSource.NONE;
  }
}
