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

package com.android.dialer.preferredsim;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsContract.RawContacts;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.preferredsim.PreferredAccountWorker.Result;
import com.android.dialer.preferredsim.PreferredSimFallbackContract.PreferredSim;
import com.android.dialer.preferredsim.suggestion.SimSuggestionComponent;
import com.android.dialer.preferredsim.suggestion.SuggestionProvider.Suggestion;
import com.android.dialer.util.PermissionsUtil;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

/** Query a preferred SIM to make a call with. */
public class PreferredAccountWorker implements Worker<Context, Result> {

  /** The result of the worker. */
  @AutoValue
  public abstract static class Result {

    /** The preferred phone account for the number. Absent if not set or invalid. */
    public abstract Optional<PhoneAccountHandle> getPhoneAccountHandle();

    /**
     * {@link android.provider.ContactsContract.Data#_ID} of the row matching the number. If the
     * preferred account is to be set it should be stored in this row
     */
    public abstract Optional<String> getDataId();

    public abstract Optional<Suggestion> getSuggestion();

    static Builder builder() {
      return new AutoValue_PreferredAccountWorker_Result.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {

      public abstract Builder setPhoneAccountHandle(
          Optional<PhoneAccountHandle> phoneAccountHandle);

      public abstract Builder setDataId(Optional<String> dataId);

      public abstract Builder setSuggestion(Optional<Suggestion> suggestion);

      public abstract Result build();
    }
  }

  @VisibleForTesting
  public static final String METADATA_SUPPORTS_PREFERRED_SIM =
      "supports_per_number_preferred_account";

  private final String phoneNumber;

  public PreferredAccountWorker(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  @NonNull
  @Override
  @WorkerThread
  public Result doInBackground(Context context) throws Throwable {
    Result.Builder resultBuilder = Result.builder();
    if (!isPreferredSimEnabled(context)) {
      return resultBuilder.build();
    }
    if (!PermissionsUtil.hasContactsReadPermissions(context)) {
      LogUtil.i("PreferredAccountWorker.doInBackground", "missing READ_CONTACTS permission");
      return resultBuilder.build();
    }
    Optional<String> dataId = getDataId(context, phoneNumber);
    Optional<PhoneAccountHandle> phoneAccountHandle = Optional.absent();
    if (dataId.isPresent()) {
      resultBuilder.setDataId(dataId);
      phoneAccountHandle = getPreferredAccount(context, dataId.get());
    }
    resultBuilder.setPhoneAccountHandle(phoneAccountHandle);
    Optional<Suggestion> suggestion = Optional.absent();
    if (!phoneAccountHandle.isPresent()) {
      suggestion =
          SimSuggestionComponent.get(context)
              .getSuggestionProvider()
              .getSuggestion(context, phoneNumber);
      resultBuilder.setSuggestion(suggestion);
    }
    return resultBuilder.build();
  }

  @WorkerThread
  @NonNull
  private static Optional<String> getDataId(
      @NonNull Context context, @Nullable String phoneNumber) {
    Assert.isWorkerThread();
    if (TextUtils.isEmpty(phoneNumber)) {
      return Optional.absent();
    }
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber)),
                new String[] {PhoneLookup.DATA_ID},
                null,
                null,
                null)) {
      if (cursor == null) {
        return Optional.absent();
      }
      ImmutableSet<String> validAccountTypes = PreferredAccountUtil.getValidAccountTypes(context);
      String result = null;
      while (cursor.moveToNext()) {
        Optional<String> accountType =
            getAccountType(context.getContentResolver(), cursor.getLong(0));
        if (accountType.isPresent() && !validAccountTypes.contains(accountType.get())) {
          // Empty accountType is treated as writable
          LogUtil.i("CallingAccountSelector.getDataId", "ignoring non-writable " + accountType);
          continue;
        }
        if (result != null && !result.equals(cursor.getString(0))) {
          // TODO(twyen): if there are multiple entries attempt to grab from the contact that
          // initiated the call.
          LogUtil.i("CallingAccountSelector.getDataId", "lookup result not unique, ignoring");
          return Optional.absent();
        }
        result = cursor.getString(0);
      }
      return Optional.fromNullable(result);
    }
  }

  @WorkerThread
  private static Optional<String> getAccountType(ContentResolver contentResolver, long dataId) {
    Assert.isWorkerThread();
    Optional<Long> rawContactId = getRawContactId(contentResolver, dataId);
    if (!rawContactId.isPresent()) {
      return Optional.absent();
    }
    try (Cursor cursor =
        contentResolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId.get()),
            new String[] {RawContacts.ACCOUNT_TYPE},
            null,
            null,
            null)) {
      if (cursor == null || !cursor.moveToFirst()) {
        return Optional.absent();
      }
      return Optional.fromNullable(cursor.getString(0));
    }
  }

  @WorkerThread
  private static Optional<Long> getRawContactId(ContentResolver contentResolver, long dataId) {
    Assert.isWorkerThread();
    try (Cursor cursor =
        contentResolver.query(
            ContentUris.withAppendedId(Data.CONTENT_URI, dataId),
            new String[] {Data.RAW_CONTACT_ID},
            null,
            null,
            null)) {
      if (cursor == null || !cursor.moveToFirst()) {
        return Optional.absent();
      }
      return Optional.of(cursor.getLong(0));
    }
  }

  @WorkerThread
  @NonNull
  private static Optional<PhoneAccountHandle> getPreferredAccount(
      @NonNull Context context, @NonNull String dataId) {
    Assert.isWorkerThread();
    Assert.isNotNull(dataId);
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                PreferredSimFallbackContract.CONTENT_URI,
                new String[] {
                  PreferredSim.PREFERRED_PHONE_ACCOUNT_COMPONENT_NAME,
                  PreferredSim.PREFERRED_PHONE_ACCOUNT_ID
                },
                PreferredSim.DATA_ID + " = ?",
                new String[] {dataId},
                null)) {
      if (cursor == null) {
        return Optional.absent();
      }
      if (!cursor.moveToFirst()) {
        return Optional.absent();
      }
      return PreferredAccountUtil.getValidPhoneAccount(
          context, cursor.getString(0), cursor.getString(1));
    }
  }

  @WorkerThread
  private static boolean isPreferredSimEnabled(Context context) {
    Assert.isWorkerThread();
    if (!ConfigProviderBindings.get(context).getBoolean("preferred_sim_enabled", true)) {
      return false;
    }

    Intent quickContactIntent = getQuickContactIntent();
    ResolveInfo resolveInfo =
        context
            .getPackageManager()
            .resolveActivity(quickContactIntent, PackageManager.GET_META_DATA);
    if (resolveInfo == null
        || resolveInfo.activityInfo == null
        || resolveInfo.activityInfo.applicationInfo == null
        || resolveInfo.activityInfo.applicationInfo.metaData == null) {
      LogUtil.e("CallingAccountSelector.isPreferredSimEnabled", "cannot resolve quick contact app");
      return false;
    }
    if (!resolveInfo.activityInfo.applicationInfo.metaData.getBoolean(
        METADATA_SUPPORTS_PREFERRED_SIM, false)) {
      LogUtil.i(
          "CallingAccountSelector.isPreferredSimEnabled",
          "system contacts does not support preferred SIM");
      return false;
    }
    return true;
  }

  @VisibleForTesting
  public static Intent getQuickContactIntent() {
    Intent intent = new Intent(QuickContact.ACTION_QUICK_CONTACT);
    intent.addCategory(Intent.CATEGORY_DEFAULT);
    intent.setData(Contacts.CONTENT_URI.buildUpon().appendPath("1").build());
    return intent;
  }
}
