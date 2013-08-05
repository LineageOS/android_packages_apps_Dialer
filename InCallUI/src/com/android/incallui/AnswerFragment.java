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

import com.google.common.base.Preconditions;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 *
 */
public class AnswerFragment extends BaseFragment<AnswerPresenter> implements
        GlowPadWrapper.AnswerListener, AnswerPresenter.AnswerUi {

    public AnswerFragment() {
    }

    @Override
    public AnswerPresenter createPresenter() {
        return new AnswerPresenter();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final GlowPadWrapper glowPad = (GlowPadWrapper) inflater.inflate(R.layout.answer_fragment,
                container, false);

        glowPad.setAnswerListener(this);
        getPresenter().onUiReady(this);

        return glowPad;
    }

    @Override
    public void showAnswerUi(boolean show) {
        getView().setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void onAnswer() {
        getPresenter().onAnswer();
    }

    @Override
    public void onDecline() {
        getPresenter().onDecline();
    }

    @Override
    public void onText() {
        getPresenter().onText();
    }
}
