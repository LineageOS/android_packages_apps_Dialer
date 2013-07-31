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
    final private IFragmentHost mFragmentHost;

    public AnswerFragment(IFragmentHost fragmentHost) {
        mFragmentHost = fragmentHost;

        // Normally called with onCreateView, however, AnswerPresenter's interaction
        // begins before any UI is displayed.
        // Order matters with this call because mFragmentHost mustn't be null when the presenter
        // asks AnswerFragment to display itself (see showAnswerWidget).
        getPresenter().onUiReady(this);
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
        return glowPad;
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

    @Override
    public void showAnswerWidget(boolean show) {
        Preconditions.checkNotNull(mFragmentHost);

        if (show) {
            mFragmentHost.addFragment(this);
        } else {
            close();
        }
    }

    private void close() {
        // TODO(klp): With a proper main presenter, we will not need to check for isAdded.
        if (isAdded()) {
            final FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
            fragmentTransaction.remove(this);
            fragmentTransaction.commit();
        }
    }

    // Stop gap until we can consolidate presenters and make InCallActivity a UI Class that relies
    // on it's the consolidated presenter.
    //
    // TODO(klp): Remove individual presenters for button/answer/callcard and have a single
    // presenter that interacts directly with this activity.  This will allow us to remove
    // this unnecessary interface.
    static interface IFragmentHost {
        public void addFragment(Fragment fragment);
    }
}
