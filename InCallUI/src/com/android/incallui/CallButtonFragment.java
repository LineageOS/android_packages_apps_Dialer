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

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

/**
 * Fragment for call control buttons
 */
public class CallButtonFragment extends BaseFragment<CallButtonPresenter>
        implements CallButtonPresenter.CallButtonUi {

    private ToggleButton mMuteButton;
    private ToggleButton mAudioButton;
    private ToggleButton mHoldButton;
    private View mEndCallButton;

    @Override
    CallButtonPresenter createPresenter() {
        return new CallButtonPresenter();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final AudioManager audioManager = (AudioManager) getActivity().getSystemService(
                Context.AUDIO_SERVICE);
        getPresenter().init(audioManager);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View parent = inflater.inflate(R.layout.call_button_fragment, container, false);

        mEndCallButton = parent.findViewById(R.id.endButton);
        mEndCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().endCallClicked();
            }
        });

        mMuteButton = (ToggleButton) parent.findViewById(R.id.muteButton);
        mMuteButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                getPresenter().muteClicked(isChecked);
            }
        });

        mAudioButton = (ToggleButton) parent.findViewById(R.id.audioButton);
        mAudioButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                getPresenter().speakerClicked(isChecked);
            }
        });

        mHoldButton = (ToggleButton) parent.findViewById(R.id.holdButton);
        mHoldButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                getPresenter().holdClicked(isChecked);
            }
        });

        return parent;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        getPresenter().onUiReady(this);
    }

    @Override
    public void setVisible(boolean on) {
        if (on) {
            getView().setVisibility(View.VISIBLE);
        } else {
            getView().setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void setMute(boolean value) {
        mMuteButton.setChecked(value);
    }

    /**
     * TODO(klp): Rename this from setSpeaker() to setAudio() once it does more than speakerphone.
     */
    @Override
    public void setSpeaker(boolean value) {
        mAudioButton.setChecked(value);
    }

    @Override
    public void setHold(boolean value) {
        mHoldButton.setChecked(value);
    }
}
