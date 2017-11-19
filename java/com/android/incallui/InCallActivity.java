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

import android.app.ActivityManager.TaskDescription;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.os.Bundle;
import android.os.Trace;
import android.support.annotation.ColorInt;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.ColorUtils;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.Toast;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.compat.ActivityCompat;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.LoggingBindings;
import com.android.dialer.logging.ScreenEvent;
import com.android.incallui.answer.bindings.AnswerBindings;
import com.android.incallui.answer.protocol.AnswerScreen;
import com.android.incallui.answer.protocol.AnswerScreenDelegate;
import com.android.incallui.answer.protocol.AnswerScreenDelegateFactory;
import com.android.incallui.answerproximitysensor.PseudoScreenState;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCall.State;
import com.android.incallui.call.TelecomAdapter;
import com.android.incallui.callpending.CallPendingActivity;
import com.android.incallui.disconnectdialog.DisconnectMessage;
import com.android.incallui.incall.bindings.InCallBindings;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.incall.protocol.InCallButtonUiDelegateFactory;
import com.android.incallui.incall.protocol.InCallScreen;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.InCallScreenDelegateFactory;
import com.android.incallui.incalluilock.InCallUiLock;
import com.android.incallui.telecomeventui.InternationalCallOnWifiDialogFragment;
import com.android.incallui.video.bindings.VideoBindings;
import com.android.incallui.video.protocol.VideoCallScreen;
import com.android.incallui.video.protocol.VideoCallScreenDelegate;
import com.android.incallui.video.protocol.VideoCallScreenDelegateFactory;

/** Version of {@link InCallActivity} that shows the new UI */
public class InCallActivity extends TransactionSafeFragmentActivity
    implements AnswerScreenDelegateFactory,
        InCallScreenDelegateFactory,
        InCallButtonUiDelegateFactory,
        VideoCallScreenDelegateFactory,
        PseudoScreenState.StateChangedListener {

  public static final int PENDING_INTENT_REQUEST_CODE_NON_FULL_SCREEN = 0;
  public static final int PENDING_INTENT_REQUEST_CODE_FULL_SCREEN = 1;
  public static final int PENDING_INTENT_REQUEST_CODE_BUBBLE = 2;

  private static final String DIALPAD_TEXT_KEY = "InCallActivity.dialpad_text";

  private static final String INTENT_EXTRA_SHOW_DIALPAD = "InCallActivity.show_dialpad";

  private static final String TAG_ANSWER_SCREEN = "tag_answer_screen";
  private static final String TAG_DIALPAD_FRAGMENT = "tag_dialpad_fragment";
  private static final String TAG_INTERNATIONAL_CALL_ON_WIFI = "tag_international_call_on_wifi";
  private static final String TAG_IN_CALL_SCREEN = "tag_in_call_screen";
  private static final String TAG_VIDEO_CALL_SCREEN = "tag_video_call_screen";

  private static final String DID_SHOW_ANSWER_SCREEN_KEY = "did_show_answer_screen";
  private static final String DID_SHOW_IN_CALL_SCREEN_KEY = "did_show_in_call_screen";
  private static final String DID_SHOW_VIDEO_CALL_SCREEN_KEY = "did_show_video_call_screen";

  private static final String CONFIG_ANSWER_AND_RELEASE_ENABLED = "answer_and_release_enabled";

  private final InCallActivityCommon common;
  private InCallOrientationEventListener inCallOrientationEventListener;
  private boolean didShowAnswerScreen;
  private boolean didShowInCallScreen;
  private boolean didShowVideoCallScreen;
  private boolean dismissKeyguard;
  private int[] backgroundDrawableColors;
  private GradientDrawable backgroundDrawable;
  private boolean isVisible;
  private View pseudoBlackScreenOverlay;
  private boolean touchDownWhenPseudoScreenOff;
  private boolean isInShowMainInCallFragment;
  private boolean needDismissPendingDialogs;
  private boolean allowOrientationChange;

  public InCallActivity() {
    common = new InCallActivityCommon(this);
  }

  public static Intent getIntent(
      Context context, boolean showDialpad, boolean newOutgoingCall, boolean isForFullScreen) {
    Intent intent = new Intent(Intent.ACTION_MAIN, null);
    intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.setClass(context, InCallActivity.class);
    InCallActivityCommon.setIntentExtras(intent, showDialpad, newOutgoingCall, isForFullScreen);
    return intent;
  }

  @Override
  protected void onResumeFragments() {
    super.onResumeFragments();
    if (needDismissPendingDialogs) {
      dismissPendingDialogs();
    }
  }

  @Override
  protected void onCreate(Bundle icicle) {
    Trace.beginSection("InCallActivity.onCreate");
    LogUtil.i("InCallActivity.onCreate", "");
    super.onCreate(icicle);

    if (getIntent().getBooleanExtra(ReturnToCallController.RETURN_TO_CALL_EXTRA_KEY, false)) {
      Logger.get(this).logImpression(DialerImpression.Type.BUBBLE_PRIMARY_BUTTON_RETURN_TO_CALL);
      getIntent().removeExtra(ReturnToCallController.RETURN_TO_CALL_EXTRA_KEY);
    }

    if (icicle != null) {
      didShowAnswerScreen = icicle.getBoolean(DID_SHOW_ANSWER_SCREEN_KEY);
      didShowInCallScreen = icicle.getBoolean(DID_SHOW_IN_CALL_SCREEN_KEY);
      didShowVideoCallScreen = icicle.getBoolean(DID_SHOW_VIDEO_CALL_SCREEN_KEY);
    }

    common.onCreate(icicle);
    inCallOrientationEventListener = new InCallOrientationEventListener(this);

    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

    pseudoBlackScreenOverlay = findViewById(R.id.psuedo_black_screen_overlay);
    sendBroadcast(CallPendingActivity.getFinishBroadcast());
    Trace.endSection();
    Logger.get(this)
        .logStopLatencyTimer(LoggingBindings.ON_CALL_ADDED_TO_ON_INCALL_UI_SHOWN_INCOMING);
    Logger.get(this)
        .logStopLatencyTimer(LoggingBindings.ON_CALL_ADDED_TO_ON_INCALL_UI_SHOWN_OUTGOING);
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    LogUtil.enterBlock("InCallActivity.onSaveInstanceState");

    // TODO: DialpadFragment should handle this as part of its own state
    out.putBoolean(INTENT_EXTRA_SHOW_DIALPAD, isDialpadVisible());
    DialpadFragment dialpadFragment = getDialpadFragment();
    if (dialpadFragment != null) {
      out.putString(DIALPAD_TEXT_KEY, dialpadFragment.getDtmfText());
    }

    out.putBoolean(DID_SHOW_ANSWER_SCREEN_KEY, didShowAnswerScreen);
    out.putBoolean(DID_SHOW_IN_CALL_SCREEN_KEY, didShowInCallScreen);
    out.putBoolean(DID_SHOW_VIDEO_CALL_SCREEN_KEY, didShowVideoCallScreen);

    super.onSaveInstanceState(out);
    isVisible = false;
  }

  @Override
  protected void onStart() {
    Trace.beginSection("InCallActivity.onStart");
    super.onStart();

    isVisible = true;
    showMainInCallFragment();

    InCallPresenter.getInstance().setActivity(this);
    enableInCallOrientationEventListener(
        getRequestedOrientation()
            == InCallOrientationEventListener.ACTIVITY_PREFERENCE_ALLOW_ROTATION);
    InCallPresenter.getInstance().onActivityStarted();

    if (ActivityCompat.isInMultiWindowMode(this)
        && !getResources().getBoolean(R.bool.incall_dialpad_allowed)) {
      // Hide the dialpad because there may not be enough room
      showDialpadFragment(false, false);
    }

    Trace.endSection();
  }

  @Override
  protected void onResume() {
    Trace.beginSection("InCallActivity.onResume");
    LogUtil.i("InCallActivity.onResume", "");
    super.onResume();
    common.onResume();
    PseudoScreenState pseudoScreenState = InCallPresenter.getInstance().getPseudoScreenState();
    pseudoScreenState.addListener(this);
    onPseudoScreenStateChanged(pseudoScreenState.isOn());
    Trace.endSection();
    // add 1 sec delay to get memory snapshot so that dialer wont react slowly on resume.
    ThreadUtil.postDelayedOnUiThread(
        () ->
            Logger.get(this)
                .logRecordMemory(LoggingBindings.INCALL_ACTIVITY_ON_RESUME_MEMORY_EVENT_NAME),
        1000);
  }

  @Override
  protected void onPause() {
    Trace.beginSection("InCallActivity.onPause");
    super.onPause();

    DialpadFragment dialpadFragment = getDialpadFragment();
    if (dialpadFragment != null) {
      dialpadFragment.onDialerKeyUp(null);
    }

    InCallPresenter.getInstance().onUiShowing(false);
    if (isFinishing()) {
      InCallPresenter.getInstance().unsetActivity(this);
    }

    InCallPresenter.getInstance().getPseudoScreenState().removeListener(this);
    Trace.endSection();
  }

  @Override
  protected void onStop() {
    Trace.beginSection("InCallActivity.onStop");
    isVisible = false;
    super.onStop();

    // Disconnects the call waiting for a phone account when the activity is hidden (e.g., after the
    // user presses the home button).
    // Without this the pending call will get stuck on phone account selection and new calls can't
    // be created.
    // Skip this when the screen is locked since the activity may complete its current life cycle
    // and restart.
    if (!common.getIsRecreating() && !getSystemService(KeyguardManager.class).isKeyguardLocked()) {
      DialerCall waitingForAccountCall = CallList.getInstance().getWaitingForAccountCall();
      if (waitingForAccountCall != null) {
        waitingForAccountCall.disconnect();
      }
    }

    enableInCallOrientationEventListener(false);
    InCallPresenter.getInstance().updateIsChangingConfigurations();
    InCallPresenter.getInstance().onActivityStopped();
    if (!common.getIsRecreating()) {
      Dialog errorDialog = common.getErrorDialog();
      if (errorDialog != null) {
        errorDialog.dismiss();
      }
    }

    Trace.endSection();
  }

  @Override
  protected void onDestroy() {
    Trace.beginSection("InCallActivity.onDestroy");
    super.onDestroy();

    InCallPresenter.getInstance().unsetActivity(this);
    InCallPresenter.getInstance().updateIsChangingConfigurations();
    Trace.endSection();
  }

  @Override
  public void finish() {
    if (shouldCloseActivityOnFinish()) {
      // When user select incall ui from recents after the call is disconnected, it tries to launch
      // a new InCallActivity but InCallPresenter is already teared down at this point, which causes
      // crash.
      // By calling finishAndRemoveTask() instead of finish() the task associated with
      // InCallActivity is cleared completely. So system won't try to create a new InCallActivity in
      // this case.
      //
      // Calling finish won't clear the task and normally when an activity finishes it shouldn't
      // clear the task since there could be parent activity in the same task that's still alive.
      // But InCallActivity is special since it's singleInstance which means it's root activity and
      // only instance of activity in the task. So it should be safe to also remove task when
      // finishing.
      // It's also necessary in the sense of it's excluded from recents. So whenever the activity
      // finishes, the task should also be removed since it doesn't make sense to go back to it in
      // anyway anymore.
      super.finishAndRemoveTask();
    }
  }

  private boolean shouldCloseActivityOnFinish() {
    if (!isVisible()) {
      LogUtil.i(
          "InCallActivity.shouldCloseActivityOnFinish",
          "allowing activity to be closed because it's not visible");
      return true;
    }

    if (InCallPresenter.getInstance().isInCallUiLocked()) {
      LogUtil.i(
          "InCallActivity.shouldCloseActivityOnFinish",
          "in call ui is locked, not closing activity");
      return false;
    }

    LogUtil.i(
        "InCallActivity.shouldCloseActivityOnFinish",
        "activity is visible and has no locks, allowing activity to close");
    return true;
  }

  @Override
  protected void onNewIntent(Intent intent) {
    LogUtil.i("InCallActivity.onNewIntent", "");

    // If the screen is off, we need to make sure it gets turned on for incoming calls.
    // This normally works just fine thanks to FLAG_TURN_SCREEN_ON but that only works
    // when the activity is first created. Therefore, to ensure the screen is turned on
    // for the call waiting case, we recreate() the current activity. There should be no jank from
    // this since the screen is already off and will remain so until our new activity is up.
    if (!isVisible()) {
      common.onNewIntent(intent, true /* isRecreating */);
      LogUtil.i("InCallActivity.onNewIntent", "Restarting InCallActivity to force screen on.");
      recreate();
    } else {
      common.onNewIntent(intent, false /* isRecreating */);
    }
  }

  @Override
  public void onBackPressed() {
    LogUtil.enterBlock("InCallActivity.onBackPressed");

    if (!isVisible()) {
      return;
    }

    if (!getCallCardFragmentVisible()) {
      return;
    }

    DialpadFragment dialpadFragment = getDialpadFragment();
    if (dialpadFragment != null && dialpadFragment.isVisible()) {
      showDialpadFragment(false /* show */, true /* animate */);
      return;
    }

    if (CallList.getInstance().getIncomingCall() != null) {
      LogUtil.i(
          "InCallActivity.onBackPressed",
          "Ignore the press of the back key when an incoming call is ringing");
      return;
    }

    // Nothing special to do. Fall back to the default behavior.
    super.onBackPressed();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    LogUtil.i("InCallActivity.onOptionsItemSelected", "item: " + item);
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    DialpadFragment dialpadFragment = getDialpadFragment();
    if (dialpadFragment != null
        && dialpadFragment.isVisible()
        && dialpadFragment.onDialerKeyUp(event)) {
      return true;
    }

    if (keyCode == KeyEvent.KEYCODE_CALL) {
      // Always consume KEYCODE_CALL to ensure the PhoneWindow won't do anything with it.
      return true;
    }

    return super.onKeyUp(keyCode, event);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_CALL:
        if (!InCallPresenter.getInstance().handleCallKey()) {
          LogUtil.e(
              "InCallActivity.onKeyDown",
              "InCallPresenter should always handle KEYCODE_CALL in onKeyDown");
        }
        // Always consume KEYCODE_CALL to ensure the PhoneWindow won't do anything with it.
        return true;

        // Note that KEYCODE_ENDCALL isn't handled here as the standard system-wide handling of it
        // is exactly what's needed, namely
        // (1) "hang up" if there's an active call, or
        // (2) "don't answer" if there's an incoming call.
        // (See PhoneWindowManager for implementation details.)

      case KeyEvent.KEYCODE_CAMERA:
        // Consume KEYCODE_CAMERA since it's easy to accidentally press the camera button.
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

      case KeyEvent.KEYCODE_SLASH:
        // When verbose logging is enabled, dump the view for debugging/testing purposes.
        if (LogUtil.isVerboseEnabled()) {
          View decorView = getWindow().getDecorView();
          LogUtil.v("InCallActivity.onKeyDown", "View dump:\n%s", decorView);
          return true;
        }
        break;

      case KeyEvent.KEYCODE_EQUALS:
        break;

      default: // fall out
    }

    // Pass other key events to DialpadFragment's "onDialerKeyDown" method in case the user types
    // in DTMF (Dual-tone multi-frequency signaling) code.
    DialpadFragment dialpadFragment = getDialpadFragment();
    if (dialpadFragment != null
        && dialpadFragment.isVisible()
        && dialpadFragment.onDialerKeyDown(event)) {
      return true;
    }

    return super.onKeyDown(keyCode, event);
  }

  public boolean isInCallScreenAnimating() {
    return false;
  }

  public void showConferenceFragment(boolean show) {
    if (show) {
      startActivity(new Intent(this, ManageConferenceActivity.class));
    }
  }

  public boolean showDialpadFragment(boolean show, boolean animate) {
    boolean didChange = common.showDialpadFragment(show, animate);
    if (didChange) {
      // Note:  onInCallScreenDialpadVisibilityChange is called here to ensure that the dialpad FAB
      // repositions itself.
      getInCallScreen().onInCallScreenDialpadVisibilityChange(show);
    }
    return didChange;
  }

  public boolean isDialpadVisible() {
    DialpadFragment dialpadFragment = getDialpadFragment();
    return dialpadFragment != null && dialpadFragment.isVisible();
  }

  /**
   * Returns the {@link DialpadFragment} that's shown by this activity, or {@code null}
   * TODO(a bug): Make this method private after InCallActivityCommon is deleted.
   */
  @Nullable
  DialpadFragment getDialpadFragment() {
    FragmentManager fragmentManager = getDialpadFragmentManager();
    if (fragmentManager == null) {
      return null;
    }
    return (DialpadFragment) fragmentManager.findFragmentByTag(TAG_DIALPAD_FRAGMENT);
  }

  public void onForegroundCallChanged(DialerCall newForegroundCall) {
    updateTaskDescription();

    if (newForegroundCall == null || !didShowAnswerScreen) {
      LogUtil.v("InCallActivity.onForegroundCallChanged", "resetting background color");
      updateWindowBackgroundColor(0 /* progress */);
    }
  }

  // TODO(a bug): Make this method private after InCallActivityCommon is deleted.
  void updateTaskDescription() {
    int color =
        getResources().getBoolean(R.bool.is_layout_landscape)
            ? ResourcesCompat.getColor(
                getResources(), R.color.statusbar_background_color, getTheme())
            : InCallPresenter.getInstance().getThemeColorManager().getSecondaryColor();
    setTaskDescription(
        new TaskDescription(
            getResources().getString(R.string.notification_ongoing_call), null /* icon */, color));
  }

  public void updateWindowBackgroundColor(@FloatRange(from = -1f, to = 1.0f) float progress) {
    ThemeColorManager themeColorManager = InCallPresenter.getInstance().getThemeColorManager();
    @ColorInt int top;
    @ColorInt int middle;
    @ColorInt int bottom;
    @ColorInt int gray = 0x66000000;

    if (ActivityCompat.isInMultiWindowMode(this)) {
      top = themeColorManager.getBackgroundColorSolid();
      middle = themeColorManager.getBackgroundColorSolid();
      bottom = themeColorManager.getBackgroundColorSolid();
    } else {
      top = themeColorManager.getBackgroundColorTop();
      middle = themeColorManager.getBackgroundColorMiddle();
      bottom = themeColorManager.getBackgroundColorBottom();
    }

    if (progress < 0) {
      float correctedProgress = Math.abs(progress);
      top = ColorUtils.blendARGB(top, gray, correctedProgress);
      middle = ColorUtils.blendARGB(middle, gray, correctedProgress);
      bottom = ColorUtils.blendARGB(bottom, gray, correctedProgress);
    }

    boolean backgroundDirty = false;
    if (backgroundDrawable == null) {
      backgroundDrawableColors = new int[] {top, middle, bottom};
      backgroundDrawable = new GradientDrawable(Orientation.TOP_BOTTOM, backgroundDrawableColors);
      backgroundDirty = true;
    } else {
      if (backgroundDrawableColors[0] != top) {
        backgroundDrawableColors[0] = top;
        backgroundDirty = true;
      }
      if (backgroundDrawableColors[1] != middle) {
        backgroundDrawableColors[1] = middle;
        backgroundDirty = true;
      }
      if (backgroundDrawableColors[2] != bottom) {
        backgroundDrawableColors[2] = bottom;
        backgroundDirty = true;
      }
      if (backgroundDirty) {
        backgroundDrawable.setColors(backgroundDrawableColors);
      }
    }

    if (backgroundDirty) {
      getWindow().setBackgroundDrawable(backgroundDrawable);
    }
  }

  public boolean isVisible() {
    return isVisible;
  }

  public boolean getCallCardFragmentVisible() {
    return didShowInCallScreen || didShowVideoCallScreen;
  }

  public void dismissKeyguard(boolean dismiss) {
    if (dismissKeyguard == dismiss) {
      return;
    }

    dismissKeyguard = dismiss;
    if (dismiss) {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    } else {
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }
  }

  public void showPostCharWaitDialog(String callId, String chars) {
    common.showPostCharWaitDialog(callId, chars);
  }

  public void showDialogOrToastForDisconnectedCall(DisconnectMessage disconnectMessage) {
    LogUtil.i(
        "InCallActivity.showDialogOrToastForDisconnectedCall",
        "disconnect cause: %s",
        disconnectMessage);

    if (disconnectMessage.dialog == null || isFinishing()) {
      return;
    }

    dismissPendingDialogs();

    // Show a toast if the app is in background when a dialog can't be visible.
    if (!isVisible()) {
      Toast.makeText(getApplicationContext(), disconnectMessage.toastMessage, Toast.LENGTH_LONG)
          .show();
      return;
    }

    // Show the dialog.
    common.setErrorDialog(disconnectMessage.dialog);
    InCallUiLock lock = InCallPresenter.getInstance().acquireInCallUiLock("showErrorDialog");
    disconnectMessage.dialog.setOnDismissListener(
        dialogInterface -> {
          lock.release();
          onDialogDismissed();
        });
    disconnectMessage.dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    disconnectMessage.dialog.show();
  }

  private void onDialogDismissed() {
    common.setErrorDialog(null);
    CallList.getInstance().onErrorDialogDismissed();
  }

  public void dismissPendingDialogs() {
    LogUtil.i("InCallActivity.dismissPendingDialogs", "");

    if (!isVisible) {
      // Defer the dismissing action as the activity is not visible and onSaveInstanceState may have
      // been called.
      LogUtil.i(
          "InCallActivity.dismissPendingDialogs", "defer actions since activity is not visible");
      needDismissPendingDialogs = true;
      return;
    }

    // Dismiss the error dialog
    Dialog errorDialog = common.getErrorDialog();
    if (errorDialog != null) {
      errorDialog.dismiss();
      common.setErrorDialog(null);
    }

    // Dismiss the phone account selection dialog
    SelectPhoneAccountDialogFragment selectPhoneAccountDialogFragment =
        common.getSelectPhoneAccountDialogFragment();
    if (selectPhoneAccountDialogFragment != null) {
      selectPhoneAccountDialogFragment.dismiss();
      common.setSelectPhoneAccountDialogFragment(null);
    }

    // Dismiss the dialog for international call on WiFi
    InternationalCallOnWifiDialogFragment internationalCallOnWifiFragment =
        (InternationalCallOnWifiDialogFragment)
            getSupportFragmentManager().findFragmentByTag(TAG_INTERNATIONAL_CALL_ON_WIFI);
    if (internationalCallOnWifiFragment != null) {
      internationalCallOnWifiFragment.dismiss();
    }

    // Dismiss the answer screen
    AnswerScreen answerScreen = getAnswerScreen();
    if (answerScreen != null) {
      answerScreen.dismissPendingDialogs();
    }

    needDismissPendingDialogs = false;
  }

  // TODO(a bug): Make this method private after InCallActivityCommon is deleted.
  void enableInCallOrientationEventListener(boolean enable) {
    if (enable) {
      inCallOrientationEventListener.enable(true /* notifyDeviceOrientationChange */);
    } else {
      inCallOrientationEventListener.disable();
    }
  }

  public void setExcludeFromRecents(boolean exclude) {
    common.setExcludeFromRecents(exclude);
  }

  @Nullable
  public FragmentManager getDialpadFragmentManager() {
    InCallScreen inCallScreen = getInCallScreen();
    if (inCallScreen != null) {
      return inCallScreen.getInCallScreenFragment().getChildFragmentManager();
    }
    return null;
  }

  public int getDialpadContainerId() {
    return getInCallScreen().getAnswerAndDialpadContainerResourceId();
  }

  @Override
  public AnswerScreenDelegate newAnswerScreenDelegate(AnswerScreen answerScreen) {
    DialerCall call = CallList.getInstance().getCallById(answerScreen.getCallId());
    if (call == null) {
      // This is a work around for a bug where we attempt to create a new delegate after the call
      // has already been removed. An example of when this can happen is:
      // 1. incoming video call in landscape mode
      // 2. remote party hangs up
      // 3. activity switches from landscape to portrait
      // At step #3 the answer fragment will try to create a new answer delegate but the call won't
      // exist. In this case we'll simply return a stub delegate that does nothing. This is ok
      // because this new state is transient and the activity will be destroyed soon.
      LogUtil.i("InCallActivity.onPrimaryCallStateChanged", "call doesn't exist, using stub");
      return new AnswerScreenPresenterStub();
    } else {
      return new AnswerScreenPresenter(
          this, answerScreen, CallList.getInstance().getCallById(answerScreen.getCallId()));
    }
  }

  @Override
  public InCallScreenDelegate newInCallScreenDelegate() {
    return new CallCardPresenter(this);
  }

  @Override
  public InCallButtonUiDelegate newInCallButtonUiDelegate() {
    return new CallButtonPresenter(this);
  }

  @Override
  public VideoCallScreenDelegate newVideoCallScreenDelegate(VideoCallScreen videoCallScreen) {
    DialerCall dialerCall = CallList.getInstance().getCallById(videoCallScreen.getCallId());
    if (dialerCall != null && dialerCall.getVideoTech().shouldUseSurfaceView()) {
      return dialerCall.getVideoTech().createVideoCallScreenDelegate(this, videoCallScreen);
    }
    return new VideoCallPresenter();
  }

  public void onPrimaryCallStateChanged() {
    Trace.beginSection("InCallActivity.onPrimaryCallStateChanged");
    LogUtil.i("InCallActivity.onPrimaryCallStateChanged", "");
    showMainInCallFragment();
    Trace.endSection();
  }

  public void showToastForWiFiToLteHandover(DialerCall call) {
    if (call.hasShownWiFiToLteHandoverToast()) {
      return;
    }

    Toast.makeText(this, R.string.video_call_wifi_to_lte_handover_toast, Toast.LENGTH_LONG).show();
    call.setHasShownWiFiToLteHandoverToast();
  }

  public void showDialogOrToastForWifiHandoverFailure(DialerCall call) {
    if (call.showWifiHandoverAlertAsToast()) {
      Toast.makeText(this, R.string.video_call_lte_to_wifi_failed_message, Toast.LENGTH_SHORT)
          .show();
      return;
    }

    dismissPendingDialogs();

    AlertDialog.Builder builder =
        new AlertDialog.Builder(this).setTitle(R.string.video_call_lte_to_wifi_failed_title);

    // This allows us to use the theme of the dialog instead of the activity
    View dialogCheckBoxView =
        View.inflate(builder.getContext(), R.layout.video_call_lte_to_wifi_failed, null /* root */);
    CheckBox wifiHandoverFailureCheckbox =
        (CheckBox) dialogCheckBoxView.findViewById(R.id.video_call_lte_to_wifi_failed_checkbox);
    wifiHandoverFailureCheckbox.setChecked(false);

    InCallUiLock lock = InCallPresenter.getInstance().acquireInCallUiLock("WifiFailedDialog");
    Dialog errorDialog =
        builder
            .setView(dialogCheckBoxView)
            .setMessage(R.string.video_call_lte_to_wifi_failed_message)
            .setOnCancelListener(dialogInterface -> onDialogDismissed())
            .setPositiveButton(
                android.R.string.ok,
                (dialogInterface, id) -> {
                  call.setDoNotShowDialogForHandoffToWifiFailure(
                      wifiHandoverFailureCheckbox.isChecked());
                  dialogInterface.cancel();
                  onDialogDismissed();
                })
            .setOnDismissListener(dialogInterface -> lock.release())
            .create();

    common.setErrorDialog(errorDialog);
    errorDialog.show();
  }

  public void showDialogForInternationalCallOnWifi(@NonNull DialerCall call) {
    if (!InternationalCallOnWifiDialogFragment.shouldShow(this)) {
      LogUtil.i(
          "InCallActivity.showDialogForInternationalCallOnWifi",
          "InternationalCallOnWifiDialogFragment.shouldShow returned false");
      return;
    }

    InternationalCallOnWifiDialogFragment fragment =
        InternationalCallOnWifiDialogFragment.newInstance(
            call.getId(), common.getCallbackForInternationalCallOnWifiDialog());
    fragment.show(getSupportFragmentManager(), TAG_INTERNATIONAL_CALL_ON_WIFI);
  }

  @Override
  public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
    super.onMultiWindowModeChanged(isInMultiWindowMode);
    updateNavigationBar(isDialpadVisible());
  }

  // TODO(a bug): Make this method private after InCallActivityCommon is deleted.
  void updateNavigationBar(boolean isDialpadVisible) {
    if (ActivityCompat.isInMultiWindowMode(this)) {
      return;
    }

    View navigationBarBackground = getWindow().findViewById(R.id.navigation_bar_background);
    if (navigationBarBackground != null) {
      navigationBarBackground.setVisibility(isDialpadVisible ? View.VISIBLE : View.GONE);
    }
  }

  public void setAllowOrientationChange(boolean allowOrientationChange) {
    if (this.allowOrientationChange == allowOrientationChange) {
      return;
    }
    this.allowOrientationChange = allowOrientationChange;
    if (!allowOrientationChange) {
      setRequestedOrientation(InCallOrientationEventListener.ACTIVITY_PREFERENCE_DISALLOW_ROTATION);
    } else {
      setRequestedOrientation(InCallOrientationEventListener.ACTIVITY_PREFERENCE_ALLOW_ROTATION);
    }
    enableInCallOrientationEventListener(allowOrientationChange);
  }

  public void hideMainInCallFragment() {
    LogUtil.i("InCallActivity.hideMainInCallFragment", "");
    if (didShowInCallScreen || didShowVideoCallScreen) {
      FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
      hideInCallScreenFragment(transaction);
      hideVideoCallScreenFragment(transaction);
      transaction.commitAllowingStateLoss();
      getSupportFragmentManager().executePendingTransactions();
    }
  }

  private void showMainInCallFragment() {
    Trace.beginSection("InCallActivity.showMainInCallFragment");
    // If the activity's onStart method hasn't been called yet then defer doing any work.
    if (!isVisible) {
      LogUtil.i("InCallActivity.showMainInCallFragment", "not visible yet/anymore");
      Trace.endSection();
      return;
    }

    // Don't let this be reentrant.
    if (isInShowMainInCallFragment) {
      LogUtil.i("InCallActivity.showMainInCallFragment", "already in method, bailing");
      Trace.endSection();
      return;
    }

    isInShowMainInCallFragment = true;
    ShouldShowUiResult shouldShowAnswerUi = getShouldShowAnswerUi();
    ShouldShowUiResult shouldShowVideoUi = getShouldShowVideoUi();
    LogUtil.i(
        "InCallActivity.showMainInCallFragment",
        "shouldShowAnswerUi: %b, shouldShowVideoUi: %b, "
            + "didShowAnswerScreen: %b, didShowInCallScreen: %b, didShowVideoCallScreen: %b",
        shouldShowAnswerUi.shouldShow,
        shouldShowVideoUi.shouldShow,
        didShowAnswerScreen,
        didShowInCallScreen,
        didShowVideoCallScreen);
    // Only video call ui allows orientation change.
    setAllowOrientationChange(shouldShowVideoUi.shouldShow);

    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
    boolean didChangeInCall;
    boolean didChangeVideo;
    boolean didChangeAnswer;
    if (shouldShowAnswerUi.shouldShow) {
      didChangeInCall = hideInCallScreenFragment(transaction);
      didChangeVideo = hideVideoCallScreenFragment(transaction);
      didChangeAnswer = showAnswerScreenFragment(transaction, shouldShowAnswerUi.call);
    } else if (shouldShowVideoUi.shouldShow) {
      didChangeInCall = hideInCallScreenFragment(transaction);
      didChangeVideo = showVideoCallScreenFragment(transaction, shouldShowVideoUi.call);
      didChangeAnswer = hideAnswerScreenFragment(transaction);
    } else {
      didChangeInCall = showInCallScreenFragment(transaction);
      didChangeVideo = hideVideoCallScreenFragment(transaction);
      didChangeAnswer = hideAnswerScreenFragment(transaction);
    }

    if (didChangeInCall || didChangeVideo || didChangeAnswer) {
      Trace.beginSection("InCallActivity.commitTransaction");
      transaction.commitNow();
      Trace.endSection();
      Logger.get(this).logScreenView(ScreenEvent.Type.INCALL, this);
    }
    isInShowMainInCallFragment = false;
    Trace.endSection();
  }

  private ShouldShowUiResult getShouldShowAnswerUi() {
    DialerCall call = CallList.getInstance().getIncomingCall();
    if (call != null) {
      LogUtil.i("InCallActivity.getShouldShowAnswerUi", "found incoming call");
      return new ShouldShowUiResult(true, call);
    }

    call = CallList.getInstance().getVideoUpgradeRequestCall();
    if (call != null) {
      LogUtil.i("InCallActivity.getShouldShowAnswerUi", "found video upgrade request");
      return new ShouldShowUiResult(true, call);
    }

    // Check if we're showing the answer screen and the call is disconnected. If this condition is
    // true then we won't switch from the answer UI to the in call UI. This prevents flicker when
    // the user rejects an incoming call.
    call = CallList.getInstance().getFirstCall();
    if (call == null) {
      call = CallList.getInstance().getBackgroundCall();
    }
    if (didShowAnswerScreen && (call == null || call.getState() == State.DISCONNECTED)) {
      LogUtil.i("InCallActivity.getShouldShowAnswerUi", "found disconnecting incoming call");
      return new ShouldShowUiResult(true, call);
    }

    return new ShouldShowUiResult(false, null);
  }

  private static ShouldShowUiResult getShouldShowVideoUi() {
    DialerCall call = CallList.getInstance().getFirstCall();
    if (call == null) {
      LogUtil.i("InCallActivity.getShouldShowVideoUi", "null call");
      return new ShouldShowUiResult(false, null);
    }

    if (call.isVideoCall()) {
      LogUtil.i("InCallActivity.getShouldShowVideoUi", "found video call");
      return new ShouldShowUiResult(true, call);
    }

    if (call.hasSentVideoUpgradeRequest()) {
      LogUtil.i("InCallActivity.getShouldShowVideoUi", "upgrading to video");
      return new ShouldShowUiResult(true, call);
    }

    return new ShouldShowUiResult(false, null);
  }

  private boolean showAnswerScreenFragment(FragmentTransaction transaction, DialerCall call) {
    // When rejecting a call the active call can become null in which case we should continue
    // showing the answer screen.
    if (didShowAnswerScreen && call == null) {
      return false;
    }

    Assert.checkArgument(call != null, "didShowAnswerScreen was false but call was still null");

    boolean isVideoUpgradeRequest = call.hasReceivedVideoUpgradeRequest();

    // Check if we're already showing an answer screen for this call.
    if (didShowAnswerScreen) {
      AnswerScreen answerScreen = getAnswerScreen();
      if (answerScreen.getCallId().equals(call.getId())
          && answerScreen.isVideoCall() == call.isVideoCall()
          && answerScreen.isVideoUpgradeRequest() == isVideoUpgradeRequest
          && !answerScreen.isActionTimeout()) {
        LogUtil.d(
            "InCallActivity.showAnswerScreenFragment",
            "answer fragment exists for same call and has NOT been accepted/rejected/timed out");
        return false;
      }
      if (answerScreen.isActionTimeout()) {
        LogUtil.i(
            "InCallActivity.showAnswerScreenFragment",
            "answer fragment exists but has been accepted/rejected and timed out");
      } else {
        LogUtil.i(
            "InCallActivity.showAnswerScreenFragment",
            "answer fragment exists but arguments do not match");
      }
      hideAnswerScreenFragment(transaction);
    }

    // Show a new answer screen.
    AnswerScreen answerScreen =
        AnswerBindings.createAnswerScreen(
            call.getId(),
            call.isVideoCall(),
            isVideoUpgradeRequest,
            call.getVideoTech().isSelfManagedCamera(),
            shouldAllowAnswerAndRelease(call),
            CallList.getInstance().getBackgroundCall() != null);
    transaction.add(R.id.main, answerScreen.getAnswerScreenFragment(), TAG_ANSWER_SCREEN);

    Logger.get(this).logScreenView(ScreenEvent.Type.INCOMING_CALL, this);
    didShowAnswerScreen = true;
    return true;
  }

  private boolean shouldAllowAnswerAndRelease(DialerCall call) {
    if (CallList.getInstance().getActiveCall() == null) {
      LogUtil.i("InCallActivity.shouldAllowAnswerAndRelease", "no active call");
      return false;
    }
    if (getSystemService(TelephonyManager.class).getPhoneType()
        == TelephonyManager.PHONE_TYPE_CDMA) {
      LogUtil.i("InCallActivity.shouldAllowAnswerAndRelease", "PHONE_TYPE_CDMA not supported");
      return false;
    }
    if (call.isVideoCall() || call.hasReceivedVideoUpgradeRequest()) {
      LogUtil.i("InCallActivity.shouldAllowAnswerAndRelease", "video call");
      return false;
    }
    if (!ConfigProviderBindings.get(this).getBoolean(CONFIG_ANSWER_AND_RELEASE_ENABLED, true)) {
      LogUtil.i("InCallActivity.shouldAllowAnswerAndRelease", "disabled by config");
      return false;
    }

    return true;
  }

  private boolean hideAnswerScreenFragment(FragmentTransaction transaction) {
    if (!didShowAnswerScreen) {
      return false;
    }
    AnswerScreen answerScreen = getAnswerScreen();
    if (answerScreen != null) {
      transaction.remove(answerScreen.getAnswerScreenFragment());
    }

    didShowAnswerScreen = false;
    return true;
  }

  private boolean showInCallScreenFragment(FragmentTransaction transaction) {
    if (didShowInCallScreen) {
      return false;
    }
    InCallScreen inCallScreen = InCallBindings.createInCallScreen();
    transaction.add(R.id.main, inCallScreen.getInCallScreenFragment(), TAG_IN_CALL_SCREEN);
    Logger.get(this).logScreenView(ScreenEvent.Type.INCALL, this);
    didShowInCallScreen = true;
    return true;
  }

  private boolean hideInCallScreenFragment(FragmentTransaction transaction) {
    if (!didShowInCallScreen) {
      return false;
    }
    InCallScreen inCallScreen = getInCallScreen();
    if (inCallScreen != null) {
      transaction.remove(inCallScreen.getInCallScreenFragment());
    }
    didShowInCallScreen = false;
    return true;
  }

  private boolean showVideoCallScreenFragment(FragmentTransaction transaction, DialerCall call) {
    if (didShowVideoCallScreen) {
      VideoCallScreen videoCallScreen = getVideoCallScreen();
      if (videoCallScreen.getCallId().equals(call.getId())) {
        return false;
      }
      LogUtil.i(
          "InCallActivity.showVideoCallScreenFragment",
          "video call fragment exists but arguments do not match");
      hideVideoCallScreenFragment(transaction);
    }

    LogUtil.i("InCallActivity.showVideoCallScreenFragment", "call: %s", call);

    VideoCallScreen videoCallScreen =
        VideoBindings.createVideoCallScreen(
            call.getId(), call.getVideoTech().shouldUseSurfaceView());
    transaction.add(R.id.main, videoCallScreen.getVideoCallScreenFragment(), TAG_VIDEO_CALL_SCREEN);

    Logger.get(this).logScreenView(ScreenEvent.Type.INCALL, this);
    didShowVideoCallScreen = true;
    return true;
  }

  private boolean hideVideoCallScreenFragment(FragmentTransaction transaction) {
    if (!didShowVideoCallScreen) {
      return false;
    }
    VideoCallScreen videoCallScreen = getVideoCallScreen();
    if (videoCallScreen != null) {
      transaction.remove(videoCallScreen.getVideoCallScreenFragment());
    }
    didShowVideoCallScreen = false;
    return true;
  }

  AnswerScreen getAnswerScreen() {
    return (AnswerScreen) getSupportFragmentManager().findFragmentByTag(TAG_ANSWER_SCREEN);
  }

  InCallScreen getInCallScreen() {
    return (InCallScreen) getSupportFragmentManager().findFragmentByTag(TAG_IN_CALL_SCREEN);
  }

  VideoCallScreen getVideoCallScreen() {
    return (VideoCallScreen) getSupportFragmentManager().findFragmentByTag(TAG_VIDEO_CALL_SCREEN);
  }

  @Override
  public void onPseudoScreenStateChanged(boolean isOn) {
    LogUtil.i("InCallActivity.onPseudoScreenStateChanged", "isOn: " + isOn);
    pseudoBlackScreenOverlay.setVisibility(isOn ? View.GONE : View.VISIBLE);
  }

  /**
   * For some touch related issue, turning off the screen can be faked by drawing a black view over
   * the activity. All touch events started when the screen is "off" is rejected.
   *
   * @see PseudoScreenState
   */
  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    // Reject any gesture that started when the screen is in the fake off state.
    if (touchDownWhenPseudoScreenOff) {
      if (event.getAction() == MotionEvent.ACTION_UP) {
        touchDownWhenPseudoScreenOff = false;
      }
      return true;
    }
    // Reject all touch event when the screen is in the fake off state.
    if (!InCallPresenter.getInstance().getPseudoScreenState().isOn()) {
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
        touchDownWhenPseudoScreenOff = true;
        LogUtil.i("InCallActivity.dispatchTouchEvent", "touchDownWhenPseudoScreenOff");
      }
      return true;
    }
    return super.dispatchTouchEvent(event);
  }

  private static class ShouldShowUiResult {
    public final boolean shouldShow;
    public final DialerCall call;

    ShouldShowUiResult(boolean shouldShow, DialerCall call) {
      this.shouldShow = shouldShow;
      this.call = call;
    }
  }
}
