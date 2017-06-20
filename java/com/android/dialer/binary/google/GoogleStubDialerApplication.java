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

package com.android.dialer.binary.google;

import android.support.annotation.NonNull;
import com.android.dialer.binary.common.DialerApplication;
import com.android.dialer.inject.ContextModule;

/**
 * The application class for the Google Stub Dialer. This is a version of the Dialer app that
 * depends on Google Play Services.
 */
public class GoogleStubDialerApplication extends DialerApplication {

  /** Returns a new instance of the root component for the Google Stub Dialer. */
  @Override
  @NonNull
  protected Object buildRootComponent() {
    return DaggerGoogleStubDialerRootComponent.builder()
        .contextModule(new ContextModule(this))
        .build();
  }
}
