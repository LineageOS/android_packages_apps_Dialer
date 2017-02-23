/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.incallui.answer.impl.answermethod;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import com.android.dialer.common.FragmentUtils;

/** A fragment that can be used to answer/reject calls. */
public abstract class AnswerMethod extends Fragment {

  public abstract void setHintText(@Nullable CharSequence hintText);

  public abstract void setShowIncomingWillDisconnect(boolean incomingWillDisconnect);

  public void setContactPhoto(@Nullable Drawable contactPhoto) {
    // default implementation does nothing. Only some AnswerMethods show a photo
  }

  protected AnswerMethodHolder getParent() {
    return FragmentUtils.getParentUnsafe(this, AnswerMethodHolder.class);
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    FragmentUtils.checkParent(this, AnswerMethodHolder.class);
  }
}
