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
import android.content.Context;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment.SelectPhoneAccountListener;
import com.android.contacts.common.widget.SelectPhoneAccountDialogOptions;
import com.android.contacts.common.widget.SelectPhoneAccountDialogOptionsUtil;
import com.android.dialer.activecalls.ActiveCallInfo;
import com.android.dialer.activecalls.ActiveCallsComponent;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.logging.DialerImpression.Type;
import com.android.dialer.logging.Logger;
import com.android.dialer.precall.PreCallAction;
import com.android.dialer.precall.PreCallCoordinator;
import com.android.dialer.precall.PreCallCoordinator.PendingAction;
import com.android.dialer.preferredsim.PreferredAccountRecorder;
import com.android.dialer.preferredsim.PreferredAccountWorker;
import com.android.dialer.preferredsim.PreferredAccountWorker.Result;
import com.android.dialer.preferredsim.suggestion.SuggestionProvider;
import com.android.dialer.preferredsim.suggestion.SuggestionProvider.Suggestion;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;

/** PreCallAction to select which phone account to call with. Ignored if there's only one account */
@SuppressWarnings("MissingPermission")
public class CallingAccountSelector implements PreCallAction {

  @VisibleForTesting static final String TAG_CALLING_ACCOUNT_SELECTOR = "CallingAccountSelector";

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
    if (PhoneNumberUtils.isEmergencyNumber(builder.getUri().getSchemeSpecificPart())) {
      return false;
    }

    TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
    List<PhoneAccountHandle> accounts = telecomManager.getCallCapablePhoneAccounts();
    if (accounts.size() <= 1) {
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
              if (result.getPhoneAccountHandle().isPresent()) {
                usePreferredAccount(coordinator, pendingAction, result);
                return;
              }
              PhoneAccountHandle defaultPhoneAccount =
                  activity
                      .getSystemService(TelecomManager.class)
                      .getDefaultOutgoingPhoneAccount(builder.getUri().getScheme());
              if (defaultPhoneAccount != null) {
                useDefaultAccount(coordinator, pendingAction, result, defaultPhoneAccount);
                return;
              }
              if (result.getSuggestion().isPresent()) {
                LogUtil.i(
                    "CallingAccountSelector.processPreferredAccount",
                    "SIM suggested: " + result.getSuggestion().get().reason);
                if (result.getSuggestion().get().shouldAutoSelect) {
                  useSuggestedAccount(coordinator, pendingAction, result);
                  return;
                }
              }
              showDialog(
                  coordinator,
                  pendingAction,
                  result.getDataId().orNull(),
                  phoneNumber,
                  result.getSuggestion().orNull());
            }))
        .build()
        .executeParallel(activity);
  }

  private void usePreferredAccount(
      PreCallCoordinator coordinator, PendingAction pendingAction, Result result) {
    String phoneNumber = coordinator.getBuilder().getUri().getSchemeSpecificPart();
    if (isSelectable(coordinator.getActivity(), result.getPhoneAccountHandle().get())) {
      Logger.get(coordinator.getActivity()).logImpression(Type.DUAL_SIM_SELECTION_PREFERRED_USED);
      coordinator.getBuilder().setPhoneAccountHandle(result.getPhoneAccountHandle().get());
      pendingAction.finish();
    } else {
      Logger.get(coordinator.getActivity())
          .logImpression(Type.DUAL_SIM_SELECTION_PREFERRED_NOT_SELECTABLE);
      LogUtil.i("CallingAccountSelector.usePreferredAccount", "preferred account not selectable");
      showDialog(
          coordinator,
          pendingAction,
          result.getDataId().orNull(),
          phoneNumber,
          result.getSuggestion().orNull());
    }
  }

  private void useDefaultAccount(
      PreCallCoordinator coordinator,
      PendingAction pendingAction,
      Result result,
      PhoneAccountHandle defaultPhoneAccount) {
    CallIntentBuilder builder = coordinator.getBuilder();
    String phoneNumber = builder.getUri().getSchemeSpecificPart();
    if (isSelectable(coordinator.getActivity(), defaultPhoneAccount)) {
      Logger.get(coordinator.getActivity()).logImpression(Type.DUAL_SIM_SELECTION_GLOBAL_USED);
      builder.setPhoneAccountHandle(defaultPhoneAccount);
      pendingAction.finish();
    } else {
      Logger.get(coordinator.getActivity())
          .logImpression(Type.DUAL_SIM_SELECTION_GLOBAL_NOT_SELECTABLE);
      LogUtil.i("CallingAccountSelector.useDefaultAccount", "default account not selectable");
      showDialog(
          coordinator,
          pendingAction,
          result.getDataId().orNull(),
          phoneNumber,
          result.getSuggestion().orNull());
    }
  }

  private void useSuggestedAccount(
      PreCallCoordinator coordinator, PendingAction pendingAction, Result result) {
    CallIntentBuilder builder = coordinator.getBuilder();
    String phoneNumber = builder.getUri().getSchemeSpecificPart();
    if (isSelectable(coordinator.getActivity(), result.getSuggestion().get().phoneAccountHandle)) {
      Logger.get(coordinator.getActivity())
          .logImpression(Type.DUAL_SIM_SELECTION_SUGGESTION_AUTO_SELECTED);
      LogUtil.i("CallingAccountSelector.processPreferredAccount", "Auto selected suggestion");
      builder.setPhoneAccountHandle(result.getSuggestion().get().phoneAccountHandle);
      builder
          .getInCallUiIntentExtras()
          .putString(
              SuggestionProvider.EXTRA_SIM_SUGGESTION_REASON,
              result.getSuggestion().get().reason.name());
      pendingAction.finish();
    } else {
      LogUtil.i("CallingAccountSelector.useSuggestedAccount", "suggested account not selectable");
      Logger.get(coordinator.getActivity())
          .logImpression(Type.DUAL_SIM_SELECTION_SUGGESTION_AUTO_NOT_SELECTABLE);
      showDialog(
          coordinator,
          pendingAction,
          result.getDataId().orNull(),
          phoneNumber,
          result.getSuggestion().orNull());
    }
  }

  /**
   * Most devices are DSDS (dual SIM dual standby) which only one SIM can have active calls at a
   * time. TODO(twyen): support other dual SIM modes when the API is exposed.
   */
  private static boolean isSelectable(Context context, PhoneAccountHandle phoneAccountHandle) {
    ImmutableList<ActiveCallInfo> activeCalls =
        ActiveCallsComponent.get(context).activeCalls().getActiveCalls();
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

  private static Optional<String> getActiveCallLabel(Context context) {
    ImmutableList<ActiveCallInfo> activeCalls =
        ActiveCallsComponent.get(context).activeCalls().getActiveCalls();

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
        context
            .getSystemService(TelecomManager.class)
            .getPhoneAccount(activeCall.phoneAccountHandle().get());
    if (phoneAccount == null) {
      LogUtil.e("CallingAccountSelector.getActiveCallLabel", "phone account not found");
      return Optional.absent();
    }
    return Optional.of(phoneAccount.getLabel().toString());
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
    SelectPhoneAccountDialogOptions.Builder optionsBuilder =
        SelectPhoneAccountDialogOptions.newBuilder()
            .setTitle(R.string.pre_call_select_phone_account)
            .setCanSetDefault(dataId != null)
            .setSetDefaultLabel(R.string.pre_call_select_phone_account_remember);

    for (PhoneAccountHandle phoneAccountHandle :
        coordinator
            .getActivity()
            .getSystemService(TelecomManager.class)
            .getCallCapablePhoneAccounts()) {
      SelectPhoneAccountDialogOptions.Entry.Builder entryBuilder =
          SelectPhoneAccountDialogOptions.Entry.newBuilder();
      SelectPhoneAccountDialogOptionsUtil.setPhoneAccountHandle(entryBuilder, phoneAccountHandle);
      if (isSelectable(coordinator.getActivity(), phoneAccountHandle)) {
        Optional<String> hint =
            SuggestionProvider.getHint(coordinator.getActivity(), phoneAccountHandle, suggestion);
        if (hint.isPresent()) {
          entryBuilder.setHint(hint.get());
        }
      } else {
        entryBuilder.setEnabled(false);
        Optional<String> activeCallLabel = getActiveCallLabel(coordinator.getActivity());
        if (activeCallLabel.isPresent()) {
          entryBuilder.setHint(
              coordinator
                  .getActivity()
                  .getString(
                      R.string.pre_call_select_phone_account_hint_other_sim_in_use,
                      activeCallLabel.get()));
        }
      }
      optionsBuilder.addEntries(entryBuilder);
    }
    selectPhoneAccountDialogFragment =
        SelectPhoneAccountDialogFragment.newInstance(
            optionsBuilder.build(),
            new SelectedListener(
                coordinator,
                pendingAction,
                new PreferredAccountRecorder(number, suggestion, dataId)));
    selectPhoneAccountDialogFragment.show(
        coordinator.getActivity().getFragmentManager(), TAG_CALLING_ACCOUNT_SELECTOR);
  }

  @MainThread
  @Override
  public void onDiscard() {
    isDiscarding = true;
    if (selectPhoneAccountDialogFragment != null) {
      selectPhoneAccountDialogFragment.dismiss();
    }
  }

  private class SelectedListener extends SelectPhoneAccountListener {

    private final PreCallCoordinator coordinator;
    private final PreCallCoordinator.PendingAction listener;
    private final PreferredAccountRecorder recorder;

    public SelectedListener(
        @NonNull PreCallCoordinator builder,
        @NonNull PreCallCoordinator.PendingAction listener,
        @NonNull PreferredAccountRecorder recorder) {
      this.coordinator = Assert.isNotNull(builder);
      this.listener = Assert.isNotNull(listener);
      this.recorder = Assert.isNotNull(recorder);
    }

    @MainThread
    @Override
    public void onPhoneAccountSelected(
        PhoneAccountHandle selectedAccountHandle, boolean setDefault, @Nullable String callId) {
      coordinator.getBuilder().setPhoneAccountHandle(selectedAccountHandle);
      recorder.record(coordinator.getActivity(), selectedAccountHandle, setDefault);
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
}
