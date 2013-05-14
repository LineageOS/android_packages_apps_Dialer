/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.dialer.dialpad;

import android.content.Context;
import android.content.res.Resources;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.View.OnLongClickListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.dialer.R;

import com.google.common.collect.Lists;

import java.util.List;

/**
* This class controls the display and animation logic behind the smart dialing suggestion strip.
*
* It allows a list of SmartDialEntries to be assigned to the suggestion strip via
* {@link #setEntries}, and also animates the removal of old suggestions.
*
* To avoid creating new views every time new entries are assigned, references to 2 *
* {@link #NUM_SUGGESTIONS} views are kept in {@link #mViews} and {@link #mViewOverlays}.
*
* {@code mViews} contains the active views that are currently being displayed to the user,
* while {@code mViewOverlays} contains the views that are used as view overlays. The view
* overlays are used to provide the illusion of the former suggestions fading out. These two
* lists of views are rotated each time a new set of entries is assigned to achieve the appropriate
* cross fade animations using the new {@link View#getOverlay()} API.
*/
public class SmartDialController {
    public static final String LOG_TAG = "SmartDial";

    /**
     * Handtuned interpolator used to achieve the bounce effect when suggestions slide up. It
     * uses a combination of a decelerate interpolator and overshoot interpolator to first
     * decelerate, and then overshoot its top bounds and bounce back to its final position.
     */
    private class DecelerateAndOvershootInterpolator implements Interpolator {
        private DecelerateInterpolator a;
        private OvershootInterpolator b;

        public DecelerateAndOvershootInterpolator() {
            a = new DecelerateInterpolator(1.5f);
            b = new OvershootInterpolator(1.3f);
        }

        @Override
        public float getInterpolation(float input) {
            if (input > 0.6) {
                return b.getInterpolation(input);
            } else {
                return a.getInterpolation(input);
            }
        }

    }

    private DecelerateAndOvershootInterpolator mDecelerateAndOvershootInterpolator =
            new DecelerateAndOvershootInterpolator();
    private AccelerateDecelerateInterpolator mAccelerateDecelerateInterpolator =
            new AccelerateDecelerateInterpolator();

    private List<SmartDialEntry> mEntries;
    private List<SmartDialEntry> mOldEntries;

    private final int mNameHighlightedTextColor;
    private final int mNumberHighlightedTextColor;

    private final LinearLayout mList;
    private final View mBackground;

    private final List<LinearLayout> mViewOverlays = Lists.newArrayList();
    private final List<LinearLayout> mViews = Lists.newArrayList();

    private static final int NUM_SUGGESTIONS = 3;

    private static final long ANIM_DURATION = 200;

    private static final float BACKGROUND_FADE_AMOUNT = 0.25f;

    Resources mResources;

    public SmartDialController(Context context, ViewGroup parent,
            OnClickListener shortClickListener, OnLongClickListener longClickListener) {
        final Resources res = context.getResources();
        mResources = res;

        mNameHighlightedTextColor = res.getColor(R.color.smartdial_name_highlighted_text_color);
        mNumberHighlightedTextColor = res.getColor(
                R.color.smartdial_number_highlighted_text_color);

        mList = (LinearLayout) parent.findViewById(R.id.dialpad_smartdial_list);
        mBackground = parent.findViewById(R.id.dialpad_smartdial_list_background);

        mEntries = Lists.newArrayList();
        for (int i = 0; i < NUM_SUGGESTIONS; i++) {
            mEntries.add(SmartDialEntry.NULL);
        }

        mOldEntries = mEntries;

        final LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        for (int i = 0; i < NUM_SUGGESTIONS * 2; i++) {
            final LinearLayout view = (LinearLayout) inflater.inflate(
                    R.layout.dialpad_smartdial_item, mList, false);
            view.setOnClickListener(shortClickListener);
            view.setOnLongClickListener(longClickListener);
            if (i < NUM_SUGGESTIONS) {
                mViews.add(view);
            } else {
                mViewOverlays.add(view);
            }
            // Add all the views to mList so that they can get measured properly for animation
            // purposes. Once setEntries is called they will be removed and added as appropriate.
            view.setEnabled(false);
            mList.addView(view);
        }
    }

    /** Remove all entries. */
    public void clear() {
        mOldEntries = mEntries;
        mEntries = Lists.newArrayList();
        for (int i = 0; i < NUM_SUGGESTIONS; i++) {
            mEntries.add(SmartDialEntry.NULL);
        }
        updateViews();
    }

    /** Set entries. At the end of this method {@link #mEntries} should contain exactly
     *  {@link #NUM_SUGGESTIONS} entries.*/
    public void setEntries(List<SmartDialEntry> entries) {
        if (entries == null) throw new IllegalArgumentException();
        mOldEntries = mEntries;
        mEntries = entries;

        final int size = mEntries.size();
        if (size <= 1) {
            if (size == 0) {
                mEntries.add(SmartDialEntry.NULL);
            }
            // add a null entry to push the single entry into the middle
            mEntries.add(0, SmartDialEntry.NULL);
        } else if (size >= 2) {
            // swap the 1st and 2nd entries so that the highest confidence match goes into the
            // middle
            swap(0, 1);
        }

        while (mEntries.size() < NUM_SUGGESTIONS) {
            mEntries.add(SmartDialEntry.NULL);
        }

        updateViews();
    }

    /**
     * This method is called every time a new set of SmartDialEntries is to be assigned to the
     * suggestions view. The current set of active views are to be used as view overlays and
     * faded out, while the former view overlays are assigned the current entries, added to
     * {@link #mList} and faded into view.
     */
    private void updateViews() {
        // Remove all views from the root in preparation to swap the two sets of views
        mList.removeAllViews();
        try {
            mList.getOverlay().clear();
        } catch (NullPointerException e) {
            // Catch possible NPE b/8895794
        }

        // Used to track whether or not to animate the overlay. In the case where the suggestion
        // at position i will slide from the left or right, or if the suggestion at position i
        // has not changed, the overlay at i should be hidden immediately. Overlay animations are
        // set in a separate loop from the active views to avoid unnecessarily reanimating the same
        // overlay multiple times.
        boolean[] dontAnimateOverlay = new boolean[NUM_SUGGESTIONS];
        boolean noSuggestions = true;

        // At this point in time {@link #mViews} contains the former active views with old
        // suggestions that will be swapped out to serve as view overlays, while
        // {@link #mViewOverlays} contains the former overlays that will now serve as active
        // views.
        for (int i = 0; i < NUM_SUGGESTIONS; i++) {
            // Retrieve the former overlay to be used as the new active view
            final LinearLayout active = mViewOverlays.get(i);
            final SmartDialEntry item = mEntries.get(i);

            noSuggestions &= (item == SmartDialEntry.NULL);

            assignEntryToView(active, mEntries.get(i));
            final SmartDialEntry oldItem = mOldEntries.get(i);
            // The former active view will now be used as an overlay for the cross-fade effect
            final LinearLayout overlay = mViews.get(i);
            show(active);
            if (!containsSameContact(oldItem, item)) {
                // Determine what kind of animation to use for the new view
                if (i == 1) { // Middle suggestion
                    if (containsSameContact(item, mOldEntries.get(0))) {
                        // Suggestion went from the left to the middle, slide it left to right
                        animateSlideFromLeft(active);
                        dontAnimateOverlay[0] = true;
                    } else if (containsSameContact(item, mOldEntries.get(2))) {
                        // Suggestion sent from the right to the middle, slide it right to left
                        animateSlideFromRight(active);
                        dontAnimateOverlay[2] = true;
                    } else {
                        animateFadeInAndSlideUp(active);
                    }
                } else { // Left/Right suggestion
                    if (i == 2 && containsSameContact(item, mOldEntries.get(1))) {
                        // Suggestion went from middle to the right, slide it left to right
                        animateSlideFromLeft(active);
                        dontAnimateOverlay[1] = true;
                    } else if (i == 0 && containsSameContact(item, mOldEntries.get(1))) {
                        // Suggestion went from middle to the left, slide it right to left
                        animateSlideFromRight(active);
                        dontAnimateOverlay[1] = true;
                    } else {
                        animateFadeInAndSlideUp(active);
                    }
                }
            } else {
                // Since the same item is in the same spot, don't do any animations and just
                // show the new view.
                dontAnimateOverlay[i] = true;
            }
            mList.getOverlay().add(overlay);
            mList.addView(active);
            // Keep track of active views and view overlays
            mViews.set(i, active);
            mViewOverlays.set(i, overlay);
        }

        // Separate loop for overlay animations. At this point in time {@link #mViewOverlays}
        // contains the actual overlays.
        for (int i = 0; i < NUM_SUGGESTIONS; i++) {
            final LinearLayout overlay = mViewOverlays.get(i);
            if (!dontAnimateOverlay[i]) {
                animateFadeOutAndSlideDown(overlay);
            } else {
                hide(overlay);
            }
        }

        // Fade out the background to 25% opacity if there are suggestions. If there are no
        // suggestions, display the background as usual.
        mBackground.animate().withLayer().alpha(noSuggestions ? 1.0f : BACKGROUND_FADE_AMOUNT);
    }

    private void show(View view) {
        view.animate().cancel();
        view.setAlpha(1);
        view.setTranslationX(0);
        view.setTranslationY(0);
    }

    private void hide(View view) {
        view.animate().cancel();
        view.setAlpha(0);
    }

    private void animateFadeInAndSlideUp(View view) {
        view.animate().cancel();
        view.setAlpha(0.2f);
        view.setTranslationY(view.getHeight());
        view.animate().withLayer().alpha(1).translationY(0).setDuration(ANIM_DURATION).
                setInterpolator(mDecelerateAndOvershootInterpolator);
    }

    private void animateFadeOutAndSlideDown(View view) {
        view.animate().cancel();
        view.setAlpha(1);
        view.setTranslationY(0);
        view.animate().withLayer().alpha(0).translationY(view.getHeight()).setDuration(
                ANIM_DURATION).setInterpolator(mAccelerateDecelerateInterpolator);
    }

    private void animateSlideFromLeft(View view) {
        view.animate().cancel();
        view.setAlpha(1);
        view.setTranslationX(-1 * view.getWidth());
        view.animate().withLayer().translationX(0).setDuration(ANIM_DURATION).setInterpolator(
                mAccelerateDecelerateInterpolator);
    }

    private void animateSlideFromRight(View view) {
        view.animate().cancel();
        view.setAlpha(1);
        view.setTranslationX(view.getWidth());
        view.animate().withLayer().translationX(0).setDuration(ANIM_DURATION).setInterpolator(
                mAccelerateDecelerateInterpolator);
    }

    // Swaps the items in pos1 and pos2 of mEntries
    private void swap(int pos1, int pos2) {
        if (pos1 == pos2) {
            return;
        }
        final SmartDialEntry temp = mEntries.get(pos1);
        mEntries.set(pos1, mEntries.get(pos2));
        mEntries.set(pos2, temp);
    }

    // Returns whether two SmartDialEntries contain the same contact
    private boolean containsSameContact(SmartDialEntry x, SmartDialEntry y) {
        return x.contactUri.equals(y.contactUri);
    }

    // Sets the information within a SmartDialEntry to the provided view
    private void assignEntryToView(LinearLayout view, SmartDialEntry item) {
        final TextView nameView = (TextView) view.findViewById(R.id.contact_name);

        final TextView numberView = (TextView) view.findViewById(
                R.id.contact_number);

        if (item == SmartDialEntry.NULL) {
            // Clear the text in case the view was reused.
            nameView.setText("");
            numberView.setText("");
            view.setEnabled(false);
            return;
        }

        // Highlight the display name with the provided match positions
        if (!TextUtils.isEmpty(item.displayName)) {
            final SpannableString displayName = new SpannableString(item.displayName);
            for (final SmartDialMatchPosition p : item.matchPositions) {
                if (p.start < p.end) {
                    if (p.end > displayName.length()) {
                        p.end = displayName.length();
                    }
                    // Create a new ForegroundColorSpan for each section of the name to highlight,
                    // otherwise multiple highlights won't work.
                    displayName.setSpan(new ForegroundColorSpan(mNameHighlightedTextColor), p.start,
                            p.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            nameView.setText(displayName);
        }

        // Highlight the phone number with the provided match positions
        if (!TextUtils.isEmpty(item.phoneNumber)) {
            final SmartDialMatchPosition p = item.phoneNumberMatchPosition;
            final SpannableString phoneNumber = new SpannableString(item.phoneNumber);
            if (p != null && p.start < p.end) {
                if (p.end > phoneNumber.length()) {
                    p.end = phoneNumber.length();
                }
                phoneNumber.setSpan(new ForegroundColorSpan(mNumberHighlightedTextColor), p.start,
                        p.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            numberView.setText(phoneNumber);
        }
        view.setEnabled(true);
        view.setTag(item);
    }
}
