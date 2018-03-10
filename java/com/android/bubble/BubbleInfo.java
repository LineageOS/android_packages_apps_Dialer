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

package com.android.bubble;

import android.app.PendingIntent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Px;
import com.google.auto.value.AutoValue;
import java.util.Collections;
import java.util.List;

/** Info for displaying a {@link Bubble} */
@AutoValue
public abstract class BubbleInfo {
  @ColorInt
  public abstract int getPrimaryColor();

  public abstract Icon getPrimaryIcon();

  @Nullable
  public abstract Drawable getAvatar();

  @Px
  public abstract int getStartingYPosition();

  @NonNull
  public abstract List<Action> getActions();

  public static Builder builder() {
    return new AutoValue_BubbleInfo.Builder().setActions(Collections.emptyList());
  }

  public static Builder from(@NonNull BubbleInfo bubbleInfo) {
    return builder()
        .setPrimaryColor(bubbleInfo.getPrimaryColor())
        .setPrimaryIcon(bubbleInfo.getPrimaryIcon())
        .setStartingYPosition(bubbleInfo.getStartingYPosition())
        .setActions(bubbleInfo.getActions())
        .setAvatar(bubbleInfo.getAvatar());
  }

  /** Builder for {@link BubbleInfo} */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setPrimaryColor(@ColorInt int primaryColor);

    public abstract Builder setPrimaryIcon(@NonNull Icon primaryIcon);

    public abstract Builder setAvatar(@Nullable Drawable avatar);

    public abstract Builder setStartingYPosition(@Px int startingYPosition);

    public abstract Builder setActions(List<Action> actions);

    public abstract BubbleInfo build();
  }

  /** Represents actions to be shown in the bubble when expanded */
  @AutoValue
  public abstract static class Action {

    public abstract Drawable getIconDrawable();

    @Nullable
    public abstract Drawable getSecondaryIconDrawable();

    @NonNull
    public abstract CharSequence getName();

    @NonNull
    public abstract PendingIntent getIntent();

    public abstract boolean isCheckable();

    public abstract boolean isChecked();

    public static Builder builder() {
      return new AutoValue_BubbleInfo_Action.Builder().setCheckable(true).setChecked(false);
    }

    public static Builder from(@NonNull Action action) {
      return builder()
          .setIntent(action.getIntent())
          .setChecked(action.isChecked())
          .setCheckable(action.isCheckable())
          .setName(action.getName())
          .setIconDrawable(action.getIconDrawable())
          .setSecondaryIconDrawable(action.getSecondaryIconDrawable());
    }

    /** Builder for {@link Action} */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setIconDrawable(Drawable iconDrawable);

      public abstract Builder setSecondaryIconDrawable(@Nullable Drawable secondaryIconDrawable);

      public abstract Builder setName(@NonNull CharSequence name);

      public abstract Builder setIntent(@NonNull PendingIntent intent);

      public abstract Builder setCheckable(boolean enabled);

      public abstract Builder setChecked(boolean checked);

      public abstract Action build();
    }
  }
}
