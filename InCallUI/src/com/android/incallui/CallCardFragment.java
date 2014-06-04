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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.telephony.DisconnectCause;
import android.text.TextUtils;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.common.animation.AnimUtils;
import com.android.contacts.common.util.ViewUtil;

import java.util.List;

/**
 * Fragment for call card.
 */
public class CallCardFragment extends BaseFragment<CallCardPresenter, CallCardPresenter.CallCardUi>
        implements CallCardPresenter.CallCardUi {

    private static final int REVEAL_ANIMATION_DURATION = 333;
    private static final int SHRINK_ANIMATION_DURATION = 333;

    // Primary caller info
    private TextView mPhoneNumber;
    private TextView mNumberLabel;
    private TextView mPrimaryName;
    private TextView mCallStateLabel;
    private TextView mCallTypeLabel;
    private View mCallNumberAndLabel;
    private ImageView mPhoto;
    private TextView mElapsedTime;

    // Container view that houses the entire primary call card, including the call buttons
    private View mPrimaryCallCardContainer;
    // Container view that houses the primary call information
    private View mPrimaryCallInfo;
    private View mCallButtonsContainer;

    // Secondary caller info
    private View mSecondaryCallInfo;
    private TextView mSecondaryCallName;

    private View mEndCallButton;
    private ImageButton mHandoffButton;

    // Cached DisplayMetrics density.
    private float mDensity;

    private float mTranslationOffset;
    private Animation mPulseAnimation;

    @Override
    CallCardPresenter.CallCardUi getUi() {
        return this;
    }

    @Override
    CallCardPresenter createPresenter() {
        return new CallCardPresenter();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final CallList calls = CallList.getInstance();
        final Call call = calls.getFirstCall();
        getPresenter().init(getActivity(), call);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        mDensity = getResources().getDisplayMetrics().density;
        mTranslationOffset =
                getResources().getDimensionPixelSize(R.dimen.call_card_anim_translate_y_offset);

        return inflater.inflate(R.layout.call_card, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPulseAnimation =
                AnimationUtils.loadAnimation(view.getContext(), R.anim.call_status_pulse);

        mPhoneNumber = (TextView) view.findViewById(R.id.phoneNumber);
        mPrimaryName = (TextView) view.findViewById(R.id.name);
        mNumberLabel = (TextView) view.findViewById(R.id.label);
        mSecondaryCallInfo = (View) view.findViewById(R.id.secondary_call_info);
        mPhoto = (ImageView) view.findViewById(R.id.photo);
        mCallStateLabel = (TextView) view.findViewById(R.id.callStateLabel);
        mCallNumberAndLabel = view.findViewById(R.id.labelAndNumber);
        mCallTypeLabel = (TextView) view.findViewById(R.id.callTypeLabel);
        mElapsedTime = (TextView) view.findViewById(R.id.elapsedTime);
        mPrimaryCallCardContainer = view.findViewById(R.id.primary_call_info_container);
        mPrimaryCallInfo = view.findViewById(R.id.primary_call_banner);
        mCallButtonsContainer = view.findViewById(R.id.callButtonFragment);

        mEndCallButton = view.findViewById(R.id.endButton);
        mEndCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().endCallClicked();
            }
        });
        ViewUtil.setupFloatingActionButton(mEndCallButton, getResources());

        mHandoffButton = (ImageButton) view.findViewById(R.id.handoffButton);
        mHandoffButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                getPresenter().connectionHandoffClicked();
            }
        });
        ViewUtil.setupFloatingActionButton(mHandoffButton, getResources());

        mPrimaryName.setElegantTextHeight(false);
        mCallStateLabel.setElegantTextHeight(false);
    }

    @Override
    public void setVisible(boolean on) {
        if (on) {
            getView().setVisibility(View.VISIBLE);
        } else {
            getView().setVisibility(View.INVISIBLE);
        }
    }

    public void setShowConnectionHandoff(boolean showConnectionHandoff) {
        Log.v(this, "setShowConnectionHandoff: " + showConnectionHandoff);
    }

    @Override
    public void setPrimaryName(String name, boolean nameIsNumber) {
        if (TextUtils.isEmpty(name)) {
            mPrimaryName.setText("");
        } else {
            mPrimaryName.setText(name);

            // Set direction of the name field
            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mPrimaryName.setTextDirection(nameDirection);
        }
    }

    @Override
    public void setPrimaryImage(Drawable image) {
        if (image != null) {
            setDrawableToImageView(mPhoto, image);
        }
    }

    @Override
    public void setPrimaryPhoneNumber(String number) {
        // Set the number
        if (TextUtils.isEmpty(number)) {
            mPhoneNumber.setText("");
            mPhoneNumber.setVisibility(View.GONE);
        } else {
            mPhoneNumber.setText(number);
            mPhoneNumber.setVisibility(View.VISIBLE);
            mPhoneNumber.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
    }

    @Override
    public void setPrimaryLabel(String label) {
        if (!TextUtils.isEmpty(label)) {
            mNumberLabel.setText(label);
            mNumberLabel.setVisibility(View.VISIBLE);
        } else {
            mNumberLabel.setVisibility(View.GONE);
        }

    }

    @Override
    public void setPrimary(String number, String name, boolean nameIsNumber, String label,
            Drawable photo, boolean isConference, boolean isGeneric, boolean isSipCall) {
        Log.d(this, "Setting primary call");

        if (isConference) {
            name = getConferenceString(isGeneric);
            photo = getConferencePhoto(isGeneric);
            nameIsNumber = false;
        }

        // set the name field.
        setPrimaryName(name, nameIsNumber);

        if (TextUtils.isEmpty(number) && TextUtils.isEmpty(label)) {
            mCallNumberAndLabel.setVisibility(View.GONE);
        } else {
            mCallNumberAndLabel.setVisibility(View.VISIBLE);
        }

        setPrimaryPhoneNumber(number);

        // Set the label (Mobile, Work, etc)
        setPrimaryLabel(label);

        showInternetCallLabel(isSipCall);

        setDrawableToImageView(mPhoto, photo);
    }

    @Override
    public void setSecondary(boolean show, String name, boolean nameIsNumber, String label,
            boolean isConference, boolean isGeneric) {

        if (show) {
            if (isConference) {
                name = getConferenceString(isGeneric);
                nameIsNumber = false;
            }

            showAndInitializeSecondaryCallInfo();
            mSecondaryCallName.setText(name);

            int nameDirection = View.TEXT_DIRECTION_INHERIT;
            if (nameIsNumber) {
                nameDirection = View.TEXT_DIRECTION_LTR;
            }
            mSecondaryCallName.setTextDirection(nameDirection);
        } else {
            mSecondaryCallInfo.setVisibility(View.GONE);
        }
    }

    @Override
    public void setCallState(int state, int cause, boolean bluetoothOn, String gatewayLabel,
            String gatewayNumber, boolean isWiFi, boolean isHandoffCapable,
            boolean isHandoffPending) {
        String callStateLabel = null;

        if (Call.State.isDialing(state) && !TextUtils.isEmpty(gatewayLabel)) {
            // Provider info: (e.g. "Calling via <gatewayLabel>")
            callStateLabel = gatewayLabel;
        } else {
            callStateLabel = getCallStateLabelFromState(state, cause);
        }

        Log.v(this, "setCallState " + callStateLabel);
        Log.v(this, "DisconnectCause " + DisconnectCause.toString(cause));
        Log.v(this, "bluetooth on " + bluetoothOn);
        Log.v(this, "gateway " + gatewayLabel + gatewayNumber);
        Log.v(this, "isWiFi " + isWiFi);
        Log.v(this, "isHandoffCapable " + isHandoffCapable);
        Log.v(this, "isHandoffPending " + isHandoffPending);

        // Update the call state label.
        if (!TextUtils.isEmpty(callStateLabel)) {
            mCallStateLabel.setText(callStateLabel);
            mCallStateLabel.setVisibility(View.VISIBLE);
            if (state != Call.State.CONFERENCED) {
                mCallStateLabel.startAnimation(mPulseAnimation);
            }
        } else {
            mCallStateLabel.getAnimation().cancel();
            mCallStateLabel.setAlpha(0);
            mCallStateLabel.setVisibility(View.GONE);
        }

        if (Call.State.INCOMING == state) {
            setBluetoothOn(bluetoothOn);
        }

        mHandoffButton.setEnabled(isHandoffCapable && !isHandoffPending);
        mHandoffButton.setVisibility(mHandoffButton.isEnabled() ? View.VISIBLE : View.GONE);
        mHandoffButton.setImageResource(isWiFi ?
                R.drawable.ic_in_call_wifi : R.drawable.ic_in_call_pstn);
    }

    private void showInternetCallLabel(boolean show) {
        if (show) {
            final String label = getView().getContext().getString(
                    R.string.incall_call_type_label_sip);
            mCallTypeLabel.setVisibility(View.VISIBLE);
            mCallTypeLabel.setText(label);
        } else {
            mCallTypeLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public void setPrimaryCallElapsedTime(boolean show, String callTimeElapsed) {
        if (show) {
            if (mElapsedTime.getVisibility() != View.VISIBLE) {
                AnimUtils.fadeIn(mElapsedTime, AnimUtils.DEFAULT_DURATION);
            }
            mElapsedTime.setText(callTimeElapsed);
        } else {
            // hide() animation has no effect if it is already hidden.
            AnimUtils.fadeOut(mElapsedTime, AnimUtils.DEFAULT_DURATION);
        }
    }

    private void setDrawableToImageView(ImageView view, Drawable photo) {
        if (photo == null) {
            photo = view.getResources().getDrawable(R.drawable.picture_unknown);
        }

        final Drawable current = view.getDrawable();
        if (current == null) {
            view.setImageDrawable(photo);
            AnimUtils.fadeIn(mElapsedTime, AnimUtils.DEFAULT_DURATION);
        } else {
            InCallAnimationUtils.startCrossFade(view, current, photo);
            view.setVisibility(View.VISIBLE);
        }
    }

    private String getConferenceString(boolean isGeneric) {
        Log.v(this, "isGenericString: " + isGeneric);
        final int resId = isGeneric ? R.string.card_title_in_call : R.string.card_title_conf_call;
        return getView().getResources().getString(resId);
    }

    private Drawable getConferencePhoto(boolean isGeneric) {
        Log.v(this, "isGenericPhoto: " + isGeneric);
        final int resId = isGeneric ? R.drawable.picture_dialing : R.drawable.picture_conference;
        return getView().getResources().getDrawable(resId);
    }

    private void setBluetoothOn(boolean onOff) {
        // Also, display a special icon (alongside the "Incoming call"
        // label) if there's an incoming call and audio will be routed
        // to bluetooth when you answer it.
        final int bluetoothIconId = R.drawable.ic_in_call_bt_dk;

        if (onOff) {
            mCallStateLabel.setCompoundDrawablesWithIntrinsicBounds(bluetoothIconId, 0, 0, 0);
            mCallStateLabel.setCompoundDrawablePadding((int) (mDensity * 5));
        } else {
            // Clear out any icons
            mCallStateLabel.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    /**
     * Gets the call state label based on the state of the call and
     * cause of disconnect
     */
    private String getCallStateLabelFromState(int state, int cause) {
        final Context context = getView().getContext();
        String callStateLabel = null;  // Label to display as part of the call banner

        if (Call.State.IDLE == state) {
            // "Call state" is meaningless in this state.

        } else if (Call.State.ACTIVE == state) {
            // We normally don't show a "call state label" at all in
            // this state (but see below for some special cases).

        } else if (Call.State.ONHOLD == state) {
            callStateLabel = context.getString(R.string.card_title_on_hold);
        } else if (Call.State.DIALING == state) {
            callStateLabel = context.getString(R.string.card_title_dialing);
        } else if (Call.State.REDIALING == state) {
            callStateLabel = context.getString(R.string.card_title_redialing);
        } else if (Call.State.INCOMING == state || Call.State.CALL_WAITING == state) {
            callStateLabel = context.getString(R.string.card_title_incoming_call);

        } else if (Call.State.DISCONNECTING == state) {
            // While in the DISCONNECTING state we display a "Hanging up"
            // message in order to make the UI feel more responsive.  (In
            // GSM it's normal to see a delay of a couple of seconds while
            // negotiating the disconnect with the network, so the "Hanging
            // up" state at least lets the user know that we're doing
            // something.  This state is currently not used with CDMA.)
            callStateLabel = context.getString(R.string.card_title_hanging_up);

        } else if (Call.State.DISCONNECTED == state) {
            callStateLabel = getCallFailedString(cause);

        } else {
            Log.wtf(this, "updateCallStateWidgets: unexpected call: " + state);
        }

        return callStateLabel;
    }

    /**
     * Maps the disconnect cause to a resource string.
     *
     * @param cause disconnect cause as defined in {@link DisconnectCause}
     */
    private String getCallFailedString(int cause) {
        int resID = R.string.card_title_call_ended;

        // TODO: The card *title* should probably be "Call ended" in all
        // cases, but if the DisconnectCause was an error condition we should
        // probably also display the specific failure reason somewhere...

        switch (cause) {
            case DisconnectCause.BUSY:
                resID = R.string.callFailed_userBusy;
                break;

            case DisconnectCause.CONGESTION:
                resID = R.string.callFailed_congestion;
                break;

            case DisconnectCause.TIMED_OUT:
                resID = R.string.callFailed_timedOut;
                break;

            case DisconnectCause.SERVER_UNREACHABLE:
                resID = R.string.callFailed_server_unreachable;
                break;

            case DisconnectCause.NUMBER_UNREACHABLE:
                resID = R.string.callFailed_number_unreachable;
                break;

            case DisconnectCause.INVALID_CREDENTIALS:
                resID = R.string.callFailed_invalid_credentials;
                break;

            case DisconnectCause.SERVER_ERROR:
                resID = R.string.callFailed_server_error;
                break;

            case DisconnectCause.OUT_OF_NETWORK:
                resID = R.string.callFailed_out_of_network;
                break;

            case DisconnectCause.LOST_SIGNAL:
            case DisconnectCause.CDMA_DROP:
                resID = R.string.callFailed_noSignal;
                break;

            case DisconnectCause.LIMIT_EXCEEDED:
                resID = R.string.callFailed_limitExceeded;
                break;

            case DisconnectCause.POWER_OFF:
                resID = R.string.callFailed_powerOff;
                break;

            case DisconnectCause.ICC_ERROR:
                resID = R.string.callFailed_simError;
                break;

            case DisconnectCause.OUT_OF_SERVICE:
                resID = R.string.callFailed_outOfService;
                break;

            case DisconnectCause.INVALID_NUMBER:
            case DisconnectCause.UNOBTAINABLE_NUMBER:
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
        mSecondaryCallInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getPresenter().secondaryInfoClicked();
            }
        });
    }

    public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            dispatchPopulateAccessibilityEvent(event, mPrimaryName);
            dispatchPopulateAccessibilityEvent(event, mPhoneNumber);
            return;
        }
        dispatchPopulateAccessibilityEvent(event, mCallStateLabel);
        dispatchPopulateAccessibilityEvent(event, mPrimaryName);
        dispatchPopulateAccessibilityEvent(event, mPhoneNumber);
        dispatchPopulateAccessibilityEvent(event, mCallTypeLabel);
        dispatchPopulateAccessibilityEvent(event, mSecondaryCallName);

        return;
    }

    @Override
    public void setEndCallButtonEnabled(boolean enabled) {
        mEndCallButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    private void dispatchPopulateAccessibilityEvent(AccessibilityEvent event, View view) {
        if (view == null) return;
        final List<CharSequence> eventText = event.getText();
        int size = eventText.size();
        view.dispatchPopulateAccessibilityEvent(event);
        // if no text added write null to keep relative position
        if (size == eventText.size()) {
            eventText.add(null);
        }
    }

    public void animateForNewOutgoingCall() {
        final ViewGroup parent = (ViewGroup) mPrimaryCallCardContainer.getParent();

        final ViewTreeObserver observer = getView().getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                final ViewTreeObserver observer = getView().getViewTreeObserver();
                if (!observer.isAlive()) {
                    return;
                }
                observer.removeOnGlobalLayoutListener(this);

                final int originalHeight = mPrimaryCallCardContainer.getHeight();
                final LayoutIgnoringListener listener = new LayoutIgnoringListener();
                mPrimaryCallCardContainer.addOnLayoutChangeListener(listener);

                // Prepare the state of views before the circular reveal animation
                mPrimaryCallCardContainer.setBottom(parent.getHeight());
                mEndCallButton.setTranslationY(200);
                mCallButtonsContainer.setAlpha(0);
                mCallStateLabel.setAlpha(0);
                mPrimaryName.setAlpha(0);
                mCallTypeLabel.setAlpha(0);
                mCallNumberAndLabel.setAlpha(0);

                final Animator revealAnimator = getRevealAnimator();
                final Animator shrinkAnimator =
                        getShrinkAnimator(parent.getHeight(), originalHeight);

                final AnimatorSet set = new AnimatorSet();
                set.playSequentially(revealAnimator, shrinkAnimator);
                set.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        mPrimaryCallCardContainer.removeOnLayoutChangeListener(listener);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mPrimaryCallCardContainer.removeOnLayoutChangeListener(listener);
                    }
                });
                set.start();
            }
        });
    }

    /**
     * Animator that performs the upwards shrinking animation of the blue call card scrim.
     * At the start of the animation, each child view is moved downwards by a pre-specified amount
     * and then translated upwards together with the scrim.
     */
    private Animator getShrinkAnimator(int startHeight, int endHeight) {
        final Animator shrinkAnimator =
                ObjectAnimator.ofInt(mPrimaryCallCardContainer, "bottom",
                        startHeight, endHeight);
        shrinkAnimator.setDuration(SHRINK_ANIMATION_DURATION);
        shrinkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                assignTranslateAnimation(mCallStateLabel, 1);
                assignTranslateAnimation(mPrimaryName, 2);
                assignTranslateAnimation(mCallNumberAndLabel, 3);
                assignTranslateAnimation(mCallTypeLabel, 4);
                assignTranslateAnimation(mCallButtonsContainer, 5);

                mEndCallButton.animate().translationY(0)
                        .setDuration(SHRINK_ANIMATION_DURATION);
            }
        });
        shrinkAnimator.setInterpolator(AnimUtils.EASE_IN);
        return shrinkAnimator;
    }

    private Animator getRevealAnimator() {
        final Activity activity = getActivity();
        final View view  = activity.getWindow().getDecorView();
        final Display display = activity.getWindowManager().getDefaultDisplay();
        final Point size = new Point();
        display.getSize(size);

        final ValueAnimator valueAnimator = view.createRevealAnimator(size.x / 2, size.y / 2,
                0, Math.max(size.x, size.y));
        valueAnimator.setDuration(REVEAL_ANIMATION_DURATION);
        return valueAnimator;
    }

    private void assignTranslateAnimation(View view, int offset) {
        view.setTranslationY(mTranslationOffset * offset);
        view.animate().translationY(0).alpha(1).withLayer()
                .setDuration(SHRINK_ANIMATION_DURATION).setInterpolator(AnimUtils.EASE_IN);
    }

    private final class LayoutIgnoringListener implements View.OnLayoutChangeListener {
        @Override
        public void onLayoutChange(View v,
                int left,
                int top,
                int right,
                int bottom,
                int oldLeft,
                int oldTop,
                int oldRight,
                int oldBottom) {
            v.setLeft(oldLeft);
            v.setRight(oldRight);
            v.setTop(oldTop);
            v.setBottom(oldBottom);
        }
    }
}
