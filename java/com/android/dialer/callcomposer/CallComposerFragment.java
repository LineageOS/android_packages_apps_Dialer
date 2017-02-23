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
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;

/** Base fragment with fields and methods needed for all fragments in the call compose UI. */
public abstract class CallComposerFragment extends Fragment {

  protected static final int CAMERA_PERMISSION = 1;
  protected static final int STORAGE_PERMISSION = 2;

  private static final String LOCATION_KEY = "location_key";
  public static final int CONTENT_TOP_UNSET = Integer.MAX_VALUE;

  private View topView;
  private int contentTopPx = CONTENT_TOP_UNSET;
  private CallComposerListener testListener;

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
    View view = super.onCreateView(layoutInflater, viewGroup, bundle);
    Assert.isNotNull(topView);
    return view;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if (!(context instanceof CallComposerListener) && testListener == null) {
      LogUtil.e(
          "CallComposerFragment.onAttach",
          "Container activity must implement CallComposerListener.");
      Assert.fail();
    }
  }

  /** Call this method to declare which view is located at the top of the fragment's layout. */
  public void setTopView(View view) {
    topView = view;
    // For each fragment that extends CallComposerFragment, the heights may vary and since
    // ViewPagers cannot have their height set to wrap_content, we have to adjust the top of our
    // container to match the top of the fragment. This listener populates {@code contentTopPx} as
    // it's available.
    topView
        .getViewTreeObserver()
        .addOnGlobalLayoutListener(
            new OnGlobalLayoutListener() {
              @Override
              public void onGlobalLayout() {
                topView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                contentTopPx = topView.getTop();
              }
            });
  }

  public int getContentTopPx() {
    return contentTopPx;
  }

  public void setParentForTesting(CallComposerListener listener) {
    testListener = listener;
  }

  public CallComposerListener getListener() {
    if (testListener != null) {
      return testListener;
    }
    return FragmentUtils.getParentUnsafe(this, CallComposerListener.class);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(LOCATION_KEY, contentTopPx);
  }

  @Override
  public void onViewStateRestored(Bundle savedInstanceState) {
    super.onViewStateRestored(savedInstanceState);
    if (savedInstanceState != null) {
      contentTopPx = savedInstanceState.getInt(LOCATION_KEY);
    }
  }

  public abstract boolean shouldHide();

  /** Interface used to listen to CallComposeFragments */
  public interface CallComposerListener {
    /** Let the listener know when a call is ready to be composed. */
    void composeCall(CallComposerFragment fragment);

    /** Let the listener know when the layout has changed to full screen */
    void showFullscreen(boolean show);

    /** True is the listener is in fullscreen. */
    boolean isFullscreen();
  }
}
