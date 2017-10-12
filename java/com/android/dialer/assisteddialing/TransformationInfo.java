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

package com.android.dialer.assisteddialing;

import android.os.Bundle;
import android.support.annotation.NonNull;
import com.google.auto.value.AutoValue;

/**
 * A container class to hold information related to the Assisted Dialing operation. All member
 * variables must be set when constructing a new instance of this class.
 */
@AutoValue
public abstract class TransformationInfo {
  @NonNull private static final String ORIGINAL_NUBMER_KEY = "TRANSFORMATION_INFO_ORIGINAL_NUMBER";

  @NonNull
  private static final String TRANSFORMED_NUMBER_KEY = "TRANSFORMATION_INFO_TRANSFORMED_NUMBER";

  @NonNull
  private static final String USER_HOME_COUNTRY_CODE_KEY =
      "TRANSFORMATION_INFO_USER_HOME_COUNTRY_CODE";

  @NonNull
  private static final String USER_ROAMING_COUNTRY_CODE_KEY =
      "TRANSFORMATION_INFO_USER_ROAMING_COUNTRY_CODE";

  @NonNull
  private static final String TRANSFORMED_NUMBER_COUNTRY_CALLING_CODE_KEY =
      "TRANSFORMED_NUMBER_COUNTRY_CALLING_CODE";

  public abstract String originalNumber();

  public abstract String transformedNumber();

  public abstract String userHomeCountryCode();

  public abstract String userRoamingCountryCode();

  public abstract int transformedNumberCountryCallingCode();

  public static Builder builder() {
    return new AutoValue_TransformationInfo.Builder();
  }

  /** A builder for TransformationInfo. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setOriginalNumber(String value);

    public abstract Builder setTransformedNumber(String value);

    public abstract Builder setUserHomeCountryCode(String value);

    public abstract Builder setUserRoamingCountryCode(String value);

    public abstract Builder setTransformedNumberCountryCallingCode(int value);

    public abstract TransformationInfo build();
  }

  public static TransformationInfo newInstanceFromBundle(@NonNull Bundle transformationInfoBundle) {

    return TransformationInfo.builder()
        .setOriginalNumber(transformationInfoBundle.getString(ORIGINAL_NUBMER_KEY))
        .setTransformedNumber(transformationInfoBundle.getString(TRANSFORMED_NUMBER_KEY))
        .setUserHomeCountryCode(transformationInfoBundle.getString(USER_HOME_COUNTRY_CODE_KEY))
        .setUserRoamingCountryCode(
            transformationInfoBundle.getString(USER_ROAMING_COUNTRY_CODE_KEY))
        .setTransformedNumberCountryCallingCode(
            transformationInfoBundle.getInt(TRANSFORMED_NUMBER_COUNTRY_CALLING_CODE_KEY))
        .build();
  }

  /**
   * Callers are not expected to directly use this bundle. The bundle is provided for IPC purposes.
   * Callers wishing to use the data should call newInstanceFromBundle with the bundle to get a
   * usable POJO.
   */
  public Bundle toBundle() {
    Bundle assistedDialingExtras = new Bundle();
    assistedDialingExtras.putString(ORIGINAL_NUBMER_KEY, originalNumber());
    assistedDialingExtras.putString(TRANSFORMED_NUMBER_KEY, transformedNumber());
    assistedDialingExtras.putString(USER_HOME_COUNTRY_CODE_KEY, userHomeCountryCode());
    assistedDialingExtras.putString(USER_ROAMING_COUNTRY_CODE_KEY, userRoamingCountryCode());
    assistedDialingExtras.putInt(
        TRANSFORMED_NUMBER_COUNTRY_CALLING_CODE_KEY, transformedNumberCountryCallingCode());
    return assistedDialingExtras;
  }
}
