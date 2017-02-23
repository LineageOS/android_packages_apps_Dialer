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

package com.android.dialer.common;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;

/** Utility methods for working with Fragments */
public class FragmentUtils {

  private static Object parentForTesting;

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public static void setParentForTesting(Object parentForTesting) {
    FragmentUtils.parentForTesting = parentForTesting;
  }

  /**
   * @return The parent of frag that implements the callbackInterface or null if no such parent can
   *     be found
   */
  @CheckResult(suggest = "#checkParent(Fragment, Class)}")
  @Nullable
  public static <T> T getParent(@NonNull Fragment fragment, @NonNull Class<T> callbackInterface) {
    if (callbackInterface.isInstance(parentForTesting)) {
      @SuppressWarnings("unchecked") // Casts are checked using runtime methods
      T parent = (T) parentForTesting;
      return parent;
    }

    Fragment parentFragment = fragment.getParentFragment();
    if (callbackInterface.isInstance(parentFragment)) {
      @SuppressWarnings("unchecked") // Casts are checked using runtime methods
      T parent = (T) parentFragment;
      return parent;
    } else {
      FragmentActivity activity = fragment.getActivity();
      if (callbackInterface.isInstance(activity)) {
        @SuppressWarnings("unchecked") // Casts are checked using runtime methods
        T parent = (T) activity;
        return parent;
      }
    }
    return null;
  }

  /** Returns the parent or throws. Should perform check elsewhere(e.g. onAttach, newInstance). */
  @NonNull
  public static <T> T getParentUnsafe(
      @NonNull Fragment fragment, @NonNull Class<T> callbackInterface) {
    return Assert.isNotNull(getParent(fragment, callbackInterface));
  }

  /**
   * Ensures fragment has a parent that implements the corresponding interface
   *
   * @param frag The Fragment whose parents are to be checked
   * @param callbackInterface The interface class that a parent should implement
   * @throws IllegalStateException if no parents are found that implement callbackInterface
   */
  public static void checkParent(@NonNull Fragment frag, @NonNull Class<?> callbackInterface)
      throws IllegalStateException {
    if (parentForTesting != null) {
      return;
    }
    if (FragmentUtils.getParent(frag, callbackInterface) == null) {
      String parent =
          frag.getParentFragment() == null
              ? frag.getActivity().getClass().getName()
              : frag.getParentFragment().getClass().getName();
      throw new IllegalStateException(
          frag.getClass().getName()
              + " must be added to a parent"
              + " that implements "
              + callbackInterface.getName()
              + ". Instead found "
              + parent);
    }
  }
}
