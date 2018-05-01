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

import android.telecom.PhoneAccountHandle;
import com.android.contacts.common.widget.SelectPhoneAccountDialogOptions;
import com.android.dialer.preferredsim.suggestion.SuggestionProvider.Suggestion;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/** Query a preferred SIM to make a call with. */
@SuppressWarnings({"missingPermission", "Guava"})
public interface PreferredAccountWorker {

  /** Result of the query. */
  @AutoValue
  abstract class Result {

    /**
     * The phone account to dial with for the number. Absent if no account can be auto selected. If
     * absent, {@link #getSelectedPhoneAccountHandle()} will be present to show a dialog for the
     * user to manually select.
     */
    public abstract Optional<PhoneAccountHandle> getSelectedPhoneAccountHandle();

    /**
     * The {@link SelectPhoneAccountDialogOptions} that should be used to show the selection dialog.
     * Absent if {@link #getSelectedPhoneAccountHandle()} is present, which should be used directly
     * instead of asking the user.
     */
    public abstract Optional<SelectPhoneAccountDialogOptions.Builder> getDialogOptionsBuilder();

    /**
     * {@link android.provider.ContactsContract.Data#_ID} of the row matching the number. If the
     * preferred account is to be set it should be stored in this row
     */
    public abstract Optional<String> getDataId();

    public abstract Optional<Suggestion> getSuggestion();

    public static Builder builder(PhoneAccountHandle selectedPhoneAccountHandle) {
      return new AutoValue_PreferredAccountWorker_Result.Builder()
          .setSelectedPhoneAccountHandle(selectedPhoneAccountHandle);
    }

    public static Builder builder(SelectPhoneAccountDialogOptions.Builder optionsBuilder) {
      return new AutoValue_PreferredAccountWorker_Result.Builder()
          .setDialogOptionsBuilder(optionsBuilder);
    }

    /** For implementations of {@link PreferredAccountWorker} only. */
    @AutoValue.Builder
    public abstract static class Builder {

      abstract Builder setSelectedPhoneAccountHandle(PhoneAccountHandle phoneAccountHandle);

      public abstract Builder setDataId(String dataId);

      abstract Builder setDialogOptionsBuilder(
          SelectPhoneAccountDialogOptions.Builder optionsBuilder);

      public abstract Builder setSuggestion(Suggestion suggestion);

      public abstract Result build();
    }
  }

  /**
   * @return a {@link SelectPhoneAccountDialogOptions} for a dialog to select SIM for voicemail call
   */
  SelectPhoneAccountDialogOptions getVoicemailDialogOptions();

  /**
   * Return {@link Result} for the best {@link PhoneAccountHandle} among {@code candidates} to call
   * the number with. If none are eligible, a {@link SelectPhoneAccountDialogOptions} will be
   * provided to show a dialog for the user to manually select.
   */
  ListenableFuture<Result> selectAccount(String phoneNumber, List<PhoneAccountHandle> candidates);
}
