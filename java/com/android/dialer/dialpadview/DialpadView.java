/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.dialer.dialpadview;

import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.RippleDrawable;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.TtsSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.accessibility.AccessibilityManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.dialer.animation.AnimUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.CompatUtils;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/** View that displays a twelve-key phone dialpad. */
public class DialpadView extends LinearLayout {

  private static final String TAG = DialpadView.class.getSimpleName();

  // Parameters for animation
  private static final double DELAY_MULTIPLIER = 0.66;
  private static final double DURATION_MULTIPLIER = 0.8;
  private static final int KEY_FRAME_DURATION = 33;

  // Resource IDs for buttons (0-9, *, and #)
  private static final int[] BUTTON_IDS =
      new int[] {
        R.id.zero,
        R.id.one,
        R.id.two,
        R.id.three,
        R.id.four,
        R.id.five,
        R.id.six,
        R.id.seven,
        R.id.eight,
        R.id.nine,
        R.id.star,
        R.id.pound
      };

  private final AttributeSet attributeSet;
  private final ColorStateList rippleColor;
  private final OnPreDrawListenerForKeyLayoutAdjust onPreDrawListenerForKeyLayoutAdjust;
  private final String[] primaryLettersMapping;
  private final String[] secondaryLettersMapping;
  private final boolean isRtl; // whether the dialpad is shown in a right-to-left locale
  private final int translateDistance;

  private EditText digits;
  private ImageButton delete;
  private View overflowMenuButton;
  private ViewGroup rateContainer;
  private TextView ildCountry;
  private TextView ildRate;
  private boolean isLandscapeMode;

  public DialpadView(Context context) {
    this(context, null);
  }

  public DialpadView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public DialpadView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    attributeSet = attrs;

    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Dialpad);
    rippleColor = a.getColorStateList(R.styleable.Dialpad_dialpad_key_button_touch_tint);
    a.recycle();

    translateDistance =
        getResources().getDimensionPixelSize(R.dimen.dialpad_key_button_translate_y);
    isRtl =
        TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL;

    primaryLettersMapping = DialpadCharMappings.getDefaultKeyToCharsMap();
    secondaryLettersMapping = DialpadCharMappings.getKeyToCharsMap(context);

    onPreDrawListenerForKeyLayoutAdjust = new OnPreDrawListenerForKeyLayoutAdjust();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    getViewTreeObserver().removeOnPreDrawListener(onPreDrawListenerForKeyLayoutAdjust);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    // The orientation obtained at this point should be used as the only truth for DialpadView as we
    // observed inconsistency between configurations obtained here and in
    // OnPreDrawListenerForKeyLayoutAdjust under rare circumstances.
    isLandscapeMode =
        (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

    setupKeypad();
    digits = (EditText) findViewById(R.id.digits);
    delete = (ImageButton) findViewById(R.id.deleteButton);
    overflowMenuButton = findViewById(R.id.dialpad_overflow);
    rateContainer = (ViewGroup) findViewById(R.id.rate_container);
    ildCountry = (TextView) rateContainer.findViewById(R.id.ild_country);
    ildRate = (TextView) rateContainer.findViewById(R.id.ild_rate);

    AccessibilityManager accessibilityManager =
        (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
    if (accessibilityManager.isEnabled()) {
      // The text view must be selected to send accessibility events.
      digits.setSelected(true);
    }

    // As OnPreDrawListenerForKeyLayoutAdjust makes changes to LayoutParams, it is added here to
    // ensure it can only be triggered after the layout is inflated.
    getViewTreeObserver().removeOnPreDrawListener(onPreDrawListenerForKeyLayoutAdjust);
    getViewTreeObserver().addOnPreDrawListener(onPreDrawListenerForKeyLayoutAdjust);
  }

  private void setupKeypad() {
    final Resources resources = getContext().getResources();
    final NumberFormat numberFormat = getNumberFormat();

    for (int i = 0; i < BUTTON_IDS.length; i++) {
      DialpadKeyButton dialpadKey = (DialpadKeyButton) findViewById(BUTTON_IDS[i]);
      TextView numberView = (TextView) dialpadKey.findViewById(R.id.dialpad_key_number);

      final String numberString;
      final CharSequence numberContentDescription;
      if (BUTTON_IDS[i] == R.id.pound) {
        numberString = resources.getString(R.string.dialpad_pound_number);
        numberContentDescription = numberString;
      } else if (BUTTON_IDS[i] == R.id.star) {
        numberString = resources.getString(R.string.dialpad_star_number);
        numberContentDescription = numberString;
      } else {
        numberString = numberFormat.format(i);
        // The content description is used for Talkback key presses. The number is
        // separated by a "," to introduce a slight delay. Convert letters into a verbatim
        // span so that they are read as letters instead of as one word.
        String letters = primaryLettersMapping[i];
        Spannable spannable =
            Spannable.Factory.getInstance().newSpannable(numberString + "," + letters);
        spannable.setSpan(
            (new TtsSpan.VerbatimBuilder(letters)).build(),
            numberString.length() + 1,
            numberString.length() + 1 + letters.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        numberContentDescription = spannable;
      }

      final RippleDrawable rippleBackground =
          (RippleDrawable) getContext().getDrawable(R.drawable.btn_dialpad_key);
      if (rippleColor != null) {
        rippleBackground.setColor(rippleColor);
      }

      numberView.setText(numberString);
      numberView.setElegantTextHeight(false);
      dialpadKey.setContentDescription(numberContentDescription);
      dialpadKey.setBackground(rippleBackground);

      TextView primaryLettersView = (TextView) dialpadKey.findViewById(R.id.dialpad_key_letters);
      TextView secondaryLettersView =
          (TextView) dialpadKey.findViewById(R.id.dialpad_key_secondary_letters);
      if (primaryLettersView != null) {
        primaryLettersView.setText(primaryLettersMapping[i]);
      }
      if (primaryLettersView != null && secondaryLettersView != null) {
        if (secondaryLettersMapping == null) {
          secondaryLettersView.setVisibility(View.GONE);
        } else {
          secondaryLettersView.setVisibility(View.VISIBLE);
          secondaryLettersView.setText(secondaryLettersMapping[i]);

          // Adjust the font size of the letters if a secondary alphabet is available.
          TypedArray a =
              getContext()
                  .getTheme()
                  .obtainStyledAttributes(attributeSet, R.styleable.Dialpad, 0, 0);
          int textSize =
              a.getDimensionPixelSize(
                  R.styleable.Dialpad_dialpad_key_letters_size_for_dual_alphabets, 0);
          a.recycle();
          primaryLettersView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
          secondaryLettersView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        }
      }
    }

    final DialpadKeyButton one = (DialpadKeyButton) findViewById(R.id.one);
    one.setLongHoverContentDescription(resources.getText(R.string.description_voicemail_button));

    final DialpadKeyButton zero = (DialpadKeyButton) findViewById(R.id.zero);
    zero.setLongHoverContentDescription(resources.getText(R.string.description_image_button_plus));
  }

  private NumberFormat getNumberFormat() {
    Locale locale = CompatUtils.getLocale(getContext());

    // Return the Persian number format if the current language is Persian.
    return "fas".equals(locale.getISO3Language())
        ? DecimalFormat.getInstance(locale)
        : DecimalFormat.getInstance(Locale.ENGLISH);
  }

  /**
   * Configure whether or not the digits above the dialpad can be edited.
   *
   * <p>If we allow editing digits, the backspace button will be shown.
   */
  public void setCanDigitsBeEdited(boolean canBeEdited) {
    View deleteButton = findViewById(R.id.deleteButton);
    deleteButton.setVisibility(canBeEdited ? View.VISIBLE : View.INVISIBLE);
    View overflowMenuButton = findViewById(R.id.dialpad_overflow);
    overflowMenuButton.setVisibility(canBeEdited ? View.VISIBLE : View.GONE);

    EditText digits = (EditText) findViewById(R.id.digits);
    digits.setClickable(canBeEdited);
    digits.setLongClickable(canBeEdited);
    digits.setFocusableInTouchMode(canBeEdited);
    digits.setCursorVisible(false);
  }

  public void setCallRateInformation(String countryName, String displayRate) {
    if (TextUtils.isEmpty(countryName) && TextUtils.isEmpty(displayRate)) {
      rateContainer.setVisibility(View.GONE);
      return;
    }
    rateContainer.setVisibility(View.VISIBLE);
    ildCountry.setText(countryName);
    ildRate.setText(displayRate);
  }

  /**
   * Always returns true for onHoverEvent callbacks, to fix problems with accessibility due to the
   * dialpad overlaying other fragments.
   */
  @Override
  public boolean onHoverEvent(MotionEvent event) {
    return true;
  }

  public void animateShow() {
    // This is a hack; without this, the setTranslationY is delayed in being applied, and the
    // numbers appear at their original position (0) momentarily before animating.
    final AnimatorListenerAdapter showListener = new AnimatorListenerAdapter() {};

    for (int i = 0; i < BUTTON_IDS.length; i++) {
      int delay = (int) (getKeyButtonAnimationDelay(BUTTON_IDS[i]) * DELAY_MULTIPLIER);
      int duration = (int) (getKeyButtonAnimationDuration(BUTTON_IDS[i]) * DURATION_MULTIPLIER);
      final DialpadKeyButton dialpadKey = (DialpadKeyButton) findViewById(BUTTON_IDS[i]);

      ViewPropertyAnimator animator = dialpadKey.animate();
      if (isLandscapeMode) {
        // Landscape orientation requires translation along the X axis.
        // For RTL locales, ensure we translate negative on the X axis.
        dialpadKey.setTranslationX((isRtl ? -1 : 1) * translateDistance);
        animator.translationX(0);
      } else {
        // Portrait orientation requires translation along the Y axis.
        dialpadKey.setTranslationY(translateDistance);
        animator.translationY(0);
      }
      animator
          .setInterpolator(AnimUtils.EASE_OUT_EASE_IN)
          .setStartDelay(delay)
          .setDuration(duration)
          .setListener(showListener)
          .start();
    }
  }

  public EditText getDigits() {
    return digits;
  }

  public ImageButton getDeleteButton() {
    return delete;
  }

  public View getOverflowMenuButton() {
    return overflowMenuButton;
  }

  /**
   * Get the animation delay for the buttons, taking into account whether the dialpad is in
   * landscape left-to-right, landscape right-to-left, or portrait.
   *
   * @param buttonId The button ID.
   * @return The animation delay.
   */
  private int getKeyButtonAnimationDelay(int buttonId) {
    if (isLandscapeMode) {
      if (isRtl) {
        if (buttonId == R.id.three) {
          return KEY_FRAME_DURATION * 1;
        } else if (buttonId == R.id.six) {
          return KEY_FRAME_DURATION * 2;
        } else if (buttonId == R.id.nine) {
          return KEY_FRAME_DURATION * 3;
        } else if (buttonId == R.id.pound) {
          return KEY_FRAME_DURATION * 4;
        } else if (buttonId == R.id.two) {
          return KEY_FRAME_DURATION * 5;
        } else if (buttonId == R.id.five) {
          return KEY_FRAME_DURATION * 6;
        } else if (buttonId == R.id.eight) {
          return KEY_FRAME_DURATION * 7;
        } else if (buttonId == R.id.zero) {
          return KEY_FRAME_DURATION * 8;
        } else if (buttonId == R.id.one) {
          return KEY_FRAME_DURATION * 9;
        } else if (buttonId == R.id.four) {
          return KEY_FRAME_DURATION * 10;
        } else if (buttonId == R.id.seven || buttonId == R.id.star) {
          return KEY_FRAME_DURATION * 11;
        }
      } else {
        if (buttonId == R.id.one) {
          return KEY_FRAME_DURATION * 1;
        } else if (buttonId == R.id.four) {
          return KEY_FRAME_DURATION * 2;
        } else if (buttonId == R.id.seven) {
          return KEY_FRAME_DURATION * 3;
        } else if (buttonId == R.id.star) {
          return KEY_FRAME_DURATION * 4;
        } else if (buttonId == R.id.two) {
          return KEY_FRAME_DURATION * 5;
        } else if (buttonId == R.id.five) {
          return KEY_FRAME_DURATION * 6;
        } else if (buttonId == R.id.eight) {
          return KEY_FRAME_DURATION * 7;
        } else if (buttonId == R.id.zero) {
          return KEY_FRAME_DURATION * 8;
        } else if (buttonId == R.id.three) {
          return KEY_FRAME_DURATION * 9;
        } else if (buttonId == R.id.six) {
          return KEY_FRAME_DURATION * 10;
        } else if (buttonId == R.id.nine || buttonId == R.id.pound) {
          return KEY_FRAME_DURATION * 11;
        }
      }
    } else {
      if (buttonId == R.id.one) {
        return KEY_FRAME_DURATION * 1;
      } else if (buttonId == R.id.two) {
        return KEY_FRAME_DURATION * 2;
      } else if (buttonId == R.id.three) {
        return KEY_FRAME_DURATION * 3;
      } else if (buttonId == R.id.four) {
        return KEY_FRAME_DURATION * 4;
      } else if (buttonId == R.id.five) {
        return KEY_FRAME_DURATION * 5;
      } else if (buttonId == R.id.six) {
        return KEY_FRAME_DURATION * 6;
      } else if (buttonId == R.id.seven) {
        return KEY_FRAME_DURATION * 7;
      } else if (buttonId == R.id.eight) {
        return KEY_FRAME_DURATION * 8;
      } else if (buttonId == R.id.nine) {
        return KEY_FRAME_DURATION * 9;
      } else if (buttonId == R.id.star) {
        return KEY_FRAME_DURATION * 10;
      } else if (buttonId == R.id.zero || buttonId == R.id.pound) {
        return KEY_FRAME_DURATION * 11;
      }
    }

    LogUtil.e(TAG, "Attempted to get animation delay for invalid key button id.");
    return 0;
  }

  /**
   * Get the button animation duration, taking into account whether the dialpad is in landscape
   * left-to-right, landscape right-to-left, or portrait.
   *
   * @param buttonId The button ID.
   * @return The animation duration.
   */
  private int getKeyButtonAnimationDuration(int buttonId) {
    if (isLandscapeMode) {
      if (isRtl) {
        if (buttonId == R.id.one
            || buttonId == R.id.four
            || buttonId == R.id.seven
            || buttonId == R.id.star) {
          return KEY_FRAME_DURATION * 8;
        } else if (buttonId == R.id.two
            || buttonId == R.id.five
            || buttonId == R.id.eight
            || buttonId == R.id.zero) {
          return KEY_FRAME_DURATION * 9;
        } else if (buttonId == R.id.three
            || buttonId == R.id.six
            || buttonId == R.id.nine
            || buttonId == R.id.pound) {
          return KEY_FRAME_DURATION * 10;
        }
      } else {
        if (buttonId == R.id.one
            || buttonId == R.id.four
            || buttonId == R.id.seven
            || buttonId == R.id.star) {
          return KEY_FRAME_DURATION * 10;
        } else if (buttonId == R.id.two
            || buttonId == R.id.five
            || buttonId == R.id.eight
            || buttonId == R.id.zero) {
          return KEY_FRAME_DURATION * 9;
        } else if (buttonId == R.id.three
            || buttonId == R.id.six
            || buttonId == R.id.nine
            || buttonId == R.id.pound) {
          return KEY_FRAME_DURATION * 8;
        }
      }
    } else {
      if (buttonId == R.id.one
          || buttonId == R.id.two
          || buttonId == R.id.three
          || buttonId == R.id.four
          || buttonId == R.id.five
          || buttonId == R.id.six) {
        return KEY_FRAME_DURATION * 10;
      } else if (buttonId == R.id.seven || buttonId == R.id.eight || buttonId == R.id.nine) {
        return KEY_FRAME_DURATION * 9;
      } else if (buttonId == R.id.star || buttonId == R.id.zero || buttonId == R.id.pound) {
        return KEY_FRAME_DURATION * 8;
      }
    }

    LogUtil.e(TAG, "Attempted to get animation duration for invalid key button id.");
    return 0;
  }

  /**
   * An {@link OnPreDrawListener} that adjusts the height/width of each key layout so that they can
   * be properly aligned.
   *
   * <p>When the device is in portrait mode, the layout height for key "1" can be different from
   * those of other <b>digit</b> keys due to the voicemail icon. Adjustments are needed to ensure
   * the layouts for all <b>digit</b> keys are of the same height. Key "*" and key "#" are excluded
   * because their styles are different from other keys'.
   *
   * <p>When the device is in landscape mode, keys can have different layout widths due to the
   * icon/characters associated with them. Adjustments are needed to ensure the layouts for all keys
   * are of the same width.
   *
   * <p>Note that adjustments can only be made after the layouts are measured, which is why the
   * logic lives in an {@link OnPreDrawListener} that is invoked when the view tree is about to be
   * drawn.
   */
  private class OnPreDrawListenerForKeyLayoutAdjust implements OnPreDrawListener {

    /**
     * This method is invoked when the view tree is about to be drawn. At this point, all views in
     * the tree have been measured and given a frame.
     *
     * <p>If the keys have been adjusted, we instruct the current drawing pass to proceed by
     * returning true. Otherwise, adjustments will be made and the current drawing pass will be
     * cancelled by returning false.
     *
     * <p>It is imperative to schedule another layout pass of the view tree after adjustments are
     * made so that {@link #onPreDraw()} can be invoked again to check the layouts and proceed with
     * the drawing pass.
     */
    @Override
    public boolean onPreDraw() {
      if (!shouldAdjustKeySizes()) {
        // Return true to proceed with the current drawing pass.
        // Note that we must NOT remove this listener here. The fact that we don't need to adjust
        // keys at the moment doesn't mean they won't need adjustments in the future. For example,
        // when DialpadFragment is hidden, all keys are of the same size (0) and nothing needs to be
        // done. Removing the listener will cost us the ability to adjust them when they reappear.
        // It is only safe to remove the listener after adjusting keys for the first time. See the
        // comment below for more details.
        return true;
      }

      adjustKeySizes();

      // After all keys are adjusted for the first time, no more adjustments will be needed during
      // the rest of DialpadView's lifecycle. It is therefore safe to remove this listener.
      // Another important reason for removing the listener is that it can be triggered AFTER a
      // device orientation change but BEFORE DialpadView's onDetachedFromWindow() and
      // onFinishInflate() are called, i.e., the listener will attempt to adjust the layout before
      // it is inflated, which results in a crash.
      getViewTreeObserver().removeOnPreDrawListener(this);

      return false; // Return false to cancel the current drawing pass.
    }

    private boolean shouldAdjustKeySizes() {
      return isLandscapeMode ? shouldAdjustKeyWidths() : shouldAdjustDigitKeyHeights();
    }

    /**
     * Return true if not all key layouts have the same width. This method must be called when the
     * device is in landscape mode.
     */
    private boolean shouldAdjustKeyWidths() {
      Assert.checkState(isLandscapeMode);

      DialpadKeyButton dialpadKeyButton = (DialpadKeyButton) findViewById(BUTTON_IDS[0]);
      LinearLayout keyLayout =
          (LinearLayout) dialpadKeyButton.findViewById(R.id.dialpad_key_layout);
      final int width = keyLayout.getWidth();

      for (int i = 1; i < BUTTON_IDS.length; i++) {
        dialpadKeyButton = (DialpadKeyButton) findViewById(BUTTON_IDS[i]);
        keyLayout = (LinearLayout) dialpadKeyButton.findViewById(R.id.dialpad_key_layout);
        if (width != keyLayout.getWidth()) {
          return true;
        }
      }

      return false;
    }

    /**
     * Return true if not all <b>digit</b> key layouts have the same height. This method must be
     * called when the device is in portrait mode.
     */
    private boolean shouldAdjustDigitKeyHeights() {
      Assert.checkState(!isLandscapeMode);

      DialpadKeyButton dialpadKey = (DialpadKeyButton) findViewById(BUTTON_IDS[0]);
      LinearLayout keyLayout = (LinearLayout) dialpadKey.findViewById(R.id.dialpad_key_layout);
      final int height = keyLayout.getHeight();

      // BUTTON_IDS[i] is the resource ID for button i when 0 <= i && i <= 9.
      // For example, BUTTON_IDS[3] is the resource ID for button "3" on the dialpad.
      for (int i = 1; i <= 9; i++) {
        dialpadKey = (DialpadKeyButton) findViewById(BUTTON_IDS[i]);
        keyLayout = (LinearLayout) dialpadKey.findViewById(R.id.dialpad_key_layout);
        if (height != keyLayout.getHeight()) {
          return true;
        }
      }

      return false;
    }

    private void adjustKeySizes() {
      if (isLandscapeMode) {
        adjustKeyWidths();
      } else {
        adjustDigitKeyHeights();
      }
    }

    /**
     * Make the heights of all <b>digit</b> keys the same.
     *
     * <p>When the device is in portrait mode, we first find the maximum height among digit key
     * layouts. Then for each key, we adjust the height of the layout containing letters/the
     * voicemail icon to ensure the height of each digit key is the same.
     *
     * <p>A layout pass will be scheduled in this method by {@link
     * LinearLayout#setLayoutParams(ViewGroup.LayoutParams)}.
     */
    private void adjustDigitKeyHeights() {
      Assert.checkState(!isLandscapeMode);

      int maxHeight = 0;

      // BUTTON_IDS[i] is the resource ID for button i when 0 <= i && i <= 9.
      // For example, BUTTON_IDS[3] is the resource ID for button "3" on the dialpad.
      for (int i = 0; i <= 9; i++) {
        DialpadKeyButton dialpadKey = (DialpadKeyButton) findViewById(BUTTON_IDS[i]);
        LinearLayout keyLayout = (LinearLayout) dialpadKey.findViewById(R.id.dialpad_key_layout);
        maxHeight = Math.max(maxHeight, keyLayout.getHeight());
      }

      for (int i = 0; i <= 9; i++) {
        DialpadKeyButton dialpadKey = (DialpadKeyButton) findViewById(BUTTON_IDS[i]);
        LinearLayout keyLayout = (LinearLayout) dialpadKey.findViewById(R.id.dialpad_key_layout);

        DialpadTextView numberView =
            (DialpadTextView) keyLayout.findViewById(R.id.dialpad_key_number);
        MarginLayoutParams numberViewLayoutParams =
            (MarginLayoutParams) numberView.getLayoutParams();

        LinearLayout iconOrLettersLayout =
            (LinearLayout) keyLayout.findViewById(R.id.dialpad_key_icon_or_letters_layout);
        iconOrLettersLayout.setLayoutParams(
            new LayoutParams(
                LayoutParams.WRAP_CONTENT /* width */,
                maxHeight
                    - numberView.getHeight()
                    - numberViewLayoutParams.topMargin
                    - numberViewLayoutParams.bottomMargin /* height */));
      }
    }

    /**
     * Make the widths of all keys the same.
     *
     * <p>When the device is in landscape mode, we first find the maximum width among key layouts.
     * Then we adjust the width of each layout's horizontal placeholder so that each key has the
     * same width.
     *
     * <p>A layout pass will be scheduled in this method by {@link
     * View#setLayoutParams(ViewGroup.LayoutParams)}.
     */
    private void adjustKeyWidths() {
      Assert.checkState(isLandscapeMode);

      int maxWidth = 0;
      for (int buttonId : BUTTON_IDS) {
        DialpadKeyButton dialpadKey = (DialpadKeyButton) findViewById(buttonId);
        LinearLayout keyLayout = (LinearLayout) dialpadKey.findViewById(R.id.dialpad_key_layout);
        maxWidth = Math.max(maxWidth, keyLayout.getWidth());
      }

      for (int buttonId : BUTTON_IDS) {
        DialpadKeyButton dialpadKey = (DialpadKeyButton) findViewById(buttonId);
        LinearLayout keyLayout = (LinearLayout) dialpadKey.findViewById(R.id.dialpad_key_layout);
        View horizontalPlaceholder =
            keyLayout.findViewById(R.id.dialpad_key_horizontal_placeholder);
        horizontalPlaceholder.setLayoutParams(
            new LayoutParams(
                maxWidth - keyLayout.getWidth() /* width */,
                LayoutParams.MATCH_PARENT /* height */));
      }
    }
  }
}
