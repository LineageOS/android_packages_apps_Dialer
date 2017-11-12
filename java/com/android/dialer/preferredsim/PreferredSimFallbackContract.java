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

package com.android.dialer.preferredsim;

import android.content.ComponentName;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.constants.Constants;

/**
 * Extend fields for preferred SIM that is not available in {@link
 * android.provider.ContactsContract.Data} before P. Only query and update is supported for this
 * provider, and the update selection must be {@link PreferredSim#UPDATE_ID_SELECTION}. Caller must
 * have {@link android.Manifest.permission#READ_CONTACTS} to read or {@link
 * android.Manifest.permission#WRITE_CONTACTS} to write.
 */
public final class PreferredSimFallbackContract {

  /**
   * Check the meta-data "com.android.dialer.PREFERRED_SIM_FALLBACK_AUTHORITY" to get the authority
   * of the default dialer if it support it.
   */
  public static final String AUTHORITY = Constants.get().getPreferredSimFallbackProviderAuthority();

  public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

  /** Columns for preferred SIM. */
  public static final class PreferredSim {

    /**
     * Unique key that should match {@link
     * android.provider.ContactsContract.CommonDataKinds.Phone#_ID} of the data row it is associated
     * with.
     */
    public static final String DATA_ID = "data_id";

    /**
     * The flattened {@link android.content.ComponentName} of a {@link PhoneAccountHandle} that is
     * the preferred {@code PhoneAccountHandle} to call the contact with. Used by {@link
     * CommonDataKinds.Phone}.
     *
     * @see PhoneAccountHandle#getComponentName()
     * @see ComponentName#flattenToString()
     */
    public static final String PREFERRED_PHONE_ACCOUNT_COMPONENT_NAME =
        "preferred_phone_account_component_name";

    /**
     * The ID of a {@link PhoneAccountHandle} that is the preferred {@code PhoneAccountHandle} to
     * call the contact with. Used by {@link CommonDataKinds.Phone}.
     *
     * @see PhoneAccountHandle#getId() ()
     * @see ComponentName#flattenToString()
     */
    public static final String PREFERRED_PHONE_ACCOUNT_ID = "preferred_phone_account_id";
  }
}
