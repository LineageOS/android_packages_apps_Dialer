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

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.services.telephony.common.Call;

/**
 * Fragment for call card.
 */
public class CallCardFragment extends BaseFragment<CallCardPresenter>
        implements CallCardPresenter.CallCardUi {

    private TextView mPhoneNumber;
    private TextView mNumberLabel;
    private TextView mName;
    private ImageView mPhoto;
    private TextView mCallStateLabel;
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
        mName = (TextView) view.findViewById(R.id.name);
        mNumberLabel = (TextView) view.findViewById(R.id.label);
        mSecondaryCallInfo = (ViewStub) view.findViewById(R.id.secondary_call_info);
        mPhoto = (ImageView) view.findViewById(R.id.photo);
        mCallStateLabel = (TextView) view.findViewById(R.id.callStateLabel);

        // This method call will begin the callbacks on CallCardUi. We need to ensure
        // everything needed for the callbacks is set up before this is called.
        getPresenter().onUiReady(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getPresenter().onUiUnready(this);
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
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        getPresenter().setContext(activity);
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
        if (!TextUtils.isEmpty(number)) {
            mPhoneNumber.setText(number);
            mPhoneNumber.setVisibility(View.VISIBLE);
            // We have a real phone number as "mPhoneNumber" so make it always LTR
            mPhoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);
        } else {
            mPhoneNumber.setVisibility(View.GONE);
        }
    }

    @Override
    public void setName(String name, boolean isNumber) {
        mName.setText(name);
        mName.setVisibility(View.VISIBLE);
        if (isNumber) {
            mName.setTextDirection(View.TEXT_DIRECTION_LTR);
        } else {
            mName.setTextDirection(View.TEXT_DIRECTION_INHERIT);
        }
    }

    @Override
    public void setName(String name) {
        setName(name, false);
    }

    @Override
    public void setNumberLabel(String label) {
        if (!TextUtils.isEmpty(label)) {
            mNumberLabel.setText(label);
            mNumberLabel.setVisibility(View.VISIBLE);
        } else {
            mNumberLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public void setCallState(int state, Call.DisconnectCause cause) {
        String callStateLabel = null;

        // States other than disconnected not yet supported
        if (state == Call.State.DISCONNECTED) {
            callStateLabel = getCallFailedString(cause);
        }

        Logger.d(this, "setCallState ", callStateLabel);

        if (!TextUtils.isEmpty(callStateLabel)) {
            mCallStateLabel.setVisibility(View.VISIBLE);
            mCallStateLabel.setText(callStateLabel);
        } else {
            mCallStateLabel.setVisibility(View.GONE);
            // Gravity is aligned left when receiving an incoming call in landscape.
            // In that rare case, the gravity needs to be reset to the right.
            // Also, setText("") is used since there is a delay in making the view GONE,
            // so the user will otherwise see the text jump to the right side before disappearing.
            if(mCallStateLabel.getGravity() != Gravity.END) {
                mCallStateLabel.setText("");
                mCallStateLabel.setGravity(Gravity.END);
            }
        }
    }

    /**
     * Maps the disconnect cause to a resource string.
     */
    private String getCallFailedString(Call.DisconnectCause cause) {
        int resID = R.string.card_title_call_ended;

        // TODO: The card *title* should probably be "Call ended" in all
        // cases, but if the DisconnectCause was an error condition we should
        // probably also display the specific failure reason somewhere...

        switch (cause) {
            case BUSY:
                resID = R.string.callFailed_userBusy;
                break;

            case CONGESTION:
                resID = R.string.callFailed_congestion;
                break;

            case TIMED_OUT:
                resID = R.string.callFailed_timedOut;
                break;

            case SERVER_UNREACHABLE:
                resID = R.string.callFailed_server_unreachable;
                break;

            case NUMBER_UNREACHABLE:
                resID = R.string.callFailed_number_unreachable;
                break;

            case INVALID_CREDENTIALS:
                resID = R.string.callFailed_invalid_credentials;
                break;

            case SERVER_ERROR:
                resID = R.string.callFailed_server_error;
                break;

            case OUT_OF_NETWORK:
                resID = R.string.callFailed_out_of_network;
                break;

            case LOST_SIGNAL:
            case CDMA_DROP:
                resID = R.string.callFailed_noSignal;
                break;

            case LIMIT_EXCEEDED:
                resID = R.string.callFailed_limitExceeded;
                break;

            case POWER_OFF:
                resID = R.string.callFailed_powerOff;
                break;

            case ICC_ERROR:
                resID = R.string.callFailed_simError;
                break;

            case OUT_OF_SERVICE:
                resID = R.string.callFailed_outOfService;
                break;

            case INVALID_NUMBER:
            case UNOBTAINABLE_NUMBER:
                resID = R.string.callFailed_unobtainable_number;
                break;

            default:
                resID = R.string.card_title_call_ended;
                break;
        }
        return this.getView().getContext().getString(resID);
    }

    private void showAndInitializeSecondaryCallInfo() {
        mSecondaryCallInfo.setVisibility(View.VISIBLE);

        // mSecondaryCallName is initialized here (vs. onViewCreated) because it is inaccesible
        // until mSecondaryCallInfo is inflated in the call above.
        if (mSecondaryCallName == null) {
            mSecondaryCallName = (TextView) getView().findViewById(R.id.secondaryCallName);
        }
    }

    @Override
    public void setImage(int resource) {
        setImage(getActivity().getResources().getDrawable(resource));
    }

    @Override
    public void setImage(Drawable drawable) {
        setDrawableToImageView(mPhoto, drawable);
    }

    @Override
    public void setImage(Bitmap bitmap) {
        setImage(new BitmapDrawable(getActivity().getResources(), bitmap));
    }

    private void setDrawableToImageView(ImageView view, Drawable drawable) {
        final Drawable current = view.getDrawable();
        if (current == null) {
            view.setImageDrawable(drawable);
            AnimationUtils.Fade.show(view);
        } else {
            AnimationUtils.startCrossFade(view, current, drawable);
            mPhoto.setVisibility(View.VISIBLE);
        }
    }
}
