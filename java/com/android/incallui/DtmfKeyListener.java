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
 * limitations under the License
 */

package com.android.incallui;

import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.DialerKeyListener;
import android.view.KeyEvent;
import android.view.View;
import com.android.dialer.common.LogUtil;

/**
 * Key listener specialized to deal with Dtmf codes.
 *
 * <p>This listener will listen for valid Dtmf characters, and in response will inform the
 * associated presenter of the character. As an implementation of {@link DialerKeyListener}, this
 * class will listen for <b>hardware keyboard</b> events.
 *
 * <p>From legacy documentation:
 *
 * <ul>
 *   <li>Ignores the backspace since it is irrelevant.
 *   <li>Allow ONLY valid DTMF characters to generate a tone and be sent as a DTMF code.
 *   <li>All other remaining characters are handled by the superclass.
 *   <li>This code is purely here to handle events from the hardware keyboard while the DTMF dialpad
 *       is up.
 * </ul>
 */
final class DtmfKeyListener extends DialerKeyListener {
  /**
   * Overrides the characters used in {@link DialerKeyListener#CHARACTERS} These are the valid dtmf
   * characters.
   */
  private static final char[] VALID_DTMF_CHARACTERS =
      new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '#', '*'};

  /**
   * Spannable used to call {@link DialerKeyListener#lookup(KeyEvent, Spannable)}, so it's not
   * necessary to copy the implementation.
   *
   * <p>The Spannable is only used to determine which meta keys are pressed, e.g. shift, alt, see
   * {@link android.text.method.MetaKeyKeyListener#getMetaState(CharSequence)}, so using a dummy
   * value is fine here.
   */
  private static final Spannable EMPTY_SPANNABLE = new SpannableString("");

  private final DialpadPresenter presenter;

  DtmfKeyListener(@NonNull DialpadPresenter presenter) {
    this.presenter = presenter;
  }

  @Override
  protected char[] getAcceptedChars() {
    return VALID_DTMF_CHARACTERS;
  }

  @Override
  public boolean backspace(View view, Editable content, int keyCode, KeyEvent event) {
    return false;
  }

  /**
   * Responds to keyDown events by firing a Dtmf tone, if the given event corresponds is a {@link
   * #VALID_DTMF_CHARACTERS}.
   *
   * @return {@code true} if the event was handled.
   */
  @Override
  public boolean onKeyDown(View view, Editable content, int keyCode, KeyEvent event) {
    LogUtil.i("DtmfKeyListener.onKeyDown", "overload");
    if (!super.onKeyDown(view, content, keyCode, event)) {
      LogUtil.i("DtmfKeyListener.onKeyDown", "parent type didn't support event");
      return false;
    }

    return onKeyDown(event);
  }

  /**
   * Version of {@link #onKeyDown(View, Editable, int, KeyEvent)} used when a View/Editable isn't
   * available.
   */
  boolean onKeyDown(KeyEvent event) {
    LogUtil.enterBlock("DtmfKeyListener.onKeyDown");
    if (event.getRepeatCount() != 0) {
      LogUtil.i("DtmfKeyListener.onKeyDown", "long press, ignoring");
      return false;
    }

    char c = (char) lookup(event, EMPTY_SPANNABLE);

    if (!ok(getAcceptedChars(), c)) {
      LogUtil.i("DtmfKeyListener.onKeyDown", "not an accepted character");
      return false;
    }

    presenter.processDtmf(c);
    return true;
  }

  /**
   * Responds to keyUp events by stopping any playing Dtmf tone if the given event corresponds is a
   * {@link #VALID_DTMF_CHARACTERS}.
   *
   * <p>Null events also stop the Dtmf tone.
   *
   * @return {@code true} if the event was handled
   */
  @Override
  public boolean onKeyUp(View view, Editable content, int keyCode, KeyEvent event) {
    LogUtil.i("DtmfKeyListener.onKeyUp", "overload");
    super.onKeyUp(view, content, keyCode, event);

    return onKeyUp(event);
  }

  /**
   * Handle individual keyup events.
   *
   * @param event is the event we are trying to stop. If this is null, then we just force-stop the
   *     last tone without checking if the event is an acceptable dialer event.
   */
  boolean onKeyUp(KeyEvent event) {
    LogUtil.enterBlock("DtmfKeyListener.onKeyUp");
    if (event == null) {
      return true;
    }

    char c = (char) lookup(event, EMPTY_SPANNABLE);

    if (!ok(getAcceptedChars(), c)) {
      LogUtil.i("DtmfKeyListener.onKeyUp", "not an accepted character");
      return false;
    }

    presenter.stopDtmf();
    return true;
  }
}
