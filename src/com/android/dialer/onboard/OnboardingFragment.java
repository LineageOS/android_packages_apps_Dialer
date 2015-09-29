/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.dialer.onboard;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.dialer.R;

public class OnboardingFragment extends Fragment implements OnClickListener {
    public static final String ARG_SCREEN_ID = "arg_screen_id";
    public static final String ARG_CAN_SKIP_SCREEN = "arg_can_skip_screen";
    public static final String ARG_BACKGROUND_COLOR_RESOURCE = "arg_background_color";
    public static final String ARG_TEXT_TITLE_RESOURCE = "arg_text_title_resource";
    public static final String ARG_TEXT_CONTENT_RESOURCE = "arg_text_content_resource";

    private int mScreenId;

    public interface HostInterface {
        public void onNextClicked(int screenId);
        public void onSkipClicked(int screenId);
    }

    public OnboardingFragment() {}

    public OnboardingFragment(int screenId, boolean canSkipScreen, int backgroundColorResourceId,
            int textTitleResourceId, int textContentResourceId) {
        final Bundle args = new Bundle();
        args.putInt(ARG_SCREEN_ID, screenId);
        args.putBoolean(ARG_CAN_SKIP_SCREEN, canSkipScreen);
        args.putInt(ARG_BACKGROUND_COLOR_RESOURCE, backgroundColorResourceId);
        args.putInt(ARG_TEXT_TITLE_RESOURCE, textTitleResourceId);
        args.putInt(ARG_TEXT_CONTENT_RESOURCE, textContentResourceId);
        setArguments(args);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mScreenId = getArguments().getInt(ARG_SCREEN_ID);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.onboarding_screen_fragment, container, false);
        view.setBackgroundColor(getResources().getColor(
                getArguments().getInt(ARG_BACKGROUND_COLOR_RESOURCE), null));
        ((TextView) view.findViewById(R.id.onboarding_screen_content)).
                setText(getArguments().getInt(ARG_TEXT_CONTENT_RESOURCE));
        ((TextView) view.findViewById(R.id.onboarding_screen_title)).
        setText(getArguments().getInt(ARG_TEXT_TITLE_RESOURCE));
        if (!getArguments().getBoolean(ARG_CAN_SKIP_SCREEN)) {
            view.findViewById(R.id.onboard_skip_button).setVisibility(View.INVISIBLE);
        }

        view.findViewById(R.id.onboard_skip_button).setOnClickListener(this);
        view.findViewById(R.id.onboard_next_button).setOnClickListener(this);

        return view;
    }

    int getScreenId() {
        return mScreenId;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.onboard_skip_button) {
            ((HostInterface) getActivity()).onSkipClicked(getScreenId());
        } else if (v.getId() == R.id.onboard_next_button) {
            ((HostInterface) getActivity()).onNextClicked(getScreenId());
        }
    }
}
