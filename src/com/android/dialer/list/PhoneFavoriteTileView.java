/*

 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.dialer.list;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.list.ContactEntry;
import com.android.contacts.common.list.ContactTileView;
import com.android.dialer.list.PhoneFavoritesTileAdapter.ContactTileRow;

/**
 * A light version of the {@link com.android.contacts.common.list.ContactTileView} that is used in
 * Dialtacts for frequently called contacts. Slightly different behavior from superclass when you
 * tap it, you want to call the frequently-called number for the contact, even if that is not the
 * default number for that contact. This abstract class is the super class to both the row and tile
 * view.
 */
public abstract class PhoneFavoriteTileView extends ContactTileView {

    private static final String TAG = PhoneFavoriteTileView.class.getSimpleName();
    private static final boolean DEBUG = false;

    /** Length of all animations in miniseconds. */
    private static final int ANIMATION_LENGTH = 300;

    /** The view that holds the front layer of the favorite contact card. */
    protected View mFavoriteContactCard;
    /** The view that holds the background layer of the removal dialogue. */
    protected View mRemovalDialogue;
    /** Undo button for undoing favorite removal. */
    protected View mUndoRemovalButton;
    /** The view that holds the list view row. */
    protected ContactTileRow mParentRow;

    /** Users' most frequent phone number. */
    private String mPhoneNumberString;

    /** Custom gesture detector.*/
    protected GestureDetector mGestureDetector;
    /** Indicator of whether a scroll has started. */
    private boolean mInScroll;

    public PhoneFavoriteTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ContactTileRow getParentRow() {
        return mParentRow;
    }

    @Override
    public void loadFromContact(ContactEntry entry) {
        super.loadFromContact(entry);
        mPhoneNumberString = null; // ... in case we're reusing the view
        if (entry != null) {
            // Grab the phone-number to call directly... see {@link onClick()}
            mPhoneNumberString = entry.phoneNumber;
        }
    }

    /**
     * Gets the latest scroll gesture offset.
     */
    public void setScrollOffset(float offset) {
        // Sets the mInScroll variable to indicate a scroll is in progress.
        if (!mInScroll) {
            mInScroll = true;
        }

        // Changes the view to follow user's scroll position.
        shiftViewWithScroll(offset);
    }

    /**
     * Shifts the view to follow user's scroll position.
     */
    private void shiftViewWithScroll(float offset) {
       if (mInScroll) {
           // Shifts the foreground card to follow users' scroll gesture.
           mFavoriteContactCard.setTranslationX(offset);

           // Changes transparency of the foreground and background color
           final float alpha = 1.f - Math.abs((offset)) / getWidth();
           final float cappedAlpha = Math.min(Math.max(alpha, 0.f), 1.f);
           mFavoriteContactCard.setAlpha(cappedAlpha);
       }
    }

    /**
     * Sets the scroll has finished.
     *
     * @param isUnfinishedFling True if it is triggered from the onFling method, but the fling was
     * too short or too slow, or from the scroll that does not trigger fling.
     */
    public void setScrollEnd(boolean isUnfinishedFling) {
        mInScroll = false;

        if (isUnfinishedFling) {
            // If the fling is too short or too slow, or it is from a scroll, bring back the
            // favorite contact card.
            final ObjectAnimator fadeIn = ObjectAnimator.ofFloat(mFavoriteContactCard, "alpha",
                    1.f).setDuration(ANIMATION_LENGTH);
            final ObjectAnimator moveBack = ObjectAnimator.ofFloat(mFavoriteContactCard,
                    "translationX", 0.f).setDuration(ANIMATION_LENGTH);
            final ObjectAnimator backgroundFadeOut = ObjectAnimator.ofInt(
                    mParentRow.getBackground(), "alpha", 255).setDuration(ANIMATION_LENGTH);
            final AnimatorSet animSet = new AnimatorSet();
            animSet.playTogether(fadeIn, moveBack, backgroundFadeOut);
            animSet.start();
        } else {
            // If the fling is fast and far enough, move away the favorite contact card, bring the
            // favorite removal view to the foreground to ask user to confirm removal.
            int animationLength = (int) ((1 - Math.abs(mFavoriteContactCard.getTranslationX()) /
                    getWidth()) * ANIMATION_LENGTH);
            final ObjectAnimator fadeOut = ObjectAnimator.ofFloat(mFavoriteContactCard, "alpha",
                    0.f).setDuration(animationLength);
            final ObjectAnimator moveAway = ObjectAnimator.ofFloat(mFavoriteContactCard,
                    "translationX", getWidth()).setDuration(animationLength);
            final ObjectAnimator backgroundFadeIn = ObjectAnimator.ofInt(
                    mParentRow.getBackground(), "alpha", 0).setDuration(animationLength);
            if (mFavoriteContactCard.getTranslationX() < 0) {
                moveAway.setFloatValues(-getWidth());
            }
            final AnimatorSet animSet = new AnimatorSet();
            animSet.playTogether(fadeOut, moveAway, backgroundFadeIn);
            animSet.start();
        }
    }

    /**
     * Signals the user wants to undo removing the favorite contact.
     */
    public void undoRemove() {
        // Makes the removal dialogue invisible.
        mRemovalDialogue.setAlpha(0.0f);
        mRemovalDialogue.setVisibility(GONE);

        // Animates back the favorite contact card.
        final ObjectAnimator fadeIn = ObjectAnimator.ofFloat(mFavoriteContactCard, "alpha", 1.f).
                setDuration(ANIMATION_LENGTH);
        final ObjectAnimator moveBack = ObjectAnimator.ofFloat(mFavoriteContactCard, "translationX",
                0.f).setDuration(ANIMATION_LENGTH);
        final ObjectAnimator backgroundFadeOut = ObjectAnimator.ofInt(mParentRow.getBackground(),
                "alpha", 255).setDuration(ANIMATION_LENGTH);
        final AnimatorSet animSet = new AnimatorSet();
        animSet.playTogether(fadeIn, moveBack, backgroundFadeOut);
        animSet.start();

        // Signals the PhoneFavoritesTileAdapter to undo the potential delete.
        mParentRow.getTileAdapter().undoPotentialRemoveEntryIndex();
    }

    /**
     * Sets up the removal dialogue.
     */
    public void setupRemoveDialogue() {
        mRemovalDialogue.setVisibility(VISIBLE);
        mRemovalDialogue.setAlpha(1.0f);

        mUndoRemovalButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                undoRemove();
            }
        });
    }

    /**
     * Sets up the favorite contact card.
     */
    public void setupFavoriteContactCard() {
        if (mRemovalDialogue != null) {
            mRemovalDialogue.setVisibility(GONE);
            mRemovalDialogue.setAlpha(0.f);
        }
        mFavoriteContactCard.setAlpha(1.0f);
        mFavoriteContactCard.setTranslationX(0.f);
    }

    @Override
    protected OnClickListener createClickListener() {
        return new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener == null) return;
                if (TextUtils.isEmpty(mPhoneNumberString)) {
                    // Copy "superclass" implementation
                    mListener.onContactSelected(getLookupUri(), MoreContactUtils
                            .getTargetRectFromView(
                                    mContext, PhoneFavoriteTileView.this));
                } else {
                    // When you tap a frequently-called contact, you want to
                    // call them at the number that you usually talk to them
                    // at (i.e. the one displayed in the UI), regardless of
                    // whether that's their default number.
                    mListener.onCallNumberDirectly(mPhoneNumberString);
                }
            }
        };
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (DEBUG) {
            Log.v(TAG, event.toString());
        }
        switch (event.getAction()) {
            // If the scroll has finished without triggering a fling, handles it here.
            case MotionEvent.ACTION_UP:
                setPressed(false);
                if (mInScroll) {
                    if (!mGestureDetector.onTouchEvent(event)) {
                        setScrollEnd(true);
                    }
                    return true;
                }
                break;
            // When user starts a new gesture, clean up the pending removals.
            case MotionEvent.ACTION_DOWN:
                mParentRow.getTileAdapter().removeContactEntry();
                break;
            // When user continues with a new gesture, cleans up all the temp variables.
            case MotionEvent.ACTION_CANCEL:
                mParentRow.getTileAdapter().cleanTempVariables();
                break;
            default:
                break;
        }
        return mGestureDetector.onTouchEvent(event);
    }
}
