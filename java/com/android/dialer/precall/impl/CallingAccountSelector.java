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

package com.android.dialer.precall.impl;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsContract.RawContacts;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.support.v4.util.ArraySet;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment.SelectPhoneAccountListener;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.DialerImpression.Type;
import com.android.dialer.logging.Logger;
import com.android.dialer.precall.PreCallAction;
import com.android.dialer.precall.PreCallCoordinator;
import com.android.dialer.precall.PreCallCoordinator.PendingAction;
import com.android.dialer.preferredsim.PreferredSimFallbackContract;
import com.android.dialer.preferredsim.PreferredSimFallbackContract.PreferredSim;
import com.android.dialer.preferredsim.suggestion.SimSuggestionComponent;
import com.android.dialer.preferredsim.suggestion.SuggestionProvider.Suggestion;
import com.android.dialer.telecom.TelecomUtil;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** PreCallAction to select which phone account to call with. Ignored if there's only one account */
@SuppressWarnings("MissingPermission")
public class CallingAccountSelector implements PreCallAction {

  @VisibleForTesting static final String TAG_CALLING_ACCOUNT_SELECTOR = "CallingAccountSelector";

  @VisibleForTesting
  static final String METADATA_SUPPORTS_PREFERRED_SIM = "supports_per_number_preferred_account";

  private SelectPhoneAccountDialogFragment selectPhoneAccountDialogFragment;

  private boolean isDiscarding;

  @Override
  public boolean requiresUi(Context context, CallIntentBuilder builder) {
    if (!ConfigProviderBindings.get(context)
        .getBoolean("precall_calling_account_selector_enabled", true)) {
      return false;
    }

    if (builder.getPhoneAccountHandle() != null) {
      return false;
    }
    TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
    List<PhoneAccountHandle> accounts = telecomManager.getCallCapablePhoneAccounts();
    if (accounts.size() <= 1) {
      return false;
    }

    if (TelecomUtil.isInManagedCall(context)) {
      // Most devices are DSDS (dual SIM dual standby) which only one SIM can have active calls at
      // a time. Telecom will ignore the phone account handle and use the current active SIM, thus
      // there's no point of selecting SIMs
      // TODO(a bug): let the user know selections are not available and preferred SIM is not
      // used
      // TODO(twyen): support other dual SIM modes when the API is exposed.
      return false;
    }

    return true;
  }

  @Override
  public void runWithoutUi(Context context, CallIntentBuilder builder) {
    // do nothing.
  }

  @Override
  public void runWithUi(PreCallCoordinator coordinator) {
    CallIntentBuilder builder = coordinator.getBuilder();
    if (!requiresUi(coordinator.getActivity(), builder)) {
      return;
    }
    switch (builder.getUri().getScheme()) {
      case PhoneAccount.SCHEME_VOICEMAIL:
        showDialog(coordinator, coordinator.startPendingAction(), null, null, null);
        Logger.get(coordinator.getActivity()).logImpression(Type.DUAL_SIM_SELECTION_VOICEMAIL);
        break;
      case PhoneAccount.SCHEME_TEL:
        processPreferredAccount(coordinator);
        break;
      default:
        // might be PhoneAccount.SCHEME_SIP
        LogUtil.e(
            "CallingAccountSelector.run",
            "unable to process scheme " + builder.getUri().getScheme());
        break;
    }
  }

  /** Initiates a background worker to find if there's any preferred account. */
  @MainThread
  private void processPreferredAccount(PreCallCoordinator coordinator) {
    Assert.isMainThread();
    CallIntentBuilder builder = coordinator.getBuilder();
    Activity activity = coordinator.getActivity();
    String phoneNumber = builder.getUri().getSchemeSpecificPart();
    PendingAction pendingAction = coordinator.startPendingAction();
    DialerExecutorComponent.get(coordinator.getActivity())
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(new PreferredAccountWorker(phoneNumber))
        .onSuccess(
            (result -> {
              if (isDiscarding) {
                return;
              }
              if (result.phoneAccountHandle.isPresent()) {
                Logger.get(coordinator.getActivity())
                    .logImpression(DialerImpression.Type.DUAL_SIM_SELECTION_PREFERRED_USED);
                coordinator.getBuilder().setPhoneAccountHandle(result.phoneAccountHandle.get());
                pendingAction.finish();
                return;
              }
              PhoneAccountHandle defaultPhoneAccount =
                  activity
                      .getSystemService(TelecomManager.class)
                      .getDefaultOutgoingPhoneAccount(builder.getUri().getScheme());
              if (defaultPhoneAccount != null) {
                Logger.get(coordinator.getActivity())
                    .logImpression(DialerImpression.Type.DUAL_SIM_SELECTION_GLOBAL_USED);
                builder.setPhoneAccountHandle(defaultPhoneAccount);
                pendingAction.finish();
                return;
              }
              if (result.suggestion.isPresent()) {
                LogUtil.i(
                    "CallingAccountSelector.processPreferredAccount",
                    "SIM suggested: " + result.suggestion.get().reason);
                if (result.suggestion.get().shouldAutoSelect) {
                  Logger.get(coordinator.getActivity())
                      .logImpression(
                          DialerImpression.Type.DUAL_SIM_SELECTION_SUGGESTION_AUTO_SELECTED);
                  LogUtil.i(
                      "CallingAccountSelector.processPreferredAccount", "Auto selected suggestion");
                  builder.setPhoneAccountHandle(result.suggestion.get().phoneAccountHandle);
                  pendingAction.finish();
                  return;
                }
              }
              showDialog(
                  coordinator,
                  pendingAction,
                  result.dataId.orNull(),
                  phoneNumber,
                  result.suggestion.orNull());
            }))
        .build()
        .executeParallel(activity);
  }

  @MainThread
  private void showDialog(
      PreCallCoordinator coordinator,
      PendingAction pendingAction,
      @Nullable String dataId,
      @Nullable String number,
      @Nullable Suggestion suggestion) {
    Assert.isMainThread();
    Logger.get(coordinator.getActivity()).logImpression(Type.DUAL_SIM_SELECTION_SHOWN);
    if (dataId != null) {
      Logger.get(coordinator.getActivity()).logImpression(Type.DUAL_SIM_SELECTION_IN_CONTACTS);
    }
    if (suggestion != null) {
      Logger.get(coordinator.getActivity())
          .logImpression(Type.DUAL_SIM_SELECTION_SUGGESTION_AVAILABLE);
      switch (suggestion.reason) {
        case INTRA_CARRIER:
          Logger.get(coordinator.getActivity())
              .logImpression(Type.DUAL_SIM_SELECTION_SUGGESTED_CARRIER);
          break;
        case FREQUENT:
          Logger.get(coordinator.getActivity())
              .logImpression(Type.DUAL_SIM_SELECTION_SUGGESTED_FREQUENCY);
          break;
        default:
      }
    }
    List<PhoneAccountHandle> phoneAccountHandles =
        coordinator
            .getActivity()
            .getSystemService(TelecomManager.class)
            .getCallCapablePhoneAccounts();
    selectPhoneAccountDialogFragment =
        SelectPhoneAccountDialogFragment.newInstance(
            R.string.pre_call_select_phone_account,
            dataId != null /* canSetDefault */,
            R.string.pre_call_select_phone_account_remember,
            phoneAccountHandles,
            new SelectedListener(coordinator, pendingAction, dataId, number, suggestion),
            null /* call ID */,
            buildHint(coordinator.getActivity(), phoneAccountHandles, suggestion));
    selectPhoneAccountDialogFragment.show(
        coordinator.getActivity().getFragmentManager(), TAG_CALLING_ACCOUNT_SELECTOR);
  }

  @Nullable
  private static List<String> buildHint(
      Context context,
      List<PhoneAccountHandle> phoneAccountHandles,
      @Nullable Suggestion suggestion) {
    if (suggestion == null) {
      return null;
    }
    List<String> hints = new ArrayList<>();
    for (PhoneAccountHandle phoneAccountHandle : phoneAccountHandles) {
      if (!phoneAccountHandle.equals(suggestion.phoneAccountHandle)) {
        hints.add(null);
        continue;
      }
      switch (suggestion.reason) {
        case INTRA_CARRIER:
          hints.add(context.getString(R.string.pre_call_select_phone_account_hint_intra_carrier));
          break;
        case FREQUENT:
          hints.add(context.getString(R.string.pre_call_select_phone_account_hint_frequent));
          break;
        default:
          throw Assert.createAssertionFailException("unexpected reason " + suggestion.reason);
      }
    }
    return hints;
  }

  @MainThread
  @Override
  public void onDiscard() {
    isDiscarding = true;
    if (selectPhoneAccountDialogFragment != null) {
      selectPhoneAccountDialogFragment.dismiss();
    }
  }

  private static class PreferredAccountWorkerResult {

    /** The preferred phone account for the number. Absent if not set or invalid. */
    Optional<PhoneAccountHandle> phoneAccountHandle = Optional.absent();

    /**
     * {@link android.provider.ContactsContract.Data#_ID} of the row matching the number. If the
     * preferred account is to be set it should be stored in this row
     */
    Optional<String> dataId = Optional.absent();

    Optional<Suggestion> suggestion = Optional.absent();
  }

  private static class PreferredAccountWorker
      implements Worker<Context, PreferredAccountWorkerResult> {

    private final String phoneNumber;

    public PreferredAccountWorker(String phoneNumber) {
      this.phoneNumber = phoneNumber;
    }

    @NonNull
    @Override
    @WorkerThread
    public PreferredAccountWorkerResult doInBackground(Context context) throws Throwable {
      PreferredAccountWorkerResult result = new PreferredAccountWorkerResult();
      if (!isPreferredSimEnabled(context)) {
        return result;
      }
      result.dataId = getDataId(context, phoneNumber);
      if (result.dataId.isPresent()) {
        result.phoneAccountHandle = getPreferredAccount(context, result.dataId.get());
      }
      if (!result.phoneAccountHandle.isPresent()) {
        result.suggestion =
            SimSuggestionComponent.get(context)
                .getSuggestionProvider()
                .getSuggestion(context, phoneNumber);
      }
      return result;
    }
  }

  @WorkerThread
  @NonNull
  private static Optional<String> getDataId(
      @NonNull Context context, @Nullable String phoneNumber) {
    Assert.isWorkerThread();
    if (VERSION.SDK_INT < VERSION_CODES.N) {
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
      Set<String> result = new ArraySet<>();
      while (cursor.moveToNext()) {
        Optional<String> accountType =
            getAccountType(context.getContentResolver(), cursor.getLong(0));
        if (accountType.isPresent() && validAccountTypes.contains(accountType.get())) {
          result.add(cursor.getString(0));
        } else {
          LogUtil.i("CallingAccountSelector.getDataId", "ignoring non-writable " + accountType);
        }
      }
      // TODO(twyen): if there are multiples attempt to grab from the contact that initiated the
      // call.
      if (result.size() == 1) {
        return Optional.of(result.iterator().next());
      } else {
        LogUtil.i("CallingAccountSelector.getDataId", "lookup result not unique, ignoring");
        return Optional.absent();
      }
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
      return Optional.of(cursor.getString(0));
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

  private class SelectedListener extends SelectPhoneAccountListener {

    private final PreCallCoordinator coordinator;
    private final PreCallCoordinator.PendingAction listener;
    private final String dataId;
    private final String number;
    private final Suggestion suggestion;

    public SelectedListener(
        @NonNull PreCallCoordinator builder,
        @NonNull PreCallCoordinator.PendingAction listener,
        @Nullable String dataId,
        @Nullable String number,
        @Nullable Suggestion suggestion) {
      this.coordinator = Assert.isNotNull(builder);
      this.listener = Assert.isNotNull(listener);
      this.dataId = dataId;
      this.number = number;
      this.suggestion = suggestion;
    }

    @MainThread
    @Override
    public void onPhoneAccountSelected(
        PhoneAccountHandle selectedAccountHandle, boolean setDefault, @Nullable String callId) {
      if (suggestion != null) {
        if (suggestion.phoneAccountHandle.equals(selectedAccountHandle)) {
          Logger.get(coordinator.getActivity())
              .logImpression(DialerImpression.Type.DUAL_SIM_SELECTION_SUGGESTED_SIM_SELECTED);
        } else {
          Logger.get(coordinator.getActivity())
              .logImpression(DialerImpression.Type.DUAL_SIM_SELECTION_NON_SUGGESTED_SIM_SELECTED);
        }
      }
      coordinator.getBuilder().setPhoneAccountHandle(selectedAccountHandle);

      if (dataId != null && setDefault) {
        Logger.get(coordinator.getActivity())
            .logImpression(DialerImpression.Type.DUAL_SIM_SELECTION_PREFERRED_SET);
        DialerExecutorComponent.get(coordinator.getActivity())
            .dialerExecutorFactory()
            .createNonUiTaskBuilder(new WritePreferredAccountWorker())
            .build()
            .executeParallel(
                new WritePreferredAccountWorkerInput(
                    coordinator.getActivity(), dataId, selectedAccountHandle));
      }
      if (number != null) {
        DialerExecutorComponent.get(coordinator.getActivity())
            .dialerExecutorFactory()
            .createNonUiTaskBuilder(new UserSelectionReporter(selectedAccountHandle, number))
            .build()
            .executeParallel(coordinator.getActivity());
      }
      listener.finish();
    }

    @MainThread
    @Override
    public void onDialogDismissed(@Nullable String callId) {
      if (isDiscarding) {
        return;
      }
      coordinator.abortCall();
      listener.finish();
    }
  }

  private static class UserSelectionReporter implements Worker<Context, Void> {

    private final String number;
    private final PhoneAccountHandle phoneAccountHandle;

    public UserSelectionReporter(
        @NonNull PhoneAccountHandle phoneAccountHandle, @Nullable String number) {
      this.phoneAccountHandle = Assert.isNotNull(phoneAccountHandle);
      this.number = Assert.isNotNull(number);
    }

    @Nullable
    @Override
    public Void doInBackground(@NonNull Context context) throws Throwable {
      SimSuggestionComponent.get(context)
          .getSuggestionProvider()
          .reportUserSelection(context, number, phoneAccountHandle);
      return null;
    }
  }

  private static class WritePreferredAccountWorkerInput {
    private final Context context;
    private final String dataId;
    private final PhoneAccountHandle phoneAccountHandle;

    WritePreferredAccountWorkerInput(
        @NonNull Context context,
        @NonNull String dataId,
        @NonNull PhoneAccountHandle phoneAccountHandle) {
      this.context = Assert.isNotNull(context);
      this.dataId = Assert.isNotNull(dataId);
      this.phoneAccountHandle = Assert.isNotNull(phoneAccountHandle);
    }
  }

  private static class WritePreferredAccountWorker
      implements Worker<WritePreferredAccountWorkerInput, Void> {

    @Nullable
    @Override
    @WorkerThread
    public Void doInBackground(WritePreferredAccountWorkerInput input) throws Throwable {
      ContentValues values = new ContentValues();
      values.put(
          PreferredSim.PREFERRED_PHONE_ACCOUNT_COMPONENT_NAME,
          input.phoneAccountHandle.getComponentName().flattenToString());
      values.put(PreferredSim.PREFERRED_PHONE_ACCOUNT_ID, input.phoneAccountHandle.getId());
      input
          .context
          .getContentResolver()
          .update(
              PreferredSimFallbackContract.CONTENT_URI,
              values,
              PreferredSim.DATA_ID + " = ?",
              new String[] {String.valueOf(input.dataId)});
      return null;
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
  static Intent getQuickContactIntent() {
    Intent intent = new Intent(QuickContact.ACTION_QUICK_CONTACT);
    intent.addCategory(Intent.CATEGORY_DEFAULT);
    intent.setData(Contacts.CONTENT_URI.buildUpon().appendPath("1").build());
    return intent;
  }
}
