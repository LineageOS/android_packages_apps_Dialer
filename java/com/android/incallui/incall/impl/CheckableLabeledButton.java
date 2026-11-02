/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.incallui.incall.impl;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.SoundEffectConstants;
import android.widget.Checkable;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.android.dialer.R;
import com.google.android.material.button.MaterialButton;

/** A button to show on the incall screen */
public class CheckableLabeledButton extends LinearLayout implements Checkable {

  private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};
  private static final float DISABLED_STATE_OPACITY = .3f;
  private boolean broadcasting;
  private boolean isChecked;
  private OnCheckedChangeListener onCheckedChangeListener;
  private MaterialButton iconView;
  @DrawableRes
  private int iconResource = 0;
  private TextView labelView;
  private int iconSize;

  public CheckableLabeledButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context, attrs);
  }

  public CheckableLabeledButton(Context context) {
    this(context, null);
  }

  private void init(Context context, AttributeSet attrs) {
    boolean vertical = getOrientation() == VERTICAL;
    TypedArray typedArray =
        context.obtainStyledAttributes(attrs, R.styleable.CheckableLabeledButton);
    Drawable icon = typedArray.getDrawable(R.styleable.CheckableLabeledButton_incall_icon);
    CharSequence labelText =
        typedArray.getString(R.styleable.CheckableLabeledButton_incall_labelText);
    boolean enabled = typedArray.getBoolean(R.styleable.CheckableLabeledButton_android_enabled, true);
    typedArray.recycle();

    iconSize = getResources().getDimensionPixelSize(
        vertical ? R.dimen.incall_labeled_button_size : R.dimen.incall_more_icon_size);

    iconView = new MaterialButton(context);
    LayoutParams iconParams = generateDefaultLayoutParams();
    iconParams.width = vertical ? LayoutParams.MATCH_PARENT : iconSize;
    iconParams.height = iconSize;
    iconView.setLayoutParams(iconParams);
    iconView.setInsetTop(0);
    iconView.setInsetBottom(0);
    iconView.setPadding(0, 0, 0, 0);
    iconView.setIcon(icon);
    iconView.setIconSize(getResources().getDimensionPixelSize(
        vertical ? R.dimen.incall_labeled_button_icon_size : R.dimen.incall_more_image_size));
    iconView.setIconPadding(0);
    iconView.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
    iconView.setIconTint(
        getResources().getColorStateList(R.color.incall_button_icon, context.getTheme()));
    iconView.setBackgroundTintList(
        new ColorStateList(
            new int[][] {CHECKED_STATE_SET, new int[] {}},
            new int[] {
              context.getColor(R.color.incall_button_background_color),
              context.getColor(
                  vertical ? R.color.incall_button_unchecked_color : R.color.incall_sheet_color)
            }));
    iconView.setCornerRadius(iconSize / 2);
    iconView.setClickable(false);
    iconView.setFocusable(false);
    iconView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    iconView.setDuplicateParentStateEnabled(true);
    addView(iconView);

    labelView = new TextView(context);
    LayoutParams labelParams = generateDefaultLayoutParams();
    labelParams.width = LayoutParams.WRAP_CONTENT;
    labelParams.height = LayoutParams.WRAP_CONTENT;
    labelView.setLayoutParams(labelParams);
    labelView.setTextAppearance(R.style.Dialer_Incall_TextAppearance_Label);
    if (vertical) {
      labelParams.topMargin =
          getResources().getDimensionPixelOffset(R.dimen.incall_button_label_margin);
    } else {
      labelParams.setMarginStart(
          getResources().getDimensionPixelOffset(R.dimen.incall_more_label_margin));
      labelView.setTextSize(
          TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.incall_more_label_size));
    }
    labelView.setText(labelText);
    labelView.setSingleLine();
    labelView.setMaxEms(9);
    labelView.setEllipsize(TruncateAt.END);
    labelView.setGravity(Gravity.CENTER);
    labelView.setDuplicateParentStateEnabled(true);
    addView(labelView);

    setFocusable(true);
    setClickable(true);
    setEnabled(enabled);
    setOutlineProvider(null);
  }

  @Override
  public void setVisibility(int visibility) {
    super.setVisibility(
        visibility == INVISIBLE && getOrientation() == HORIZONTAL ? GONE : visibility);
  }

  @Override
  public void refreshDrawableState() {
    super.refreshDrawableState();
    iconView.setAlpha(isEnabled() ? 1f : DISABLED_STATE_OPACITY);
    iconView.setCornerRadius(isChecked() ? iconSize / 3 : iconSize / 2);
    labelView.setAlpha(isEnabled() ? 1f : DISABLED_STATE_OPACITY);
  }

  public void setCheckedColor(@ColorInt int color) {
    iconView.setIconTint(
        new ColorStateList(
            new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
            new int[] {color, Color.WHITE}));
  }

  public Drawable getIconDrawable() {
    return iconView.getIcon();
  }

  public void setIconDrawable(@DrawableRes int drawableRes) {
    if (iconResource != drawableRes) {
      iconView.setIconResource(drawableRes);
      iconResource = drawableRes;
    }
  }

  public void setLabelText(@StringRes int stringRes) {
    labelView.setText(stringRes);
  }

  public void setLabelText(CharSequence label) {
    labelView.setText(label);
  }

  /** Shows or hides a little down arrow to indicate that the button will pop up a menu. */
  public void setShouldShowMoreIndicator(boolean shouldShow) {
    labelView.setCompoundDrawablesRelativeWithIntrinsicBounds(
        0, 0, shouldShow ? R.drawable.incall_ic_arrow_drop_down : 0, 0);
  }

  @Override
  public boolean isChecked() {
    return isChecked;
  }

  @Override
  public void setChecked(boolean checked) {
    performSetChecked(checked);
  }

  @Override
  public void toggle() {
    userRequestedSetChecked(!isChecked());
  }

  @Override
  public int[] onCreateDrawableState(int extraSpace) {
    final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
    if (isChecked()) {
      mergeDrawableStates(drawableState, CHECKED_STATE_SET);
    }
    return drawableState;
  }

  @Override
  protected void drawableStateChanged() {
    super.drawableStateChanged();
    invalidate();
  }

  public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
    this.onCheckedChangeListener = listener;
  }

  @Override
  public boolean performClick() {
    if (!isCheckable()) {
      return super.performClick();
    }

    toggle();
    final boolean handled = super.performClick();
    if (!handled) {
      // View only makes a sound effect if the onClickListener was
      // called, so we'll need to make one here instead.
      playSoundEffect(SoundEffectConstants.CLICK);
    }
    return handled;
  }

  private boolean isCheckable() {
    return onCheckedChangeListener != null;
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    SavedState savedState = (SavedState) state;
    super.onRestoreInstanceState(savedState.getSuperState());
    performSetChecked(savedState.isChecked);
    requestLayout();
  }

  @Override
  protected Parcelable onSaveInstanceState() {
    return new SavedState(isChecked(), super.onSaveInstanceState());
  }

  /**
   * Called when the state of the button should be updated, this should not be the result of user
   * interaction.
   *
   * @param checked {@code true} if the button should be in the checked state, {@code false}
   *     otherwise.
   */
  private void performSetChecked(boolean checked) {
    if (isChecked() == checked) {
      return;
    }
    isChecked = checked;
    refreshDrawableState();
  }

  /**
   * Called when the user interacts with a button. This should not result in the button updating
   * state, rather the request should be propagated to the associated listener.
   *
   * @param checked {@code true} if the button should be in the checked state, {@code false}
   *     otherwise.
   */
  private void userRequestedSetChecked(boolean checked) {
    if (isChecked() == checked) {
      return;
    }
    if (broadcasting) {
      return;
    }
    broadcasting = true;
    if (onCheckedChangeListener != null) {
      onCheckedChangeListener.onCheckedChanged(this, checked);
    }
    broadcasting = false;
  }

  /** Callback interface to notify when the button's checked state has changed */
  public interface OnCheckedChangeListener {

    void onCheckedChanged(CheckableLabeledButton checkableLabeledButton, boolean isChecked);
  }

  private static class SavedState extends BaseSavedState {

    public static final Creator<SavedState> CREATOR =
        new Creator<SavedState>() {
          @Override
          public SavedState createFromParcel(Parcel in) {
            return new SavedState(in);
          }

          @Override
          public SavedState[] newArray(int size) {
            return new SavedState[size];
          }
        };
    public final boolean isChecked;

    private SavedState(boolean isChecked, Parcelable superState) {
      super(superState);
      this.isChecked = isChecked;
    }

    protected SavedState(Parcel in) {
      super(in);
      isChecked = in.readByte() != 0;
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      super.writeToParcel(dest, flags);
      dest.writeByte((byte) (isChecked ? 1 : 0));
    }
  }
}
