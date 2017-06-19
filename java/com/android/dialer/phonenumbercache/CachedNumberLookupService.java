/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.phonenumbercache;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import com.android.dialer.logging.ContactSource;
import java.io.InputStream;

public interface CachedNumberLookupService {

  CachedContactInfo buildCachedContactInfo(ContactInfo info);

  /**
   * Perform a lookup using the cached number lookup service to return contact information stored in
   * the cache that corresponds to the given number.
   *
   * @param context Valid context
   * @param number Phone number to lookup the cache for
   * @return A {@link CachedContactInfo} containing the contact information if the phone number is
   *     found in the cache, {@link ContactInfo#EMPTY} if the phone number was not found in the
   *     cache, and null if there was an error when querying the cache.
   */
  @WorkerThread
  CachedContactInfo lookupCachedContactFromNumber(Context context, String number);

  void addContact(Context context, CachedContactInfo info);

  boolean isCacheUri(String uri);

  boolean isBusiness(ContactSource.Type sourceType);

  boolean canReportAsInvalid(ContactSource.Type sourceType, String objectId);

  boolean reportAsInvalid(Context context, CachedContactInfo cachedContactInfo);

  /** @return return {@link Uri} to the photo or return {@code null} when failing to add photo */
  @Nullable
  Uri addPhoto(Context context, String number, InputStream in);

  /**
   * Remove all cached phone number entries from the cache, regardless of how old they are.
   *
   * @param context Valid context
   */
  void clearAllCacheEntries(Context context);

  interface CachedContactInfo {

    @NonNull
    ContactInfo getContactInfo();

    void setSource(ContactSource.Type sourceType, String name, long directoryId);

    void setDirectorySource(String name, long directoryId);

    void setExtendedSource(String name, long directoryId);

    void setLookupKey(String lookupKey);
  }
}
