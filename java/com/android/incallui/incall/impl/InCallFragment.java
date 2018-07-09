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

package com.android.incallui.incall.impl;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.telecom.CallAudioState;
import android.telephony.TelephonyManager;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.multimedia.MultimediaData;
import com.android.dialer.strictmode.StrictModeUtils;
import com.android.dialer.widget.LockableViewPager;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment.AudioRouteSelectorPresenter;
import com.android.incallui.contactgrid.ContactGridManager;
import com.android.incallui.hold.OnHoldFragment;
import com.android.incallui.incall.impl.ButtonController.CallRecordButtonController;
import com.android.incallui.incall.impl.ButtonController.SpeakerButtonController;
import com.android.incallui.incall.impl.ButtonController.UpgradeToRttButtonController;
import com.android.incallui.incall.impl.InCallButtonGridFragment.OnButtonGridCreatedListener;
import com.android.incallui.incall.protocol.InCallButtonIds;
import com.android.incallui.incall.protocol.InCallButtonIdsExtension;
import com.android.incallui.incall.protocol.InCallButtonUi;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.incall.protocol.InCallButtonUiDelegateFactory;
import com.android.incallui.incall.protocol.InCallScreen;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.InCallScreenDelegateFactory;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryCallState.ButtonState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.incall.protocol.SecondaryInfo;
import java.util.ArrayList;
import java.util.List;

/** Fragment that shows UI for an ongoing voice call. */
public class InCallFragment extends Fragment
    implements InCallScreen,
        InCallButtonUi,
        OnClickListener,
        AudioRouteSelectorPresenter,
        OnButtonGridCreatedListener {

  private List<ButtonController> buttonControllers = new ArrayList<>();
  private View endCallButton;
  private InCallPaginator paginator;
  private LockableViewPager pager;
  private InCallPagerAdapter adapter;
  private ContactGridManager contactGridManager;
  private InCallScreenDelegate inCallScreenDelegate;
  private InCallButtonUiDelegate inCallButtonUiDelegate;
  private InCallButtonGridFragment inCallButtonGridFragment;
  @Nullable private ButtonChooser buttonChooser;
  private SecondaryInfo savedSecondaryInfo;
  private int voiceNetworkType;
  private int phoneType;
  private boolean stateRestored;

  private static final int REQUEST_CODE_CALL_RECORD_PERMISSION = 1000;

  // Add animation to educate users. If a call has enriched calling attachments then we'll
  // initially show the attachment page. After a delay seconds we'll animate to the button grid.
  private final Handler handler = new Handler();
  private final Runnable pagerRunnable =
      new Runnable() {
        @Override
        public void run() {
          pager.setCurrentItem(adapter.getButtonGridPosition());
        }
      };

  private static boolean isSupportedButton(@InCallButtonIds int id) {
    return id == InCallButtonIds.BUTTON_AUDIO
        || id == InCallButtonIds.BUTTON_MUTE
        || id == InCallButtonIds.BUTTON_DIALPAD
        || id == InCallButtonIds.BUTTON_HOLD
        || id == InCallButtonIds.BUTTON_SWAP
        || id == InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO
        || id == InCallButtonIds.BUTTON_ADD_CALL
        || id == InCallButtonIds.BUTTON_MERGE
        || id == InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE
        || id == InCallButtonIds.BUTTON_SWAP_SIM
        || id == InCallButtonIds.BUTTON_UPGRADE_TO_RTT
        || id == InCallButtonIds.BUTTON_RECORD_CALL;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if (savedSecondaryInfo != null) {
      setSecondary(savedSecondaryInfo);
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    inCallButtonUiDelegate =
        FragmentUtils.getParent(this, InCallButtonUiDelegateFactory.class)
            .newInCallButtonUiDelegate();
    if (savedInstanceState != null) {
      inCallButtonUiDelegate.onRestoreInstanceState(savedInstanceState);
      stateRestored = true;
    }
  }

  @Nullable
  @Override
  @SuppressLint("MissingPermission")
  public View onCreateView(
      @NonNull LayoutInflater layoutInflater,
      @Nullable ViewGroup viewGroup,
      @Nullable Bundle bundle) {
    LogUtil.i("InCallFragment.onCreateView", null);
    getActivity().setTheme(R.style.Theme_InCallScreen);
    // Bypass to avoid StrictModeResourceMismatchViolation
    final View view =
        StrictModeUtils.bypass(
            () -> layoutInflater.inflate(R.layout.frag_incall_voice, viewGroup, false));
    contactGridManager =
        new ContactGridManager(
            view,
            (ImageView) view.findViewById(R.id.contactgrid_avatar),
            getResources().getDimensionPixelSize(R.dimen.incall_avatar_size),
            true /* showAnonymousAvatar */);
    contactGridManager.onMultiWindowModeChanged(getActivity().isInMultiWindowMode());

    paginator = (InCallPaginator) view.findViewById(R.id.incall_paginator);
    pager = (LockableViewPager) view.findViewById(R.id.incall_pager);
    pager.setOnTouchListener(
        (v, event) -> {
          handler.removeCallbacks(pagerRunnable);
          return false;
        });

    endCallButton = view.findViewById(R.id.incall_end_call);
    endCallButton.setOnClickListener(this);

    if (ContextCompat.checkSelfPermission(getContext(), permission.READ_PHONE_STATE)
        != PackageManager.PERMISSION_GRANTED) {
      voiceNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    } else {
      voiceNetworkType =
          getContext().getSystemService(TelephonyManager.class).getVoiceNetworkType();
    }
    // TODO(a bug): Change to use corresponding phone type used for current call.
    phoneType = getContext().getSystemService(TelephonyManager.class).getPhoneType();

    // Workaround to adjust padding for status bar and navigation bar since fitsSystemWindows
    // doesn't work well when switching with other fragments.
    view.addOnAttachStateChangeListener(
        new OnAttachStateChangeListener() {
          @Override
          public void onViewAttachedToWindow(View v) {
            View container = v.findViewById(R.id.incall_ui_container);
            int topInset = v.getRootWindowInsets().getSystemWindowInsetTop();
            int bottomInset = v.getRootWindowInsets().getSystemWindowInsetBottom();
            if (topInset != container.getPaddingTop()) {
              TransitionManager.beginDelayedTransition(((ViewGroup) container.getParent()));
              container.setPadding(0, topInset, 0, bottomInset);
            }
          }

          @Override
          public void onViewDetachedFromWindow(View v) {}
        });
    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
    inCallScreenDelegate.onInCallScreenResumed();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle bundle) {
    LogUtil.i("InCallFragment.onViewCreated", null);
    super.onViewCreated(view, bundle);
    inCallScreenDelegate =
        FragmentUtils.getParent(this, InCallScreenDelegateFactory.class).newInCallScreenDelegate();
    Assert.isNotNull(inCallScreenDelegate);

    buttonControllers.add(new ButtonController.MuteButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.SpeakerButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.DialpadButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.HoldButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.AddCallButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.SwapButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.MergeButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new ButtonController.SwapSimButtonController(inCallButtonUiDelegate));
    buttonControllers.add(
        new ButtonController.UpgradeToVideoButtonController(inCallButtonUiDelegate));
    buttonControllers.add(new UpgradeToRttButtonController(inCallButtonUiDelegate));
    buttonControllers.add(
        new ButtonController.ManageConferenceButtonController(inCallScreenDelegate));
    buttonControllers.add(
        new ButtonController.SwitchToSecondaryButtonController(inCallScreenDelegate));
    buttonControllers.add(new ButtonController.CallRecordButtonController(inCallButtonUiDelegate));

    inCallScreenDelegate.onInCallScreenDelegateInit(this);
    inCallScreenDelegate.onInCallScreenReady();
  }

  @Override
  public void onPause() {
    super.onPause();
    inCallScreenDelegate.onInCallScreenPaused();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    inCallScreenDelegate.onInCallScreenUnready();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    inCallButtonUiDelegate.onSaveInstanceState(outState);
  }

  @Override
  public void onClick(View view) {
    if (view == endCallButton) {
      LogUtil.i("InCallFragment.onClick", "end call button clicked");
      Logger.get(getContext())
          .logImpression(DialerImpression.Type.IN_CALL_DIALPAD_HANG_UP_BUTTON_PRESSED);
      inCallScreenDelegate.onEndCallClicked();
    } else {
      LogUtil.e("InCallFragment.onClick", "unknown view: " + view);
      Assert.fail();
    }
  }

  @Override
  public void setPrimary(@NonNull PrimaryInfo primaryInfo) {
    LogUtil.i("InCallFragment.setPrimary", primaryInfo.toString());
    setAdapterMedia(primaryInfo.multimediaData(), primaryInfo.showInCallButtonGrid());
    contactGridManager.setPrimary(primaryInfo);

    if (primaryInfo.shouldShowLocation()) {
      // Hide the avatar to make room for location
      contactGridManager.setAvatarHidden(true);

      // Need to let the dialpad move up a little further when location info is being shown
      View dialpadView = getView().findViewById(R.id.incall_dialpad_container);
      ViewGroup.LayoutParams params = dialpadView.getLayoutParams();
      if (params instanceof RelativeLayout.LayoutParams) {
        ((RelativeLayout.LayoutParams) params).removeRule(RelativeLayout.BELOW);
      }
      dialpadView.setLayoutParams(params);
    }
  }

  private void setAdapterMedia(MultimediaData multimediaData, boolean showInCallButtonGrid) {
    if (adapter == null) {
      adapter =
          new InCallPagerAdapter(getChildFragmentManager(), multimediaData, showInCallButtonGrid);
      pager.setAdapter(adapter);
    } else {
      adapter.setAttachments(multimediaData);
    }

    if (adapter.getCount() > 1 && getResources().getInteger(R.integer.incall_num_rows) > 1) {
      paginator.setVisibility(View.VISIBLE);
      paginator.setupWithViewPager(pager);
      pager.setSwipingLocked(false);
      if (!stateRestored) {
        handler.postDelayed(pagerRunnable, 4_000);
      } else {
        pager.setCurrentItem(adapter.getButtonGridPosition(), false /* animateScroll */);
      }
    } else {
      paginator.setVisibility(View.GONE);
    }
  }

  @Override
  public void setSecondary(@NonNull SecondaryInfo secondaryInfo) {
    LogUtil.i("InCallFragment.setSecondary", secondaryInfo.toString());
    updateButtonStates();

    if (!isAdded()) {
      savedSecondaryInfo = secondaryInfo;
      return;
    }
    savedSecondaryInfo = null;
    FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
    Fragment oldBanner = getChildFragmentManager().findFragmentById(R.id.incall_on_hold_banner);
    if (secondaryInfo.shouldShow()) {
      transaction.replace(R.id.incall_on_hold_banner, OnHoldFragment.newInstance(secondaryInfo));
    } else {
      if (oldBanner != null) {
        transaction.remove(oldBanner);
      }
    }
    transaction.setCustomAnimations(R.anim.abc_slide_in_top, R.anim.abc_slide_out_top);
    transaction.commitNowAllowingStateLoss();
  }

  @Override
  public void setCallState(@NonNull PrimaryCallState primaryCallState) {
    LogUtil.i("InCallFragment.setCallState", primaryCallState.toString());
    contactGridManager.setCallState(primaryCallState);
    getButtonController(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY)
        .setAllowed(primaryCallState.swapToSecondaryButtonState() != ButtonState.NOT_SUPPORT);
    getButtonController(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY)
        .setEnabled(primaryCallState.swapToSecondaryButtonState() == ButtonState.ENABLED);
    buttonChooser =
        ButtonChooserFactory.newButtonChooser(
            voiceNetworkType, primaryCallState.isWifi(), phoneType);
    updateButtonStates();
  }

  @Override
  public void setEndCallButtonEnabled(boolean enabled, boolean animate) {
    if (endCallButton != null) {
      endCallButton.setEnabled(enabled);
    }
  }

  @Override
  public void showManageConferenceCallButton(boolean visible) {
    getButtonController(InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE).setAllowed(visible);
    getButtonController(InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE).setEnabled(visible);
    updateButtonStates();
  }

  @Override
  public boolean isManageConferenceVisible() {
    return getButtonController(InCallButtonIds.BUTTON_MANAGE_VOICE_CONFERENCE).isAllowed();
  }

  @Override
  public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
    contactGridManager.dispatchPopulateAccessibilityEvent(event);
  }

  @Override
  public void showNoteSentToast() {
    LogUtil.i("InCallFragment.showNoteSentToast", null);
    Toast.makeText(getContext(), R.string.incall_note_sent, Toast.LENGTH_LONG).show();
  }

  @Override
  public void updateInCallScreenColors() {}

  @Override
  public void onInCallScreenDialpadVisibilityChange(boolean isShowing) {
    LogUtil.i("InCallFragment.onInCallScreenDialpadVisibilityChange", "isShowing: " + isShowing);
    // Take note that the dialpad button isShowing
    getButtonController(InCallButtonIds.BUTTON_DIALPAD).setChecked(isShowing);

    // This check is needed because there is a race condition where we attempt to update
    // ButtonGridFragment before it is ready, so we check whether it is ready first and once it is
    // ready, #onButtonGridCreated will mark the dialpad button as isShowing.
    if (inCallButtonGridFragment != null) {
      // Update the Android Button's state to isShowing.
      inCallButtonGridFragment.onInCallScreenDialpadVisibilityChange(isShowing);
    }
    Activity activity = getActivity();
    Window window = activity.getWindow();
    window.setNavigationBarColor(
        activity.getColor(
            isShowing ? android.R.color.background_dark : android.R.color.transparent));
  }

  @Override
  public int getAnswerAndDialpadContainerResourceId() {
    return R.id.incall_dialpad_container;
  }

  @Override
  public Fragment getInCallScreenFragment() {
    return this;
  }

  @Override
  public void showButton(@InCallButtonIds int buttonId, boolean show) {
    LogUtil.v(
        "InCallFragment.showButton",
        "buttionId: %s, show: %b",
        InCallButtonIdsExtension.toString(buttonId),
        show);
    if (isSupportedButton(buttonId)) {
      getButtonController(buttonId).setAllowed(show);
      if (buttonId == InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO && show) {
        Logger.get(getContext())
            .logImpression(DialerImpression.Type.UPGRADE_TO_VIDEO_CALL_BUTTON_SHOWN);
      }
    }
  }

  @Override
  public void enableButton(@InCallButtonIds int buttonId, boolean enable) {
    LogUtil.v(
        "InCallFragment.enableButton",
        "buttonId: %s, enable: %b",
        InCallButtonIdsExtension.toString(buttonId),
        enable);
    if (isSupportedButton(buttonId)) {
      getButtonController(buttonId).setEnabled(enable);
    }
  }

  @Override
  public void setEnabled(boolean enabled) {
    LogUtil.v("InCallFragment.setEnabled", "enabled: " + enabled);
    for (ButtonController buttonController : buttonControllers) {
      buttonController.setEnabled(enabled);
    }
  }

  @Override
  public void setHold(boolean value) {
    getButtonController(InCallButtonIds.BUTTON_HOLD).setChecked(value);
  }

  @Override
  public void setCameraSwitched(boolean isBackFacingCamera) {}

  @Override
  public void setVideoPaused(boolean isPaused) {}

  @Override
  public void setAudioState(CallAudioState audioState) {
    LogUtil.i("InCallFragment.setAudioState", "audioState: " + audioState);
    ((SpeakerButtonController) getButtonController(InCallButtonIds.BUTTON_AUDIO))
        .setAudioState(audioState);
    getButtonController(InCallButtonIds.BUTTON_MUTE).setChecked(audioState.isMuted());
  }

  @Override
  public void setCallRecordingState(boolean isRecording) {
    ((CallRecordButtonController) getButtonController(InCallButtonIds.BUTTON_RECORD_CALL))
        .setRecordingState(isRecording);
  }

  @Override
  public void setCallRecordingDuration(long durationMs) {
    ((CallRecordButtonController) getButtonController(InCallButtonIds.BUTTON_RECORD_CALL))
        .setRecordingDuration(durationMs);
  }

  @Override
  public void requestCallRecordingPermissions(String[] permissions) {
    requestPermissions(permissions, REQUEST_CODE_CALL_RECORD_PERMISSION);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
      @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == REQUEST_CODE_CALL_RECORD_PERMISSION) {
      boolean allGranted = grantResults.length > 0;
      for (int i = 0; i < grantResults.length; i++) {
        allGranted &= grantResults[i] == PackageManager.PERMISSION_GRANTED;
      }
      if (allGranted) {
        inCallButtonUiDelegate.callRecordClicked(true);
      }
    } else {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  @Override
  public void updateButtonStates() {
    // When the incall screen is ready, this method is called from #setSecondary, even though the
    // incall button ui is not ready yet. This method is called again once the incall button ui is
    // ready though, so this operation is safe and will be executed asap.
    if (inCallButtonGridFragment == null) {
      return;
    }
    int numVisibleButtons =
        inCallButtonGridFragment.updateButtonStates(
            buttonControllers, buttonChooser, voiceNetworkType, phoneType);

    int visibility = numVisibleButtons == 0 ? View.GONE : View.VISIBLE;
    pager.setVisibility(visibility);
    if (adapter != null
        && adapter.getCount() > 1
        && getResources().getInteger(R.integer.incall_num_rows) > 1) {
      paginator.setVisibility(View.VISIBLE);
      pager.setSwipingLocked(false);
    } else {
      paginator.setVisibility(View.GONE);
      if (adapter != null) {
        pager.setSwipingLocked(true);
        pager.setCurrentItem(adapter.getButtonGridPosition());
      }
    }
  }

  @Override
  public void updateInCallButtonUiColors(@ColorInt int color) {
    inCallButtonGridFragment.updateButtonColor(color);
  }

  @Override
  public Fragment getInCallButtonUiFragment() {
    return this;
  }

  @Override
  public void showAudioRouteSelector() {
    AudioRouteSelectorDialogFragment.newInstance(inCallButtonUiDelegate.getCurrentAudioState())
        .show(getChildFragmentManager(), null);
  }

  @Override
  public void onAudioRouteSelected(int audioRoute) {
    inCallButtonUiDelegate.setAudioRoute(audioRoute);
  }

  @Override
  public void onAudioRouteSelectorDismiss() {}

  @NonNull
  @Override
  public ButtonController getButtonController(@InCallButtonIds int id) {
    for (ButtonController buttonController : buttonControllers) {
      if (buttonController.getInCallButtonId() == id) {
        return buttonController;
      }
    }
    Assert.fail();
    return null;
  }

  @Override
  public void onButtonGridCreated(InCallButtonGridFragment inCallButtonGridFragment) {
    LogUtil.i("InCallFragment.onButtonGridCreated", "InCallUiReady");
    this.inCallButtonGridFragment = inCallButtonGridFragment;
    inCallButtonUiDelegate.onInCallButtonUiReady(this);
    updateButtonStates();
  }

  @Override
  public void onButtonGridDestroyed() {
    LogUtil.i("InCallFragment.onButtonGridCreated", "InCallUiUnready");
    inCallButtonUiDelegate.onInCallButtonUiUnready();
    this.inCallButtonGridFragment = null;
  }

  @Override
  public boolean isShowingLocationUi() {
    Fragment fragment = getLocationFragment();
    return fragment != null && fragment.isVisible();
  }

  @Override
  public void showLocationUi(@Nullable Fragment locationUi) {
    boolean isVisible = isShowingLocationUi();
    if (locationUi != null && !isVisible) {
      // Show the location fragment.
      getChildFragmentManager()
          .beginTransaction()
          .replace(R.id.incall_location_holder, locationUi)
          .commitAllowingStateLoss();
    } else if (locationUi == null && isVisible) {
      // Hide the location fragment
      getChildFragmentManager()
          .beginTransaction()
          .remove(getLocationFragment())
          .commitAllowingStateLoss();
    }
  }

  @Override
  public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
    super.onMultiWindowModeChanged(isInMultiWindowMode);
    if (isInMultiWindowMode == isShowingLocationUi()) {
      LogUtil.i("InCallFragment.onMultiWindowModeChanged", "hide = " + isInMultiWindowMode);
      // Need to show or hide location
      showLocationUi(isInMultiWindowMode ? null : getLocationFragment());
    }
    contactGridManager.onMultiWindowModeChanged(isInMultiWindowMode);
  }

  private Fragment getLocationFragment() {
    return getChildFragmentManager().findFragmentById(R.id.incall_location_holder);
  }
}
