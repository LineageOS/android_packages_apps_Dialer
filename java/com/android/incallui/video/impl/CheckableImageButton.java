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

package com.android.incallui.video.impl;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.SoundEffectConstants;
import android.widget.Checkable;
import android.widget.ImageButton;

/** Image button that maintains a checked state. */
public class CheckableImageButton extends ImageButton implements Checkable {

  private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};

  /** Callback interface to notify when the button's checked state has changed */
  public interface OnCheckedChangeListener {

    void onCheckedChanged(CheckableImageButton button, boolean isChecked);
  }

  private boolean broadcasting;
  private boolean isChecked;
  private OnCheckedChangeListener onCheckedChangeListener;
  private CharSequence contentDescriptionChecked;
  private CharSequence contentDescriptionUnchecked;

  public CheckableImageButton(Context context) {
    this(context, null);
  }

  public CheckableImageButton(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public CheckableImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context, attrs);
  }

  private void init(Context context, AttributeSet attrs) {
    TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.CheckableImageButton);
    setChecked(typedArray.getBoolean(R.styleable.CheckableImageButton_android_checked, false));
    contentDescriptionChecked =
        typedArray.getText(R.styleable.CheckableImageButton_contentDescriptionChecked);
    contentDescriptionUnchecked =
        typedArray.getText(R.styleable.CheckableImageButton_contentDescriptionUnchecked);
    typedArray.recycle();

    updateContentDescription();
    setClickable(true);
    setFocusable(true);
  }

  @Override
  public void setChecked(boolean checked) {
    performSetChecked(checked);
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
    CharSequence contentDescription = updateContentDescription();
    announceForAccessibility(contentDescription);
    refreshDrawableState();
  }

  private CharSequence updateContentDescription() {
    CharSequence contentDescription =
        isChecked ? contentDescriptionChecked : contentDescriptionUnchecked;
    setContentDescription(contentDescription);
    return contentDescription;
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

  @Override
  public boolean isChecked() {
    return isChecked;
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

  private static class SavedState extends BaseSavedState {

    public final boolean isChecked;

    private SavedState(boolean isChecked, Parcelable superState) {
      super(superState);
      this.isChecked = isChecked;
    }

    protected SavedState(Parcel in) {
      super(in);
      isChecked = in.readByte() != 0;
    }

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
