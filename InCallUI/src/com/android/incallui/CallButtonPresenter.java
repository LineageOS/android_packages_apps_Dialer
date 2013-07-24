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

import android.media.AudioManager;

/**
 * Logic for call buttons.
 */
public class CallButtonPresenter extends Presenter<CallButtonPresenter.CallButtonUi> {

    private AudioManager mAudioManager;

    public void init(AudioManager audioManager) {
        mAudioManager = audioManager;
    }

    @Override
    public void onUiReady(CallButtonUi ui) {
        super.onUiReady(ui);
        getUi().setMute(mAudioManager.isMicrophoneMute());
        getUi().setSpeaker(mAudioManager.isSpeakerphoneOn());
    }

    public void show() {
        getUi().setVisible();
    }

    public void muteClicked(boolean checked) {
        CallCommandClient.getInstance().mute(checked);
        getUi().setMute(checked);
    }

    public void speakerClicked(boolean checked) {
        CallCommandClient.getInstance().turnSpeakerOn(checked);
        getUi().setSpeaker(checked);
    }

    public interface CallButtonUi extends Ui {
        void setVisible();
        void setMute(boolean on);
        void setSpeaker(boolean on);
    }
}
