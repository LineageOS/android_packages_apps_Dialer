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
 * limitations under the License.
 */
package com.android.dialer.historyitemactions;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.google.auto.value.AutoValue;

/**
 * Contains information necessary to construct the primary action for a history item's bottom sheet.
 *
 * <p>A history item is one that is displayed in the call log or the voicemail fragment.
 */
@AutoValue
public abstract class HistoryItemPrimaryActionInfo {

  @Nullable
  public abstract DialerPhoneNumber number();

  @NonNull
  public abstract PhotoInfo photoInfo();

  @Nullable
  public abstract CharSequence primaryText();

  @Nullable
  public abstract CharSequence secondaryText();

  /**
   * The intent to fire when the user clicks the top row of the bottom sheet. Null if no action
   * should occur (e.g. if the number is unknown).
   */
  @Nullable
  public abstract Intent intent();

  // TODO(zachh): Add SIM info here if should be shown in bottom sheet.

  /** Builder for {@link HistoryItemPrimaryActionInfo}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setNumber(@Nullable DialerPhoneNumber dialerPhoneNumber);

    public abstract Builder setPhotoInfo(@NonNull PhotoInfo photoInfo);

    public abstract Builder setPrimaryText(@Nullable CharSequence primaryText);

    public abstract Builder setSecondaryText(@Nullable CharSequence secondaryText);

    public abstract Builder setIntent(@Nullable Intent intent);

    public abstract HistoryItemPrimaryActionInfo build();
  }

  public static Builder builder() {
    return new AutoValue_HistoryItemPrimaryActionInfo.Builder();
  }
}
