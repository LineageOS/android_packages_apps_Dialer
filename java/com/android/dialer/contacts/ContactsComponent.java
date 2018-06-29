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

package com.android.dialer.contacts;

import android.content.Context;
import com.android.dialer.contacts.displaypreference.ContactDisplayPreferences;
import com.android.dialer.contacts.hiresphoto.HighResolutionPhotoRequester;
import com.android.dialer.inject.HasRootComponent;
import com.android.dialer.inject.IncludeInDialerRoot;
import dagger.Subcomponent;

/** Component for contacts related utilities */
@Subcomponent
public abstract class ContactsComponent {

  public abstract ContactDisplayPreferences contactDisplayPreferences();

  public abstract HighResolutionPhotoRequester highResolutionPhotoLoader();

  public static ContactsComponent get(Context context) {
    return ((HasComponent) ((HasRootComponent) context.getApplicationContext()).component())
        .contactsComponent();
  }

  /** Used to refer to the root application component. */
  @IncludeInDialerRoot
  public interface HasComponent {
    ContactsComponent contactsComponent();
  }
}
