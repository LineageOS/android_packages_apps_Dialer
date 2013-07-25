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

import com.google.android.collect.Lists;

import java.util.ArrayList;

/**
 *
 */
public class AnswerPresenter extends Presenter<Ui> {

    private ArrayList<Listener> mListeners = Lists.newArrayList();

    public AnswerPresenter(Listener listener) {
        this.mListeners.add(listener);
    }

    public void addCloseListener(Listener listener) {
        mListeners.add(listener);
    }

    public void onAnswer() {
        // TODO(klp): hook in call id.
        CallCommandClient.getInstance().answerCall(1);
        notifyListeners();
    }

    public void onDecline() {
        notifyListeners();
    }

    public void onText() {
        notifyListeners();
    }

    private void notifyListeners() {
        for (Listener listener : mListeners) {
            listener.onClose();
        }
    }

    public interface Listener {
        void onClose();
    }
}
