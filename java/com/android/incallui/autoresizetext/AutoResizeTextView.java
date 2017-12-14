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

package com.android.incallui.autoresizetext;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.RectF;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.Nullable;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * A TextView that automatically scales its text to completely fill its allotted width.
 *
 * <p>Note: In some edge cases, the binary search algorithm to find the best fit may slightly
 * overshoot / undershoot its constraints. See a bug. No minimal repro case has been
 * found yet. A known workaround is the solution provided on StackOverflow:
 * http://stackoverflow.com/a/5535672
 */
public class AutoResizeTextView extends TextView {
  private static final int NO_LINE_LIMIT = -1;
  private static final float DEFAULT_MIN_TEXT_SIZE = 16.0f;
  private static final int DEFAULT_RESIZE_STEP_UNIT = TypedValue.COMPLEX_UNIT_PX;

  private final DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
  private final RectF availableSpaceRect = new RectF();
  private final SparseIntArray textSizesCache = new SparseIntArray();
  private final TextPaint textPaint = new TextPaint();
  private int resizeStepUnit = DEFAULT_RESIZE_STEP_UNIT;
  private float minTextSize = DEFAULT_MIN_TEXT_SIZE;
  private float maxTextSize;
  private int maxWidth;
  private int maxLines;
  private float lineSpacingMultiplier = 1.0f;
  private float lineSpacingExtra = 0.0f;

  public AutoResizeTextView(Context context) {
    super(context, null, 0);
    initialize(context, null, 0, 0);
  }

  public AutoResizeTextView(Context context, AttributeSet attrs) {
    super(context, attrs, 0);
    initialize(context, attrs, 0, 0);
  }

  public AutoResizeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize(context, attrs, defStyleAttr, 0);
  }

  public AutoResizeTextView(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize(context, attrs, defStyleAttr, defStyleRes);
  }

  private void initialize(
      Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    TypedArray typedArray = context.getTheme().obtainStyledAttributes(
        attrs, R.styleable.AutoResizeTextView, defStyleAttr, defStyleRes);
    readAttrs(typedArray);
    typedArray.recycle();
    textPaint.set(getPaint());
  }

  /** Overridden because getMaxLines is only defined in JB+. */
  @Override
  public final int getMaxLines() {
    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
      return super.getMaxLines();
    } else {
      return maxLines;
    }
  }

  /** Overridden because getMaxLines is only defined in JB+. */
  @Override
  public final void setMaxLines(int maxLines) {
    super.setMaxLines(maxLines);
    this.maxLines = maxLines;
  }

  /** Overridden because getLineSpacingMultiplier is only defined in JB+. */
  @Override
  public final float getLineSpacingMultiplier() {
    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
      return super.getLineSpacingMultiplier();
    } else {
      return lineSpacingMultiplier;
    }
  }

  /** Overridden because getLineSpacingExtra is only defined in JB+. */
  @Override
  public final float getLineSpacingExtra() {
    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
      return super.getLineSpacingExtra();
    } else {
      return lineSpacingExtra;
    }
  }

  /**
   * Overridden because getLineSpacingMultiplier and getLineSpacingExtra are only defined in JB+.
   */
  @Override
  public final void setLineSpacing(float add, float mult) {
    super.setLineSpacing(add, mult);
    lineSpacingMultiplier = mult;
    lineSpacingExtra = add;
  }

  /**
   * Although this overrides the setTextSize method from the TextView base class, it changes the
   * semantics a bit: Calling setTextSize now specifies the maximum text size to be used by this
   * view. If the text can't fit with that text size, the text size will be scaled down, up to the
   * minimum text size specified in {@link #setMinTextSize}.
   *
   * <p>Note that the final size unit will be truncated to the nearest integer value of the
   * specified unit.
   */
  @Override
  public final void setTextSize(int unit, float size) {
    float maxTextSize = TypedValue.applyDimension(unit, size, displayMetrics);
    if (this.maxTextSize != maxTextSize) {
      this.maxTextSize = maxTextSize;
      // TODO(tobyj): It's not actually necessary to clear the whole cache here. To optimize cache
      // deletion we'd have to delete all entries in the cache with a value equal or larger than
      // MIN(old_max_size, new_max_size) when changing maxTextSize; and all entries with a value
      // equal or smaller than MAX(old_min_size, new_min_size) when changing minTextSize.
      textSizesCache.clear();
      requestLayout();
    }
  }

  /**
   * Sets the lower text size limit and invalidate the view.
   *
   * <p>The parameters follow the same behavior as they do in {@link #setTextSize}.
   *
   * <p>Note that the final size unit will be truncated to the nearest integer value of the
   * specified unit.
   */
  public final void setMinTextSize(int unit, float size) {
    float minTextSize = TypedValue.applyDimension(unit, size, displayMetrics);
    if (this.minTextSize != minTextSize) {
      this.minTextSize = minTextSize;
      textSizesCache.clear();
      requestLayout();
    }
  }

  /**
   * Sets the unit to use as step units when computing the resized font size. This view's text
   * contents will always be rendered as a whole integer value in the unit specified here. For
   * example, if the unit is {@link TypedValue#COMPLEX_UNIT_SP}, then the text size may end up
   * being 13sp or 14sp, but never 13.5sp.
   *
   * <p>By default, the AutoResizeTextView uses the unit {@link TypedValue#COMPLEX_UNIT_PX}.
   *
   * @param unit the unit type to use; must be a known unit type from {@link TypedValue}.
   */
  public final void setResizeStepUnit(int unit) {
    if (resizeStepUnit != unit) {
      resizeStepUnit = unit;
      requestLayout();
    }
  }

  private void readAttrs(TypedArray typedArray) {
    resizeStepUnit = typedArray.getInt(
        R.styleable.AutoResizeTextView_autoResizeText_resizeStepUnit, DEFAULT_RESIZE_STEP_UNIT);
    minTextSize = (int) typedArray.getDimension(
        R.styleable.AutoResizeTextView_autoResizeText_minTextSize, DEFAULT_MIN_TEXT_SIZE);
    maxTextSize = (int) getTextSize();
  }

  private void adjustTextSize() {
    int maxWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
    int maxHeight = getMeasuredHeight() - getPaddingBottom() - getPaddingTop();

    if (maxWidth <= 0 || maxHeight <= 0) {
      return;
    }

    this.maxWidth = maxWidth;
    availableSpaceRect.right = maxWidth;
    availableSpaceRect.bottom = maxHeight;
    int minSizeInStepSizeUnits = (int) Math.ceil(convertToResizeStepUnits(minTextSize));
    int maxSizeInStepSizeUnits = (int) Math.floor(convertToResizeStepUnits(maxTextSize));
    float textSize = computeTextSize(
        minSizeInStepSizeUnits, maxSizeInStepSizeUnits, availableSpaceRect);
    super.setTextSize(resizeStepUnit, textSize);
  }

  private boolean suggestedSizeFitsInSpace(float suggestedSizeInPx, RectF availableSpace) {
    textPaint.setTextSize(suggestedSizeInPx);
    String text = getText().toString();
    int maxLines = getMaxLines();
    if (maxLines == 1) {
      // If single line, check the line's height and width.
      return textPaint.getFontSpacing() <= availableSpace.bottom
          && textPaint.measureText(text) <= availableSpace.right;
    } else {
      // If multiline, lay the text out, then check the number of lines, the layout's height,
      // and each line's width.
      StaticLayout layout = new StaticLayout(text,
          textPaint,
          maxWidth,
          Alignment.ALIGN_NORMAL,
          getLineSpacingMultiplier(),
          getLineSpacingExtra(),
          true);

      // Return false if we need more than maxLines. The text is obviously too big in this case.
      if (maxLines != NO_LINE_LIMIT && layout.getLineCount() > maxLines) {
        return false;
      }
      // Return false if the height of the layout is too big.
      return layout.getHeight() <= availableSpace.bottom;
    }
  }

  /**
   * Computes the final text size to use for this text view, factoring in any previously
   * cached computations.
   *
   * @param minSize the minimum text size to allow, in units of {@link #resizeStepUnit}
   * @param maxSize the maximum text size to allow, in units of {@link #resizeStepUnit}
   */
  private float computeTextSize(int minSize, int maxSize, RectF availableSpace) {
    CharSequence text = getText();
    if (text != null && textSizesCache.get(text.hashCode()) != 0) {
      return textSizesCache.get(text.hashCode());
    }
    int size = binarySearchSizes(minSize, maxSize, availableSpace);
    textSizesCache.put(text == null ? 0 : text.hashCode(), size);
    return size;
  }

  /**
   * Performs a binary search to find the largest font size that will still fit within the size
   * available to this view.
   * @param minSize the minimum text size to allow, in units of {@link #resizeStepUnit}
   * @param maxSize the maximum text size to allow, in units of {@link #resizeStepUnit}
   */
  private int binarySearchSizes(int minSize, int maxSize, RectF availableSpace) {
    int bestSize = minSize;
    int low = minSize + 1;
    int high = maxSize;
    int sizeToTry;
    while (low <= high) {
      sizeToTry = (low + high) / 2;
      float dimension = TypedValue.applyDimension(resizeStepUnit, sizeToTry, displayMetrics);
      if (suggestedSizeFitsInSpace(dimension, availableSpace)) {
        bestSize = low;
        low = sizeToTry + 1;
      } else {
        high = sizeToTry - 1;
        bestSize = high;
      }
    }
    return bestSize;
  }

  private float convertToResizeStepUnits(float dimension) {
    // To figure out the multiplier between a raw dimension and the resizeStepUnit, we invert the
    // conversion of 1 resizeStepUnit to a raw dimension.
    float multiplier = 1 / TypedValue.applyDimension(resizeStepUnit, 1, displayMetrics);
    return dimension * multiplier;
  }

  @Override
  protected final void onTextChanged(
      final CharSequence text, final int start, final int before, final int after) {
    super.onTextChanged(text, start, before, after);
    adjustTextSize();
  }

  @Override
  protected final void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    if (width != oldWidth || height != oldHeight) {
      textSizesCache.clear();
      adjustTextSize();
    }
  }

  @Override
  protected final void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    adjustTextSize();
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }
}
