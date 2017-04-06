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
 * limitations under the License.
 */

package com.android.dialer.callcomposer;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;

/** Base fragment with fields and methods needed for all fragments in the call compose UI. */
public abstract class CallComposerFragment extends Fragment {

  protected static final int CAMERA_PERMISSION = 1;
  protected static final int STORAGE_PERMISSION = 2;

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if (FragmentUtils.getParent(this, CallComposerListener.class) == null) {
      LogUtil.e(
          "CallComposerFragment.onAttach",
          "Container activity must implement CallComposerListener.");
      Assert.fail();
    }
  }

  @Nullable
  public CallComposerListener getListener() {
    return FragmentUtils.getParent(this, CallComposerListener.class);
  }

  public abstract boolean shouldHide();

  public abstract void clearComposer();

  /** Interface used to listen to CallComposeFragments */
  public interface CallComposerListener {
    /** Let the listener know when a call is ready to be composed. */
    void composeCall(CallComposerFragment fragment);

    /** Let the listener know when the layout has changed to full screen */
    void showFullscreen(boolean show);

    /** True is the listener is in fullscreen. */
    boolean isFullscreen();

    /** True if the layout is in landscape mode. */
    boolean isLandscapeLayout();

    /** Tell the listener that call composition is done and we should start the call. */
    void sendAndCall();
  }
}
