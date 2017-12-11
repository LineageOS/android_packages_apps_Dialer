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

package com.android.newbubble;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.os.BuildCompat;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.transition.TransitionManager;
import android.transition.TransitionValues;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewAnimator;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.util.DrawableConverter;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.newbubble.NewBubbleInfo.Action;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * Creates and manages a bubble window from information in a {@link NewBubbleInfo}. Before creating,
 * be sure to check whether bubbles may be shown using {@link #canShowBubbles(Context)} and request
 * permission if necessary ({@link #getRequestPermissionIntent(Context)} is provided for
 * convenience)
 */
public class NewBubble {
  // This class has some odd behavior that is not immediately obvious in order to avoid jank when
  // resizing. See http://go/bubble-resize for details.

  // How long text should show after showText(CharSequence) is called
  private static final int SHOW_TEXT_DURATION_MILLIS = 3000;
  // How long the new window should show before destroying the old one during resize operations.
  // This ensures the new window has had time to draw first.
  private static final int WINDOW_REDRAW_DELAY_MILLIS = 50;

  private static Boolean canShowBubblesForTesting = null;

  private final Context context;
  private final WindowManager windowManager;

  private final Handler handler;
  private LayoutParams windowParams;

  // Initialized in factory method
  @SuppressWarnings("NullableProblems")
  @NonNull
  private NewBubbleInfo currentInfo;

  @Visibility private int visibility;
  private boolean expanded;
  private boolean textShowing;
  private boolean hideAfterText;
  private CharSequence textAfterShow;
  private int collapseEndAction;

  ViewHolder viewHolder;
  private ViewPropertyAnimator collapseAnimation;
  private Integer overrideGravity;
  @VisibleForTesting AnimatorSet exitAnimatorSet;

  private final int primaryIconMoveDistance;
  private final int leftBoundary;
  private int savedYPosition = -1;

  private final Runnable collapseRunnable =
      new Runnable() {
        @Override
        public void run() {
          textShowing = false;
          if (hideAfterText) {
            // Always reset here since text shouldn't keep showing.
            hideAndReset();
          } else {
            viewHolder.getPrimaryButton().setDisplayedChild(ViewHolder.CHILD_INDEX_AVATAR_AND_ICON);
          }
        }
      };

  /** Type of action after bubble collapse */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({CollapseEnd.NOTHING, CollapseEnd.HIDE})
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  public @interface CollapseEnd {
    int NOTHING = 0;
    int HIDE = 1;
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({Visibility.ENTERING, Visibility.SHOWING, Visibility.EXITING, Visibility.HIDDEN})
  private @interface Visibility {
    int HIDDEN = 0;
    int ENTERING = 1;
    int SHOWING = 2;
    int EXITING = 3;
  }

  /** Indicate bubble expansion state. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({ExpansionState.START_EXPANDING, ExpansionState.START_COLLAPSING})
  public @interface ExpansionState {
    // TODO(yueg): add more states when needed
    int START_EXPANDING = 0;
    int START_COLLAPSING = 1;
  }

  /**
   * Determines whether bubbles can be shown based on permissions obtained. This should be checked
   * before attempting to create a Bubble.
   *
   * @return true iff bubbles are able to be shown.
   * @see Settings#canDrawOverlays(Context)
   */
  public static boolean canShowBubbles(@NonNull Context context) {
    return canShowBubblesForTesting != null
        ? canShowBubblesForTesting
        : Settings.canDrawOverlays(context);
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public static void setCanShowBubblesForTesting(boolean canShowBubbles) {
    canShowBubblesForTesting = canShowBubbles;
  }

  /** Returns an Intent to request permission to show overlays */
  @NonNull
  public static Intent getRequestPermissionIntent(@NonNull Context context) {
    return new Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.fromParts("package", context.getPackageName(), null));
  }

  /** Creates instances of Bubble. The default implementation just calls the constructor. */
  @VisibleForTesting
  public interface BubbleFactory {
    NewBubble createBubble(@NonNull Context context, @NonNull Handler handler);
  }

  private static BubbleFactory bubbleFactory = NewBubble::new;

  public static NewBubble createBubble(@NonNull Context context, @NonNull NewBubbleInfo info) {
    NewBubble bubble = bubbleFactory.createBubble(context, new Handler());
    bubble.setBubbleInfo(info);
    return bubble;
  }

  @VisibleForTesting
  public static void setBubbleFactory(@NonNull BubbleFactory bubbleFactory) {
    NewBubble.bubbleFactory = bubbleFactory;
  }

  @VisibleForTesting
  public static void resetBubbleFactory() {
    NewBubble.bubbleFactory = NewBubble::new;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  NewBubble(@NonNull Context context, @NonNull Handler handler) {
    context = new ContextThemeWrapper(context, R.style.Theme_AppCompat);
    this.context = context;
    this.handler = handler;
    windowManager = context.getSystemService(WindowManager.class);

    viewHolder = new ViewHolder(context);

    leftBoundary =
        context.getResources().getDimensionPixelOffset(R.dimen.bubble_off_screen_size_horizontal)
            - context
                .getResources()
                .getDimensionPixelSize(R.dimen.bubble_shadow_padding_size_horizontal);
    primaryIconMoveDistance =
        context.getResources().getDimensionPixelSize(R.dimen.bubble_size)
            - context.getResources().getDimensionPixelSize(R.dimen.bubble_small_icon_size);
  }

  /** Expands the main bubble menu. */
  public void expand(boolean isUserAction) {
    if (isUserAction) {
      logBasicOrCallImpression(DialerImpression.Type.BUBBLE_PRIMARY_BUTTON_EXPAND);
    }
    viewHolder.setDrawerVisibility(View.INVISIBLE);
    View expandedView = viewHolder.getExpandedView();
    expandedView
        .getViewTreeObserver()
        .addOnPreDrawListener(
            new OnPreDrawListener() {
              @Override
              public boolean onPreDraw() {
                // Move the whole bubble up so that expanded view is still in screen
                int moveUpDistance = viewHolder.getMoveUpDistance();
                if (moveUpDistance != 0) {
                  savedYPosition = windowParams.y;
                }

                // Calculate the move-to-middle distance
                int deltaX =
                    (int) viewHolder.getRoot().findViewById(R.id.bubble_primary_container).getX();
                float k = (float) moveUpDistance / deltaX;
                if (isDrawingFromRight()) {
                  deltaX = -deltaX;
                }

                // Do X-move and Y-move together

                final int startX = windowParams.x - deltaX;
                final int startY = windowParams.y;
                ValueAnimator animator = ValueAnimator.ofFloat(startX, windowParams.x);
                animator.setInterpolator(new LinearOutSlowInInterpolator());
                animator.addUpdateListener(
                    (valueAnimator) -> {
                      // Update windowParams and the root layout.
                      // We can't do ViewPropertyAnimation since it clips children.
                      float newX = (float) valueAnimator.getAnimatedValue();
                      if (moveUpDistance != 0) {
                        windowParams.y = startY - (int) (Math.abs(newX - (float) startX) * k);
                      }
                      windowParams.x = (int) newX;
                      windowManager.updateViewLayout(viewHolder.getRoot(), windowParams);
                    });
                animator.addListener(
                    new AnimatorListenerAdapter() {
                      @Override
                      public void onAnimationEnd(Animator animation) {
                        // Show expanded view
                        expandedView.setVisibility(View.VISIBLE);
                        expandedView.setTranslationY(-expandedView.getHeight());
                        expandedView.setAlpha(0);
                        expandedView
                            .animate()
                            .setInterpolator(new LinearOutSlowInInterpolator())
                            .translationY(0)
                            .alpha(1);
                      }
                    });
                animator.start();

                expandedView.getViewTreeObserver().removeOnPreDrawListener(this);
                return false;
              }
            });
    setFocused(true);
    expanded = true;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  public void startCollapse(
      @CollapseEnd int endAction, boolean isUserAction, boolean shouldRecoverYPosition) {
    View expandedView = viewHolder.getExpandedView();
    if (expandedView.getVisibility() != View.VISIBLE || collapseAnimation != null) {
      // Drawer is already collapsed or animation is running.
      return;
    }

    overrideGravity = isDrawingFromRight() ? Gravity.RIGHT : Gravity.LEFT;
    setFocused(false);

    if (collapseEndAction == CollapseEnd.NOTHING) {
      collapseEndAction = endAction;
    }
    if (isUserAction && collapseEndAction == CollapseEnd.NOTHING) {
      logBasicOrCallImpression(DialerImpression.Type.BUBBLE_COLLAPSE_BY_USER);
    }
    // Animate expanded view to move from its position to above primary button and hide
    collapseAnimation =
        expandedView
            .animate()
            .translationY(-expandedView.getHeight())
            .alpha(0)
            .setInterpolator(new FastOutLinearInInterpolator())
            .withEndAction(
                () -> {
                  collapseAnimation = null;
                  expanded = false;

                  if (textShowing) {
                    // Will do resize once the text is done.
                    return;
                  }

                  // Set drawer visibility to INVISIBLE instead of GONE to keep primary button fixed
                  viewHolder.setDrawerVisibility(View.INVISIBLE);

                  // Do X-move and Y-move together
                  int deltaX =
                      (int) viewHolder.getRoot().findViewById(R.id.bubble_primary_container).getX();
                  int startX = windowParams.x;
                  int startY = windowParams.y;
                  float k =
                      (savedYPosition != -1 && shouldRecoverYPosition)
                          ? (savedYPosition - startY) / (float) deltaX
                          : 0;
                  Path path = new Path();
                  path.moveTo(windowParams.x, windowParams.y);
                  path.lineTo(
                      windowParams.x - deltaX,
                      (savedYPosition != -1 && shouldRecoverYPosition)
                          ? savedYPosition
                          : windowParams.y);
                  // The position is not useful after collapse
                  savedYPosition = -1;

                  ValueAnimator animator = ValueAnimator.ofFloat(startX, startX - deltaX);
                  animator.setInterpolator(new LinearOutSlowInInterpolator());
                  animator.addUpdateListener(
                      (valueAnimator) -> {
                        // Update windowParams and the root layout.
                        // We can't do ViewPropertyAnimation since it clips children.
                        float newX = (float) valueAnimator.getAnimatedValue();
                        if (k != 0) {
                          windowParams.y = startY + (int) (Math.abs(newX - (float) startX) * k);
                        }
                        windowParams.x = (int) newX;
                        windowManager.updateViewLayout(viewHolder.getRoot(), windowParams);
                      });
                  animator.addListener(
                      new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                          // If collapse on the right side, the primary button move left a bit after
                          // drawer
                          // visibility becoming GONE. To avoid it, we create a new ViewHolder.
                          replaceViewHolder();
                        }
                      });
                  animator.start();

                  // If this collapse was to come before a hide, do it now.
                  if (collapseEndAction == CollapseEnd.HIDE) {
                    hide();
                  }
                  collapseEndAction = CollapseEnd.NOTHING;

                  // Resume normal gravity after any resizing is done.
                  handler.postDelayed(
                      () -> {
                        overrideGravity = null;
                        if (!viewHolder.isMoving()) {
                          viewHolder.undoGravityOverride();
                        }
                      },
                      // Need to wait twice as long for resize and layout
                      WINDOW_REDRAW_DELAY_MILLIS * 2);
                });
  }

  /**
   * Make the bubble visible. Will show a short entrance animation as it enters. If the bubble is
   * already showing this method does nothing.
   */
  public void show() {
    if (collapseEndAction == CollapseEnd.HIDE) {
      // If show() was called while collapsing, make sure we don't hide after.
      collapseEndAction = CollapseEnd.NOTHING;
    }
    if (visibility == Visibility.SHOWING || visibility == Visibility.ENTERING) {
      return;
    }

    hideAfterText = false;

    if (windowParams == null) {
      // Apps targeting O+ must use TYPE_APPLICATION_OVERLAY, which is not available prior to O.
      @SuppressWarnings("deprecation")
      @SuppressLint("InlinedApi")
      int type =
          BuildCompat.isAtLeastO()
              ? LayoutParams.TYPE_APPLICATION_OVERLAY
              : LayoutParams.TYPE_PHONE;

      windowParams =
          new LayoutParams(
              type,
              LayoutParams.FLAG_NOT_TOUCH_MODAL
                  | LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                  | LayoutParams.FLAG_NOT_FOCUSABLE
                  | LayoutParams.FLAG_LAYOUT_NO_LIMITS,
              PixelFormat.TRANSLUCENT);
      windowParams.gravity = Gravity.TOP | Gravity.LEFT;
      windowParams.x = leftBoundary;
      windowParams.y = currentInfo.getStartingYPosition();
      windowParams.height = LayoutParams.WRAP_CONTENT;
      windowParams.width = LayoutParams.WRAP_CONTENT;
    }

    if (exitAnimatorSet != null) {
      exitAnimatorSet.removeAllListeners();
      exitAnimatorSet.cancel();
      exitAnimatorSet = null;
    } else {
      windowManager.addView(viewHolder.getRoot(), windowParams);
      viewHolder.getPrimaryButton().setVisibility(View.VISIBLE);
      viewHolder.getPrimaryButton().setScaleX(0);
      viewHolder.getPrimaryButton().setScaleY(0);
      viewHolder.getPrimaryAvatar().setAlpha(0f);
      viewHolder.getPrimaryIcon().setAlpha(0f);
    }

    viewHolder.setChildClickable(true);
    visibility = Visibility.ENTERING;

    // Show bubble animation: scale the whole bubble to 1, and change avatar+icon's alpha to 1
    ObjectAnimator scaleXAnimator =
        ObjectAnimator.ofFloat(viewHolder.getPrimaryButton(), "scaleX", 1);
    ObjectAnimator scaleYAnimator =
        ObjectAnimator.ofFloat(viewHolder.getPrimaryButton(), "scaleY", 1);
    ObjectAnimator avatarAlphaAnimator =
        ObjectAnimator.ofFloat(viewHolder.getPrimaryAvatar(), "alpha", 1);
    ObjectAnimator iconAlphaAnimator =
        ObjectAnimator.ofFloat(viewHolder.getPrimaryIcon(), "alpha", 1);
    AnimatorSet enterAnimatorSet = new AnimatorSet();
    enterAnimatorSet.playTogether(
        scaleXAnimator, scaleYAnimator, avatarAlphaAnimator, iconAlphaAnimator);
    enterAnimatorSet.setInterpolator(new OvershootInterpolator());
    enterAnimatorSet.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            visibility = Visibility.SHOWING;
            // Show the queued up text, if available.
            if (textAfterShow != null) {
              showText(textAfterShow);
              textAfterShow = null;
            }
          }
        });
    enterAnimatorSet.start();

    updatePrimaryIconAnimation();
  }

  /** Hide the bubble. */
  public void hide() {
    if (hideAfterText) {
      // hideAndReset() will be called after showing text, do nothing here.
      return;
    }
    hideHelper(this::defaultAfterHidingAnimation);
  }

  /** Hide the bubble and reset {@viewHolder} to initial state */
  public void hideAndReset() {
    hideHelper(
        () -> {
          defaultAfterHidingAnimation();
          reset();
        });
  }

  /** Returns whether the bubble is currently visible */
  public boolean isVisible() {
    return visibility == Visibility.SHOWING
        || visibility == Visibility.ENTERING
        || visibility == Visibility.EXITING;
  }

  /**
   * Set the info for this Bubble to display
   *
   * @param bubbleInfo the BubbleInfo to display in this Bubble.
   */
  public void setBubbleInfo(@NonNull NewBubbleInfo bubbleInfo) {
    currentInfo = bubbleInfo;
    update();
  }

  /**
   * Update the state and behavior of actions.
   *
   * @param actions the new state of the bubble's actions
   */
  public void updateActions(@NonNull List<Action> actions) {
    currentInfo = NewBubbleInfo.from(currentInfo).setActions(actions).build();
    updateButtonStates();
  }

  /**
   * Update the avatar from photo.
   *
   * @param avatar the new photo avatar in the bubble's primary button
   */
  public void updatePhotoAvatar(@NonNull Drawable avatar) {
    // Make it round
    int bubbleSize = context.getResources().getDimensionPixelSize(R.dimen.bubble_size);
    Drawable roundAvatar =
        DrawableConverter.getRoundedDrawable(context, avatar, bubbleSize, bubbleSize);

    updateAvatar(roundAvatar);
  }

  /**
   * Update the avatar.
   *
   * @param avatar the new avatar in the bubble's primary button
   */
  public void updateAvatar(@NonNull Drawable avatar) {
    if (!avatar.equals(currentInfo.getAvatar())) {
      currentInfo = NewBubbleInfo.from(currentInfo).setAvatar(avatar).build();
      viewHolder.getPrimaryAvatar().setImageDrawable(currentInfo.getAvatar());
    }
  }

  /** Returns the currently displayed NewBubbleInfo */
  public NewBubbleInfo getBubbleInfo() {
    return currentInfo;
  }

  /**
   * Display text in the main bubble. The bubble's drawer is not expandable while text is showing,
   * and the drawer will be closed if already open.
   *
   * @param text the text to display to the user
   */
  public void showText(@NonNull CharSequence text) {
    textShowing = true;
    if (expanded) {
      startCollapse(
          CollapseEnd.NOTHING, false /* isUserAction */, false /* shouldRecoverYPosition */);
      doShowText(text);
    } else {
      // Need to transition from old bounds to new bounds manually
      NewChangeOnScreenBounds transition = new NewChangeOnScreenBounds();
      // Prepare and capture start values
      TransitionValues startValues = new TransitionValues();
      startValues.view = viewHolder.getPrimaryButton();
      transition.addTarget(startValues.view);
      transition.captureStartValues(startValues);

      // If our view is not laid out yet, postpone showing the text.
      if (startValues.values.isEmpty()) {
        textAfterShow = text;
        return;
      }

      doShowText(text);
      // Hide the text so we can animate it in
      viewHolder.getPrimaryText().setAlpha(0);

      ViewAnimator primaryButton = viewHolder.getPrimaryButton();
      // Cancel the automatic transition scheduled in doShowText
      TransitionManager.endTransitions((ViewGroup) primaryButton.getParent());
      primaryButton
          .getViewTreeObserver()
          .addOnPreDrawListener(
              new OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                  primaryButton.getViewTreeObserver().removeOnPreDrawListener(this);

                  // Prepare and capture end values, always use the size of primaryText since
                  // its invisibility makes primaryButton smaller than expected
                  TransitionValues endValues = new TransitionValues();
                  endValues.values.put(
                      NewChangeOnScreenBounds.PROPNAME_WIDTH,
                      viewHolder.getPrimaryText().getWidth());
                  endValues.values.put(
                      NewChangeOnScreenBounds.PROPNAME_HEIGHT,
                      viewHolder.getPrimaryText().getHeight());
                  endValues.view = primaryButton;
                  transition.addTarget(endValues.view);
                  transition.captureEndValues(endValues);

                  // animate the primary button bounds change
                  Animator bounds =
                      transition.createAnimator(primaryButton, startValues, endValues);

                  // Animate the text in
                  Animator alpha =
                      ObjectAnimator.ofFloat(viewHolder.getPrimaryText(), View.ALPHA, 1f);

                  AnimatorSet set = new AnimatorSet();
                  set.play(bounds).before(alpha);
                  set.start();
                  return false;
                }
              });
    }
    handler.removeCallbacks(collapseRunnable);
    handler.postDelayed(collapseRunnable, SHOW_TEXT_DURATION_MILLIS);
  }

  @Nullable
  Integer getGravityOverride() {
    return overrideGravity;
  }

  void onMoveStart() {
    if (viewHolder.getExpandedView().getVisibility() == View.VISIBLE) {
      viewHolder.setDrawerVisibility(View.INVISIBLE);
    }
    expanded = false;
    savedYPosition = -1;

    viewHolder
        .getPrimaryButton()
        .animate()
        .translationZ(
            context
                .getResources()
                .getDimensionPixelOffset(R.dimen.bubble_dragging_elevation_change));
  }

  void onMoveFinish() {
    viewHolder.getPrimaryButton().animate().translationZ(0);
  }

  void primaryButtonClick() {
    if (textShowing || currentInfo.getActions().isEmpty()) {
      return;
    }
    if (expanded) {
      startCollapse(
          CollapseEnd.NOTHING, true /* isUserAction */, true /* shouldRecoverYPosition */);
    } else {
      expand(true);
    }
  }

  void onLeftRightSwitch(boolean onRight) {
    // Move primary icon to the other side so it's not partially hiden
    View primaryIcon = viewHolder.getPrimaryIcon();
    primaryIcon.animate().translationX(onRight ? -primaryIconMoveDistance : 0).start();
  }

  LayoutParams getWindowParams() {
    return windowParams;
  }

  View getRootView() {
    return viewHolder.getRoot();
  }

  /**
   * Hide the bubble if visible. Will run a short exit animation and before hiding, and {@code
   * afterHiding} after hiding. If the bubble is currently showing text, will hide after the text is
   * done displaying. If the bubble is not visible this method does nothing.
   */
  @VisibleForTesting
  void hideHelper(Runnable afterHiding) {
    if (visibility == Visibility.HIDDEN || visibility == Visibility.EXITING) {
      return;
    }

    // Make bubble non clickable to prevent further buggy actions
    viewHolder.setChildClickable(false);

    if (textShowing) {
      hideAfterText = true;
      return;
    }

    if (collapseAnimation != null) {
      collapseEndAction = CollapseEnd.HIDE;
      return;
    }

    if (expanded) {
      startCollapse(CollapseEnd.HIDE, false /* isUserAction */, false /* shouldRecoverYPosition */);
      return;
    }

    visibility = Visibility.EXITING;

    // Hide bubble animation: scale the whole bubble to 0, and change avatar+icon's alpha to 0
    ObjectAnimator scaleXAnimator =
        ObjectAnimator.ofFloat(viewHolder.getPrimaryButton(), "scaleX", 0);
    ObjectAnimator scaleYAnimator =
        ObjectAnimator.ofFloat(viewHolder.getPrimaryButton(), "scaleY", 0);
    ObjectAnimator avatarAlphaAnimator =
        ObjectAnimator.ofFloat(viewHolder.getPrimaryAvatar(), "alpha", 0);
    ObjectAnimator iconAlphaAnimator =
        ObjectAnimator.ofFloat(viewHolder.getPrimaryIcon(), "alpha", 0);
    exitAnimatorSet = new AnimatorSet();
    exitAnimatorSet.playTogether(
        scaleXAnimator, scaleYAnimator, avatarAlphaAnimator, iconAlphaAnimator);
    exitAnimatorSet.setInterpolator(new AnticipateInterpolator());
    exitAnimatorSet.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            afterHiding.run();
          }
        });
    exitAnimatorSet.start();
  }

  private void reset() {
    viewHolder = new ViewHolder(viewHolder.getRoot().getContext());
    update();
  }

  private void update() {
    // Whole primary button background
    Drawable backgroundCirle =
        context.getResources().getDrawable(R.drawable.bubble_shape_circle, context.getTheme());
    int primaryTint =
        ColorUtils.compositeColors(
            context.getColor(R.color.bubble_primary_background_darken),
            currentInfo.getPrimaryColor());
    backgroundCirle.mutate().setTint(primaryTint);
    viewHolder.getPrimaryButton().setBackground(backgroundCirle);

    // Small icon
    Drawable smallIconBackgroundCircle =
        context
            .getResources()
            .getDrawable(R.drawable.bubble_shape_circle_small, context.getTheme());
    smallIconBackgroundCircle.setTint(context.getColor(R.color.bubble_button_color_blue));
    viewHolder.getPrimaryIcon().setBackground(smallIconBackgroundCircle);
    viewHolder.getPrimaryIcon().setImageIcon(currentInfo.getPrimaryIcon());
    viewHolder.getPrimaryAvatar().setImageDrawable(currentInfo.getAvatar());

    updatePrimaryIconAnimation();
    updateButtonStates();
  }

  private void updatePrimaryIconAnimation() {
    Drawable drawable = viewHolder.getPrimaryIcon().getDrawable();
    if (drawable instanceof Animatable) {
      if (isVisible()) {
        ((Animatable) drawable).start();
      } else {
        ((Animatable) drawable).stop();
      }
    }
  }

  private void updateButtonStates() {
    configureButton(currentInfo.getActions().get(0), viewHolder.getFullScreenButton());
    configureButton(currentInfo.getActions().get(1), viewHolder.getMuteButton());
    configureButton(currentInfo.getActions().get(2), viewHolder.getAudioRouteButton());
    configureButton(currentInfo.getActions().get(3), viewHolder.getEndCallButton());
  }

  @VisibleForTesting
  void doShowText(@NonNull CharSequence text) {
    TransitionManager.beginDelayedTransition((ViewGroup) viewHolder.getPrimaryButton().getParent());
    viewHolder.getPrimaryText().setText(text);
    viewHolder.getPrimaryButton().setDisplayedChild(ViewHolder.CHILD_INDEX_TEXT);
  }

  private void configureButton(Action action, NewCheckableButton button) {
    button.setCompoundDrawablesWithIntrinsicBounds(action.getIconDrawable(), null, null, null);
    button.setChecked(action.isChecked());
    button.setEnabled(action.isEnabled());
    button.setText(action.getName());
    button.setOnClickListener(v -> doAction(action));
  }

  private void doAction(Action action) {
    try {
      action.getIntent().send();
    } catch (CanceledException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Create a new ViewHolder object to replace the old one.It only happens when not moving and
   * collapsed.
   */
  void replaceViewHolder() {
    LogUtil.enterBlock("NewBubble.replaceViewHolder");
    ViewHolder oldViewHolder = viewHolder;

    // Create a new ViewHolder and copy needed info.
    viewHolder = new ViewHolder(oldViewHolder.getRoot().getContext());
    viewHolder
        .getPrimaryButton()
        .setDisplayedChild(oldViewHolder.getPrimaryButton().getDisplayedChild());
    viewHolder.getPrimaryText().setText(oldViewHolder.getPrimaryText().getText());
    viewHolder.getPrimaryIcon().setX(isDrawingFromRight() ? 0 : primaryIconMoveDistance);
    viewHolder
        .getPrimaryIcon()
        .setTranslationX(isDrawingFromRight() ? -primaryIconMoveDistance : 0);

    update();

    // Add new view at its horizontal boundary
    ViewGroup root = viewHolder.getRoot();
    windowParams.x = leftBoundary;
    windowParams.gravity = Gravity.TOP | (isDrawingFromRight() ? Gravity.RIGHT : Gravity.LEFT);
    windowManager.addView(root, windowParams);

    // Remove the old view after delay
    root.getViewTreeObserver()
        .addOnPreDrawListener(
            new OnPreDrawListener() {
              @Override
              public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                // Wait a bit before removing the old view; make sure the new one has drawn over it.
                handler.postDelayed(
                    () -> windowManager.removeView(oldViewHolder.getRoot()),
                    WINDOW_REDRAW_DELAY_MILLIS);
                return true;
              }
            });
  }

  int getDrawerVisibility() {
    return viewHolder.getExpandedView().getVisibility();
  }

  private boolean isDrawingFromRight() {
    return (windowParams.gravity & Gravity.RIGHT) == Gravity.RIGHT;
  }

  private void setFocused(boolean focused) {
    if (focused) {
      windowParams.flags &= ~LayoutParams.FLAG_NOT_FOCUSABLE;
    } else {
      windowParams.flags |= LayoutParams.FLAG_NOT_FOCUSABLE;
    }
    windowManager.updateViewLayout(getRootView(), windowParams);
  }

  private void defaultAfterHidingAnimation() {
    exitAnimatorSet = null;
    viewHolder.getPrimaryButton().setVisibility(View.INVISIBLE);
    windowManager.removeView(viewHolder.getRoot());
    visibility = Visibility.HIDDEN;

    updatePrimaryIconAnimation();
  }

  private void logBasicOrCallImpression(DialerImpression.Type impressionType) {
    DialerCall call = CallList.getInstance().getActiveOrBackgroundCall();
    if (call != null) {
      Logger.get(context)
          .logCallImpression(impressionType, call.getUniqueCallId(), call.getTimeAddedMs());
    } else {
      Logger.get(context).logImpression(impressionType);
    }
  }

  @VisibleForTesting
  class ViewHolder {

    public static final int CHILD_INDEX_AVATAR_AND_ICON = 0;
    public static final int CHILD_INDEX_TEXT = 1;

    private final NewMoveHandler moveHandler;
    private final NewWindowRoot root;
    private final ViewAnimator primaryButton;
    private final ImageView primaryIcon;
    private final ImageView primaryAvatar;
    private final TextView primaryText;

    private final NewCheckableButton fullScreenButton;
    private final NewCheckableButton muteButton;
    private final NewCheckableButton audioRouteButton;
    private final NewCheckableButton endCallButton;
    private final View expandedView;

    public ViewHolder(Context context) {
      // Window root is not in the layout file so that the inflater has a view to inflate into
      this.root = new NewWindowRoot(context);
      LayoutInflater inflater = LayoutInflater.from(root.getContext());
      View contentView = inflater.inflate(R.layout.new_bubble_base, root, true);
      expandedView = contentView.findViewById(R.id.bubble_expanded_layout);
      primaryButton = contentView.findViewById(R.id.bubble_button_primary);
      primaryAvatar = contentView.findViewById(R.id.bubble_icon_avatar);
      primaryIcon = contentView.findViewById(R.id.bubble_icon_primary);
      primaryText = contentView.findViewById(R.id.bubble_text);

      fullScreenButton = contentView.findViewById(R.id.bubble_button_full_screen);
      muteButton = contentView.findViewById(R.id.bubble_button_mute);
      audioRouteButton = contentView.findViewById(R.id.bubble_button_audio_route);
      endCallButton = contentView.findViewById(R.id.bubble_button_end_call);

      root.setOnBackPressedListener(
          () -> {
            if (visibility == Visibility.SHOWING && expanded) {
              startCollapse(
                  CollapseEnd.NOTHING, true /* isUserAction */, true /* shouldRecoverYPosition */);
              return true;
            }
            return false;
          });
      root.setOnConfigurationChangedListener(
          (configuration) -> {
            // The values in the current MoveHandler may be stale, so replace it. Then ensure the
            // Window is in bounds
            moveHandler = new NewMoveHandler(primaryButton, NewBubble.this);
            moveHandler.snapToBounds();
          });
      root.setOnTouchListener(
          (v, event) -> {
            if (expanded && event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
              startCollapse(
                  CollapseEnd.NOTHING, true /* isUserAction */, true /* shouldRecoverYPosition */);
              return true;
            }
            return false;
          });
      moveHandler = new NewMoveHandler(primaryButton, NewBubble.this);
    }

    private void setChildClickable(boolean clickable) {
      fullScreenButton.setClickable(clickable);
      muteButton.setClickable(clickable);
      audioRouteButton.setClickable(clickable);
      endCallButton.setClickable(clickable);

      // For primaryButton
      moveHandler.setClickable(clickable);
    }

    public int getMoveUpDistance() {
      int deltaAllowed =
          expandedView.getHeight()
              - context
                      .getResources()
                      .getDimensionPixelOffset(R.dimen.bubble_button_padding_vertical)
                  * 2;
      return moveHandler.getMoveUpDistance(deltaAllowed);
    }

    public ViewGroup getRoot() {
      return root;
    }

    public ViewAnimator getPrimaryButton() {
      return primaryButton;
    }

    public ImageView getPrimaryIcon() {
      return primaryIcon;
    }

    public ImageView getPrimaryAvatar() {
      return primaryAvatar;
    }

    public TextView getPrimaryText() {
      return primaryText;
    }

    public View getExpandedView() {
      return expandedView;
    }

    public NewCheckableButton getFullScreenButton() {
      return fullScreenButton;
    }

    public NewCheckableButton getMuteButton() {
      return muteButton;
    }

    public NewCheckableButton getAudioRouteButton() {
      return audioRouteButton;
    }

    public NewCheckableButton getEndCallButton() {
      return endCallButton;
    }

    public void setDrawerVisibility(int visibility) {
      expandedView.setVisibility(visibility);
    }

    public boolean isMoving() {
      return moveHandler.isMoving();
    }

    public void undoGravityOverride() {
      moveHandler.undoGravityOverride();
    }
  }
}
