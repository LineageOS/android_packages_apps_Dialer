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

package com.android.incallui.incall.impl;

import android.animation.AnimatorInflater;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.SoundEffectConstants;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A button to show on the incall screen */
public class CheckableLabeledButton extends LinearLayout implements Checkable {

  private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};
  private static final float DISABLED_STATE_OPACITY = .3f;
  private boolean broadcasting;
  private boolean isChecked;
  private OnCheckedChangeListener onCheckedChangeListener;
  private ImageView iconView;
  private TextView labelView;
  private Drawable background;
  private Drawable backgroundMore;

  public CheckableLabeledButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context, attrs);
  }

  public CheckableLabeledButton(Context context) {
    this(context, null);
  }

  private void init(Context context, AttributeSet attrs) {
    setOrientation(VERTICAL);
    setGravity(Gravity.CENTER_HORIZONTAL);
    Drawable icon;
    CharSequence labelText;
    boolean enabled;

    backgroundMore = getResources().getDrawable(R.drawable.incall_button_background_more, null);
    background = getResources().getDrawable(R.drawable.incall_button_background, null);

    TypedArray typedArray =
        context.obtainStyledAttributes(attrs, R.styleable.CheckableLabeledButton);
    icon = typedArray.getDrawable(R.styleable.CheckableLabeledButton_incall_icon);
    labelText = typedArray.getString(R.styleable.CheckableLabeledButton_incall_labelText);
    enabled = typedArray.getBoolean(R.styleable.CheckableLabeledButton_android_enabled, true);
    typedArray.recycle();

    int paddingSize = getResources().getDimensionPixelOffset(R.dimen.incall_button_padding);
    setPadding(paddingSize, paddingSize, paddingSize, paddingSize);

    int iconSize = getResources().getDimensionPixelSize(R.dimen.incall_labeled_button_size);

    iconView = new ImageView(context, null, android.R.style.Widget_Material_Button_Colored);
    LayoutParams iconParams = generateDefaultLayoutParams();
    iconParams.width = iconSize;
    iconParams.height = iconSize;
    iconView.setLayoutParams(iconParams);
    iconView.setScaleType(ScaleType.CENTER_INSIDE);
    iconView.setImageDrawable(icon);
    iconView.setImageTintMode(Mode.SRC_IN);
    iconView.setImageTintList(getResources().getColorStateList(R.color.incall_button_icon, null));
    iconView.setBackground(getResources().getDrawable(R.drawable.incall_button_background, null));
    iconView.setDuplicateParentStateEnabled(true);
    iconView.setElevation(getResources().getDimension(R.dimen.incall_button_elevation));
    iconView.setStateListAnimator(
        AnimatorInflater.loadStateListAnimator(context, R.animator.incall_button_elevation));
    addView(iconView);

    labelView = new TextView(context);
    LayoutParams labelParams = generateDefaultLayoutParams();
    labelParams.width = LayoutParams.WRAP_CONTENT;
    labelParams.height = LayoutParams.WRAP_CONTENT;
    labelParams.topMargin =
        context.getResources().getDimensionPixelOffset(R.dimen.incall_button_label_margin);
    labelView.setLayoutParams(labelParams);
    labelView.setTextAppearance(R.style.Dialer_Incall_TextAppearance_Label);
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
  public void refreshDrawableState() {
    super.refreshDrawableState();
    iconView.setAlpha(isEnabled() ? 1f : DISABLED_STATE_OPACITY);
    labelView.setAlpha(isEnabled() ? 1f : DISABLED_STATE_OPACITY);
  }

  public void setIconDrawable(@DrawableRes int drawableRes) {
    iconView.setImageResource(drawableRes);
  }

  public void setLabelText(@StringRes int stringRes) {
    labelView.setText(stringRes);
  }

  public void setLabelText(CharSequence label) {
    labelView.setText(label);
  }

  /** Shows or hides a little down arrow to indicate that the button will pop up a menu. */
  public void setShouldShowMoreIndicator(boolean shouldShow) {
    iconView.setBackground(shouldShow ? backgroundMore : background);
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
