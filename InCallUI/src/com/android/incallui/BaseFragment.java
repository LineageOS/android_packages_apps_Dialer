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

package com.android.incallui;

import android.app.Fragment;
import android.os.Bundle;
import android.view.View;

import com.android.internal.util.Preconditions;

/**
 *
 */
public abstract class BaseFragment<T extends Presenter<U>, U extends Ui> extends Fragment {

    private T mPresenter;

    abstract T createPresenter();

    abstract U getUi();

    protected BaseFragment() {
        mPresenter = createPresenter();
    }

    /**
     * Presenter will be available after onActivityCreated().
     *
     * @return The presenter associated with this fragment.
     */
    public T getPresenter() {
        return mPresenter;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPresenter.onUiReady(getUi());
    }
}
