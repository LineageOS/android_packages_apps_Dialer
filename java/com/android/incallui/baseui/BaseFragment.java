/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.incallui.baseui;

import android.os.Bundle;
import android.support.v4.app.Fragment;

/** Parent for all fragments that use Presenters and Ui design. */
public abstract class BaseFragment<T extends Presenter<U>, U extends Ui> extends Fragment {

  private static final String KEY_FRAGMENT_HIDDEN = "key_fragment_hidden";

  private T mPresenter;

  protected BaseFragment() {
    mPresenter = createPresenter();
  }

  public abstract T createPresenter();

  public abstract U getUi();

  /**
   * Presenter will be available after onActivityCreated().
   *
   * @return The presenter associated with this fragment.
   */
  public T getPresenter() {
    return mPresenter;
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    mPresenter.onUiReady(getUi());
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (savedInstanceState != null) {
      mPresenter.onRestoreInstanceState(savedInstanceState);
      if (savedInstanceState.getBoolean(KEY_FRAGMENT_HIDDEN)) {
        getFragmentManager().beginTransaction().hide(this).commit();
      }
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    mPresenter.onUiDestroy(getUi());
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mPresenter.onSaveInstanceState(outState);
    outState.putBoolean(KEY_FRAGMENT_HIDDEN, isHidden());
  }
}
