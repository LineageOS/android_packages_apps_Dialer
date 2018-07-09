/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.incallui.rtt.impl;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.telecom.CallAudioState;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment.AudioRouteSelectorPresenter;
import com.android.incallui.call.DialerCall.State;
import com.android.incallui.incall.protocol.InCallButtonUi;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.incall.protocol.InCallButtonUiDelegateFactory;
import com.android.incallui.incall.protocol.InCallScreen;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.InCallScreenDelegateFactory;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.incall.protocol.SecondaryInfo;
import com.android.incallui.rtt.impl.RttChatAdapter.MessageListener;
import com.android.incallui.rtt.protocol.Constants;
import com.android.incallui.rtt.protocol.RttCallScreen;
import com.android.incallui.rtt.protocol.RttCallScreenDelegate;
import com.android.incallui.rtt.protocol.RttCallScreenDelegateFactory;

/** RTT chat fragment to show chat bubbles. */
public class RttChatFragment extends Fragment
    implements OnEditorActionListener,
        TextWatcher,
        MessageListener,
        RttCallScreen,
        InCallScreen,
        InCallButtonUi,
        AudioRouteSelectorPresenter {

  private static final String ARG_CALL_ID = "call_id";

  private RecyclerView recyclerView;
  private RttChatAdapter adapter;
  private EditText editText;
  private ImageButton submitButton;
  private boolean isClearingInput;

  private final OnScrollListener onScrollListener =
      new OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
          if (dy < 0) {
            hideKeyboard();
          }
        }
      };
  private InCallScreenDelegate inCallScreenDelegate;
  private RttCallScreenDelegate rttCallScreenDelegate;
  private InCallButtonUiDelegate inCallButtonUiDelegate;
  private View endCallButton;
  private TextView nameTextView;
  private Chronometer chronometer;
  private boolean isTimerStarted;
  private RttOverflowMenu overflowMenu;

  /**
   * Create a new instance of RttChatFragment.
   *
   * @param callId call id of the RTT call.
   * @return new RttChatFragment
   */
  public static RttChatFragment newInstance(String callId) {
    Bundle bundle = new Bundle();
    bundle.putString(ARG_CALL_ID, callId);
    RttChatFragment instance = new RttChatFragment();
    instance.setArguments(bundle);
    return instance;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    LogUtil.i("RttChatFragment.onCreate", null);
    inCallButtonUiDelegate =
        FragmentUtils.getParent(this, InCallButtonUiDelegateFactory.class)
            .newInCallButtonUiDelegate();
    if (savedInstanceState != null) {
      inCallButtonUiDelegate.onRestoreInstanceState(savedInstanceState);
    }
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle bundle) {
    super.onViewCreated(view, bundle);
    LogUtil.i("RttChatFragment.onViewCreated", null);

    inCallScreenDelegate =
        FragmentUtils.getParentUnsafe(this, InCallScreenDelegateFactory.class)
            .newInCallScreenDelegate();
    rttCallScreenDelegate =
        FragmentUtils.getParentUnsafe(this, RttCallScreenDelegateFactory.class)
            .newRttCallScreenDelegate(this);

    rttCallScreenDelegate.initRttCallScreenDelegate(this);

    inCallScreenDelegate.onInCallScreenDelegateInit(this);
    inCallScreenDelegate.onInCallScreenReady();
    inCallButtonUiDelegate.onInCallButtonUiReady(this);
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.frag_rtt_chat, container, false);
    editText = view.findViewById(R.id.rtt_chat_input);
    editText.setOnEditorActionListener(this);
    editText.addTextChangedListener(this);
    recyclerView = view.findViewById(R.id.rtt_recycler_view);
    LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
    layoutManager.setStackFromEnd(true);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setHasFixedSize(false);
    adapter = new RttChatAdapter(getContext(), this);
    recyclerView.setAdapter(adapter);
    recyclerView.addOnScrollListener(onScrollListener);
    submitButton = view.findViewById(R.id.rtt_chat_submit_button);
    submitButton.setOnClickListener(
        v -> {
          adapter.submitLocalMessage();
          isClearingInput = true;
          editText.setText("");
          isClearingInput = false;
          rttCallScreenDelegate.onLocalMessage(Constants.BUBBLE_BREAKER);
        });
    submitButton.setEnabled(false);
    endCallButton = view.findViewById(R.id.rtt_end_call_button);
    endCallButton.setOnClickListener(
        v -> {
          LogUtil.i("RttChatFragment.onClick", "end call button clicked");
          inCallButtonUiDelegate.onEndCallClicked();
        });

    overflowMenu = new RttOverflowMenu(getContext(), inCallButtonUiDelegate);
    view.findViewById(R.id.rtt_overflow_button)
        .setOnClickListener(v -> overflowMenu.showAtLocation(v, Gravity.TOP | Gravity.RIGHT, 0, 0));

    nameTextView = view.findViewById(R.id.rtt_name_or_number);
    chronometer = view.findViewById(R.id.rtt_timer);
    return view;
  }

  @Override
  public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
    if (actionId == EditorInfo.IME_ACTION_DONE) {
      submitButton.performClick();
      return true;
    }
    return false;
  }

  @Override
  public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

  @Override
  public void onTextChanged(CharSequence s, int start, int before, int count) {
    if (isClearingInput) {
      return;
    }
    String messageToAppend = RttChatMessage.getChangedString(s, start, before, count);
    if (!TextUtils.isEmpty(messageToAppend)) {
      adapter.addLocalMessage(messageToAppend);
      rttCallScreenDelegate.onLocalMessage(messageToAppend);
    }
  }

  @Override
  public void onRemoteMessage(String message) {
    adapter.addRemoteMessage(message);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    LogUtil.enterBlock("RttChatFragment.onDestroyView");
    inCallButtonUiDelegate.onInCallButtonUiUnready();
    inCallScreenDelegate.onInCallScreenUnready();
  }

  @Override
  public void afterTextChanged(Editable s) {
    if (TextUtils.isEmpty(s)) {
      submitButton.setEnabled(false);
    } else {
      submitButton.setEnabled(true);
    }
  }

  @Override
  public void newMessageAdded() {
    recyclerView.smoothScrollToPosition(adapter.getItemCount());
  }

  @Override
  public void onStart() {
    LogUtil.enterBlock("RttChatFragment.onStart");
    super.onStart();
    onRttScreenStart();
  }

  @Override
  public void onStop() {
    LogUtil.enterBlock("RttChatFragment.onStop");
    super.onStop();
    if (overflowMenu.isShowing()) {
      overflowMenu.dismiss();
    }
    onRttScreenStop();
  }

  private void hideKeyboard() {
    InputMethodManager inputMethodManager = getContext().getSystemService(InputMethodManager.class);
    if (inputMethodManager.isAcceptingText()) {
      inputMethodManager.hideSoftInputFromWindow(
          getActivity().getCurrentFocus().getWindowToken(), 0);
    }
  }

  @Override
  public void onRttScreenStart() {
    rttCallScreenDelegate.onRttCallScreenUiReady();
    Activity activity = getActivity();
    Window window = getActivity().getWindow();
    window.setStatusBarColor(activity.getColor(R.color.rtt_status_bar_color));
    window.setNavigationBarColor(activity.getColor(R.color.rtt_navigation_bar_color));
    window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  }

  @Override
  public void onRttScreenStop() {
    rttCallScreenDelegate.onRttCallScreenUiUnready();
  }

  @Override
  public Fragment getRttCallScreenFragment() {
    return this;
  }

  @Override
  public String getCallId() {
    return Assert.isNotNull(getArguments().getString(ARG_CALL_ID));
  }

  @Override
  public void setPrimary(@NonNull PrimaryInfo primaryInfo) {
    LogUtil.i("RttChatFragment.setPrimary", primaryInfo.toString());
    nameTextView.setText(primaryInfo.name());
  }

  @Override
  public void setSecondary(@NonNull SecondaryInfo secondaryInfo) {}

  @Override
  public void setCallState(@NonNull PrimaryCallState primaryCallState) {
    LogUtil.i("RttChatFragment.setCallState", primaryCallState.toString());
    if (!isTimerStarted && primaryCallState.state() == State.ACTIVE) {
      LogUtil.i(
          "RttChatFragment.setCallState", "starting timer with base: %d", chronometer.getBase());
      chronometer.setBase(
          primaryCallState.connectTimeMillis()
              - System.currentTimeMillis()
              + SystemClock.elapsedRealtime());
      chronometer.start();
      isTimerStarted = true;
    }
  }

  @Override
  public void setEndCallButtonEnabled(boolean enabled, boolean animate) {}

  @Override
  public void showManageConferenceCallButton(boolean visible) {}

  @Override
  public boolean isManageConferenceVisible() {
    return false;
  }

  @Override
  public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {}

  @Override
  public void showNoteSentToast() {}

  @Override
  public void updateInCallScreenColors() {}

  @Override
  public void onInCallScreenDialpadVisibilityChange(boolean isShowing) {}

  @Override
  public int getAnswerAndDialpadContainerResourceId() {
    return 0;
  }

  @Override
  public void showLocationUi(Fragment locationUi) {}

  @Override
  public boolean isShowingLocationUi() {
    return false;
  }

  @Override
  public Fragment getInCallScreenFragment() {
    return this;
  }

  @Override
  public void showButton(int buttonId, boolean show) {}

  @Override
  public void enableButton(int buttonId, boolean enable) {}

  @Override
  public void setEnabled(boolean on) {}

  @Override
  public void setHold(boolean on) {}

  @Override
  public void setCameraSwitched(boolean isBackFacingCamera) {}

  @Override
  public void setVideoPaused(boolean isPaused) {}

  @Override
  public void setAudioState(CallAudioState audioState) {
    LogUtil.i("RttChatFragment.setAudioState", "audioState: " + audioState);
    overflowMenu.setMuteButtonChecked(audioState.isMuted());
    overflowMenu.setAudioState(audioState);
  }

  @Override
  public void updateButtonStates() {}

  @Override
  public void updateInCallButtonUiColors(int color) {}

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

  @Override
  public void requestCallRecordingPermissions(String[] permissions) {}

  @Override
  public void setCallRecordingDuration(long duration) {}

  @Override
  public void setCallRecordingState(boolean isRecording) {}
}
