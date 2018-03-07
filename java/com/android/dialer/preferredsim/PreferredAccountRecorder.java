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

import android.content.ContentValues;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.telecom.PhoneAccountHandle;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.preferredsim.PreferredSimFallbackContract.PreferredSim;
import com.android.dialer.preferredsim.suggestion.SimSuggestionComponent;
import com.android.dialer.preferredsim.suggestion.SuggestionProvider.Suggestion;

/** Records selected preferred SIM and reports related metric to the suggestion provider */
public class PreferredAccountRecorder {

  @Nullable private final String number;
  @Nullable private final Suggestion suggestion;
  @Nullable private final String dataId;

  public PreferredAccountRecorder(
      @Nullable String number, @Nullable Suggestion suggestion, @Nullable String dataId) {
    this.number = number;
    this.suggestion = suggestion;
    this.dataId = dataId;
  }

  /**
   * Record the result from {@link
   * com.android.contacts.common.widget.SelectPhoneAccountDialogFragment.SelectPhoneAccountListener#onPhoneAccountSelected}
   */
  public void record(
      Context context, PhoneAccountHandle selectedAccountHandle, boolean setDefault) {

    if (suggestion != null) {
      if (suggestion.phoneAccountHandle.equals(selectedAccountHandle)) {
        Logger.get(context)
            .logImpression(DialerImpression.Type.DUAL_SIM_SELECTION_SUGGESTED_SIM_SELECTED);
      } else {
        Logger.get(context)
            .logImpression(DialerImpression.Type.DUAL_SIM_SELECTION_NON_SUGGESTED_SIM_SELECTED);
      }
    }

    if (dataId != null && setDefault) {
      Logger.get(context).logImpression(DialerImpression.Type.DUAL_SIM_SELECTION_PREFERRED_SET);
      DialerExecutorComponent.get(context)
          .dialerExecutorFactory()
          .createNonUiTaskBuilder(new WritePreferredAccountWorker())
          .build()
          .executeParallel(
              new WritePreferredAccountWorkerInput(context, dataId, selectedAccountHandle));
    }
    if (number != null) {
      DialerExecutorComponent.get(context)
          .dialerExecutorFactory()
          .createNonUiTaskBuilder(
              new UserSelectionReporter(selectedAccountHandle, number, setDefault))
          .build()
          .executeParallel(context);
    }
  }

  private static class UserSelectionReporter implements Worker<Context, Void> {

    private final String number;
    private final PhoneAccountHandle phoneAccountHandle;
    private final boolean remember;

    public UserSelectionReporter(
        @NonNull PhoneAccountHandle phoneAccountHandle, @Nullable String number, boolean remember) {
      this.phoneAccountHandle = Assert.isNotNull(phoneAccountHandle);
      this.number = Assert.isNotNull(number);
      this.remember = remember;
    }

    @Nullable
    @Override
    public Void doInBackground(@NonNull Context context) throws Throwable {
      SimSuggestionComponent.get(context)
          .getSuggestionProvider()
          .reportUserSelection(context, number, phoneAccountHandle, remember);
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
}
