/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.widget;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;

/** Fragment used to compose call with message fragment. */
public class MessageFragment extends Fragment
    implements OnClickListener, TextWatcher, OnEditorActionListener {
  private static final String CHAR_LIMIT_KEY = "char_limit";
  private static final String SHOW_SEND_ICON_KEY = "show_send_icon";
  private static final String MESSAGE_LIST_KEY = "message_list";

  public static final int NO_CHAR_LIMIT = -1;

  private EditText customMessage;
  private ImageView sendMessage;
  private View sendMessageContainer;
  private TextView remainingChar;
  private int charLimit;

  private static MessageFragment newInstance(Builder builder) {
    MessageFragment fragment = new MessageFragment();
    Bundle args = new Bundle();
    args.putInt(CHAR_LIMIT_KEY, builder.charLimit);
    args.putBoolean(SHOW_SEND_ICON_KEY, builder.showSendIcon);
    args.putStringArray(MESSAGE_LIST_KEY, builder.messages);
    fragment.setArguments(args);
    return fragment;
  }

  @Nullable
  public String getMessage() {
    return customMessage == null ? null : customMessage.getText().toString();
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_message, container, false);

    sendMessage = view.findViewById(R.id.send_message);
    sendMessageContainer = view.findViewById(R.id.count_and_send_container);
    if (getArguments().getBoolean(SHOW_SEND_ICON_KEY, false)) {
      sendMessage.setVisibility(View.VISIBLE);
      sendMessage.setEnabled(false);
      sendMessageContainer.setOnClickListener(this);
    }

    customMessage = view.findViewById(R.id.custom_message);
    customMessage.addTextChangedListener(this);
    customMessage.setOnEditorActionListener(this);
    charLimit = getArguments().getInt(CHAR_LIMIT_KEY, NO_CHAR_LIMIT);
    if (charLimit != NO_CHAR_LIMIT) {
      remainingChar = view.findViewById(R.id.remaining_characters);
      remainingChar.setVisibility(View.VISIBLE);
      remainingChar = view.findViewById(R.id.remaining_characters);
      remainingChar.setText(Integer.toString(charLimit));
      customMessage.setFilters(new InputFilter[] {new InputFilter.LengthFilter(charLimit)});
    }

    LinearLayout messageContainer = view.findViewById(R.id.message_container);
    for (String message : getArguments().getStringArray(MESSAGE_LIST_KEY)) {
      TextView textView = (TextView) inflater.inflate(R.layout.selectable_text_view, null);
      textView.setOnClickListener(this);
      textView.setText(message);
      messageContainer.addView(textView);
    }
    return view;
  }

  @Override
  public void onClick(View view) {
    if (view == sendMessageContainer) {
      if (!TextUtils.isEmpty(customMessage.getText())) {
        getListener().onMessageFragmentSendMessage(customMessage.getText().toString());
      }
    } else if (view.getId() == R.id.selectable_text_view) {
      customMessage.setText(((TextView) view).getText());
      customMessage.setSelection(customMessage.getText().length());
    } else {
      Assert.fail("Unknown view clicked");
    }
  }

  @Override
  public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

  @Override
  public void onTextChanged(CharSequence s, int start, int before, int count) {
    sendMessage.setEnabled(s.length() > 0);
  }

  @Override
  public void afterTextChanged(Editable s) {
    if (charLimit != NO_CHAR_LIMIT) {
      remainingChar.setText(Integer.toString(charLimit - s.length()));
    }
    getListener().onMessageFragmentAfterTextChange(s.toString());
  }

  @Override
  public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
    if (!TextUtils.isEmpty(getMessage())) {
      getListener().onMessageFragmentSendMessage(getMessage());
    }
    return true;
  }

  private Listener getListener() {
    return FragmentUtils.getParentUnsafe(this, Listener.class);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link MessageFragment}. */
  public static class Builder {
    private String[] messages;
    private boolean showSendIcon;
    private int charLimit = NO_CHAR_LIMIT;

    /**
     * @throws NullPointerException if message is null
     * @throws IllegalArgumentException if messages.length is outside the range [1,3].
     */
    public Builder setMessages(String... messages) {
      // Since we only allow up to 3 messages, crash if more are set.
      Assert.checkArgument(messages.length > 0 && messages.length <= 3);
      this.messages = messages;
      return this;
    }

    public Builder showSendIcon() {
      showSendIcon = true;
      return this;
    }

    public Builder setCharLimit(int charLimit) {
      this.charLimit = charLimit;
      return this;
    }

    public MessageFragment build() {
      return MessageFragment.newInstance(this);
    }
  }

  /** Interface for parent activity to implement to listen for important events. */
  public interface Listener {
    void onMessageFragmentSendMessage(String message);

    void onMessageFragmentAfterTextChange(String message);
  }
}
