/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.incallui;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Trace;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.android.contacts.common.activity.TransactionSafeActivity;
import com.android.contacts.common.compat.CompatUtils;
import com.android.contacts.common.interactions.TouchPointManager;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment.SelectPhoneAccountListener;
import com.android.dialer.R;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.incallui.Call.State;
import com.android.incallui.util.AccessibilityUtil;
import com.android.phone.common.animation.AnimUtils;
import com.android.phone.common.animation.AnimationListenerAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Main activity that the user interacts with while in a live call.
 */
public class InCallActivity extends TransactionSafeActivity implements FragmentDisplayManager {

    public static final String TAG = InCallActivity.class.getSimpleName();

    public static final String SHOW_DIALPAD_EXTRA = "InCallActivity.show_dialpad";
    public static final String DIALPAD_TEXT_EXTRA = "InCallActivity.dialpad_text";
    public static final String NEW_OUTGOING_CALL_EXTRA = "InCallActivity.new_outgoing_call";

    private static final String TAG_DIALPAD_FRAGMENT = "tag_dialpad_fragment";
    private static final String TAG_CONFERENCE_FRAGMENT = "tag_conference_manager_fragment";
    private static final String TAG_CALLCARD_FRAGMENT = "tag_callcard_fragment";
    private static final String TAG_ANSWER_FRAGMENT = "tag_answer_fragment";
    private static final String TAG_SELECT_ACCT_FRAGMENT = "tag_select_acct_fragment";

    private static final int DIALPAD_REQUEST_NONE = 1;
    private static final int DIALPAD_REQUEST_SHOW = 2;
    private static final int DIALPAD_REQUEST_HIDE = 3;

    /**
     * This is used to relaunch the activity if resizing beyond which it needs to load different
     * layout file.
     */
    private static final int SCREEN_HEIGHT_RESIZE_THRESHOLD = 500;

    private CallButtonFragment mCallButtonFragment;
    private CallCardFragment mCallCardFragment;
    private AnswerFragment mAnswerFragment;
    private DialpadFragment mDialpadFragment;
    private ConferenceManagerFragment mConferenceManagerFragment;
    private FragmentManager mChildFragmentManager;

    private AlertDialog mDialog;
    private InCallOrientationEventListener mInCallOrientationEventListener;

    /**
     * Used to indicate whether the dialpad should be hidden or shown {@link #onResume}.
     * {@code #DIALPAD_REQUEST_SHOW} indicates that the dialpad should be shown.
     * {@code #DIALPAD_REQUEST_HIDE} indicates that the dialpad should be hidden.
     * {@code #DIALPAD_REQUEST_NONE} indicates no change should be made to dialpad visibility.
     */
    private int mShowDialpadRequest = DIALPAD_REQUEST_NONE;

    /**
     * Use to determine if the dialpad should be animated on show.
     */
    private boolean mAnimateDialpadOnShow;

    /**
     * Use to determine the DTMF Text which should be pre-populated in the dialpad.
     */
    private String mDtmfText;

    /**
     * Use to pass parameters for showing the PostCharDialog to {@link #onResume}
     */
    private boolean mShowPostCharWaitDialogOnResume;
    private String mShowPostCharWaitDialogCallId;
    private String mShowPostCharWaitDialogChars;

    private boolean mIsLandscape;
    private Animation mSlideIn;
    private Animation mSlideOut;
    private boolean mDismissKeyguard = false;

    AnimationListenerAdapter mSlideOutListener = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
            showFragment(TAG_DIALPAD_FRAGMENT, false, true);
        }
    };

    private OnTouchListener mDispatchTouchEventListener;

    private SelectPhoneAccountListener mSelectAcctListener = new SelectPhoneAccountListener() {
        @Override
        public void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle,
                boolean setDefault) {
            InCallPresenter.getInstance().handleAccountSelection(selectedAccountHandle,
                    setDefault);
        }

        @Override
        public void onDialogDismissed() {
            InCallPresenter.getInstance().cancelAccountSelection();
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        Log.d(this, "onCreate()...  this = " + this);

        super.onCreate(icicle);

        // set this flag so this activity will stay in front of the keyguard
        // Have the WindowManager filter out touch events that are "too fat".
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES;

        getWindow().addFlags(flags);

        // Setup action bar for the conference call manager.
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.hide();
        }

        // TODO(klp): Do we need to add this back when prox sensor is not available?
        // lp.inputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_DISABLE_USER_ACTIVITY;

        setContentView(R.layout.incall_screen);

        internalResolveIntent(getIntent());

        mIsLandscape = getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE;

        final boolean isRtl = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) ==
                View.LAYOUT_DIRECTION_RTL;

        if (mIsLandscape) {
            mSlideIn = AnimationUtils.loadAnimation(this,
                    isRtl ? R.anim.dialpad_slide_in_left : R.anim.dialpad_slide_in_right);
            mSlideOut = AnimationUtils.loadAnimation(this,
                    isRtl ? R.anim.dialpad_slide_out_left : R.anim.dialpad_slide_out_right);
        } else {
            mSlideIn = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_in_bottom);
            mSlideOut = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_out_bottom);
        }

        mSlideIn.setInterpolator(AnimUtils.EASE_IN);
        mSlideOut.setInterpolator(AnimUtils.EASE_OUT);

        mSlideOut.setAnimationListener(mSlideOutListener);

        // If the dialpad fragment already exists, retrieve it.  This is important when rotating as
        // we will not be able to hide or show the dialpad after the rotation otherwise.
        Fragment existingFragment =
                getFragmentManager().findFragmentByTag(DialpadFragment.class.getName());
        if (existingFragment != null) {
            mDialpadFragment = (DialpadFragment) existingFragment;
        }

        if (icicle != null) {
            // If the dialpad was shown before, set variables indicating it should be shown and
            // populated with the previous DTMF text.  The dialpad is actually shown and populated
            // in onResume() to ensure the hosting CallCardFragment has been inflated and is ready
            // to receive it.
            if (icicle.containsKey(SHOW_DIALPAD_EXTRA)) {
                boolean showDialpad = icicle.getBoolean(SHOW_DIALPAD_EXTRA);
                mShowDialpadRequest = showDialpad ? DIALPAD_REQUEST_SHOW : DIALPAD_REQUEST_HIDE;
                mAnimateDialpadOnShow = false;
            }
            mDtmfText = icicle.getString(DIALPAD_TEXT_EXTRA);

            SelectPhoneAccountDialogFragment dialogFragment = (SelectPhoneAccountDialogFragment)
                    getFragmentManager().findFragmentByTag(TAG_SELECT_ACCT_FRAGMENT);
            if (dialogFragment != null) {
                dialogFragment.setListener(mSelectAcctListener);
            }
        }
        mInCallOrientationEventListener = new InCallOrientationEventListener(this);

        Log.d(this, "onCreate(): exit");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        // TODO: The dialpad fragment should handle this as part of its own state
        out.putBoolean(SHOW_DIALPAD_EXTRA,
                mCallButtonFragment != null && mCallButtonFragment.isDialpadVisible());
        if (mDialpadFragment != null) {
            out.putString(DIALPAD_TEXT_EXTRA, mDialpadFragment.getDtmfText());
        }
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onStart() {
        Log.d(this, "onStart()...");
        super.onStart();

        // setting activity should be last thing in setup process
        InCallPresenter.getInstance().setActivity(this);
        enableInCallOrientationEventListener(getRequestedOrientation() ==
                InCallOrientationEventListener.FULL_SENSOR_SCREEN_ORIENTATION);

        InCallPresenter.getInstance().onActivityStarted();
    }

    @Override
    protected void onResume() {
        Log.i(this, "onResume()...");
        super.onResume();

        InCallPresenter.getInstance().setThemeColors();
        InCallPresenter.getInstance().onUiShowing(true);

        // Clear fullscreen state onResume; the stored value may not match reality.
        InCallPresenter.getInstance().clearFullscreen();

        // If there is a pending request to show or hide the dialpad, handle that now.
        if (mShowDialpadRequest != DIALPAD_REQUEST_NONE) {
            if (mShowDialpadRequest == DIALPAD_REQUEST_SHOW) {
                // Exit fullscreen so that the user has access to the dialpad hide/show button and
                // can hide the dialpad.  Important when showing the dialpad from within dialer.
                InCallPresenter.getInstance().setFullScreen(false, true /* force */);

                mCallButtonFragment.displayDialpad(true /* show */,
                        mAnimateDialpadOnShow /* animate */);
                mAnimateDialpadOnShow = false;

                if (mDialpadFragment != null) {
                    mDialpadFragment.setDtmfText(mDtmfText);
                    mDtmfText = null;
                }
            } else {
                Log.v(this, "onResume : force hide dialpad");
                if (mDialpadFragment != null) {
                    mCallButtonFragment.displayDialpad(false /* show */, false /* animate */);
                }
            }
            mShowDialpadRequest = DIALPAD_REQUEST_NONE;
        }

        if (mShowPostCharWaitDialogOnResume) {
            showPostCharWaitDialog(mShowPostCharWaitDialogCallId, mShowPostCharWaitDialogChars);
        }
    }

    // onPause is guaranteed to be called when the InCallActivity goes
    // in the background.
    @Override
    protected void onPause() {
        Log.d(this, "onPause()...");
        if (mDialpadFragment != null) {
            mDialpadFragment.onDialerKeyUp(null);
        }

        InCallPresenter.getInstance().onUiShowing(false);
        if (isFinishing()) {
            InCallPresenter.getInstance().unsetActivity(this);
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(this, "onStop()...");
        enableInCallOrientationEventListener(false);
        InCallPresenter.getInstance().updateIsChangingConfigurations();
        InCallPresenter.getInstance().onActivityStopped();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(this, "onDestroy()...  this = " + this);
        InCallPresenter.getInstance().unsetActivity(this);
        InCallPresenter.getInstance().updateIsChangingConfigurations();
        super.onDestroy();
    }

    /**
     * When fragments have a parent fragment, onAttachFragment is not called on the parent
     * activity. To fix this, register our own callback instead that is always called for
     * all fragments.
     *
     * @see {@link BaseFragment#onAttach(Activity)}
     */
    @Override
    public void onFragmentAttached(Fragment fragment) {
        if (fragment instanceof DialpadFragment) {
            mDialpadFragment = (DialpadFragment) fragment;
        } else if (fragment instanceof AnswerFragment) {
            mAnswerFragment = (AnswerFragment) fragment;
        } else if (fragment instanceof CallCardFragment) {
            mCallCardFragment = (CallCardFragment) fragment;
            mChildFragmentManager = mCallCardFragment.getChildFragmentManager();
        } else if (fragment instanceof ConferenceManagerFragment) {
            mConferenceManagerFragment = (ConferenceManagerFragment) fragment;
        } else if (fragment instanceof CallButtonFragment) {
            mCallButtonFragment = (CallButtonFragment) fragment;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Configuration oldConfig = getResources().getConfiguration();
        Log.v(this, String.format(
                "incallui config changed, screen size: w%ddp x h%ddp old:w%ddp x h%ddp",
                newConfig.screenWidthDp, newConfig.screenHeightDp,
                oldConfig.screenWidthDp, oldConfig.screenHeightDp));
        // Recreate this activity if height is changing beyond the threshold to load different
        // layout file.
        if (oldConfig.screenHeightDp < SCREEN_HEIGHT_RESIZE_THRESHOLD &&
                newConfig.screenHeightDp > SCREEN_HEIGHT_RESIZE_THRESHOLD ||
                oldConfig.screenHeightDp > SCREEN_HEIGHT_RESIZE_THRESHOLD &&
                        newConfig.screenHeightDp < SCREEN_HEIGHT_RESIZE_THRESHOLD) {
            Log.i(this, String.format(
                    "Recreate activity due to resize beyond threshold: %d dp",
                    SCREEN_HEIGHT_RESIZE_THRESHOLD));
            recreate();
        }
    }

    /**
     * Returns true when the Activity is currently visible.
     */
    /* package */ boolean isVisible() {
        return isSafeToCommitTransactions();
    }

    private boolean hasPendingDialogs() {
        return mDialog != null || (mAnswerFragment != null && mAnswerFragment.hasPendingDialogs());
    }

    @Override
    public void finish() {
        Log.i(this, "finish().  Dialog showing: " + (mDialog != null));

        // skip finish if we are still showing a dialog.
        if (!hasPendingDialogs()) {
            super.finish();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(this, "onNewIntent: intent = " + intent);

        // We're being re-launched with a new Intent.  Since it's possible for a
        // single InCallActivity instance to persist indefinitely (even if we
        // finish() ourselves), this sequence can potentially happen any time
        // the InCallActivity needs to be displayed.

        // Stash away the new intent so that we can get it in the future
        // by calling getIntent().  (Otherwise getIntent() will return the
        // original Intent from when we first got created!)
        setIntent(intent);

        // Activities are always paused before receiving a new intent, so
        // we can count on our onResume() method being called next.

        // Just like in onCreate(), handle the intent.
        internalResolveIntent(intent);
    }

    @Override
    public void onBackPressed() {
        Log.i(this, "onBackPressed");

        // BACK is also used to exit out of any "special modes" of the
        // in-call UI:
        if (!isVisible()) {
            return;
        }

        if ((mConferenceManagerFragment == null || !mConferenceManagerFragment.isVisible())
                && (mCallCardFragment == null || !mCallCardFragment.isVisible())) {
            return;
        }

        if (mDialpadFragment != null && mDialpadFragment.isVisible()) {
            mCallButtonFragment.displayDialpad(false /* show */, true /* animate */);
            return;
        } else if (mConferenceManagerFragment != null && mConferenceManagerFragment.isVisible()) {
            showConferenceFragment(false);
            return;
        }

        // Always disable the Back key while an incoming call is ringing
        final Call call = CallList.getInstance().getIncomingCall();
        if (call != null) {
            Log.i(this, "Consume Back press for an incoming call");
            return;
        }

        // Nothing special to do.  Fall back to the default behavior.
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // push input to the dialer.
        if (mDialpadFragment != null && (mDialpadFragment.isVisible()) &&
                (mDialpadFragment.onDialerKeyUp(event))) {
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_CALL) {
            // Always consume CALL to be sure the PhoneWindow won't do anything with it
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mDispatchTouchEventListener != null) {
            boolean handled = mDispatchTouchEventListener.onTouch(null, ev);
            if (handled) {
                return true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL:
                boolean handled = InCallPresenter.getInstance().handleCallKey();
                if (!handled) {
                    Log.w(this, "InCallActivity should always handle KEYCODE_CALL in onKeyDown");
                }
                // Always consume CALL to be sure the PhoneWindow won't do anything with it
                return true;

            // Note there's no KeyEvent.KEYCODE_ENDCALL case here.
            // The standard system-wide handling of the ENDCALL key
            // (see PhoneWindowManager's handling of KEYCODE_ENDCALL)
            // already implements exactly what the UI spec wants,
            // namely (1) "hang up" if there's a current active call,
            // or (2) "don't answer" if there's a current ringing call.

            case KeyEvent.KEYCODE_CAMERA:
                // Disable the CAMERA button while in-call since it's too
                // easy to press accidentally.
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                // Ringer silencing handled by PhoneWindowManager.
                break;

            case KeyEvent.KEYCODE_MUTE:
                // toggle mute
                TelecomAdapter.getInstance().mute(!AudioModeProvider.getInstance().getMute());
                return true;

            // Various testing/debugging features, enabled ONLY when VERBOSE == true.
            case KeyEvent.KEYCODE_SLASH:
                if (Log.VERBOSE) {
                    Log.v(this, "----------- InCallActivity View dump --------------");
                    // Dump starting from the top-level view of the entire activity:
                    Window w = this.getWindow();
                    View decorView = w.getDecorView();
                    Log.d(this, "View dump:" + decorView);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_EQUALS:
                // TODO: Dump phone state?
                break;
        }

        if (event.getRepeatCount() == 0 && handleDialerKeyDown(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean handleDialerKeyDown(int keyCode, KeyEvent event) {
        Log.v(this, "handleDialerKeyDown: keyCode " + keyCode + ", event " + event + "...");

        // As soon as the user starts typing valid dialable keys on the
        // keyboard (presumably to type DTMF tones) we start passing the
        // key events to the DTMFDialer's onDialerKeyDown.
        if (mDialpadFragment != null && mDialpadFragment.isVisible()) {
            return mDialpadFragment.onDialerKeyDown(event);
        }

        return false;
    }

    public CallButtonFragment getCallButtonFragment() {
        return mCallButtonFragment;
    }

    public CallCardFragment getCallCardFragment() {
        return mCallCardFragment;
    }

    public AnswerFragment getAnswerFragment() {
        return mAnswerFragment;
    }

    private void internalResolveIntent(Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_MAIN)) {
            // This action is the normal way to bring up the in-call UI.
            //
            // But we do check here for one extra that can come along with the
            // ACTION_MAIN intent:

            if (intent.hasExtra(SHOW_DIALPAD_EXTRA)) {
                // SHOW_DIALPAD_EXTRA can be used here to specify whether the DTMF
                // dialpad should be initially visible.  If the extra isn't
                // present at all, we just leave the dialpad in its previous state.

                final boolean showDialpad = intent.getBooleanExtra(SHOW_DIALPAD_EXTRA, false);
                Log.d(this, "- internalResolveIntent: SHOW_DIALPAD_EXTRA: " + showDialpad);

                relaunchedFromDialer(showDialpad);
            }

            boolean newOutgoingCall = false;
            if (intent.getBooleanExtra(NEW_OUTGOING_CALL_EXTRA, false)) {
                intent.removeExtra(NEW_OUTGOING_CALL_EXTRA);
                Call call = CallList.getInstance().getOutgoingCall();
                if (call == null) {
                    call = CallList.getInstance().getPendingOutgoingCall();
                }

                Bundle extras = null;
                if (call != null) {
                    extras = call.getTelecomCall().getDetails().getIntentExtras();
                }
                if (extras == null) {
                    // Initialize the extras bundle to avoid NPE
                    extras = new Bundle();
                }

                Point touchPoint = null;
                if (TouchPointManager.getInstance().hasValidPoint()) {
                    // Use the most immediate touch point in the InCallUi if available
                    touchPoint = TouchPointManager.getInstance().getPoint();
                } else {
                    // Otherwise retrieve the touch point from the call intent
                    if (call != null) {
                        touchPoint = (Point) extras.getParcelable(TouchPointManager.TOUCH_POINT);
                    }
                }

                // Start animation for new outgoing call
                CircularRevealFragment.startCircularReveal(getFragmentManager(), touchPoint,
                        InCallPresenter.getInstance());

                // InCallActivity is responsible for disconnecting a new outgoing call if there
                // is no way of making it (i.e. no valid call capable accounts).
                // If the version is not MSIM compatible, then ignore this code.
                if (CompatUtils.isMSIMCompatible()
                        && InCallPresenter.isCallWithNoValidAccounts(call)) {
                    TelecomAdapter.getInstance().disconnectCall(call.getId());
                }

                dismissKeyguard(true);
                newOutgoingCall = true;
            }

            Call pendingAccountSelectionCall = CallList.getInstance().getWaitingForAccountCall();
            if (pendingAccountSelectionCall != null) {
                showCallCardFragment(false);
                Bundle extras =
                        pendingAccountSelectionCall.getTelecomCall().getDetails().getIntentExtras();

                final List<PhoneAccountHandle> phoneAccountHandles;
                if (extras != null) {
                    phoneAccountHandles = extras.getParcelableArrayList(
                            android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS);
                } else {
                    phoneAccountHandles = new ArrayList<>();
                }

                DialogFragment dialogFragment = SelectPhoneAccountDialogFragment.newInstance(
                        R.string.select_phone_account_for_calls, true, phoneAccountHandles,
                        mSelectAcctListener);
                dialogFragment.show(getFragmentManager(), TAG_SELECT_ACCT_FRAGMENT);
            } else if (!newOutgoingCall) {
                showCallCardFragment(true);
            }
            return;
        }
    }

    /**
     * When relaunching from the dialer app, {@code showDialpad} indicates whether the dialpad
     * should be shown on launch.
     *
     * @param showDialpad {@code true} to indicate the dialpad should be shown on launch, and
     *                                {@code false} to indicate no change should be made to the
     *                                dialpad visibility.
     */
    private void relaunchedFromDialer(boolean showDialpad) {
        mShowDialpadRequest = showDialpad ? DIALPAD_REQUEST_SHOW : DIALPAD_REQUEST_NONE;
        mAnimateDialpadOnShow = true;

        if (mShowDialpadRequest == DIALPAD_REQUEST_SHOW) {
            // If there's only one line in use, AND it's on hold, then we're sure the user
            // wants to use the dialpad toward the exact line, so un-hold the holding line.
            final Call call = CallList.getInstance().getActiveOrBackgroundCall();
            if (call != null && call.getState() == State.ONHOLD) {
                TelecomAdapter.getInstance().unholdCall(call.getId());
            }
        }
    }

    public void dismissKeyguard(boolean dismiss) {
        if (mDismissKeyguard == dismiss) {
            return;
        }
        mDismissKeyguard = dismiss;
        if (dismiss) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
    }

    private void showFragment(String tag, boolean show, boolean executeImmediately) {
        Trace.beginSection("showFragment - " + tag);
        final FragmentManager fm = getFragmentManagerForTag(tag);

        if (fm == null) {
            Log.w(TAG, "Fragment manager is null for : " + tag);
            return;
        }

        Fragment fragment = fm.findFragmentByTag(tag);
        if (!show && fragment == null) {
            // Nothing to show, so bail early.
            return;
        }

        final FragmentTransaction transaction = fm.beginTransaction();
        if (show) {
            if (fragment == null) {
                fragment = createNewFragmentForTag(tag);
                transaction.add(getContainerIdForFragment(tag), fragment, tag);
            } else {
                transaction.show(fragment);
            }
            Logger.logScreenView(getScreenTypeForTag(tag), this);
        } else {
            transaction.hide(fragment);
        }

        transaction.commitAllowingStateLoss();
        if (executeImmediately) {
            fm.executePendingTransactions();
        }
        Trace.endSection();
    }

    private Fragment createNewFragmentForTag(String tag) {
        if (TAG_DIALPAD_FRAGMENT.equals(tag)) {
            mDialpadFragment = new DialpadFragment();
            return mDialpadFragment;
        } else if (TAG_ANSWER_FRAGMENT.equals(tag)) {
            if (AccessibilityUtil.isTalkBackEnabled(this)) {
                mAnswerFragment = new AccessibleAnswerFragment();
            } else {
                mAnswerFragment = new GlowPadAnswerFragment();
            }
            return mAnswerFragment;
        } else if (TAG_CONFERENCE_FRAGMENT.equals(tag)) {
            mConferenceManagerFragment = new ConferenceManagerFragment();
            return mConferenceManagerFragment;
        } else if (TAG_CALLCARD_FRAGMENT.equals(tag)) {
            mCallCardFragment = new CallCardFragment();
            return mCallCardFragment;
        }
        throw new IllegalStateException("Unexpected fragment: " + tag);
    }

    private FragmentManager getFragmentManagerForTag(String tag) {
        if (TAG_DIALPAD_FRAGMENT.equals(tag)) {
            return mChildFragmentManager;
        } else if (TAG_ANSWER_FRAGMENT.equals(tag)) {
            return mChildFragmentManager;
        } else if (TAG_CONFERENCE_FRAGMENT.equals(tag)) {
            return getFragmentManager();
        } else if (TAG_CALLCARD_FRAGMENT.equals(tag)) {
            return getFragmentManager();
        }
        throw new IllegalStateException("Unexpected fragment: " + tag);
    }

    private int getScreenTypeForTag(String tag) {
        switch (tag) {
            case TAG_DIALPAD_FRAGMENT:
                return ScreenEvent.INCALL_DIALPAD;
            case TAG_CALLCARD_FRAGMENT:
                return ScreenEvent.INCALL;
            case TAG_CONFERENCE_FRAGMENT:
                return ScreenEvent.CONFERENCE_MANAGEMENT;
            case TAG_ANSWER_FRAGMENT:
                return ScreenEvent.INCOMING_CALL;
            default:
                return ScreenEvent.UNKNOWN;
        }
    }

    private int getContainerIdForFragment(String tag) {
        if (TAG_DIALPAD_FRAGMENT.equals(tag)) {
            return R.id.answer_and_dialpad_container;
        } else if (TAG_ANSWER_FRAGMENT.equals(tag)) {
            return R.id.answer_and_dialpad_container;
        } else if (TAG_CONFERENCE_FRAGMENT.equals(tag)) {
            return R.id.main;
        } else if (TAG_CALLCARD_FRAGMENT.equals(tag)) {
            return R.id.main;
        }
        throw new IllegalStateException("Unexpected fragment: " + tag);
    }

    /**
     * @return {@code true} while the visibility of the dialpad has actually changed.
     */
    public boolean showDialpadFragment(boolean show, boolean animate) {
        // If the dialpad is already visible, don't animate in. If it's gone, don't animate out.
        if ((show && isDialpadVisible()) || (!show && !isDialpadVisible())) {
            return false;
        }
        // We don't do a FragmentTransaction on the hide case because it will be dealt with when
        // the listener is fired after an animation finishes.
        if (!animate) {
            showFragment(TAG_DIALPAD_FRAGMENT, show, true);
        } else {
            if (show) {
                showFragment(TAG_DIALPAD_FRAGMENT, true, true);
                mDialpadFragment.animateShowDialpad();
            }
            mDialpadFragment.getView().startAnimation(show ? mSlideIn : mSlideOut);
        }
        // Note:  onDialpadVisibilityChange is called here to ensure that the dialpad FAB
        // repositions itself.
        mCallCardFragment.onDialpadVisibilityChange(show);

        final ProximitySensor sensor = InCallPresenter.getInstance().getProximitySensor();
        if (sensor != null) {
            sensor.onDialpadVisible(show);
        }
        return true;
    }

    public boolean isDialpadVisible() {
        return mDialpadFragment != null && mDialpadFragment.isVisible();
    }

    public void showCallCardFragment(boolean show) {
        showFragment(TAG_CALLCARD_FRAGMENT, show, true);
    }

    /**
     * Hides or shows the conference manager fragment.
     *
     * @param show {@code true} if the conference manager should be shown, {@code false} if it
     * should be hidden.
     */
    public void showConferenceFragment(boolean show) {
        showFragment(TAG_CONFERENCE_FRAGMENT, show, true);
        mConferenceManagerFragment.onVisibilityChanged(show);

        // Need to hide the call card fragment to ensure that accessibility service does not try to
        // give focus to the call card when the conference manager is visible.
        mCallCardFragment.getView().setVisibility(show ? View.GONE : View.VISIBLE);
    }

    public void showAnswerFragment(boolean show) {
        showFragment(TAG_ANSWER_FRAGMENT, show, true);
    }

    public void showPostCharWaitDialog(String callId, String chars) {
        if (isVisible()) {
            final PostCharDialogFragment fragment = new PostCharDialogFragment(callId, chars);
            fragment.show(getFragmentManager(), "postCharWait");

            mShowPostCharWaitDialogOnResume = false;
            mShowPostCharWaitDialogCallId = null;
            mShowPostCharWaitDialogChars = null;
        } else {
            mShowPostCharWaitDialogOnResume = true;
            mShowPostCharWaitDialogCallId = callId;
            mShowPostCharWaitDialogChars = chars;
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (mCallCardFragment != null) {
            mCallCardFragment.dispatchPopulateAccessibilityEvent(event);
        }
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    public void maybeShowErrorDialogOnDisconnect(DisconnectCause disconnectCause) {
        Log.d(this, "maybeShowErrorDialogOnDisconnect");

        if (!isFinishing() && !TextUtils.isEmpty(disconnectCause.getDescription())
                && (disconnectCause.getCode() == DisconnectCause.ERROR ||
                disconnectCause.getCode() == DisconnectCause.RESTRICTED)) {
            showErrorDialog(disconnectCause.getDescription());
        }
    }

    public void dismissPendingDialogs() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        if (mAnswerFragment != null) {
            mAnswerFragment.dismissPendingDialogs();
        }
    }

    /**
     * Utility function to bring up a generic "error" dialog.
     */
    private void showErrorDialog(CharSequence msg) {
        Log.i(this, "Show Dialog: " + msg);

        dismissPendingDialogs();

        mDialog = new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onDialogDismissed();
                    }
                })
                .setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        onDialogDismissed();
                    }
                })
                .create();

        mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        mDialog.show();
    }

    private void onDialogDismissed() {
        mDialog = null;
        CallList.getInstance().onErrorDialogDismissed();
        InCallPresenter.getInstance().onDismissDialog();
    }

    public void setExcludeFromRecents(boolean exclude) {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.AppTask> tasks = am.getAppTasks();
        int taskId = getTaskId();
        for (int i = 0; i < tasks.size(); i++) {
            ActivityManager.AppTask task = tasks.get(i);
            if (task.getTaskInfo().id == taskId) {
                try {
                    task.setExcludeFromRecents(exclude);
                } catch (RuntimeException e) {
                    Log.e(TAG, "RuntimeException when excluding task from recents.", e);
                }
            }
        }
    }


    public OnTouchListener getDispatchTouchEventListener() {
        return mDispatchTouchEventListener;
    }

    public void setDispatchTouchEventListener(OnTouchListener mDispatchTouchEventListener) {
        this.mDispatchTouchEventListener = mDispatchTouchEventListener;
    }

    /**
     * Enables the OrientationEventListener if enable flag is true. Disables it if enable is
     * false
     * @param enable true or false.
     */
    public void enableInCallOrientationEventListener(boolean enable) {
        if (enable) {
            mInCallOrientationEventListener.enable(enable);
        } else {
            mInCallOrientationEventListener.disable();
        }
    }
}
