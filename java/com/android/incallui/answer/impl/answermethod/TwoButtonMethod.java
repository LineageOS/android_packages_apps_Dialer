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
 * limitations under the License
 */

package com.android.incallui.answer.impl.answermethod;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.os.Bundle;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.incallui.answer.impl.answermethod.FlingUpDownTouchHandler.OnProgressChangedListener;
import com.android.incallui.util.AccessibilityUtil;

/** Answer method that shows two buttons for answer/reject. */
public class TwoButtonMethod extends AnswerMethod
    implements OnClickListener, AnimatorUpdateListener {

  private static final String STATE_HINT_TEXT = "hintText";
  private static final String STATE_INCOMING_WILL_DISCONNECT = "incomingWillDisconnect";

  private View answerButton;
  private View answerLabel;
  private View declineButton;
  private View declineLabel;
  private TextView hintTextView;
  private boolean incomingWillDisconnect;
  private boolean buttonClicked;
  private CharSequence hintText;
  @Nullable private FlingUpDownTouchHandler touchHandler;

  @Override
  public void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    if (bundle != null) {
      incomingWillDisconnect = bundle.getBoolean(STATE_INCOMING_WILL_DISCONNECT);
      hintText = bundle.getCharSequence(STATE_HINT_TEXT);
    }
  }

  @Override
  public void onSaveInstanceState(Bundle bundle) {
    super.onSaveInstanceState(bundle);
    bundle.putBoolean(STATE_INCOMING_WILL_DISCONNECT, incomingWillDisconnect);
    bundle.putCharSequence(STATE_HINT_TEXT, hintText);
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
    View view = layoutInflater.inflate(R.layout.two_button_method, viewGroup, false);

    hintTextView = (TextView) view.findViewById(R.id.two_button_hint_text);
    updateHintText();

    answerButton = view.findViewById(R.id.two_button_answer_button);
    answerLabel = view.findViewById(R.id.two_button_answer_label);
    declineButton = view.findViewById(R.id.two_button_decline_button);
    declineLabel = view.findViewById(R.id.two_button_decline_label);

    boolean showLabels = getResources().getBoolean(R.bool.two_button_show_button_labels);
    answerLabel.setVisibility(showLabels ? View.VISIBLE : View.GONE);
    declineLabel.setVisibility(showLabels ? View.VISIBLE : View.GONE);

    answerButton.setOnClickListener(this);
    declineButton.setOnClickListener(this);

    if (AccessibilityUtil.isTouchExplorationEnabled(getContext())) {
      /* Falsing already handled by AccessibilityManager */
      touchHandler =
          FlingUpDownTouchHandler.attach(
              view,
              new OnProgressChangedListener() {
                @Override
                public void onProgressChanged(@FloatRange(from = -1f, to = 1f) float progress) {}

                @Override
                public void onTrackingStart() {}

                @Override
                public void onTrackingStopped() {}

                @Override
                public void onMoveReset(boolean showHint) {}

                @Override
                public void onMoveFinish(boolean accept) {
                  if (accept) {
                    answerCall();
                  } else {
                    rejectCall();
                  }
                }

                @Override
                public boolean shouldUseFalsing(@NonNull MotionEvent downEvent) {
                  return false;
                }
              },
              null /* Falsing already handled by AccessibilityManager */);
      touchHandler.setFlingEnabled(false);
    }
    return view;
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    if (touchHandler != null) {
      touchHandler.detach();
      touchHandler = null;
    }
  }

  @Override
  public void setHintText(@Nullable CharSequence hintText) {
    this.hintText = hintText;
    updateHintText();
  }

  @Override
  public void setShowIncomingWillDisconnect(boolean incomingWillDisconnect) {
    this.incomingWillDisconnect = incomingWillDisconnect;
    updateHintText();
  }

  private void updateHintText() {
    if (hintTextView == null) {
      return;
    }
    hintTextView.setVisibility(getActivity().isInMultiWindowMode() ? View.GONE : View.VISIBLE);
    if (!TextUtils.isEmpty(hintText) && !buttonClicked) {
      hintTextView.setText(hintText);
      hintTextView.animate().alpha(1f).start();
    } else if (incomingWillDisconnect && !buttonClicked) {
      hintTextView.setText(R.string.call_incoming_will_disconnect);
      hintTextView.animate().alpha(1f).start();
    } else {
      hintTextView.animate().alpha(0f).start();
    }
  }

  @Override
  public void onClick(View view) {
    if (view == answerButton) {
      answerCall();
      LogUtil.v("TwoButtonMethod.onClick", "Call answered");
    } else if (view == declineButton) {
      rejectCall();
      LogUtil.v("TwoButtonMethod.onClick", "two_buttonMethod Call rejected");
    } else {
      Assert.fail("Unknown click from view: " + view);
    }
    buttonClicked = true;
  }

  private void answerCall() {
    ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
    animator.addUpdateListener(this);
    animator.addListener(
        new AnimatorListenerAdapter() {
          private boolean canceled;

          @Override
          public void onAnimationCancel(Animator animation) {
            canceled = true;
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            if (!canceled) {
              getParent().answerFromMethod();
            }
          }
        });
    AnimatorSet animatorSet = new AnimatorSet();
    animatorSet.play(animator).with(createViewHideAnimation());
    animatorSet.start();
  }

  private void rejectCall() {
    ValueAnimator animator = ValueAnimator.ofFloat(0, -1);
    animator.addUpdateListener(this);
    animator.addListener(
        new AnimatorListenerAdapter() {
          private boolean canceled;

          @Override
          public void onAnimationCancel(Animator animation) {
            canceled = true;
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            if (!canceled) {
              getParent().rejectFromMethod();
            }
          }
        });
    AnimatorSet animatorSet = new AnimatorSet();
    animatorSet.play(animator).with(createViewHideAnimation());
    animatorSet.start();
  }

  @Override
  public void onAnimationUpdate(ValueAnimator animation) {
    getParent().onAnswerProgressUpdate(((float) animation.getAnimatedValue()));
  }

  private Animator createViewHideAnimation() {
    ObjectAnimator answerButtonHide =
        ObjectAnimator.ofPropertyValuesHolder(
            answerButton,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0f));

    ObjectAnimator declineButtonHide =
        ObjectAnimator.ofPropertyValuesHolder(
            declineButton,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0f));

    ObjectAnimator answerLabelHide = ObjectAnimator.ofFloat(answerLabel, View.ALPHA, 0f);

    ObjectAnimator declineLabelHide = ObjectAnimator.ofFloat(declineLabel, View.ALPHA, 0f);

    ObjectAnimator hintHide = ObjectAnimator.ofFloat(hintTextView, View.ALPHA, 0f);

    AnimatorSet hideSet = new AnimatorSet();
    hideSet
        .play(answerButtonHide)
        .with(declineButtonHide)
        .with(answerLabelHide)
        .with(declineLabelHide)
        .with(hintHide);
    return hideSet;
  }
}
