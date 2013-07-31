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

/**
 * Presenter for the Incoming call widget.
 */
public class AnswerPresenter extends Presenter<AnswerPresenter.AnswerUi>
        implements CallList.Listener {

    @Override
    public void onUiReady(AnswerUi ui) {
        super.onUiReady(ui);
        CallList.getInstance().addListener(this);
    }

    @Override
    public void onCallListChange(CallList callList) {
        // TODO(klp): The answer widget and call cards are independently managing their behavior
        // from CallList events. We need to create a class to manage the behavior of all the
        // Presenters from a single place.
        final boolean showWidget = (callList.getIncomingCall() != null);

        final AnswerUi ui = getUi();
        if (ui != null) {
            ui.showAnswerWidget(showWidget);
        }
    }

    public void onAnswer() {
        // TODO(klp): hook in call id.
        CallCommandClient.getInstance().answerCall(1);
    }

    public void onDecline() {
        // TODO(klp): hook in call id.
        CallCommandClient.getInstance().disconnectCall(1);
    }

    public void onText() {
    }

    interface AnswerUi extends Ui {
        public void showAnswerWidget(boolean show);
    }
}
