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

package com.android.incallui.contactgrid;

import android.content.Context;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.incallui.call.state.DialerCallState;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryInfo;

/**
 * Gets the content of the bottom row. For example:
 *
 * <ul>
 *   <li>Mobile +1 (650) 253-0000
 *   <li>[HD attempting icon]/[HD icon] 00:15
 *   <li>Call ended
 *   <li>Hanging up
 * </ul>
 */
public class BottomRow {

  /** Content of the bottom row. */
  public static class Info {

    @Nullable public final CharSequence label;
    public final boolean isTimerVisible;
    public final boolean isWorkIconVisible;
    public final boolean isHdAttemptingIconVisible;
    public final boolean isHdIconVisible;
    public final boolean isForwardIconVisible;
    public final boolean isSpamIconVisible;
    public final boolean shouldPopulateAccessibilityEvent;

    public Info(
        @Nullable CharSequence label,
        boolean isTimerVisible,
        boolean isWorkIconVisible,
        boolean isHdAttemptingIconVisible,
        boolean isHdIconVisible,
        boolean isForwardIconVisible,
        boolean isSpamIconVisible,
        boolean shouldPopulateAccessibilityEvent) {
      this.label = label;
      this.isTimerVisible = isTimerVisible;
      this.isWorkIconVisible = isWorkIconVisible;
      this.isHdAttemptingIconVisible = isHdAttemptingIconVisible;
      this.isHdIconVisible = isHdIconVisible;
      this.isForwardIconVisible = isForwardIconVisible;
      this.isSpamIconVisible = isSpamIconVisible;
      this.shouldPopulateAccessibilityEvent = shouldPopulateAccessibilityEvent;
    }
  }

  private BottomRow() {}

  public static Info getInfo(Context context, PrimaryCallState state, PrimaryInfo primaryInfo) {
    CharSequence label;
    boolean isTimerVisible = state.state() == DialerCallState.ACTIVE;
    boolean isForwardIconVisible = state.isForwardedNumber();
    boolean isWorkIconVisible = state.isWorkCall();
    boolean isHdIconVisible = state.isHdAudioCall() && !isForwardIconVisible;
    boolean isHdAttemptingIconVisible = state.isHdAttempting();
    boolean isSpamIconVisible = false;
    boolean shouldPopulateAccessibilityEvent = true;

    if (isIncoming(state) && primaryInfo.isSpam()) {
      label = context.getString(R.string.contact_grid_incoming_suspected_spam);
      isSpamIconVisible = true;
      isHdIconVisible = false;
    } else if (state.state() == DialerCallState.DISCONNECTING) {
      // While in the DISCONNECTING state we display a "Hanging up" message in order to make the UI
      // feel more responsive.  (In GSM it's normal to see a delay of a couple of seconds while
      // negotiating the disconnect with the network, so the "Hanging up" state at least lets the
      // user know that we're doing something.  This state is currently not used with CDMA.)
      label = context.getString(R.string.incall_hanging_up);
    } else if (state.state() == DialerCallState.DISCONNECTED) {
      label = state.disconnectCause().getLabel();
      if (TextUtils.isEmpty(label)) {
        label = context.getString(R.string.incall_call_ended);
      }
    } else {
      label = getLabelForPhoneNumber(primaryInfo);
      shouldPopulateAccessibilityEvent = primaryInfo.nameIsNumber();
    }

    return new Info(
        label,
        isTimerVisible,
        isWorkIconVisible,
        isHdAttemptingIconVisible,
        isHdIconVisible,
        isForwardIconVisible,
        isSpamIconVisible,
        shouldPopulateAccessibilityEvent);
  }

  private static CharSequence getLabelForPhoneNumber(PrimaryInfo primaryInfo) {
    if (primaryInfo.location() != null) {
      return primaryInfo.location();
    }
    if (!primaryInfo.nameIsNumber() && !TextUtils.isEmpty(primaryInfo.number())) {
      if (primaryInfo.label() == null) {
        return primaryInfo.number();
      } else {
        return TextUtils.concat(primaryInfo.label(), " ", primaryInfo.number());
      }
    }
    return null;
  }

  private static boolean isIncoming(PrimaryCallState state) {
    return state.state() == DialerCallState.INCOMING
        || state.state() == DialerCallState.CALL_WAITING;
  }
}
