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

import android.annotation.TargetApi;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import com.android.dialer.common.LogUtil;
import java.util.Optional;

/**
 * The Mediator for Assisted Dialing.
 *
 * <p>This class is responsible for mediating location discovery of the user, determining if the
 * call is eligible for assisted dialing, and performing the transformation of numbers eligible for
 * assisted dialing.
 */
@RequiresApi(VERSION_CODES.N)
final class AssistedDialingMediatorImpl implements AssistedDialingMediator {

  private final LocationDetector locationDetector;
  private final NumberTransformer numberTransformer;

  AssistedDialingMediatorImpl(
      @NonNull LocationDetector locationDetector, @NonNull NumberTransformer numberTransformer) {
    if (locationDetector == null) {
      throw new NullPointerException("locationDetector was null");
    }

    if (numberTransformer == null) {
      throw new NullPointerException("numberTransformer was null");
    }
    this.locationDetector = locationDetector;
    this.numberTransformer = numberTransformer;
  }

  @Override
  public boolean isPlatformEligible() {
    // This impl is only instantiated if it passes platform checks in ConcreteCreator,
    // so we return true here.
    return true;
  }

  /** Returns the country code in which the library thinks the user typically resides. */
  @Override
  @SuppressWarnings("AndroidApiChecker") // Use of optional
  @TargetApi(VERSION_CODES.N)
  public Optional<String> userHomeCountryCode() {
    return locationDetector.getUpperCaseUserHomeCountry();
  }

  /**
   * Returns an Optional of type String containing the transformed number that was provided. The
   * transformed number should be capable of dialing out of the User's current country and
   * successfully connecting with a contact in the User's home country.
   */
  @SuppressWarnings("AndroidApiChecker") // Use of optional
  @TargetApi(VERSION_CODES.N)
  @Override
  public Optional<TransformationInfo> attemptAssistedDial(@NonNull String numberToTransform) {
    Optional<String> userHomeCountryCode = locationDetector.getUpperCaseUserHomeCountry();
    Optional<String> userRoamingCountryCode = locationDetector.getUpperCaseUserRoamingCountry();

    if (!userHomeCountryCode.isPresent() || !userRoamingCountryCode.isPresent()) {
      LogUtil.i("AssistedDialingMediator.attemptAssistedDial", "Unable to determine country codes");
      return Optional.empty();
    }

    return numberTransformer.doAssistedDialingTransformation(
        numberToTransform, userHomeCountryCode.get(), userRoamingCountryCode.get());
  }
}
