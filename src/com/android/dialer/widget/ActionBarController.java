package com.android.dialer.widget;

import com.google.common.annotations.VisibleForTesting;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.os.Bundle;
import android.util.Log;

import com.android.contacts.common.animation.AnimUtils;
import com.android.contacts.common.animation.AnimUtils.AnimationCallback;
import com.android.dialer.DialtactsActivity;

/**
 * Controls the various animated properties of the actionBar: showing/hiding, fading/revealing,
 * and collapsing/expanding, and assigns suitable properties to the actionBar based on the
 * current state of the UI.
 */
public class ActionBarController {
    public static final boolean DEBUG = DialtactsActivity.DEBUG;
    public static final String TAG = "ActionBarController";
    private static final String KEY_IS_SLID_UP = "key_actionbar_is_slid_up";
    private static final String KEY_IS_FADED_OUT = "key_actionbar_is_faded_out";
    private static final String KEY_IS_EXPANDED = "key_actionbar_is_expanded";

    private ActivityUi mActivityUi;
    private SearchEditTextLayout mSearchBox;

    private boolean mIsActionBarSlidUp;

    private final AnimationCallback mFadeOutCallback = new AnimationCallback() {
        @Override
        public void onAnimationEnd() {
            slideActionBarUp(false);
        }

        @Override
        public void onAnimationCancel() {
            slideActionBarUp(false);
        }
    };

    public interface ActivityUi {
        public boolean isInSearchUi();
        public boolean hasSearchQuery();
        public boolean shouldShowActionBar();
        public int getActionBarHeight();
        public int getActionBarHideOffset();
        public void setActionBarHideOffset(int hideOffset);
    }

    public ActionBarController(ActivityUi activityUi, SearchEditTextLayout searchBox) {
        mActivityUi = activityUi;
        mSearchBox = searchBox;
    }

    /**
     * @return The offset the action bar is being translated upwards by
     */
    public int getHideOffset() {
        return mActivityUi.getActionBarHideOffset();
    }

    /**
     * @return Whether or not the action bar is currently showing (both slid down and visible)
     */
    public boolean isActionBarShowing() {
        return !mIsActionBarSlidUp && !mSearchBox.isFadedOut();
    }

    /**
     * Called when the user has tapped on the collapsed search box, to start a new search query.
     */
    public void onSearchBoxTapped() {
        if (DEBUG) {
            Log.d(TAG, "OnSearchBoxTapped: isInSearchUi " + mActivityUi.isInSearchUi());
        }
        if (!mActivityUi.isInSearchUi()) {
            mSearchBox.expand(true /* animate */, true /* requestFocus */);
        }
    }

    /**
     * Called when search UI has been exited for some reason.
     */
    public void onSearchUiExited() {
        if (DEBUG) {
            Log.d(TAG, "OnSearchUIExited: isExpanded " + mSearchBox.isExpanded()
                    + " isFadedOut: " + mSearchBox.isFadedOut()
                    + " shouldShowActionBar: " + mActivityUi.shouldShowActionBar());
        }
        if (mSearchBox.isExpanded()) {
            mSearchBox.collapse(true /* animate */);
        }
        if (mSearchBox.isFadedOut()) {
            mSearchBox.fadeIn();
        }

        if (mActivityUi.shouldShowActionBar()) {
            slideActionBarDown(false /* animate */);
        } else {
            slideActionBarUp(false /* animate */);
        }
    }

    /**
     * Called to indicate that the user is trying to hide the dialpad. Should be called before
     * any state changes have actually occurred.
     */
    public void onDialpadDown() {
        if (DEBUG) {
            Log.d(TAG, "OnDialpadDown: isInSearchUi " + mActivityUi.isInSearchUi()
                    + " hasSearchQuery: " + mActivityUi.hasSearchQuery()
                    + " isFadedOut: " + mSearchBox.isFadedOut()
                    + " isExpanded: " + mSearchBox.isExpanded());
        }
        if (mActivityUi.isInSearchUi()) {
            if (mActivityUi.hasSearchQuery()) {
                if (mSearchBox.isFadedOut()) {
                    mSearchBox.setVisible(true);
                }
                if (!mSearchBox.isExpanded()) {
                    mSearchBox.expand(false /* animate */, false /* requestFocus */);
                }
                slideActionBarDown(true /* animate */);
            } else {
                mSearchBox.fadeIn();
            }
        }
    }

    /**
     * Called to indicate that the user is trying to show the dialpad. Should be called before
     * any state changes have actually occurred.
     */
    public void onDialpadUp() {
        if (DEBUG) {
            Log.d(TAG, "OnDialpadUp: isInSearchUi " + mActivityUi.isInSearchUi());
        }
        if (mActivityUi.isInSearchUi()) {
            slideActionBarUp(true);
        } else {
            // From the lists fragment
            mSearchBox.fadeOut(mFadeOutCallback);
        }
    }

    public void slideActionBarUp(boolean animate) {
        if (DEBUG) {
            Log.d(TAG, "Sliding actionBar up - animate: " + animate);
        }
        if (animate) {
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
            animator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    final float value = (float) animation.getAnimatedValue();
                    mActivityUi.setActionBarHideOffset(
                            (int) (mActivityUi.getActionBarHeight() * value));
                }
            });
            animator.start();
        } else {
           mActivityUi.setActionBarHideOffset(mActivityUi.getActionBarHeight());
        }
        mIsActionBarSlidUp = true;
    }

    public void slideActionBarDown(boolean animate) {
        if (DEBUG) {
            Log.d(TAG, "Sliding actionBar down - animate: " + animate);
        }
        if (animate) {
            ValueAnimator animator = ValueAnimator.ofFloat(1, 0);
            animator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    final float value = (float) animation.getAnimatedValue();
                    mActivityUi.setActionBarHideOffset(
                            (int) (mActivityUi.getActionBarHeight() * value));
                }
            });
            animator.start();
        } else {
            mActivityUi.setActionBarHideOffset(0);
        }
        mIsActionBarSlidUp = false;
    }

    /**
     * Saves the current state of the action bar into a provided {@link Bundle}
     */
    public void saveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_IS_SLID_UP, mIsActionBarSlidUp);
        outState.putBoolean(KEY_IS_FADED_OUT, mSearchBox.isFadedOut());
        outState.putBoolean(KEY_IS_EXPANDED, mSearchBox.isExpanded());
    }

    /**
     * Restores the action bar state from a provided {@link Bundle}
     */
    public void restoreInstanceState(Bundle inState) {
        mIsActionBarSlidUp = inState.getBoolean(KEY_IS_SLID_UP);
        if (mIsActionBarSlidUp) {
            slideActionBarUp(false);
        } else {
            slideActionBarDown(false);
        }

        final boolean isSearchBoxFadedOut = inState.getBoolean(KEY_IS_FADED_OUT);
        if (isSearchBoxFadedOut) {
            if (!mSearchBox.isFadedOut()) {
                mSearchBox.setVisible(false);
            }
        } else if (mSearchBox.isFadedOut()) {
                mSearchBox.setVisible(true);
        }

        final boolean isSearchBoxExpanded = inState.getBoolean(KEY_IS_EXPANDED);
        if (isSearchBoxExpanded) {
            if (!mSearchBox.isExpanded()) {
                mSearchBox.expand(false, false);
            }
        } else if (mSearchBox.isExpanded()) {
                mSearchBox.collapse(false);
        }
    }

    @VisibleForTesting
    public boolean getIsActionBarSlidUp() {
        return mIsActionBarSlidUp;
    }
}