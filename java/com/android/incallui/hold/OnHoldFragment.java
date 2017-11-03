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

package com.android.incallui.hold;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.incallui.incall.protocol.SecondaryInfo;

/** Shows banner UI for background call */
public class OnHoldFragment extends Fragment {

  private static final String ARG_INFO = "info";
  private boolean padTopInset = true;
  private int topInset;

  public static OnHoldFragment newInstance(@NonNull SecondaryInfo info) {
    OnHoldFragment fragment = new OnHoldFragment();
    Bundle args = new Bundle();
    args.putParcelable(ARG_INFO, info);
    fragment.setArguments(args);
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
    final View view = layoutInflater.inflate(R.layout.incall_on_hold_banner, viewGroup, false);

    SecondaryInfo secondaryInfo = getArguments().getParcelable(ARG_INFO);
    secondaryInfo = Assert.isNotNull(secondaryInfo);

    ((TextView) view.findViewById(R.id.hold_contact_name))
        .setText(
            secondaryInfo.nameIsNumber
                ? PhoneNumberUtils.createTtsSpannable(
                    BidiFormatter.getInstance()
                        .unicodeWrap(secondaryInfo.name, TextDirectionHeuristics.LTR))
                : secondaryInfo.name);
    ((ImageView) view.findViewById(R.id.hold_phone_icon))
        .setImageResource(
            secondaryInfo.isVideoCall
                ? R.drawable.quantum_ic_videocam_white_18
                : R.drawable.quantum_ic_phone_paused_vd_theme_24);
    view.addOnAttachStateChangeListener(
        new OnAttachStateChangeListener() {
          @Override
          public void onViewAttachedToWindow(View v) {
            topInset = v.getRootWindowInsets().getSystemWindowInsetTop();
            applyInset();
          }

          @Override
          public void onViewDetachedFromWindow(View v) {}
        });
    return view;
  }

  public void setPadTopInset(boolean padTopInset) {
    this.padTopInset = padTopInset;
    applyInset();
  }

  private void applyInset() {
    if (getView() == null) {
      return;
    }

    int newPadding = padTopInset ? topInset : 0;
    if (newPadding != getView().getPaddingTop()) {
      TransitionManager.beginDelayedTransition(((ViewGroup) getView().getParent()));
      getView().setPadding(0, newPadding, 0, 0);
    }
  }
}
