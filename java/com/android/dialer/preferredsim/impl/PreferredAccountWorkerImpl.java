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

package com.android.dialer.preferredsim.impl;

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
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import com.android.contacts.common.widget.SelectPhoneAccountDialogOptions;
import com.android.contacts.common.widget.SelectPhoneAccountDialogOptionsUtil;
import com.android.dialer.activecalls.ActiveCallInfo;
import com.android.dialer.activecalls.ActiveCallsComponent;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.configprovider.ConfigProviderComponent;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.logging.DialerImpression.Type;
import com.android.dialer.logging.Logger;
import com.android.dialer.preferredsim.PreferredAccountUtil;
import com.android.dialer.preferredsim.PreferredAccountWorker;
import com.android.dialer.preferredsim.PreferredAccountWorker.Result.Builder;
import com.android.dialer.preferredsim.PreferredSimFallbackContract;
import com.android.dialer.preferredsim.PreferredSimFallbackContract.PreferredSim;
import com.android.dialer.preferredsim.suggestion.SimSuggestionComponent;
import com.android.dialer.preferredsim.suggestion.SuggestionProvider;
import com.android.dialer.preferredsim.suggestion.SuggestionProvider.Suggestion;
import com.android.dialer.util.PermissionsUtil;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;

/** Implements {@link PreferredAccountWorker}. */
@SuppressWarnings({"missingPermission", "Guava"})
public class PreferredAccountWorkerImpl implements PreferredAccountWorker {

  private final Context appContext;
  private final ListeningExecutorService backgroundExecutor;

  @VisibleForTesting
  public static final String METADATA_SUPPORTS_PREFERRED_SIM =
      "supports_per_number_preferred_account";

  @Inject
  public PreferredAccountWorkerImpl(
      @ApplicationContext Context appContext,
      @BackgroundExecutor ListeningExecutorService backgroundExecutor) {
    this.appContext = appContext;
    this.backgroundExecutor = backgroundExecutor;
  }

  @Override
  public SelectPhoneAccountDialogOptions getVoicemailDialogOptions() {
    return SelectPhoneAccountDialogOptionsUtil.builderWithAccounts(
            appContext.getSystemService(TelecomManager.class).getCallCapablePhoneAccounts())
        .setTitle(R.string.pre_call_select_phone_account)
        .setCanSetDefault(false)
        .build();
  }

  @Override
  public ListenableFuture<Result> selectAccount(
      String phoneNumber, List<PhoneAccountHandle> candidates) {
    return backgroundExecutor.submit(() -> doInBackground(phoneNumber, candidates));
  }

  private Result doInBackground(String phoneNumber, List<PhoneAccountHandle> candidates) {

    Optional<String> dataId = getDataId(phoneNumber);
    if (dataId.isPresent()) {
      Optional<PhoneAccountHandle> preferred = getPreferredAccount(appContext, dataId.get());
      if (preferred.isPresent()) {
        return usePreferredSim(preferred.get(), candidates, dataId.get());
      }
    }

    PhoneAccountHandle defaultPhoneAccount =
        appContext
            .getSystemService(TelecomManager.class)
            .getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
    if (defaultPhoneAccount != null) {
      return useDefaultSim(defaultPhoneAccount, candidates, dataId.orNull());
    }

    Optional<Suggestion> suggestion =
        SimSuggestionComponent.get(appContext)
            .getSuggestionProvider()
            .getSuggestion(appContext, phoneNumber);
    if (suggestion.isPresent() && suggestion.get().shouldAutoSelect) {
      return useSuggestedSim(suggestion.get(), candidates, dataId.orNull());
    }

    Builder resultBuilder =
        Result.builder(
            createDialogOptionsBuilder(candidates, dataId.orNull(), suggestion.orNull()));
    if (suggestion.isPresent()) {
      resultBuilder.setSuggestion(suggestion.get());
    }
    if (dataId.isPresent()) {
      resultBuilder.setDataId(dataId.get());
    }
    return resultBuilder.build();
  }

  private Result usePreferredSim(
      PhoneAccountHandle preferred, List<PhoneAccountHandle> candidates, String dataId) {
    Builder resultBuilder;
    if (isSelectable(preferred)) {
      Logger.get(appContext).logImpression(Type.DUAL_SIM_SELECTION_PREFERRED_USED);
      resultBuilder = Result.builder(preferred);
    } else {
      Logger.get(appContext).logImpression(Type.DUAL_SIM_SELECTION_PREFERRED_NOT_SELECTABLE);
      LogUtil.i("CallingAccountSelector.usePreferredAccount", "preferred account not selectable");
      resultBuilder = Result.builder(createDialogOptionsBuilder(candidates, dataId, null));
    }
    resultBuilder.setDataId(dataId);
    return resultBuilder.build();
  }

  private Result useDefaultSim(
      PhoneAccountHandle defaultPhoneAccount,
      List<PhoneAccountHandle> candidates,
      @Nullable String dataId) {
    if (isSelectable(defaultPhoneAccount)) {
      Logger.get(appContext).logImpression(Type.DUAL_SIM_SELECTION_GLOBAL_USED);
      return Result.builder(defaultPhoneAccount).build();
    } else {
      Logger.get(appContext).logImpression(Type.DUAL_SIM_SELECTION_GLOBAL_NOT_SELECTABLE);
      LogUtil.i("CallingAccountSelector.usePreferredAccount", "global account not selectable");
      return Result.builder(createDialogOptionsBuilder(candidates, dataId, null)).build();
    }
  }

  private Result useSuggestedSim(
      Suggestion suggestion, List<PhoneAccountHandle> candidates, @Nullable String dataId) {
    Builder resultBuilder;
    PhoneAccountHandle suggestedPhoneAccount = suggestion.phoneAccountHandle;
    if (isSelectable(suggestedPhoneAccount)) {
      resultBuilder = Result.builder(suggestedPhoneAccount);
      Logger.get(appContext).logImpression(Type.DUAL_SIM_SELECTION_SUGGESTION_AUTO_SELECTED);
    } else {
      Logger.get(appContext).logImpression(Type.DUAL_SIM_SELECTION_SUGGESTION_AUTO_NOT_SELECTABLE);
      LogUtil.i("CallingAccountSelector.usePreferredAccount", "global account not selectable");
      resultBuilder = Result.builder(createDialogOptionsBuilder(candidates, dataId, suggestion));
      return resultBuilder.build();
    }
    resultBuilder.setSuggestion(suggestion);
    return resultBuilder.build();
  }

  SelectPhoneAccountDialogOptions.Builder createDialogOptionsBuilder(
      List<PhoneAccountHandle> candidates,
      @Nullable String dataId,
      @Nullable Suggestion suggestion) {
    Logger.get(appContext).logImpression(Type.DUAL_SIM_SELECTION_SHOWN);
    if (dataId != null) {
      Logger.get(appContext).logImpression(Type.DUAL_SIM_SELECTION_IN_CONTACTS);
    }
    if (suggestion != null) {
      Logger.get(appContext).logImpression(Type.DUAL_SIM_SELECTION_SUGGESTION_AVAILABLE);
      switch (suggestion.reason) {
        case INTRA_CARRIER:
          Logger.get(appContext).logImpression(Type.DUAL_SIM_SELECTION_SUGGESTED_CARRIER);
          break;
        case FREQUENT:
          Logger.get(appContext).logImpression(Type.DUAL_SIM_SELECTION_SUGGESTED_FREQUENCY);
          break;
        default:
      }
    }
    SelectPhoneAccountDialogOptions.Builder optionsBuilder =
        SelectPhoneAccountDialogOptions.newBuilder()
            .setTitle(R.string.pre_call_select_phone_account)
            .setCanSetDefault(dataId != null)
            .setSetDefaultLabel(R.string.pre_call_select_phone_account_remember);

    for (PhoneAccountHandle phoneAccountHandle : candidates) {
      SelectPhoneAccountDialogOptions.Entry.Builder entryBuilder =
          SelectPhoneAccountDialogOptions.Entry.newBuilder();
      SelectPhoneAccountDialogOptionsUtil.setPhoneAccountHandle(entryBuilder, phoneAccountHandle);
      if (isSelectable(phoneAccountHandle)) {
        Optional<String> hint =
            SuggestionProvider.getHint(appContext, phoneAccountHandle, suggestion);
        if (hint.isPresent()) {
          entryBuilder.setHint(hint.get());
        }
      } else {
        entryBuilder.setEnabled(false);
        Optional<String> activeCallLabel = getActiveCallLabel();
        if (activeCallLabel.isPresent()) {
          entryBuilder.setHint(
              appContext.getString(
                  R.string.pre_call_select_phone_account_hint_other_sim_in_use,
                  activeCallLabel.get()));
        }
      }
      optionsBuilder.addEntries(entryBuilder);
    }

    return optionsBuilder;
  }

  @WorkerThread
  @NonNull
  private Optional<String> getDataId(@Nullable String phoneNumber) {
    Assert.isWorkerThread();

    if (!isPreferredSimEnabled(appContext)) {
      return Optional.absent();
    }
    if (!PermissionsUtil.hasContactsReadPermissions(appContext)) {
      LogUtil.i("PreferredAccountWorker.doInBackground", "missing READ_CONTACTS permission");
      return Optional.absent();
    }

    if (TextUtils.isEmpty(phoneNumber)) {
      return Optional.absent();
    }
    try (Cursor cursor =
        appContext
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
      ImmutableSet<String> validAccountTypes =
          PreferredAccountUtil.getValidAccountTypes(appContext);
      String result = null;
      while (cursor.moveToNext()) {
        Optional<String> accountType =
            getAccountType(appContext.getContentResolver(), cursor.getLong(0));
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
    if (!ConfigProviderComponent.get(context)
        .getConfigProvider()
        .getBoolean("preferred_sim_enabled", true)) {
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

  /**
   * Most devices are DSDS (dual SIM dual standby) which only one SIM can have active calls at a
   * time. TODO(twyen): support other dual SIM modes when the API is exposed.
   */
  private boolean isSelectable(PhoneAccountHandle phoneAccountHandle) {
    ImmutableList<ActiveCallInfo> activeCalls =
        ActiveCallsComponent.get(appContext).activeCalls().getActiveCalls();
    if (activeCalls.isEmpty()) {
      return true;
    }
    for (ActiveCallInfo activeCall : activeCalls) {
      if (Objects.equals(phoneAccountHandle, activeCall.phoneAccountHandle().orNull())) {
        return true;
      }
    }
    return false;
  }

  private Optional<String> getActiveCallLabel() {
    ImmutableList<ActiveCallInfo> activeCalls =
        ActiveCallsComponent.get(appContext).activeCalls().getActiveCalls();

    if (activeCalls.isEmpty()) {
      LogUtil.e("CallingAccountSelector.getActiveCallLabel", "active calls no longer exist");
      return Optional.absent();
    }
    ActiveCallInfo activeCall = activeCalls.get(0);
    if (!activeCall.phoneAccountHandle().isPresent()) {
      LogUtil.e("CallingAccountSelector.getActiveCallLabel", "active call has no phone account");
      return Optional.absent();
    }
    PhoneAccount phoneAccount =
        appContext
            .getSystemService(TelecomManager.class)
            .getPhoneAccount(activeCall.phoneAccountHandle().get());
    if (phoneAccount == null) {
      LogUtil.e("CallingAccountSelector.getActiveCallLabel", "phone account not found");
      return Optional.absent();
    }
    return Optional.of(phoneAccount.getLabel().toString());
  }
}
