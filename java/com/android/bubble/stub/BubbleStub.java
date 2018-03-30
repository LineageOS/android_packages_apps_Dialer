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

package com.android.bubble.stub;

import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import com.android.bubble.Bubble;
import com.android.bubble.BubbleInfo;
import java.util.List;
import javax.inject.Inject;

public class BubbleStub implements Bubble {

  @Inject
  public BubbleStub() {}

  @Override
  public void show() {}

  @Override
  public void hide() {}

  @Override
  public boolean isVisible() {
    return false;
  }

  @Override
  public boolean isDismissed() {
    return false;
  }

  @Override
  public void setBubbleInfo(@NonNull BubbleInfo bubbleInfo) {}

  @Override
  public void updateActions(@NonNull List<BubbleInfo.Action> actions) {}

  @Override
  public void updatePhotoAvatar(@NonNull Drawable avatar) {}

  @Override
  public void updateAvatar(@NonNull Drawable avatar) {}

  @Override
  public void showText(@NonNull CharSequence text) {}
}
