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

package com.android.dialer.widget;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.View;
import android.widget.ImageButton;

import com.android.contacts.common.util.ViewUtil;
import com.android.dialer.R;
import com.android.dialer.list.ListsFragment;

/**
 * Controls the movement and appearance of the FAB.
 */
public class FloatingActionButtonController {
    private static final String KEY_IS_DIALPAD_VISIBLE = "key_is_dialpad_visible";
    private static final String KEY_CURRENT_TAB_POSITION = "key_current_tab_position";
    private static final int ANIMATION_DURATION = 250;

    private int mScreenWidth;

    private int mCurrentTabPosition;

    private ImageButton mFloatingActionButton;
    private View mFloatingActionButtonContainer;

    private boolean mIsLandscape;
    private boolean mIsDialpadVisible;
    private boolean mAnimateFloatingActionButton;

    private String mDescriptionDialButtonStr;
    private String mActionMenuDialpadButtonStr;

    /**
     * Interpolator for FAB animations.
     */
    private Interpolator mFabInterpolator;

    /**
     * Additional offset for FAB to be lowered when dialpad is open.
     */
    private int mFloatingActionButtonDialpadMarginBottomOffset;

    public FloatingActionButtonController(Activity activity, boolean isLandscape,
                View container) {
        Resources resources = activity.getResources();
        mIsLandscape = isLandscape;
        mFabInterpolator = AnimationUtils.loadInterpolator(activity,
                android.R.interpolator.fast_out_slow_in);
        mFloatingActionButtonDialpadMarginBottomOffset = resources.getDimensionPixelOffset(
                R.dimen.floating_action_button_dialpad_margin_bottom_offset);
        mFloatingActionButton = (ImageButton) activity.
                findViewById(R.id.floating_action_button);
        mDescriptionDialButtonStr = resources.getString(R.string.description_dial_button);
        mActionMenuDialpadButtonStr = resources.getString(R.string.action_menu_dialpad_button);
        mFloatingActionButtonContainer = container;
        ViewUtil.setupFloatingActionButton(mFloatingActionButtonContainer, resources);
    }

    /**
     * Passes the screen width into the class. Necessary for translation calculations.
     *
     * @param screenWidth the width of the screen
     */
    public void setScreenWidth(int screenWidth) {
        mScreenWidth = screenWidth;
        updateByDialpadVisibility(mIsDialpadVisible);
    }

    public void setVisible(boolean visible) {
        mFloatingActionButtonContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Updates the FAB location (middle to right position) as the PageView scrolls.
     *
     * @param position tab position to align for
     * @param positionOffset a fraction used to calculate position of the FAB during page scroll
     */
    public void onPageScrolled(int position, float positionOffset) {
        // As the page is scrolling, if we're on the first tab, update the FAB position so it
        // moves along with it.
        if (position == ListsFragment.TAB_INDEX_SPEED_DIAL) {
            mFloatingActionButtonContainer.setTranslationX(
                    (int) (positionOffset * (mScreenWidth / 2f
                            - mFloatingActionButton.getWidth())));
            mFloatingActionButtonContainer.setTranslationY(0);
        }
    }

    /**
     * Updates the FAB location given a tab position.
     *
     * @param position tab position to align for
     */
    public void updateByTab(int position) {
        // If the screen width hasn't been set yet, don't do anything.
        if (mScreenWidth == 0 || mIsDialpadVisible) return;
        alignFloatingActionButtonByTab(position, false);
        mAnimateFloatingActionButton = true;
    }

    /**
     * Updates the FAB location to the proper location given whether or not the dialer is open.
     *
     * @param dialpadVisible whether or not the dialpad is currently open
     */
    public void updateByDialpadVisibility(boolean dialpadVisible) {
        // If the screen width hasn't been set yet, don't do anything.
        if (mScreenWidth == 0) return;
        mIsDialpadVisible = dialpadVisible;

        moveFloatingActionButton(mAnimateFloatingActionButton);
        mAnimateFloatingActionButton = true;
    }

    /**
     * Moves the FAB to the best known location given what the class currently knows.
     *
     * @param animate whether or not to smoothly animate the button
     */
    private void moveFloatingActionButton(boolean animate) {
        if (mIsDialpadVisible) {
            mFloatingActionButton.setImageResource(R.drawable.fab_ic_call);
            mFloatingActionButton.setContentDescription(mDescriptionDialButtonStr);
            alignFloatingActionButton(animate);
        } else {
            mFloatingActionButton.setImageResource(R.drawable.fab_ic_dial);
            mFloatingActionButton.setContentDescription(mActionMenuDialpadButtonStr);
            alignFloatingActionButtonByTab(mCurrentTabPosition, mAnimateFloatingActionButton);
        }
    }

    /**
     * Aligns the FAB to the position for the indicated tab.
     *
     * @param position tab position to align for
     * @param animate whether or not to smoothly animate the button
     */
    private void alignFloatingActionButtonByTab(int position, boolean animate) {
        mCurrentTabPosition = position;
        alignFloatingActionButton(animate);
    }

    /**
     * Aligns the FAB to the correct position.
     *
     * @param animate whether or not to smoothly animate the button
     */
    private void alignFloatingActionButton(boolean animate) {
        int translationX = calculateTranslationX();
        int translationY = mIsDialpadVisible ? mFloatingActionButtonDialpadMarginBottomOffset : 0;
        if (animate) {
            mFloatingActionButtonContainer.animate()
                    .translationX(translationX)
                    .translationY(translationY)
                    .setInterpolator(mFabInterpolator)
                    .setDuration(ANIMATION_DURATION).start();
        } else {
            mFloatingActionButtonContainer.setTranslationX(translationX);
            mFloatingActionButtonContainer.setTranslationY(translationY);
        }
    }

    /**
     * Calculates the translationX distance for the FAB.
     */
    private int calculateTranslationX() {
        if (mIsDialpadVisible) {
            return mIsLandscape ? mScreenWidth / 4 : 0;
        }
        if (mCurrentTabPosition == ListsFragment.TAB_INDEX_SPEED_DIAL) {
            return 0;
        }
        return mScreenWidth / 2 - mFloatingActionButton.getWidth();
    }

    /**
     * Saves the current state of the floating action button into a provided {@link Bundle}
     */
    public void saveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_IS_DIALPAD_VISIBLE, mIsDialpadVisible);
        outState.putInt(KEY_CURRENT_TAB_POSITION, mCurrentTabPosition);
    }

    /**
     * Restores the floating action button state from a provided {@link Bundle}
     */
    public void restoreInstanceState(Bundle inState) {
        mIsDialpadVisible = inState.getBoolean(KEY_IS_DIALPAD_VISIBLE);
        mCurrentTabPosition = inState.getInt(KEY_CURRENT_TAB_POSITION);
    }
}
