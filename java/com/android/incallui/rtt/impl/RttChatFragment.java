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

import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import com.android.incallui.rtt.impl.RttChatAdapter.MessageListener;

/** RTT chat fragment to show chat bubbles. */
public class RttChatFragment extends Fragment
    implements OnClickListener, OnEditorActionListener, TextWatcher, MessageListener {

  private static final String ARG_CALL_ID = "call_id";
  private static final String ARG_NAME_OR_NUMBER = "name_or_number";
  private static final String ARG_SESSION_START_TIME = "session_start_time";

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

  /**
   * Create a new instance of RttChatFragment.
   *
   * @param callId call id of the RTT call.
   * @param nameOrNumber name or number of the caller to be displayed
   * @param sessionStartTimeMillis start time of RTT session in terms of {@link
   *     SystemClock#elapsedRealtime}.
   * @return new RttChatFragment
   */
  public static RttChatFragment newInstance(
      String callId, String nameOrNumber, long sessionStartTimeMillis) {
    Bundle bundle = new Bundle();
    bundle.putString(ARG_CALL_ID, callId);
    bundle.putString(ARG_NAME_OR_NUMBER, nameOrNumber);
    bundle.putLong(ARG_SESSION_START_TIME, sessionStartTimeMillis);
    RttChatFragment instance = new RttChatFragment();
    instance.setArguments(bundle);
    return instance;
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
    submitButton.setOnClickListener(this);
    submitButton.setEnabled(false);

    String nameOrNumber = null;
    Bundle bundle = getArguments();
    if (bundle != null) {
      nameOrNumber = bundle.getString(ARG_NAME_OR_NUMBER, getString(R.string.unknown));
    }
    TextView nameTextView = view.findViewById(R.id.rtt_name_or_number);
    nameTextView.setText(nameOrNumber);

    long sessionStartTime = SystemClock.elapsedRealtime();
    if (bundle != null) {
      sessionStartTime = bundle.getLong(ARG_SESSION_START_TIME, sessionStartTime);
    }
    Chronometer chronometer = view.findViewById(R.id.rtt_timer);
    chronometer.setBase(sessionStartTime);
    chronometer.start();
    return view;
  }

  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.rtt_chat_submit_button) {
      adapter.submitLocalMessage();
      isClearingInput = true;
      editText.setText("");
      isClearingInput = false;
    }
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
    adapter.addLocalMessage(RttChatMessage.getChangedString(s, start, before, count));
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

  private void hideKeyboard() {
    InputMethodManager inputMethodManager = getContext().getSystemService(InputMethodManager.class);
    if (inputMethodManager.isAcceptingText()) {
      inputMethodManager.hideSoftInputFromWindow(
          getActivity().getCurrentFocus().getWindowToken(), 0);
    }
  }
}
