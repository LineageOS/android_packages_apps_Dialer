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
import android.app.ActivityManager.TaskDescription;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.res.ResourcesCompat;
import android.telecom.PhoneAccountHandle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.Toast;
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
import com.android.incallui.call.TelecomAdapter;
import com.android.incallui.disconnectdialog.DisconnectMessage;
import com.android.incallui.telecomeventui.InternationalCallOnWifiDialogFragment;
import com.android.incallui.telecomeventui.InternationalCallOnWifiDialogFragment.Callback;
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

  private final InCallActivity inCallActivity;
  private boolean dismissKeyguard;
  private boolean showPostCharWaitDialogOnResume;
  private String showPostCharWaitDialogCallId;
  private String showPostCharWaitDialogChars;
  private Dialog dialog;
  private SelectPhoneAccountDialogFragment selectPhoneAccountDialogFragment;
  private InCallOrientationEventListener inCallOrientationEventListener;
  private Animation dialpadSlideInAnimation;
  private Animation dialpadSlideOutAnimation;
  private boolean animateDialpadOnShow;
  private String dtmfTextToPreopulate;
  @DialpadRequestType private int showDialpadRequest = DIALPAD_REQUEST_NONE;

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
    // set this flag so this activity will stay in front of the keyguard
    // Have the WindowManager filter out touch events that are "too fat".
    int flags =
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES;

    inCallActivity.getWindow().addFlags(flags);

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

    if (icicle != null) {
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

    inCallOrientationEventListener = new InCallOrientationEventListener(inCallActivity);
  }

  public void onSaveInstanceState(Bundle out) {
    // TODO: The dialpad fragment should handle this as part of its own state
    out.putBoolean(INTENT_EXTRA_SHOW_DIALPAD, isDialpadVisible());
    DialpadFragment dialpadFragment = getDialpadFragment();
    if (dialpadFragment != null) {
      out.putString(DIALPAD_TEXT_KEY, dialpadFragment.getDtmfText());
    }
  }

  public void onStart() {
    // setting activity should be last thing in setup process
    InCallPresenter.getInstance().setActivity(inCallActivity);
    enableInCallOrientationEventListener(
        inCallActivity.getRequestedOrientation()
            == InCallOrientationEventListener.ACTIVITY_PREFERENCE_ALLOW_ROTATION);

    InCallPresenter.getInstance().onActivityStarted();
  }

  public void onResume() {
    if (InCallPresenter.getInstance().isReadyForTearDown()) {
      LogUtil.i(
          "InCallActivityCommon.onResume",
          "InCallPresenter is ready for tear down, not sending updates");
    } else {
      updateTaskDescription();
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

        DialpadFragment dialpadFragment = getDialpadFragment();
        if (dialpadFragment != null) {
          dialpadFragment.setDtmfText(dtmfTextToPreopulate);
          dtmfTextToPreopulate = null;
        }
      } else {
        LogUtil.i("InCallActivityCommon.onResume", "force hide dialpad");
        if (getDialpadFragment() != null) {
          inCallActivity.showDialpadFragment(false /* show */, false /* animate */);
        }
      }
      showDialpadRequest = DIALPAD_REQUEST_NONE;
    }

    if (showPostCharWaitDialogOnResume) {
      showPostCharWaitDialog(showPostCharWaitDialogCallId, showPostCharWaitDialogChars);
    }

    CallList.getInstance()
        .onInCallUiShown(
            inCallActivity.getIntent().getBooleanExtra(INTENT_EXTRA_FOR_FULL_SCREEN, false));
  }

  // onPause is guaranteed to be called when the InCallActivity goes
  // in the background.
  public void onPause() {
    DialpadFragment dialpadFragment = getDialpadFragment();
    if (dialpadFragment != null) {
      dialpadFragment.onDialerKeyUp(null);
    }

    InCallPresenter.getInstance().onUiShowing(false);
    if (inCallActivity.isFinishing()) {
      InCallPresenter.getInstance().unsetActivity(inCallActivity);
    }
  }

  public void onStop() {
    enableInCallOrientationEventListener(false);
    InCallPresenter.getInstance().updateIsChangingConfigurations();
    InCallPresenter.getInstance().onActivityStopped();
  }

  public void onDestroy() {
    InCallPresenter.getInstance().unsetActivity(inCallActivity);
    InCallPresenter.getInstance().updateIsChangingConfigurations();
  }

  void onNewIntent(Intent intent, boolean isRecreating) {
    LogUtil.i("InCallActivityCommon.onNewIntent", "");

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

  public boolean onBackPressed(boolean isInCallScreenVisible) {
    LogUtil.i("InCallActivityCommon.onBackPressed", "");

    // BACK is also used to exit out of any "special modes" of the
    // in-call UI:
    if (!inCallActivity.isVisible()) {
      return true;
    }

    if (!isInCallScreenVisible) {
      return true;
    }

    DialpadFragment dialpadFragment = getDialpadFragment();
    if (dialpadFragment != null && dialpadFragment.isVisible()) {
      inCallActivity.showDialpadFragment(false /* show */, true /* animate */);
      return true;
    }

    // Always disable the Back key while an incoming call is ringing
    DialerCall call = CallList.getInstance().getIncomingCall();
    if (call != null) {
      LogUtil.i("InCallActivityCommon.onBackPressed", "consume Back press for an incoming call");
      return true;
    }

    // Nothing special to do. Fall back to the default behavior.
    return false;
  }

  public boolean onKeyUp(int keyCode, KeyEvent event) {
    DialpadFragment dialpadFragment = getDialpadFragment();
    // push input to the dialer.
    if (dialpadFragment != null
        && (dialpadFragment.isVisible())
        && (dialpadFragment.onDialerKeyUp(event))) {
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_CALL) {
      // Always consume CALL to be sure the PhoneWindow won't do anything with it
      return true;
    }
    return false;
  }

  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_CALL:
        boolean handled = InCallPresenter.getInstance().handleCallKey();
        if (!handled) {
          LogUtil.e(
              "InCallActivityCommon.onKeyDown",
              "InCallPresenter should always handle KEYCODE_CALL in onKeyDown");
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
        TelecomAdapter.getInstance()
            .mute(!AudioModeProvider.getInstance().getAudioState().isMuted());
        return true;

        // Various testing/debugging features, enabled ONLY when VERBOSE == true.
      case KeyEvent.KEYCODE_SLASH:
        if (LogUtil.isVerboseEnabled()) {
          LogUtil.v(
              "InCallActivityCommon.onKeyDown",
              "----------- InCallActivity View dump --------------");
          // Dump starting from the top-level view of the entire activity:
          Window w = inCallActivity.getWindow();
          View decorView = w.getDecorView();
          LogUtil.v("InCallActivityCommon.onKeyDown", "View dump:" + decorView);
          return true;
        }
        break;
      case KeyEvent.KEYCODE_EQUALS:
        break;
      default: // fall out
    }

    return event.getRepeatCount() == 0 && handleDialerKeyDown(keyCode, event);
  }

  private boolean handleDialerKeyDown(int keyCode, KeyEvent event) {
    LogUtil.v("InCallActivityCommon.handleDialerKeyDown", "keyCode %d, event: %s", keyCode, event);

    // As soon as the user starts typing valid dialable keys on the
    // keyboard (presumably to type DTMF tones) we start passing the
    // key events to the DTMFDialer's onDialerKeyDown.
    DialpadFragment dialpadFragment = getDialpadFragment();
    if (dialpadFragment != null && dialpadFragment.isVisible()) {
      return dialpadFragment.onDialerKeyDown(event);
    }

    return false;
  }

  public void dismissKeyguard(boolean dismiss) {
    if (dismissKeyguard == dismiss) {
      return;
    }
    dismissKeyguard = dismiss;
    if (dismiss) {
      inCallActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    } else {
      inCallActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }
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

  public void maybeShowErrorDialogOnDisconnect(DisconnectMessage disconnectMessage) {
    LogUtil.i(
        "InCallActivityCommon.maybeShowErrorDialogOnDisconnect",
        "disconnect cause: %s",
        disconnectMessage);

    if (!inCallActivity.isFinishing()) {
      if (disconnectMessage.dialog != null) {
        showErrorDialog(disconnectMessage.dialog, disconnectMessage.toastMessage);
      }
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

  void dismissPendingDialogs() {
    if (dialog != null) {
      dialog.dismiss();
      dialog = null;
    }
    if (selectPhoneAccountDialogFragment != null) {
      selectPhoneAccountDialogFragment.dismiss();
      selectPhoneAccountDialogFragment = null;
    }

    InternationalCallOnWifiDialogFragment internationalCallOnWifiFragment =
        (InternationalCallOnWifiDialogFragment)
            inCallActivity
                .getSupportFragmentManager()
                .findFragmentByTag(TAG_INTERNATIONAL_CALL_ON_WIFI);
    if (internationalCallOnWifiFragment != null) {
      LogUtil.i(
          "InCallActivityCommon.dismissPendingDialogs",
          "dismissing InternationalCallOnWifiDialogFragment");
      internationalCallOnWifiFragment.dismiss();
    }
  }

  private void showErrorDialog(Dialog dialog, CharSequence message) {
    LogUtil.i("InCallActivityCommon.showErrorDialog", "message: %s", message);
    inCallActivity.dismissPendingDialogs();

    // Show toast if apps is in background when dialog won't be visible.
    if (!inCallActivity.isVisible()) {
      Toast.makeText(inCallActivity.getApplicationContext(), message, Toast.LENGTH_LONG).show();
      return;
    }

    this.dialog = dialog;
    dialog.setOnDismissListener(
        new OnDismissListener() {
          @Override
          public void onDismiss(DialogInterface dialog) {
            LogUtil.i("InCallActivityCommon.showErrorDialog", "dialog dismissed");
            onDialogDismissed();
          }
        });
    dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    dialog.show();
  }

  private void onDialogDismissed() {
    dialog = null;
    CallList.getInstance().onErrorDialogDismissed();
    InCallPresenter.getInstance().onDismissDialog();
  }

  public void enableInCallOrientationEventListener(boolean enable) {
    if (enable) {
      inCallOrientationEventListener.enable(true);
    } else {
      inCallOrientationEventListener.disable();
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

  void showInternationalCallOnWifiDialog(@NonNull DialerCall call) {
    LogUtil.enterBlock("InCallActivityCommon.showInternationalCallOnWifiDialog");
    if (!InternationalCallOnWifiDialogFragment.shouldShow(inCallActivity)) {
      LogUtil.i(
          "InCallActivityCommon.showInternationalCallOnWifiDialog",
          "InternationalCallOnWifiDialogFragment.shouldShow returned false");
      return;
    }

    InternationalCallOnWifiDialogFragment fragment =
        InternationalCallOnWifiDialogFragment.newInstance(
            call.getId(), internationalCallOnWifiCallback);
    fragment.show(inCallActivity.getSupportFragmentManager(), TAG_INTERNATIONAL_CALL_ON_WIFI);
  }

  public void showWifiToLteHandoverToast(DialerCall call) {
    if (call.hasShownWiFiToLteHandoverToast()) {
      return;
    }
    Toast.makeText(
            inCallActivity, R.string.video_call_wifi_to_lte_handover_toast, Toast.LENGTH_LONG)
        .show();
    call.setHasShownWiFiToLteHandoverToast();
  }

  public void showWifiFailedDialog(final DialerCall call) {
    if (call.showWifiHandoverAlertAsToast()) {
      LogUtil.i("InCallActivityCommon.showWifiFailedDialog", "as toast");
      Toast.makeText(
              inCallActivity, R.string.video_call_lte_to_wifi_failed_message, Toast.LENGTH_SHORT)
          .show();
      return;
    }

    dismissPendingDialogs();

    AlertDialog.Builder builder =
        new AlertDialog.Builder(inCallActivity)
            .setTitle(R.string.video_call_lte_to_wifi_failed_title);

    // This allows us to use the theme of the dialog instead of the activity
    View dialogCheckBoxView =
        View.inflate(builder.getContext(), R.layout.video_call_lte_to_wifi_failed, null);
    final CheckBox wifiHandoverFailureCheckbox =
        (CheckBox) dialogCheckBoxView.findViewById(R.id.video_call_lte_to_wifi_failed_checkbox);
    wifiHandoverFailureCheckbox.setChecked(false);

    dialog =
        builder
            .setView(dialogCheckBoxView)
            .setMessage(R.string.video_call_lte_to_wifi_failed_message)
            .setOnCancelListener(
                new OnCancelListener() {
                  @Override
                  public void onCancel(DialogInterface dialog) {
                    onDialogDismissed();
                  }
                })
            .setPositiveButton(
                android.R.string.ok,
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int id) {
                    call.setDoNotShowDialogForHandoffToWifiFailure(
                        wifiHandoverFailureCheckbox.isChecked());
                    dialog.cancel();
                    onDialogDismissed();
                  }
                })
            .create();

    LogUtil.i("InCallActivityCommon.showWifiFailedDialog", "as dialog");
    dialog.show();
  }

  public boolean showDialpadFragment(boolean show, boolean animate) {
    // If the dialpad is already visible, don't animate in. If it's gone, don't animate out.
    boolean isDialpadVisible = isDialpadVisible();
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
        getDialpadFragment().animateShowDialpad();
      }
      getDialpadFragment()
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
    DialpadFragment dialpadFragment = getDialpadFragment();
    if (dialpadFragment == null) {
      transaction.add(
          inCallActivity.getDialpadContainerId(), new DialpadFragment(), TAG_DIALPAD_FRAGMENT);
    } else {
      transaction.show(dialpadFragment);
    }

    transaction.commitAllowingStateLoss();
    dialpadFragmentManager.executePendingTransactions();

    Logger.get(inCallActivity).logScreenView(ScreenEvent.Type.INCALL_DIALPAD, inCallActivity);
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
  }

  public boolean isDialpadVisible() {
    DialpadFragment dialpadFragment = getDialpadFragment();
    return dialpadFragment != null && dialpadFragment.isVisible();
  }

  /** Returns the {@link DialpadFragment} that's shown by this activity, or {@code null} */
  @Nullable
  private DialpadFragment getDialpadFragment() {
    FragmentManager fragmentManager = inCallActivity.getDialpadFragmentManager();
    if (fragmentManager == null) {
      return null;
    }
    return (DialpadFragment) fragmentManager.findFragmentByTag(TAG_DIALPAD_FRAGMENT);
  }

  public void updateTaskDescription() {
    Resources resources = inCallActivity.getResources();
    int color;
    if (resources.getBoolean(R.bool.is_layout_landscape)) {
      color =
          ResourcesCompat.getColor(
              resources, R.color.statusbar_background_color, inCallActivity.getTheme());
    } else {
      color = InCallPresenter.getInstance().getThemeColorManager().getSecondaryColor();
    }

    TaskDescription td =
        new TaskDescription(resources.getString(R.string.notification_ongoing_call), null, color);
    inCallActivity.setTaskDescription(td);
  }

  public boolean hasPendingDialogs() {
    return dialog != null;
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

      dismissKeyguard(true);
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
            phoneAccountHandles,
            selectAccountListener,
            waitingForAccountCall.getId());
    selectPhoneAccountDialogFragment.show(
        inCallActivity.getFragmentManager(), TAG_SELECT_ACCOUNT_FRAGMENT);
    return true;
  }
}
