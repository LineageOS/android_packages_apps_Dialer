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

package com.android.dialer.app.calllog;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.QuickContactBadge;
import com.android.dialer.app.calllog.CallLogAdapter.OnActionModeStateChangedListener;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;

/** Allows us to click the contact badge for non multi select mode. */
class DialerQuickContactBadge extends QuickContactBadge {

  private View.OnClickListener mExtraOnClickListener;
  private OnActionModeStateChangedListener onActionModeStateChangeListener;

  public DialerQuickContactBadge(Context context) {
    super(context);
  }

  public DialerQuickContactBadge(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public DialerQuickContactBadge(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  @Override
  public void onClick(View v) {
    if (mExtraOnClickListener != null
        && onActionModeStateChangeListener.isActionModeStateEnabled()) {
      Logger.get(v.getContext())
          .logImpression(DialerImpression.Type.MULTISELECT_SINGLE_PRESS_TAP_VIA_CONTACT_BADGE);
      mExtraOnClickListener.onClick(v);
    } else {
      super.onClick(v);
    }
  }

  public void setMulitSelectListeners(
      View.OnClickListener extraOnClickListener,
      OnActionModeStateChangedListener actionModeStateChangeListener) {
    mExtraOnClickListener = extraOnClickListener;
    onActionModeStateChangeListener = actionModeStateChangeListener;
  }
}
