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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.TtsSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
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

  private final AttributeSet mAttributeSet;
  private final ColorStateList mRippleColor;
  private final String[] mPrimaryLettersMapping;
  private final String[] mSecondaryLettersMapping;
  private final boolean mIsLandscape; // whether the device is in landscape mode
  private final boolean mIsRtl; // whether the dialpad is shown in a right-to-left locale
  private final int mTranslateDistance;

  private EditText mDigits;
  private ImageButton mDelete;
  private View mOverflowMenuButton;
  private ViewGroup mRateContainer;
  private TextView mIldCountry;
  private TextView mIldRate;
  private boolean mCanDigitsBeEdited;

  public DialpadView(Context context) {
    this(context, null);
  }

  public DialpadView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public DialpadView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    mAttributeSet = attrs;

    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Dialpad);
    mRippleColor = a.getColorStateList(R.styleable.Dialpad_dialpad_key_button_touch_tint);
    a.recycle();

    mTranslateDistance =
        getResources().getDimensionPixelSize(R.dimen.dialpad_key_button_translate_y);

    mIsLandscape =
        getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    mIsRtl =
        TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL;

    mPrimaryLettersMapping = DialpadAlphabets.getDefaultAlphabet();
    mSecondaryLettersMapping =
        DialpadAlphabets.getAlphabetForLanguage(CompatUtils.getLocale(context).getISO3Language());
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    setupKeypad();
    mDigits = (EditText) findViewById(R.id.digits);
    mDelete = (ImageButton) findViewById(R.id.deleteButton);
    mOverflowMenuButton = findViewById(R.id.dialpad_overflow);
    mRateContainer = (ViewGroup) findViewById(R.id.rate_container);
    mIldCountry = (TextView) mRateContainer.findViewById(R.id.ild_country);
    mIldRate = (TextView) mRateContainer.findViewById(R.id.ild_rate);

    AccessibilityManager accessibilityManager =
        (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
    if (accessibilityManager.isEnabled()) {
      // The text view must be selected to send accessibility events.
      mDigits.setSelected(true);
    }
  }

  private void setupKeypad() {
    final Resources resources = getContext().getResources();

    final Locale currentLocale = resources.getConfiguration().locale;
    final NumberFormat nf;
    // We translate dialpad numbers only for "fa" and not any other locale
    // ("ar" anybody ?).
    if ("fa".equals(currentLocale.getLanguage())) {
      nf = DecimalFormat.getInstance(CompatUtils.getLocale(getContext()));
    } else {
      nf = DecimalFormat.getInstance(Locale.ENGLISH);
    }

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
        numberString = nf.format(i);
        // The content description is used for Talkback key presses. The number is
        // separated by a "," to introduce a slight delay. Convert letters into a verbatim
        // span so that they are read as letters instead of as one word.
        String letters = mPrimaryLettersMapping[i];
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
          (RippleDrawable) getDrawableCompat(getContext(), R.drawable.btn_dialpad_key);
      if (mRippleColor != null) {
        rippleBackground.setColor(mRippleColor);
      }

      numberView.setText(numberString);
      numberView.setElegantTextHeight(false);
      dialpadKey.setContentDescription(numberContentDescription);
      dialpadKey.setBackground(rippleBackground);

      TextView primaryLettersView = (TextView) dialpadKey.findViewById(R.id.dialpad_key_letters);
      TextView secondaryLettersView =
          (TextView) dialpadKey.findViewById(R.id.dialpad_key_secondary_letters);
      if (primaryLettersView != null) {
        primaryLettersView.setText(mPrimaryLettersMapping[i]);
      }
      if (primaryLettersView != null && secondaryLettersView != null) {
        if (mSecondaryLettersMapping == null) {
          secondaryLettersView.setVisibility(View.GONE);
        } else {
          secondaryLettersView.setVisibility(View.VISIBLE);
          secondaryLettersView.setText(mSecondaryLettersMapping[i]);

          // Adjust the font size of the letters if a secondary alphabet is available.
          TypedArray a =
              getContext()
                  .getTheme()
                  .obtainStyledAttributes(mAttributeSet, R.styleable.Dialpad, 0, 0);
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

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);

    if (changed) {
      if (mIsLandscape) {
        adjustKeyWidths();
      } else {
        adjustDigitKeyHeights();
      }
    }
  }

  /**
   * Make the heights of all digit keys the same.
   *
   * <p>When the device is in portrait mode, we first find the maximum height among digit key
   * layouts. Then for each key, we adjust the height of the layout containing letters/the voice
   * mail icon to ensure the height of each digit key is the same.
   *
   * <p>This method should be called after the sizes of related layouts have been calculated by the
   * framework.
   */
  private void adjustDigitKeyHeights() {
    Assert.checkState(!mIsLandscape);

    int maxHeight = 0;
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
      MarginLayoutParams numberViewLayoutParams = (MarginLayoutParams) numberView.getLayoutParams();

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
   * Adjust key widths to align keys in each column.
   *
   * <p>When the device is in landscape mode, we first find the maximum among a pre-defined width
   * and the width of each key layout. Then we adjust the width of each layout's horizontal
   * placeholder to align keys in each column. This is to accommodate the scenario where not all
   * letters associated with a key can be displayed in one line due to large font size.
   *
   * <p>This method should be called after the sizes of related layouts have been calculated by the
   * framework.
   */
  private void adjustKeyWidths() {
    Assert.checkState(mIsLandscape);

    // A pre-defined minimum width for the letters shown beside a key.
    final int minimumKeyLettersWidth =
        getContext().getResources().getDimensionPixelSize(R.dimen.dialpad_key_text_width);

    // The maximum width of the key layouts. A key layout includes both the number and the letters.
    int maxWidth = 0;

    for (int buttonId : BUTTON_IDS) {
      DialpadKeyButton dialpadKey = (DialpadKeyButton) findViewById(buttonId);
      LinearLayout keyLayout = (LinearLayout) dialpadKey.findViewById(R.id.dialpad_key_layout);
      TextView keyLettersView = (TextView) keyLayout.findViewById(R.id.dialpad_key_letters);
      if (keyLettersView != null && keyLettersView.getWidth() < minimumKeyLettersWidth) {
        // If the width of the letters is less than the pre-defined minimum, use the pre-defined
        // minimum to obtain the maximum width.
        maxWidth =
            Math.max(
                maxWidth,
                keyLayout.getWidth() - keyLettersView.getWidth() + minimumKeyLettersWidth);
      } else {
        maxWidth = Math.max(maxWidth, keyLayout.getWidth());
      }
    }

    for (int buttonId : BUTTON_IDS) {
      DialpadKeyButton dialpadKey = (DialpadKeyButton) findViewById(buttonId);
      LinearLayout keyLayout = (LinearLayout) dialpadKey.findViewById(R.id.dialpad_key_layout);
      View horizontalPlaceholder = keyLayout.findViewById(R.id.dialpad_key_horizontal_placeholder);
      horizontalPlaceholder.setLayoutParams(
          new LayoutParams(
              maxWidth - keyLayout.getWidth() /* width */, LayoutParams.MATCH_PARENT /* height */));
    }
  }

  private Drawable getDrawableCompat(Context context, int id) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return context.getDrawable(id);
    } else {
      return context.getResources().getDrawable(id);
    }
  }

  public void setShowVoicemailButton(boolean show) {
    View view = findViewById(R.id.dialpad_key_voicemail);
    if (view != null) {
      view.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }
  }

  /**
   * Whether or not the digits above the dialer can be edited.
   *
   * @param canBeEdited If true, the backspace button will be shown and the digits EditText will be
   *     configured to allow text manipulation.
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

    mCanDigitsBeEdited = canBeEdited;
  }

  public void setCallRateInformation(String countryName, String displayRate) {
    if (TextUtils.isEmpty(countryName) && TextUtils.isEmpty(displayRate)) {
      mRateContainer.setVisibility(View.GONE);
      return;
    }
    mRateContainer.setVisibility(View.VISIBLE);
    mIldCountry.setText(countryName);
    mIldRate.setText(displayRate);
  }

  public boolean canDigitsBeEdited() {
    return mCanDigitsBeEdited;
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
      if (mIsLandscape) {
        // Landscape orientation requires translation along the X axis.
        // For RTL locales, ensure we translate negative on the X axis.
        dialpadKey.setTranslationX((mIsRtl ? -1 : 1) * mTranslateDistance);
        animator.translationX(0);
      } else {
        // Portrait orientation requires translation along the Y axis.
        dialpadKey.setTranslationY(mTranslateDistance);
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
    return mDigits;
  }

  public ImageButton getDeleteButton() {
    return mDelete;
  }

  public View getOverflowMenuButton() {
    return mOverflowMenuButton;
  }

  /**
   * Get the animation delay for the buttons, taking into account whether the dialpad is in
   * landscape left-to-right, landscape right-to-left, or portrait.
   *
   * @param buttonId The button ID.
   * @return The animation delay.
   */
  private int getKeyButtonAnimationDelay(int buttonId) {
    if (mIsLandscape) {
      if (mIsRtl) {
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
    if (mIsLandscape) {
      if (mIsRtl) {
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
}
