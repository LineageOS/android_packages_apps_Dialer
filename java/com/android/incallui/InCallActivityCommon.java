/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.app.ActivityManager;
import android.app.ActivityManager.AppTask;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Trace;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.telecom.CallAudioState;
import android.telecom.PhoneAccountHandle;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment.SelectPhoneAccountListener;
import com.android.dialer.animation.AnimUtils;
import com.android.dialer.animation.AnimationListenerAdapter;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.CompatUtils;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.dialer.util.ViewUtil;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCall.State;
import com.android.incallui.telecomeventui.InternationalCallOnWifiDialogFragment;
import com.android.incallui.telecomeventui.InternationalCallOnWifiDialogFragment.Callback;
import com.google.common.base.Optional;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/** Shared functionality between the new and old in call activity. */
public class InCallActivityCommon {

  private static final String INTENT_EXTRA_SHOW_DIALPAD = "InCallActivity.show_dialpad";
  private static final String INTENT_EXTRA_NEW_OUTGOING_CALL = "InCallActivity.new_outgoing_call";
  private static final String INTENT_EXTRA_FOR_FULL_SCREEN =
      "InCallActivity.for_full_screen_intent";

  private static final String DIALPAD_TEXT_KEY = "InCallActivity.dialpad_text";

  private static final String TAG_SELECT_ACCOUNT_FRAGMENT = "tag_select_account_fragment";
  private static final String TAG_DIALPAD_FRAGMENT = "tag_dialpad_fragment";
  private static final String TAG_INTERNATIONAL_CALL_ON_WIFI = "tag_international_call_on_wifi";

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    DIALPAD_REQUEST_NONE,
    DIALPAD_REQUEST_SHOW,
    DIALPAD_REQUEST_HIDE,
  })
  @interface DialpadRequestType {}

  private static final int DIALPAD_REQUEST_NONE = 1;
  private static final int DIALPAD_REQUEST_SHOW = 2;
  private static final int DIALPAD_REQUEST_HIDE = 3;

  private static Optional<Integer> audioRouteForTesting = Optional.absent();

  private final InCallActivity inCallActivity;
  private boolean showPostCharWaitDialogOnResume;
  private String showPostCharWaitDialogCallId;
  private String showPostCharWaitDialogChars;
  private Dialog errorDialog;
  private SelectPhoneAccountDialogFragment selectPhoneAccountDialogFragment;
  private Animation dialpadSlideInAnimation;
  private Animation dialpadSlideOutAnimation;
  private boolean animateDialpadOnShow;
  private String dtmfTextToPreopulate;
  @DialpadRequestType private int showDialpadRequest = DIALPAD_REQUEST_NONE;
  // If activity is going to be recreated. This is usually happening in {@link onNewIntent}.
  private boolean isRecreating;

  private final SelectPhoneAccountListener selectAccountListener =
      new SelectPhoneAccountListener() {
        @Override
        public void onPhoneAccountSelected(
            PhoneAccountHandle selectedAccountHandle, boolean setDefault, String callId) {
          DialerCall call = CallList.getInstance().getCallById(callId);
          LogUtil.i(
              "InCallActivityCommon.SelectPhoneAccountListener.onPhoneAccountSelected",
              "call: " + call);
          if (call != null) {
            call.phoneAccountSelected(selectedAccountHandle, setDefault);
          }
        }

        @Override
        public void onDialogDismissed(String callId) {
          DialerCall call = CallList.getInstance().getCallById(callId);
          LogUtil.i(
              "InCallActivityCommon.SelectPhoneAccountListener.onDialogDismissed",
              "disconnecting call: " + call);
          if (call != null) {
            call.disconnect();
          }
        }
      };

  private InternationalCallOnWifiDialogFragment.Callback internationalCallOnWifiCallback =
      new Callback() {
        @Override
        public void continueCall(@NonNull String callId) {
          LogUtil.i("InCallActivityCommon.continueCall", "continuing call with id: %s", callId);
        }

        @Override
        public void cancelCall(@NonNull String callId) {
          DialerCall call = CallList.getInstance().getCallById(callId);
          if (call == null) {
            LogUtil.i("InCallActivityCommon.cancelCall", "call destroyed before dialog closed");
            return;
          }
          LogUtil.i("InCallActivityCommon.cancelCall", "disconnecting international call on wifi");
          call.disconnect();
        }
      };

  public static void setIntentExtras(
      Intent intent, boolean showDialpad, boolean newOutgoingCall, boolean isForFullScreen) {
    if (showDialpad) {
      intent.putExtra(INTENT_EXTRA_SHOW_DIALPAD, true);
    }
    intent.putExtra(INTENT_EXTRA_NEW_OUTGOING_CALL, newOutgoingCall);
    intent.putExtra(INTENT_EXTRA_FOR_FULL_SCREEN, isForFullScreen);
  }

  public InCallActivityCommon(InCallActivity inCallActivity) {
    this.inCallActivity = inCallActivity;
  }

  public void onCreate(Bundle icicle) {
    setWindowFlags();

    inCallActivity.setContentView(R.layout.incall_screen);

    internalResolveIntent(inCallActivity.getIntent());

    boolean isLandscape =
        inCallActivity.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_LANDSCAPE;
    boolean isRtl = ViewUtil.isRtl();

    if (isLandscape) {
      dialpadSlideInAnimation =
          AnimationUtils.loadAnimation(
              inCallActivity, isRtl ? R.anim.dialpad_slide_in_left : R.anim.dialpad_slide_in_right);
      dialpadSlideOutAnimation =
          AnimationUtils.loadAnimation(
              inCallActivity,
              isRtl ? R.anim.dialpad_slide_out_left : R.anim.dialpad_slide_out_right);
    } else {
      dialpadSlideInAnimation =
          AnimationUtils.loadAnimation(inCallActivity, R.anim.dialpad_slide_in_bottom);
      dialpadSlideOutAnimation =
          AnimationUtils.loadAnimation(inCallActivity, R.anim.dialpad_slide_out_bottom);
    }

    dialpadSlideInAnimation.setInterpolator(AnimUtils.EASE_IN);
    dialpadSlideOutAnimation.setInterpolator(AnimUtils.EASE_OUT);

    dialpadSlideOutAnimation.setAnimationListener(
        new AnimationListenerAdapter() {
          @Override
          public void onAnimationEnd(Animation animation) {
            performHideDialpadFragment();
          }
        });

    // Don't override the value if show dialpad request is true in intent extras.
    if (icicle != null && showDialpadRequest == DIALPAD_REQUEST_NONE) {
      // If the dialpad was shown before, set variables indicating it should be shown and
      // populated with the previous DTMF text.  The dialpad is actually shown and populated
      // in onResume() to ensure the hosting fragment has been inflated and is ready to receive it.
      if (icicle.containsKey(INTENT_EXTRA_SHOW_DIALPAD)) {
        boolean showDialpad = icicle.getBoolean(INTENT_EXTRA_SHOW_DIALPAD);
        showDialpadRequest = showDialpad ? DIALPAD_REQUEST_SHOW : DIALPAD_REQUEST_HIDE;
        animateDialpadOnShow = false;
      }
      dtmfTextToPreopulate = icicle.getString(DIALPAD_TEXT_KEY);

      SelectPhoneAccountDialogFragment dialogFragment =
          (SelectPhoneAccountDialogFragment)
              inCallActivity.getFragmentManager().findFragmentByTag(TAG_SELECT_ACCOUNT_FRAGMENT);
      if (dialogFragment != null) {
        dialogFragment.setListener(selectAccountListener);
      }
    }

    InternationalCallOnWifiDialogFragment existingInternationalFragment =
        (InternationalCallOnWifiDialogFragment)
            inCallActivity
                .getSupportFragmentManager()
                .findFragmentByTag(TAG_INTERNATIONAL_CALL_ON_WIFI);
    if (existingInternationalFragment != null) {
      LogUtil.i(
          "InCallActivityCommon.onCreate", "international fragment exists attaching callback");
      existingInternationalFragment.setCallback(internationalCallOnWifiCallback);
    }
  }

  public void onResume() {
    Trace.beginSection("InCallActivityCommon.onResume");
    if (InCallPresenter.getInstance().isReadyForTearDown()) {
      LogUtil.i(
          "InCallActivityCommon.onResume",
          "InCallPresenter is ready for tear down, not sending updates");
    } else {
      inCallActivity.updateTaskDescription();
      InCallPresenter.getInstance().onUiShowing(true);
    }

    // If there is a pending request to show or hide the dialpad, handle that now.
    if (showDialpadRequest != DIALPAD_REQUEST_NONE) {
      if (showDialpadRequest == DIALPAD_REQUEST_SHOW) {
        // Exit fullscreen so that the user has access to the dialpad hide/show button and
        // can hide the dialpad.  Important when showing the dialpad from within dialer.
        InCallPresenter.getInstance().setFullScreen(false, true /* force */);

        inCallActivity.showDialpadFragment(true /* show */, animateDialpadOnShow /* animate */);
        animateDialpadOnShow = false;

        DialpadFragment dialpadFragment = inCallActivity.getDialpadFragment();
        if (dialpadFragment != null) {
          dialpadFragment.setDtmfText(dtmfTextToPreopulate);
          dtmfTextToPreopulate = null;
        }
      } else {
        LogUtil.i("InCallActivityCommon.onResume", "force hide dialpad");
        if (inCallActivity.getDialpadFragment() != null) {
          inCallActivity.showDialpadFragment(false /* show */, false /* animate */);
        }
      }
      showDialpadRequest = DIALPAD_REQUEST_NONE;
    }
    inCallActivity.updateNavigationBar(inCallActivity.isDialpadVisible());

    if (showPostCharWaitDialogOnResume) {
      showPostCharWaitDialog(showPostCharWaitDialogCallId, showPostCharWaitDialogChars);
    }

    CallList.getInstance()
        .onInCallUiShown(
            inCallActivity.getIntent().getBooleanExtra(INTENT_EXTRA_FOR_FULL_SCREEN, false));
    Trace.endSection();
  }

  void onNewIntent(Intent intent, boolean isRecreating) {
    LogUtil.i("InCallActivityCommon.onNewIntent", "");
    this.isRecreating = isRecreating;

    // We're being re-launched with a new Intent.  Since it's possible for a
    // single InCallActivity instance to persist indefinitely (even if we
    // finish() ourselves), this sequence can potentially happen any time
    // the InCallActivity needs to be displayed.

    // Stash away the new intent so that we can get it in the future
    // by calling getIntent().  (Otherwise getIntent() will return the
    // original Intent from when we first got created!)
    inCallActivity.setIntent(intent);

    // Activities are always paused before receiving a new intent, so
    // we can count on our onResume() method being called next.

    // Just like in onCreate(), handle the intent.
    // Skip if InCallActivity is going to recreate since this will be called in onCreate().
    if (!isRecreating) {
      internalResolveIntent(intent);
    }
  }

  private void setWindowFlags() {
    // Allow the activity to be shown when the screen is locked and filter out touch events that are
    // "too fat".
    int flags =
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            | WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES;

    // When the audio stream is not directed through Bluetooth, turn the screen on once the
    // activity is shown.
    final int audioRoute = getAudioRoute();
    if (audioRoute != CallAudioState.ROUTE_BLUETOOTH) {
      flags |= WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
    }

    inCallActivity.getWindow().addFlags(flags);
  }

  private static int getAudioRoute() {
    if (audioRouteForTesting.isPresent()) {
      return audioRouteForTesting.get();
    }

    return AudioModeProvider.getInstance().getAudioState().getRoute();
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public static void setAudioRouteForTesting(int audioRoute) {
    audioRouteForTesting = Optional.of(audioRoute);
  }

  public void showPostCharWaitDialog(String callId, String chars) {
    if (inCallActivity.isVisible()) {
      PostCharDialogFragment fragment = new PostCharDialogFragment(callId, chars);
      fragment.show(inCallActivity.getSupportFragmentManager(), "postCharWait");

      showPostCharWaitDialogOnResume = false;
      showPostCharWaitDialogCallId = null;
      showPostCharWaitDialogChars = null;
    } else {
      showPostCharWaitDialogOnResume = true;
      showPostCharWaitDialogCallId = callId;
      showPostCharWaitDialogChars = chars;
    }
  }

  /**
   * When relaunching from the dialer app, {@code showDialpad} indicates whether the dialpad should
   * be shown on launch.
   *
   * @param showDialpad {@code true} to indicate the dialpad should be shown on launch, and {@code
   *     false} to indicate no change should be made to the dialpad visibility.
   */
  private void relaunchedFromDialer(boolean showDialpad) {
    showDialpadRequest = showDialpad ? DIALPAD_REQUEST_SHOW : DIALPAD_REQUEST_NONE;
    animateDialpadOnShow = true;

    if (showDialpadRequest == DIALPAD_REQUEST_SHOW) {
      // If there's only one line in use, AND it's on hold, then we're sure the user
      // wants to use the dialpad toward the exact line, so un-hold the holding line.
      DialerCall call = CallList.getInstance().getActiveOrBackgroundCall();
      if (call != null && call.getState() == State.ONHOLD) {
        call.unhold();
      }
    }
  }

  public void setExcludeFromRecents(boolean exclude) {
    List<AppTask> tasks = inCallActivity.getSystemService(ActivityManager.class).getAppTasks();
    int taskId = inCallActivity.getTaskId();
    for (int i = 0; i < tasks.size(); i++) {
      ActivityManager.AppTask task = tasks.get(i);
      try {
        if (task.getTaskInfo().id == taskId) {
          task.setExcludeFromRecents(exclude);
        }
      } catch (RuntimeException e) {
        LogUtil.e(
            "InCallActivityCommon.setExcludeFromRecents",
            "RuntimeException when excluding task from recents.",
            e);
      }
    }
  }

  public boolean showDialpadFragment(boolean show, boolean animate) {
    // If the dialpad is already visible, don't animate in. If it's gone, don't animate out.
    boolean isDialpadVisible = inCallActivity.isDialpadVisible();
    LogUtil.i(
        "InCallActivityCommon.showDialpadFragment",
        "show: %b, animate: %b, " + "isDialpadVisible: %b",
        show,
        animate,
        isDialpadVisible);
    if (show == isDialpadVisible) {
      return false;
    }

    FragmentManager dialpadFragmentManager = inCallActivity.getDialpadFragmentManager();
    if (dialpadFragmentManager == null) {
      LogUtil.i(
          "InCallActivityCommon.showDialpadFragment", "unable to show or hide dialpad fragment");
      return false;
    }

    // We don't do a FragmentTransaction on the hide case because it will be dealt with when
    // the listener is fired after an animation finishes.
    if (!animate) {
      if (show) {
        performShowDialpadFragment(dialpadFragmentManager);
      } else {
        performHideDialpadFragment();
      }
    } else {
      if (show) {
        performShowDialpadFragment(dialpadFragmentManager);
        inCallActivity.getDialpadFragment().animateShowDialpad();
      }
      inCallActivity
          .getDialpadFragment()
          .getView()
          .startAnimation(show ? dialpadSlideInAnimation : dialpadSlideOutAnimation);
    }

    ProximitySensor sensor = InCallPresenter.getInstance().getProximitySensor();
    if (sensor != null) {
      sensor.onDialpadVisible(show);
    }
    showDialpadRequest = DIALPAD_REQUEST_NONE;
    return true;
  }

  private void performShowDialpadFragment(@NonNull FragmentManager dialpadFragmentManager) {
    FragmentTransaction transaction = dialpadFragmentManager.beginTransaction();
    DialpadFragment dialpadFragment = inCallActivity.getDialpadFragment();
    if (dialpadFragment == null) {
      transaction.add(
          inCallActivity.getDialpadContainerId(), new DialpadFragment(), TAG_DIALPAD_FRAGMENT);
    } else {
      transaction.show(dialpadFragment);
    }

    transaction.commitAllowingStateLoss();
    dialpadFragmentManager.executePendingTransactions();

    Logger.get(inCallActivity).logScreenView(ScreenEvent.Type.INCALL_DIALPAD, inCallActivity);
    inCallActivity.updateNavigationBar(true /* isDialpadVisible */);
  }

  private void performHideDialpadFragment() {
    FragmentManager fragmentManager = inCallActivity.getDialpadFragmentManager();
    if (fragmentManager == null) {
      LogUtil.e(
          "InCallActivityCommon.performHideDialpadFragment", "child fragment manager is null");
      return;
    }

    Fragment fragment = fragmentManager.findFragmentByTag(TAG_DIALPAD_FRAGMENT);
    if (fragment != null) {
      FragmentTransaction transaction = fragmentManager.beginTransaction();
      transaction.hide(fragment);
      transaction.commitAllowingStateLoss();
      fragmentManager.executePendingTransactions();
    }
    inCallActivity.updateNavigationBar(false /* isDialpadVisible */);
  }

  private void internalResolveIntent(Intent intent) {
    if (!intent.getAction().equals(Intent.ACTION_MAIN)) {
      return;
    }

    if (intent.hasExtra(INTENT_EXTRA_SHOW_DIALPAD)) {
      // SHOW_DIALPAD_EXTRA can be used here to specify whether the DTMF
      // dialpad should be initially visible.  If the extra isn't
      // present at all, we just leave the dialpad in its previous state.
      boolean showDialpad = intent.getBooleanExtra(INTENT_EXTRA_SHOW_DIALPAD, false);
      LogUtil.i("InCallActivityCommon.internalResolveIntent", "SHOW_DIALPAD_EXTRA: " + showDialpad);

      relaunchedFromDialer(showDialpad);
    }

    DialerCall outgoingCall = CallList.getInstance().getOutgoingCall();
    if (outgoingCall == null) {
      outgoingCall = CallList.getInstance().getPendingOutgoingCall();
    }

    if (intent.getBooleanExtra(INTENT_EXTRA_NEW_OUTGOING_CALL, false)) {
      intent.removeExtra(INTENT_EXTRA_NEW_OUTGOING_CALL);

      // InCallActivity is responsible for disconnecting a new outgoing call if there
      // is no way of making it (i.e. no valid call capable accounts).
      // If the version is not MSIM compatible, then ignore this code.
      if (CompatUtils.isMSIMCompatible()
          && InCallPresenter.isCallWithNoValidAccounts(outgoingCall)) {
        LogUtil.i(
            "InCallActivityCommon.internalResolveIntent",
            "call with no valid accounts, disconnecting");
        outgoingCall.disconnect();
      }

      inCallActivity.dismissKeyguard(true);
    }

    boolean didShowAccountSelectionDialog = maybeShowAccountSelectionDialog();
    if (didShowAccountSelectionDialog) {
      inCallActivity.hideMainInCallFragment();
    }
  }

  private boolean maybeShowAccountSelectionDialog() {
    DialerCall waitingForAccountCall = CallList.getInstance().getWaitingForAccountCall();
    if (waitingForAccountCall == null) {
      return false;
    }

    Bundle extras = waitingForAccountCall.getIntentExtras();
    List<PhoneAccountHandle> phoneAccountHandles;
    if (extras != null) {
      phoneAccountHandles =
          extras.getParcelableArrayList(android.telecom.Call.AVAILABLE_PHONE_ACCOUNTS);
    } else {
      phoneAccountHandles = new ArrayList<>();
    }

    selectPhoneAccountDialogFragment =
        SelectPhoneAccountDialogFragment.newInstance(
            R.string.select_phone_account_for_calls,
            true,
            0,
            phoneAccountHandles,
            selectAccountListener,
            waitingForAccountCall.getId(),
            null);
    selectPhoneAccountDialogFragment.show(
        inCallActivity.getFragmentManager(), TAG_SELECT_ACCOUNT_FRAGMENT);
    return true;
  }

  /** @deprecated Only for temporary use during the deprecation of {@link InCallActivityCommon} */
  @Deprecated
  @Nullable
  Dialog getErrorDialog() {
    return errorDialog;
  }

  /** @deprecated Only for temporary use during the deprecation of {@link InCallActivityCommon} */
  @Deprecated
  void setErrorDialog(@Nullable Dialog errorDialog) {
    this.errorDialog = errorDialog;
  }

  /** @deprecated Only for temporary use during the deprecation of {@link InCallActivityCommon} */
  @Deprecated
  boolean getIsRecreating() {
    return isRecreating;
  }

  /** @deprecated Only for temporary use during the deprecation of {@link InCallActivityCommon} */
  @Deprecated
  @Nullable
  SelectPhoneAccountDialogFragment getSelectPhoneAccountDialogFragment() {
    return selectPhoneAccountDialogFragment;
  }

  /** @deprecated Only for temporary use during the deprecation of {@link InCallActivityCommon} */
  @Deprecated
  void setSelectPhoneAccountDialogFragment(
      @Nullable SelectPhoneAccountDialogFragment selectPhoneAccountDialogFragment) {
    this.selectPhoneAccountDialogFragment = selectPhoneAccountDialogFragment;
  }

  /** @deprecated Only for temporary use during the deprecation of {@link InCallActivityCommon} */
  @Deprecated
  InternationalCallOnWifiDialogFragment.Callback getCallbackForInternationalCallOnWifiDialog() {
    return internationalCallOnWifiCallback;
  }
}
