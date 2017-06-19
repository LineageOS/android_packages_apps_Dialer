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

package com.android.dialershared.bubble;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Px;
import com.google.auto.value.AutoValue;
import java.util.Collections;
import java.util.List;

/** Info for displaying a {@link Bubble} */
@AutoValue
public abstract class BubbleInfo {
  @ColorInt
  public abstract int getPrimaryColor();

  @NonNull
  public abstract Icon getPrimaryIcon();

  @NonNull
  public abstract PendingIntent getPrimaryIntent();

  @Px
  public abstract int getStartingYPosition();

  @NonNull
  public abstract List<Action> getActions();

  public static Builder builder() {
    return new AutoValue_BubbleInfo.Builder().setActions(Collections.emptyList());
  }

  public static Builder from(@NonNull BubbleInfo bubbleInfo) {
    return builder()
        .setPrimaryIntent(bubbleInfo.getPrimaryIntent())
        .setPrimaryColor(bubbleInfo.getPrimaryColor())
        .setPrimaryIcon(bubbleInfo.getPrimaryIcon())
        .setStartingYPosition(bubbleInfo.getStartingYPosition())
        .setActions(bubbleInfo.getActions());
  }

  /** Builder for {@link BubbleInfo} */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setPrimaryColor(@ColorInt int primaryColor);

    public abstract Builder setPrimaryIcon(@NonNull Icon primaryIcon);

    public abstract Builder setPrimaryIntent(@NonNull PendingIntent primaryIntent);

    public abstract Builder setStartingYPosition(@Px int startingYPosition);

    public abstract Builder setActions(List<Action> actions);

    public abstract BubbleInfo build();
  }

  /** Represents actions to be shown in the bubble when expanded */
  @AutoValue
  public abstract static class Action {

    @NonNull
    public abstract Icon getIcon();

    @NonNull
    public abstract CharSequence getName();

    @NonNull
    public abstract PendingIntent getIntent();

    public abstract boolean isEnabled();

    public abstract boolean isChecked();

    public static Builder builder() {
      return new AutoValue_BubbleInfo_Action.Builder().setEnabled(true).setChecked(false);
    }

    public static Builder from(@NonNull Action action) {
      return builder()
          .setIntent(action.getIntent())
          .setChecked(action.isChecked())
          .setEnabled(action.isEnabled())
          .setName(action.getName())
          .setIcon(action.getIcon());
    }

    /** Builder for {@link Action} */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setIcon(@NonNull Icon icon);

      public abstract Builder setName(@NonNull CharSequence name);

      public abstract Builder setIntent(@NonNull PendingIntent intent);

      public abstract Builder setEnabled(boolean enabled);

      public abstract Builder setChecked(boolean checked);

      public abstract Action build();
    }
  }
}
