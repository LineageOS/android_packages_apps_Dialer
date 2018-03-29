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
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Trace;
import android.support.annotation.ColorInt;
import android.support.annotation.FloatRange;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.support.v4.view.animation.PathInterpolatorCompat;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.dialer.common.DpUtil;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.MathUtil;
import com.android.dialer.util.DrawableConverter;
import com.android.dialer.util.ViewUtil;
import com.android.incallui.answer.impl.answermethod.FlingUpDownTouchHandler.OnProgressChangedListener;
import com.android.incallui.answer.impl.classifier.FalsingManager;
import com.android.incallui.answer.impl.hint.AnswerHint;
import com.android.incallui.answer.impl.hint.AnswerHintFactory;
import com.android.incallui.answer.impl.hint.PawImageLoaderImpl;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Answer method that swipes up to answer or down to reject. */
@SuppressLint("ClickableViewAccessibility")
public class FlingUpDownMethod extends AnswerMethod implements OnProgressChangedListener {

  private static final float SWIPE_LERP_PROGRESS_FACTOR = 0.5f;
  private static final long ANIMATE_DURATION_SHORT_MILLIS = 667;
  private static final long ANIMATE_DURATION_NORMAL_MILLIS = 1_333;
  private static final long ANIMATE_DURATION_LONG_MILLIS = 1_500;
  private static final long BOUNCE_ANIMATION_DELAY = 167;
  private static final long VIBRATION_TIME_MILLIS = 1_833;
  private static final long SETTLE_ANIMATION_DURATION_MILLIS = 100;
  private static final int HINT_JUMP_DP = 60;
  private static final int HINT_DIP_DP = 8;
  private static final float HINT_SCALE_RATIO = 1.15f;
  private static final long SWIPE_TO_DECLINE_FADE_IN_DELAY_MILLIS = 333;
  private static final int HINT_REJECT_SHOW_DURATION_MILLIS = 2000;
  private static final int ICON_END_CALL_ROTATION_DEGREES = 135;
  private static final int HINT_REJECT_FADE_TRANSLATION_Y_DP = -8;
  private static final float SWIPE_TO_ANSWER_MAX_TRANSLATION_Y_DP = 150;
  private static final int SWIPE_TO_REJECT_MAX_TRANSLATION_Y_DP = 24;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
    value = {
      AnimationState.NONE,
      AnimationState.ENTRY,
      AnimationState.BOUNCE,
      AnimationState.SWIPE,
      AnimationState.SETTLE,
      AnimationState.HINT,
      AnimationState.COMPLETED
    }
  )
  @VisibleForTesting
  @interface AnimationState {

    int NONE = 0;
    int ENTRY = 1; // Entry animation for incoming call
    int BOUNCE = 2; // An idle state in which text and icon slightly bounces off its base repeatedly
    int SWIPE = 3; // A special state in which text and icon follows the finger movement
    int SETTLE = 4; // A short animation to reset from swipe and prepare for hint or bounce
    int HINT = 5; // Jump animation to suggest what to do
    int COMPLETED = 6; // Animation loop completed. Occurs after user swipes beyond threshold
  }

  private static void moveTowardY(View view, float newY) {
    view.setTranslationY(MathUtil.lerp(view.getTranslationY(), newY, SWIPE_LERP_PROGRESS_FACTOR));
  }

  private static void moveTowardX(View view, float newX) {
    view.setTranslationX(MathUtil.lerp(view.getTranslationX(), newX, SWIPE_LERP_PROGRESS_FACTOR));
  }

  private static void fadeToward(View view, float newAlpha) {
    view.setAlpha(MathUtil.lerp(view.getAlpha(), newAlpha, SWIPE_LERP_PROGRESS_FACTOR));
  }

  private static void rotateToward(View view, float newRotation) {
    view.setRotation(MathUtil.lerp(view.getRotation(), newRotation, SWIPE_LERP_PROGRESS_FACTOR));
  }

  private TextView swipeToAnswerText;
  private TextView swipeToRejectText;
  private View contactPuckContainer;
  private ImageView contactPuckBackground;
  private ImageView contactPuckIcon;
  private View incomingDisconnectText;
  private View spaceHolder;
  private Animator lockBounceAnim;
  private AnimatorSet lockEntryAnim;
  private AnimatorSet lockHintAnim;
  private AnimatorSet lockSettleAnim;
  @AnimationState private int animationState = AnimationState.NONE;
  @AnimationState private int afterSettleAnimationState = AnimationState.NONE;
  // a value for finger swipe progress. -1 or less for "reject"; 1 or more for "accept".
  private float swipeProgress;
  private Animator rejectHintHide;
  private Animator vibrationAnimator;
  private Drawable contactPhoto;
  private boolean incomingWillDisconnect;
  private FlingUpDownTouchHandler touchHandler;
  private FalsingManager falsingManager;

  private AnswerHint answerHint;

  @Override
  public void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    falsingManager = new FalsingManager(getContext());
  }

  @Override
  public void onStart() {
    Trace.beginSection("FlingUpDownMethod.onStart");
    super.onStart();
    falsingManager.onScreenOn();
    if (getView() != null) {
      if (animationState == AnimationState.SWIPE || animationState == AnimationState.HINT) {
        swipeProgress = 0;
        updateContactPuck();
        onMoveReset(false);
      } else if (animationState == AnimationState.ENTRY) {
        // When starting from the lock screen, the activity may be stopped and started briefly.
        // Don't let that interrupt the entry animation
        startSwipeToAnswerEntryAnimation();
      }
    }
    Trace.endSection();
  }

  @Override
  public void onStop() {
    Trace.beginSection("FlingUpDownMethod.onStop");
    endAnimation();
    falsingManager.onScreenOff();
    if (getActivity().isFinishing()) {
      setAnimationState(AnimationState.COMPLETED);
    }
    super.onStop();
    Trace.endSection();
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
    Trace.beginSection("FlingUpDownMethod.onCreateView");
    View view = layoutInflater.inflate(R.layout.swipe_up_down_method, viewGroup, false);

    contactPuckContainer = view.findViewById(R.id.incoming_call_puck_container);
    contactPuckBackground = (ImageView) view.findViewById(R.id.incoming_call_puck_bg);
    contactPuckIcon = (ImageView) view.findViewById(R.id.incoming_call_puck_icon);
    swipeToAnswerText = (TextView) view.findViewById(R.id.incoming_swipe_to_answer_text);
    swipeToRejectText = (TextView) view.findViewById(R.id.incoming_swipe_to_reject_text);
    incomingDisconnectText = view.findViewById(R.id.incoming_will_disconnect_text);
    incomingDisconnectText.setVisibility(incomingWillDisconnect ? View.VISIBLE : View.GONE);
    incomingDisconnectText.setAlpha(incomingWillDisconnect ? 1 : 0);
    spaceHolder = view.findViewById(R.id.incoming_bouncer_space_holder);
    spaceHolder.setVisibility(incomingWillDisconnect ? View.GONE : View.VISIBLE);

    view.findViewById(R.id.incoming_swipe_to_answer_container)
        .setAccessibilityDelegate(
            new AccessibilityDelegate() {
              @Override
              public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(
                    new AccessibilityAction(
                        R.id.accessibility_action_answer,
                        getString(R.string.call_incoming_answer)));
                info.addAction(
                    new AccessibilityAction(
                        R.id.accessibility_action_decline,
                        getString(R.string.call_incoming_decline)));
              }

              @Override
              public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action == R.id.accessibility_action_answer) {
                  performAccept();
                  return true;
                } else if (action == R.id.accessibility_action_decline) {
                  performReject();
                  return true;
                }
                return super.performAccessibilityAction(host, action, args);
              }
            });

    swipeProgress = 0;

    updateContactPuck();

    touchHandler = FlingUpDownTouchHandler.attach(view, this, falsingManager);

    answerHint =
        new AnswerHintFactory(new PawImageLoaderImpl())
            .create(getContext(), ANIMATE_DURATION_LONG_MILLIS, BOUNCE_ANIMATION_DELAY);
    answerHint.onCreateView(
        layoutInflater,
        (ViewGroup) view.findViewById(R.id.hint_container),
        contactPuckContainer,
        swipeToAnswerText);
    Trace.endSection();
    return view;
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle bundle) {
    super.onViewCreated(view, bundle);
    setAnimationState(AnimationState.ENTRY);
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
  public void onProgressChanged(@FloatRange(from = -1f, to = 1f) float progress) {
    swipeProgress = progress;
    if (animationState == AnimationState.SWIPE && getContext() != null && isVisible()) {
      updateSwipeTextAndPuckForTouch();
    }
  }

  @Override
  public void onTrackingStart() {
    setAnimationState(AnimationState.SWIPE);
  }

  @Override
  public void onTrackingStopped() {}

  @Override
  public void onMoveReset(boolean showHint) {
    if (showHint) {
      showSwipeHint();
    } else {
      setAnimationState(AnimationState.BOUNCE);
    }
    resetTouchState();
    getParent().resetAnswerProgress();
  }

  @Override
  public void onMoveFinish(boolean accept) {
    touchHandler.setTouchEnabled(false);
    answerHint.onAnswered();
    if (accept) {
      performAccept();
    } else {
      performReject();
    }
  }

  @Override
  public boolean shouldUseFalsing(@NonNull MotionEvent downEvent) {
    if (contactPuckContainer == null) {
      return false;
    }

    float puckCenterX = contactPuckContainer.getX() + (contactPuckContainer.getWidth() / 2);
    float puckCenterY = contactPuckContainer.getY() + (contactPuckContainer.getHeight() / 2);
    double radius = contactPuckContainer.getHeight() / 2;

    // Squaring a number is more performant than taking a sqrt, so we compare the square of the
    // distance with the square of the radius.
    double distSq =
        Math.pow(downEvent.getX() - puckCenterX, 2) + Math.pow(downEvent.getY() - puckCenterY, 2);
    return distSq >= Math.pow(radius, 2);
  }

  @Override
  public void setContactPhoto(Drawable contactPhoto) {
    this.contactPhoto = contactPhoto;

    updateContactPuck();
  }

  private void updateContactPuck() {
    if (contactPuckIcon == null) {
      return;
    }
    if (getParent().isVideoCall() || getParent().isVideoUpgradeRequest()) {
      contactPuckIcon.setImageResource(R.drawable.quantum_ic_videocam_vd_white_24);
    } else if (getParent().isRttCall()) {
      contactPuckIcon.setImageResource(R.drawable.quantum_ic_rtt_vd_theme_24);
    } else {
      contactPuckIcon.setImageResource(R.drawable.quantum_ic_call_white_24);
    }

    int size =
        contactPuckBackground
            .getResources()
            .getDimensionPixelSize(
                shouldShowPhotoInPuck()
                    ? R.dimen.answer_contact_puck_size_photo
                    : R.dimen.answer_contact_puck_size_no_photo);
    contactPuckBackground.setImageDrawable(
        shouldShowPhotoInPuck()
            ? makeRoundedDrawable(contactPuckBackground.getContext(), contactPhoto, size)
            : null);
    ViewGroup.LayoutParams contactPuckParams = contactPuckBackground.getLayoutParams();
    contactPuckParams.height = size;
    contactPuckParams.width = size;
    contactPuckBackground.setLayoutParams(contactPuckParams);
    contactPuckIcon.setAlpha(shouldShowPhotoInPuck() ? 0f : 1f);
  }

  private Drawable makeRoundedDrawable(Context context, Drawable contactPhoto, int size) {
    return DrawableConverter.getRoundedDrawable(context, contactPhoto, size, size);
  }

  private boolean shouldShowPhotoInPuck() {
    return (getParent().isVideoCall() || getParent().isVideoUpgradeRequest())
        && contactPhoto != null;
  }

  @Override
  public void setHintText(@Nullable CharSequence hintText) {
    if (hintText == null) {
      swipeToAnswerText.setText(R.string.call_incoming_swipe_to_answer);
    } else {
      swipeToAnswerText.setText(hintText);
    }
    swipeToRejectText.setText(R.string.call_incoming_swipe_to_reject);
  }

  @Override
  public void setShowIncomingWillDisconnect(boolean incomingWillDisconnect) {
    this.incomingWillDisconnect = incomingWillDisconnect;
    if (incomingDisconnectText != null) {
      if (incomingWillDisconnect) {
        incomingDisconnectText.setVisibility(View.VISIBLE);
        spaceHolder.setVisibility(View.GONE);
        incomingDisconnectText.animate().alpha(1);
      } else {
        incomingDisconnectText
            .animate()
            .alpha(0)
            .setListener(
                new AnimatorListenerAdapter() {
                  @Override
                  public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    incomingDisconnectText.setVisibility(View.GONE);
                    spaceHolder.setVisibility(View.VISIBLE);
                  }
                });
      }
    }
  }

  private void showSwipeHint() {
    setAnimationState(AnimationState.HINT);
  }

  private void updateSwipeTextAndPuckForTouch() {
    Trace.beginSection("FlingUpDownMethod.updateSwipeTextAndPuckForTouch");
    // Clamp progress value between -1 and 1.
    final float clampedProgress = MathUtil.clamp(swipeProgress, -1 /* min */, 1 /* max */);
    final float positiveAdjustedProgress = Math.abs(clampedProgress);
    final boolean isAcceptingFlow = clampedProgress >= 0;

    // Cancel view property animators on views we're about to mutate
    swipeToAnswerText.animate().cancel();
    contactPuckIcon.animate().cancel();

    // Since the animation progression is controlled by user gesture instead of real timeline, the
    // spec timeline can be divided into 9 slots. Each slot is equivalent to 83ms in the spec.
    // Therefore, we use 9 slots of 83ms to map user gesture into the spec timeline.
    //

    final float progressSlots = 9;

    // Fade out the "swipe up to answer". It only takes 1 slot to complete the fade.
    float swipeTextAlpha = Math.max(0, 1 - Math.abs(clampedProgress) * progressSlots);
    fadeToward(swipeToAnswerText, swipeTextAlpha);
    // Fade out the "swipe down to dismiss" at the same time. Don't ever increase its alpha
    fadeToward(swipeToRejectText, Math.min(swipeTextAlpha, swipeToRejectText.getAlpha()));
    // Fade out the "incoming will disconnect" text
    fadeToward(incomingDisconnectText, incomingWillDisconnect ? swipeTextAlpha : 0);

    // Move swipe text back to zero.
    moveTowardX(swipeToAnswerText, 0 /* newX */);
    moveTowardY(swipeToAnswerText, 0 /* newY */);

    // Animate puck color
    @ColorInt
    int destPuckColor =
        getContext()
            .getColor(
                isAcceptingFlow ? R.color.call_accept_background : R.color.call_hangup_background);
    destPuckColor =
        ColorUtils.setAlphaComponent(destPuckColor, (int) (0xFF * positiveAdjustedProgress));
    contactPuckBackground.setBackgroundTintList(ColorStateList.valueOf(destPuckColor));
    contactPuckBackground.setBackgroundTintMode(Mode.SRC_ATOP);
    contactPuckBackground.setColorFilter(destPuckColor);

    // Animate decline icon
    if (isAcceptingFlow || getParent().isVideoCall() || getParent().isVideoUpgradeRequest()) {
      rotateToward(contactPuckIcon, 0f);
    } else {
      rotateToward(contactPuckIcon, positiveAdjustedProgress * ICON_END_CALL_ROTATION_DEGREES);
    }

    // Fade in icon
    if (shouldShowPhotoInPuck()) {
      fadeToward(contactPuckIcon, positiveAdjustedProgress);
    }
    float iconProgress = Math.min(1f, positiveAdjustedProgress * 4);
    @ColorInt
    int iconColor =
        ColorUtils.setAlphaComponent(
            contactPuckIcon.getContext().getColor(R.color.incoming_answer_icon),
            (int) (0xFF * (1 - iconProgress)));
    contactPuckIcon.setImageTintList(ColorStateList.valueOf(iconColor));

    // Move puck.
    if (isAcceptingFlow) {
      moveTowardY(
          contactPuckContainer,
          -clampedProgress * DpUtil.dpToPx(getContext(), SWIPE_TO_ANSWER_MAX_TRANSLATION_Y_DP));
    } else {
      moveTowardY(
          contactPuckContainer,
          -clampedProgress * DpUtil.dpToPx(getContext(), SWIPE_TO_REJECT_MAX_TRANSLATION_Y_DP));
    }

    getParent().onAnswerProgressUpdate(clampedProgress);
    Trace.endSection();
  }

  private void startSwipeToAnswerSwipeAnimation() {
    LogUtil.i("FlingUpDownMethod.startSwipeToAnswerSwipeAnimation", "Start swipe animation.");
    resetTouchState();
    endAnimation();
  }

  private void setPuckTouchState() {
    contactPuckBackground.setActivated(touchHandler.isTracking());
  }

  private void resetTouchState() {
    if (getContext() == null) {
      // State will be reset in onStart(), so just abort.
      return;
    }
    contactPuckContainer.animate().scaleX(1 /* scaleX */);
    contactPuckContainer.animate().scaleY(1 /* scaleY */);
    contactPuckBackground.animate().scaleX(1 /* scaleX */);
    contactPuckBackground.animate().scaleY(1 /* scaleY */);
    contactPuckBackground.setBackgroundTintList(null);
    contactPuckBackground.setColorFilter(null);
    contactPuckIcon.setImageTintList(
        ColorStateList.valueOf(getContext().getColor(R.color.incoming_answer_icon)));
    contactPuckIcon.animate().rotation(0);

    getParent().resetAnswerProgress();
    setPuckTouchState();

    final float alpha = 1;
    swipeToAnswerText.animate().alpha(alpha);
    contactPuckContainer.animate().alpha(alpha);
    contactPuckBackground.animate().alpha(alpha);
    contactPuckIcon.animate().alpha(shouldShowPhotoInPuck() ? 0 : alpha);
  }

  @VisibleForTesting
  void setAnimationState(@AnimationState int state) {
    if (state != AnimationState.HINT && animationState == state) {
      return;
    }

    if (animationState == AnimationState.COMPLETED) {
      LogUtil.e(
          "FlingUpDownMethod.setAnimationState",
          "Animation loop has completed. Cannot switch to new state: " + state);
      return;
    }

    if (state == AnimationState.HINT || state == AnimationState.BOUNCE) {
      if (animationState == AnimationState.SWIPE) {
        afterSettleAnimationState = state;
        state = AnimationState.SETTLE;
      }
    }

    LogUtil.i("FlingUpDownMethod.setAnimationState", "animation state: " + state);
    animationState = state;

    // Start animation after the current one is finished completely.
    View view = getView();
    if (view != null) {
      // As long as the fragment is added, we can start update the animation state.
      if (isAdded() && (animationState == state)) {
        updateAnimationState();
      } else {
        endAnimation();
      }
    }
  }

  @AnimationState
  @VisibleForTesting
  int getAnimationState() {
    return animationState;
  }

  private void updateAnimationState() {
    switch (animationState) {
      case AnimationState.ENTRY:
        startSwipeToAnswerEntryAnimation();
        break;
      case AnimationState.BOUNCE:
        startSwipeToAnswerBounceAnimation();
        break;
      case AnimationState.SWIPE:
        startSwipeToAnswerSwipeAnimation();
        break;
      case AnimationState.SETTLE:
        startSwipeToAnswerSettleAnimation();
        break;
      case AnimationState.COMPLETED:
        clearSwipeToAnswerUi();
        break;
      case AnimationState.HINT:
        startSwipeToAnswerHintAnimation();
        break;
      case AnimationState.NONE:
      default:
        LogUtil.e(
            "FlingUpDownMethod.updateAnimationState",
            "Unexpected animation state: " + animationState);
        break;
    }
  }

  private void startSwipeToAnswerEntryAnimation() {
    LogUtil.i("FlingUpDownMethod.startSwipeToAnswerEntryAnimation", "Swipe entry animation.");
    endAnimation();

    lockEntryAnim = new AnimatorSet();
    Animator textUp =
        ObjectAnimator.ofFloat(
            swipeToAnswerText,
            View.TRANSLATION_Y,
            DpUtil.dpToPx(getContext(), 192 /* dp */),
            DpUtil.dpToPx(getContext(), -20 /* dp */));
    textUp.setDuration(ANIMATE_DURATION_NORMAL_MILLIS);
    textUp.setInterpolator(new LinearOutSlowInInterpolator());

    Animator textDown =
        ObjectAnimator.ofFloat(
            swipeToAnswerText,
            View.TRANSLATION_Y,
            DpUtil.dpToPx(getContext(), -20) /* dp */,
            0 /* end pos */);
    textDown.setDuration(ANIMATE_DURATION_NORMAL_MILLIS);
    textUp.setInterpolator(new FastOutSlowInInterpolator());

    // "Swipe down to reject" text fades in with a slight translation
    swipeToRejectText.setAlpha(0f);
    Animator rejectTextShow =
        ObjectAnimator.ofPropertyValuesHolder(
            swipeToRejectText,
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f),
            PropertyValuesHolder.ofFloat(
                View.TRANSLATION_Y,
                DpUtil.dpToPx(getContext(), HINT_REJECT_FADE_TRANSLATION_Y_DP),
                0f));
    rejectTextShow.setInterpolator(new FastOutLinearInInterpolator());
    rejectTextShow.setDuration(ANIMATE_DURATION_SHORT_MILLIS);
    rejectTextShow.setStartDelay(SWIPE_TO_DECLINE_FADE_IN_DELAY_MILLIS);

    Animator puckUp =
        ObjectAnimator.ofFloat(
            contactPuckContainer,
            View.TRANSLATION_Y,
            DpUtil.dpToPx(getContext(), 400 /* dp */),
            DpUtil.dpToPx(getContext(), -12 /* dp */));
    puckUp.setDuration(ANIMATE_DURATION_LONG_MILLIS);
    puckUp.setInterpolator(
        PathInterpolatorCompat.create(
            0 /* controlX1 */, 0 /* controlY1 */, 0 /* controlX2 */, 1 /* controlY2 */));

    Animator puckDown =
        ObjectAnimator.ofFloat(
            contactPuckContainer,
            View.TRANSLATION_Y,
            DpUtil.dpToPx(getContext(), -12 /* dp */),
            0 /* end pos */);
    puckDown.setDuration(ANIMATE_DURATION_NORMAL_MILLIS);
    puckDown.setInterpolator(new FastOutSlowInInterpolator());

    Animator puckScaleUp =
        createUniformScaleAnimators(
            contactPuckBackground,
            0.33f /* beginScale */,
            1.1f /* endScale */,
            ANIMATE_DURATION_NORMAL_MILLIS,
            PathInterpolatorCompat.create(
                0.4f /* controlX1 */, 0 /* controlY1 */, 0 /* controlX2 */, 1 /* controlY2 */));
    Animator puckScaleDown =
        createUniformScaleAnimators(
            contactPuckBackground,
            1.1f /* beginScale */,
            1 /* endScale */,
            ANIMATE_DURATION_NORMAL_MILLIS,
            new FastOutSlowInInterpolator());

    // Upward animation chain.
    lockEntryAnim.play(textUp).with(puckScaleUp).with(puckUp);

    // Downward animation chain.
    lockEntryAnim.play(textDown).with(puckDown).with(puckScaleDown).after(puckUp);

    lockEntryAnim.play(rejectTextShow).after(puckUp);

    // Add vibration animation.
    addVibrationAnimator(lockEntryAnim);

    lockEntryAnim.addListener(
        new AnimatorListenerAdapter() {

          public boolean canceled;

          @Override
          public void onAnimationCancel(Animator animation) {
            super.onAnimationCancel(animation);
            canceled = true;
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            if (!canceled) {
              onEntryAnimationDone();
            }
          }
        });
    lockEntryAnim.start();
  }

  @VisibleForTesting
  void onEntryAnimationDone() {
    LogUtil.i("FlingUpDownMethod.onEntryAnimationDone", "Swipe entry anim ends.");
    if (animationState == AnimationState.ENTRY) {
      setAnimationState(AnimationState.BOUNCE);
    }
  }

  private void startSwipeToAnswerBounceAnimation() {
    LogUtil.i("FlingUpDownMethod.startSwipeToAnswerBounceAnimation", "Swipe bounce animation.");
    endAnimation();

    if (ViewUtil.areAnimationsDisabled(getContext())) {
      swipeToAnswerText.setTranslationY(0);
      contactPuckContainer.setTranslationY(0);
      contactPuckBackground.setScaleY(1f);
      contactPuckBackground.setScaleX(1f);
      swipeToRejectText.setAlpha(1f);
      swipeToRejectText.setTranslationY(0);
      return;
    }

    lockBounceAnim = createBreatheAnimation();

    answerHint.onBounceStart();
    lockBounceAnim.addListener(
        new AnimatorListenerAdapter() {
          boolean firstPass = true;

          @Override
          public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            if (getContext() != null
                && lockBounceAnim != null
                && animationState == AnimationState.BOUNCE) {
              // AnimatorSet doesn't have repeat settings. Instead, we start a new one after the
              // previous set is completed, until endAnimation is called.
              LogUtil.v("FlingUpDownMethod.onAnimationEnd", "Bounce again.");

              // If this is the first time repeating the animation, we should recreate it so its
              // starting values will be correct
              if (firstPass) {
                lockBounceAnim = createBreatheAnimation();
                lockBounceAnim.addListener(this);
              }
              firstPass = false;
              answerHint.onBounceStart();
              lockBounceAnim.start();
            }
          }
        });
    lockBounceAnim.start();
  }

  private Animator createBreatheAnimation() {
    AnimatorSet breatheAnimation = new AnimatorSet();
    float textOffset = DpUtil.dpToPx(getContext(), 42 /* dp */);
    Animator textUp =
        ObjectAnimator.ofFloat(
            swipeToAnswerText, View.TRANSLATION_Y, 0 /* begin pos */, -textOffset);
    textUp.setInterpolator(new FastOutSlowInInterpolator());
    textUp.setDuration(ANIMATE_DURATION_NORMAL_MILLIS);

    Animator textDown =
        ObjectAnimator.ofFloat(swipeToAnswerText, View.TRANSLATION_Y, -textOffset, 0 /* end pos */);
    textDown.setInterpolator(new FastOutSlowInInterpolator());
    textDown.setDuration(ANIMATE_DURATION_NORMAL_MILLIS);

    // "Swipe down to reject" text fade in
    Animator rejectTextShow = ObjectAnimator.ofFloat(swipeToRejectText, View.ALPHA, 1f);
    rejectTextShow.setInterpolator(new LinearOutSlowInInterpolator());
    rejectTextShow.setDuration(ANIMATE_DURATION_SHORT_MILLIS);
    rejectTextShow.setStartDelay(SWIPE_TO_DECLINE_FADE_IN_DELAY_MILLIS);

    // reject hint text translate in
    Animator rejectTextTranslate =
        ObjectAnimator.ofFloat(
            swipeToRejectText,
            View.TRANSLATION_Y,
            DpUtil.dpToPx(getContext(), HINT_REJECT_FADE_TRANSLATION_Y_DP),
            0f);
    rejectTextTranslate.setInterpolator(new FastOutSlowInInterpolator());
    rejectTextTranslate.setDuration(ANIMATE_DURATION_NORMAL_MILLIS);

    // reject hint text fade out
    Animator rejectTextHide = ObjectAnimator.ofFloat(swipeToRejectText, View.ALPHA, 0f);
    rejectTextHide.setInterpolator(new FastOutLinearInInterpolator());
    rejectTextHide.setDuration(ANIMATE_DURATION_SHORT_MILLIS);

    Interpolator curve =
        PathInterpolatorCompat.create(
            0.4f /* controlX1 */, 0 /* controlY1 */, 0 /* controlX2 */, 1 /* controlY2 */);
    float puckOffset = DpUtil.dpToPx(getContext(), 42 /* dp */);
    Animator puckUp = ObjectAnimator.ofFloat(contactPuckContainer, View.TRANSLATION_Y, -puckOffset);
    puckUp.setInterpolator(curve);
    puckUp.setDuration(ANIMATE_DURATION_LONG_MILLIS);

    final float scale = 1.0625f;
    Animator puckScaleUp =
        createUniformScaleAnimators(
            contactPuckBackground,
            1 /* beginScale */,
            scale,
            ANIMATE_DURATION_NORMAL_MILLIS,
            curve);

    Animator puckDown =
        ObjectAnimator.ofFloat(contactPuckContainer, View.TRANSLATION_Y, 0 /* end pos */);
    puckDown.setInterpolator(new FastOutSlowInInterpolator());
    puckDown.setDuration(ANIMATE_DURATION_NORMAL_MILLIS);

    Animator puckScaleDown =
        createUniformScaleAnimators(
            contactPuckBackground,
            scale,
            1 /* endScale */,
            ANIMATE_DURATION_NORMAL_MILLIS,
            new FastOutSlowInInterpolator());

    // Bounce upward animation chain.
    breatheAnimation
        .play(textUp)
        .with(rejectTextHide)
        .with(puckUp)
        .with(puckScaleUp)
        .after(167 /* delay */);

    // Bounce downward animation chain.
    breatheAnimation
        .play(puckDown)
        .with(textDown)
        .with(puckScaleDown)
        .with(rejectTextShow)
        .with(rejectTextTranslate)
        .after(puckUp);

    // Add vibration animation to the animator set.
    addVibrationAnimator(breatheAnimation);

    return breatheAnimation;
  }

  private void startSwipeToAnswerSettleAnimation() {
    endAnimation();

    ObjectAnimator puckScale =
        ObjectAnimator.ofPropertyValuesHolder(
            contactPuckBackground,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1));
    puckScale.setDuration(SETTLE_ANIMATION_DURATION_MILLIS);

    ObjectAnimator iconRotation = ObjectAnimator.ofFloat(contactPuckIcon, View.ROTATION, 0);
    iconRotation.setDuration(SETTLE_ANIMATION_DURATION_MILLIS);

    ObjectAnimator swipeToAnswerTextFade =
        createFadeAnimation(swipeToAnswerText, 1, SETTLE_ANIMATION_DURATION_MILLIS);

    ObjectAnimator contactPuckContainerFade =
        createFadeAnimation(contactPuckContainer, 1, SETTLE_ANIMATION_DURATION_MILLIS);

    ObjectAnimator contactPuckBackgroundFade =
        createFadeAnimation(contactPuckBackground, 1, SETTLE_ANIMATION_DURATION_MILLIS);

    ObjectAnimator contactPuckIconFade =
        createFadeAnimation(
            contactPuckIcon, shouldShowPhotoInPuck() ? 0 : 1, SETTLE_ANIMATION_DURATION_MILLIS);

    ObjectAnimator contactPuckTranslation =
        ObjectAnimator.ofPropertyValuesHolder(
            contactPuckContainer,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0));
    contactPuckTranslation.setDuration(SETTLE_ANIMATION_DURATION_MILLIS);

    lockSettleAnim = new AnimatorSet();
    lockSettleAnim
        .play(puckScale)
        .with(iconRotation)
        .with(swipeToAnswerTextFade)
        .with(contactPuckContainerFade)
        .with(contactPuckBackgroundFade)
        .with(contactPuckIconFade)
        .with(contactPuckTranslation);

    lockSettleAnim.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationCancel(Animator animation) {
            afterSettleAnimationState = AnimationState.NONE;
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            onSettleAnimationDone();
          }
        });

    lockSettleAnim.start();
  }

  @VisibleForTesting
  void onSettleAnimationDone() {
    if (afterSettleAnimationState != AnimationState.NONE) {
      int nextState = afterSettleAnimationState;
      afterSettleAnimationState = AnimationState.NONE;
      lockSettleAnim = null;

      setAnimationState(nextState);
    }
  }

  private ObjectAnimator createFadeAnimation(View target, float targetAlpha, long duration) {
    ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(target, View.ALPHA, targetAlpha);
    objectAnimator.setDuration(duration);
    return objectAnimator;
  }

  private void startSwipeToAnswerHintAnimation() {
    if (rejectHintHide != null) {
      rejectHintHide.cancel();
    }

    endAnimation();
    resetTouchState();

    if (ViewUtil.areAnimationsDisabled(getContext())) {
      onHintAnimationDone(false);
      return;
    }

    lockHintAnim = new AnimatorSet();
    float jumpOffset = DpUtil.dpToPx(getContext(), HINT_JUMP_DP);
    float dipOffset = DpUtil.dpToPx(getContext(), HINT_DIP_DP);
    float scaleSize = HINT_SCALE_RATIO;
    float textOffset = jumpOffset + (scaleSize - 1) * contactPuckBackground.getHeight();
    int shortAnimTime =
        getContext().getResources().getInteger(android.R.integer.config_shortAnimTime);
    int mediumAnimTime =
        getContext().getResources().getInteger(android.R.integer.config_mediumAnimTime);

    // Puck squashes to anticipate jump
    ObjectAnimator puckAnticipate =
        ObjectAnimator.ofPropertyValuesHolder(
            contactPuckContainer,
            PropertyValuesHolder.ofFloat(View.SCALE_Y, .95f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.05f));
    puckAnticipate.setRepeatCount(1);
    puckAnticipate.setRepeatMode(ValueAnimator.REVERSE);
    puckAnticipate.setDuration(shortAnimTime / 2);
    puckAnticipate.setInterpolator(new DecelerateInterpolator());
    puckAnticipate.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationStart(Animator animation) {
            super.onAnimationStart(animation);
            contactPuckContainer.setPivotY(contactPuckContainer.getHeight());
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            contactPuckContainer.setPivotY(contactPuckContainer.getHeight() / 2);
          }
        });

    // Ensure puck is at the right starting point for the jump
    ObjectAnimator puckResetTranslation =
        ObjectAnimator.ofPropertyValuesHolder(
            contactPuckContainer,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0));
    puckResetTranslation.setDuration(shortAnimTime / 2);
    puckAnticipate.setInterpolator(new DecelerateInterpolator());

    Animator textUp = ObjectAnimator.ofFloat(swipeToAnswerText, View.TRANSLATION_Y, -textOffset);
    textUp.setInterpolator(new LinearOutSlowInInterpolator());
    textUp.setDuration(shortAnimTime);

    Animator puckUp = ObjectAnimator.ofFloat(contactPuckContainer, View.TRANSLATION_Y, -jumpOffset);
    puckUp.setInterpolator(new LinearOutSlowInInterpolator());
    puckUp.setDuration(shortAnimTime);

    Animator puckScaleUp =
        createUniformScaleAnimators(
            contactPuckBackground, 1f, scaleSize, shortAnimTime, new LinearOutSlowInInterpolator());

    Animator rejectHintShow =
        ObjectAnimator.ofPropertyValuesHolder(
            swipeToRejectText,
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f));
    rejectHintShow.setDuration(shortAnimTime);

    Animator rejectHintDip =
        ObjectAnimator.ofFloat(swipeToRejectText, View.TRANSLATION_Y, dipOffset);
    rejectHintDip.setInterpolator(new LinearOutSlowInInterpolator());
    rejectHintDip.setDuration(shortAnimTime);

    Animator textDown = ObjectAnimator.ofFloat(swipeToAnswerText, View.TRANSLATION_Y, 0);
    textDown.setInterpolator(new LinearOutSlowInInterpolator());
    textDown.setDuration(mediumAnimTime);

    Animator puckDown = ObjectAnimator.ofFloat(contactPuckContainer, View.TRANSLATION_Y, 0);
    BounceInterpolator bounce = new BounceInterpolator();
    puckDown.setInterpolator(bounce);
    puckDown.setDuration(mediumAnimTime);

    Animator puckScaleDown =
        createUniformScaleAnimators(
            contactPuckBackground, scaleSize, 1f, shortAnimTime, new LinearOutSlowInInterpolator());

    Animator rejectHintUp = ObjectAnimator.ofFloat(swipeToRejectText, View.TRANSLATION_Y, 0);
    rejectHintUp.setInterpolator(new LinearOutSlowInInterpolator());
    rejectHintUp.setDuration(mediumAnimTime);

    lockHintAnim.play(puckAnticipate).with(puckResetTranslation).before(puckUp);
    lockHintAnim
        .play(textUp)
        .with(puckUp)
        .with(puckScaleUp)
        .with(rejectHintDip)
        .with(rejectHintShow);
    lockHintAnim.play(textDown).with(puckDown).with(puckScaleDown).with(rejectHintUp).after(puckUp);
    lockHintAnim.start();

    rejectHintHide = ObjectAnimator.ofFloat(swipeToRejectText, View.ALPHA, 0);
    rejectHintHide.setStartDelay(HINT_REJECT_SHOW_DURATION_MILLIS);
    rejectHintHide.addListener(
        new AnimatorListenerAdapter() {

          private boolean canceled;

          @Override
          public void onAnimationCancel(Animator animation) {
            super.onAnimationCancel(animation);
            canceled = true;
            rejectHintHide = null;
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            onHintAnimationDone(canceled);
          }
        });
    rejectHintHide.start();
  }

  @VisibleForTesting
  void onHintAnimationDone(boolean canceled) {
    if (!canceled && animationState == AnimationState.HINT) {
      setAnimationState(AnimationState.BOUNCE);
    }
    rejectHintHide = null;
  }

  private void clearSwipeToAnswerUi() {
    LogUtil.i("FlingUpDownMethod.clearSwipeToAnswerUi", "Clear swipe animation.");
    endAnimation();
    swipeToAnswerText.setVisibility(View.GONE);
    contactPuckContainer.setVisibility(View.GONE);
  }

  private void endAnimation() {
    LogUtil.i("FlingUpDownMethod.endAnimation", "End animations.");
    if (lockSettleAnim != null) {
      lockSettleAnim.cancel();
      lockSettleAnim = null;
    }
    if (lockBounceAnim != null) {
      lockBounceAnim.cancel();
      lockBounceAnim = null;
    }
    if (lockEntryAnim != null) {
      lockEntryAnim.cancel();
      lockEntryAnim = null;
    }
    if (lockHintAnim != null) {
      lockHintAnim.cancel();
      lockHintAnim = null;
    }
    if (rejectHintHide != null) {
      rejectHintHide.cancel();
      rejectHintHide = null;
    }
    if (vibrationAnimator != null) {
      vibrationAnimator.end();
      vibrationAnimator = null;
    }
    answerHint.onBounceEnd();
  }

  // Create an animator to scale on X/Y directions uniformly.
  private Animator createUniformScaleAnimators(
      View target, float begin, float end, long duration, Interpolator interpolator) {
    ObjectAnimator animator =
        ObjectAnimator.ofPropertyValuesHolder(
            target,
            PropertyValuesHolder.ofFloat(View.SCALE_X, begin, end),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, begin, end));
    animator.setDuration(duration);
    animator.setInterpolator(interpolator);
    return animator;
  }

  private void addVibrationAnimator(AnimatorSet animatorSet) {
    if (vibrationAnimator != null) {
      vibrationAnimator.end();
    }

    // Note that we animate the value between 0 and 1, but internally VibrateInterpolator will
    // translate it into actually X translation value.
    vibrationAnimator =
        ObjectAnimator.ofFloat(
            contactPuckContainer, View.TRANSLATION_X, 0 /* begin value */, 1 /* end value */);
    vibrationAnimator.setDuration(VIBRATION_TIME_MILLIS);
    vibrationAnimator.setInterpolator(new VibrateInterpolator(getContext()));

    animatorSet.play(vibrationAnimator).after(0 /* delay */);
  }

  private void performAccept() {
    LogUtil.i("FlingUpDownMethod.performAccept", null);
    swipeToAnswerText.setVisibility(View.GONE);
    contactPuckContainer.setVisibility(View.GONE);

    // Complete the animation loop.
    setAnimationState(AnimationState.COMPLETED);
    getParent().answerFromMethod();
  }

  private void performReject() {
    LogUtil.i("FlingUpDownMethod.performReject", null);
    swipeToAnswerText.setVisibility(View.GONE);
    contactPuckContainer.setVisibility(View.GONE);

    // Complete the animation loop.
    setAnimationState(AnimationState.COMPLETED);
    getParent().rejectFromMethod();
  }

  /** Custom interpolator class for puck vibration. */
  private static class VibrateInterpolator implements Interpolator {

    private static final long RAMP_UP_BEGIN_MS = 583;
    private static final long RAMP_UP_DURATION_MS = 167;
    private static final long RAMP_UP_END_MS = RAMP_UP_BEGIN_MS + RAMP_UP_DURATION_MS;
    private static final long RAMP_DOWN_BEGIN_MS = 1_583;
    private static final long RAMP_DOWN_DURATION_MS = 250;
    private static final long RAMP_DOWN_END_MS = RAMP_DOWN_BEGIN_MS + RAMP_DOWN_DURATION_MS;
    private static final long RAMP_TOTAL_TIME_MS = RAMP_DOWN_END_MS;
    private final float ampMax;
    private final float freqMax = 80;
    private Interpolator sliderInterpolator = new FastOutSlowInInterpolator();

    VibrateInterpolator(Context context) {
      ampMax = DpUtil.dpToPx(context, 1 /* dp */);
    }

    @Override
    public float getInterpolation(float t) {
      float slider = 0;
      float time = t * RAMP_TOTAL_TIME_MS;

      // Calculate the slider value based on RAMP_UP and RAMP_DOWN times. Between RAMP_UP and
      // RAMP_DOWN, the slider remains the maximum value of 1.
      if (time > RAMP_UP_BEGIN_MS && time < RAMP_UP_END_MS) {
        // Ramp up.
        slider =
            sliderInterpolator.getInterpolation(
                (time - RAMP_UP_BEGIN_MS) / (float) RAMP_UP_DURATION_MS);
      } else if ((time >= RAMP_UP_END_MS) && time <= RAMP_DOWN_BEGIN_MS) {
        // Vibrate at maximum
        slider = 1;
      } else if (time > RAMP_DOWN_BEGIN_MS && time < RAMP_DOWN_END_MS) {
        // Ramp down.
        slider =
            1
                - sliderInterpolator.getInterpolation(
                    (time - RAMP_DOWN_BEGIN_MS) / (float) RAMP_DOWN_DURATION_MS);
      }

      float ampNormalized = ampMax * slider;
      float freqNormalized = freqMax * slider;

      return (float) (ampNormalized * Math.sin(time * freqNormalized));
    }
  }
}
