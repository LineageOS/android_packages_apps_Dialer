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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.TextView;

/**
 * Fragment for call card.
 */
public class CallCardFragment extends BaseFragment<CallCardPresenter>
        implements CallCardPresenter.CallCardUi {

    private TextView mPhoneNumber;

    private ViewStub mSecondaryCallInfo;
    private TextView mSecondaryCallName;

    @Override
    CallCardPresenter createPresenter() {
        return new CallCardPresenter();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.call_card, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mPhoneNumber = (TextView) view.findViewById(R.id.phoneNumber);
        mSecondaryCallInfo = (ViewStub) view.findViewById(R.id.secondary_call_info);

        // This method call will begin the callbacks on CallCardUi. We need to ensure
        // everything needed for the callbacks is set up before this is called.
        getPresenter().onUiReady(this);
    }

    @Override
    public void setSecondaryCallInfo(boolean show, String number) {
        if (show) {
            showAndInitializeSecondaryCallInfo();

            // Until we have the name source, use the number as the main text for secondary calls.
            mSecondaryCallName.setText(number);
        } else {
            mSecondaryCallInfo.setVisibility(View.GONE);
        }
    }

    @Override
    public void setNumber(String number)  {
        mPhoneNumber.setText(number);
    }

    @Override
    public void setName(String name) {
    }

    private void showAndInitializeSecondaryCallInfo() {
        mSecondaryCallInfo.setVisibility(View.VISIBLE);

        // mSecondaryCallName is initialized here (vs. onViewCreated) because it is inaccesible
        // until mSecondaryCallInfo is inflated in the call above.
        if (mSecondaryCallName == null) {
            mSecondaryCallName = (TextView) getView().findViewById(R.id.secondaryCallName);
        }
    }

}
