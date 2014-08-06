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

import android.app.ActionBar;
import android.content.Context;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.dialer.widget.ActionBarController.ActivityUi;

@SmallTest
public class ActionBarControllerTest extends InstrumentationTestCase {

    private static final int ACTION_BAR_HEIGHT = 100;
    private ActionBarController mActionBarController;
    private SearchEditTextLayout mSearchBox;
    private MockActivityUi mActivityUi;

    private class MockActivityUi implements ActivityUi {
        boolean isInSearchUi;
        boolean hasSearchQuery;
        boolean shouldShowActionBar;
        int actionBarHideOffset;

        @Override
        public boolean isInSearchUi() {
            return isInSearchUi;
        }

        @Override
        public boolean hasSearchQuery() {
            return hasSearchQuery;
        }

        @Override
        public boolean shouldShowActionBar() {
            return shouldShowActionBar;
        }

        @Override
        public int getActionBarHeight() {
            return ACTION_BAR_HEIGHT;
        }

        @Override
        public ActionBar getActionBar() {
            return null;
        }
    }

    /**
     * Mock version of the searchbox, that updates its state immediately instead of animating
     */
    private class MockSearchBox extends SearchEditTextLayout {

        public MockSearchBox(Context context) {
            super(context, null);
        }

        @Override
        public void expand(boolean animate, boolean requestFocus) {
            mIsExpanded = true;
        }

        @Override
        public void collapse(boolean animate) {
            mIsExpanded = false;
        }
    }

    @Override
    protected void setUp() {
        mActivityUi = new MockActivityUi();
        mSearchBox = new MockSearchBox(this.getInstrumentation().getContext());
        mActionBarController = new ActionBarController(mActivityUi, mSearchBox);
    }

    // Tapping the search box should only do something when the activity is not in the search UI
    public void testSearchBoxTapped() {
        mSearchBox.collapse(false);
        mActivityUi.isInSearchUi = false;
        mActionBarController.onSearchBoxTapped();
        assertActionBarState(true, false, false);

        // Collapse the search box manually again. This time tapping on the search box should not
        // expand the search box because isInSearchUi is not true.
        mSearchBox.collapse(false);
        mActivityUi.isInSearchUi = true;
        mActionBarController.onSearchBoxTapped();
        assertActionBarState(false, false, false);
    }

    // The search box should always end up being faded in and collapsed. If necessary, it should
    // be slid down or up depending on what the state of the action bar was before that.
    public void testOnSearchUiExited() {
        // ActionBar shown previously before entering searchUI
        mSearchBox.expand(true, false);
        mSearchBox.setVisible(false);
        mActivityUi.shouldShowActionBar = true;
        mActionBarController.onSearchUiExited();
        assertActionBarState(false, false, false);

        // ActionBar slid up previously before entering searchUI
        mSearchBox.collapse(false);
        mSearchBox.setVisible(false);
        mActivityUi.shouldShowActionBar = false;
        mActionBarController.onSearchUiExited();
        assertActionBarState(false, false, true);
    }

    // Depending on what state the UI was in previously, sliding the dialpad down can mean either
    // displaying the expanded search box by sliding it down, displaying the unexpanded search box,
    // or nothing at all.
    public void testOnDialpadDown() {
        // No search query typed in the dialpad and action bar was showing before
        mActivityUi.shouldShowActionBar = true;
        mActivityUi.isInSearchUi = true;
        mSearchBox.setVisible(false);
        mActionBarController.onDialpadDown();
        assertActionBarState(false, false, false);

        // No search query typed in the dialpad, but action bar was not showing before
        mActionBarController.slideActionBar(true /* slideUp */, false /* animate */);
        mActivityUi.shouldShowActionBar = false;
        mSearchBox.setVisible(false);
        mActionBarController.onDialpadDown();
        assertActionBarState(false, false, true);

        // Something typed in the dialpad - so remain in search UI and slide the expanded search
        // box down
        mActionBarController.slideActionBar(true /* slideUp */, false /* animate */);
        mActivityUi.shouldShowActionBar = true;
        mActivityUi.hasSearchQuery= true;
        mSearchBox.setVisible(false);
        mSearchBox.expand(false, false);
        mActionBarController.onDialpadDown();
        assertActionBarState(true, false, false);
    }

    // Sliding the dialpad up should fade out the search box if we weren't already in search, or
    // slide up the search box otherwise
    public void testOnDialpadUp() {
        mActivityUi.isInSearchUi = false;
        mActionBarController.onDialpadUp();
        assertActionBarState(false, true, false);

        // In Search UI, with expanded search box and something currently typed in the search box
        mActivityUi.isInSearchUi = true;
        mActivityUi.hasSearchQuery = true;
        mSearchBox.expand(true, false);
        mSearchBox.setVisible(true);
        mActionBarController.slideActionBar(true /* slideUp */, false /* animate */);
        mActionBarController.onDialpadUp();
        assertActionBarState(true, false, true);
    }

    private void assertActionBarState(boolean isExpanded, boolean isFadedOut, boolean isSlidUp) {
        assertEquals(isExpanded, mSearchBox.isExpanded());
        assertEquals(isFadedOut, mSearchBox.isFadedOut());
        assertEquals(isSlidUp, mActionBarController.getIsActionBarSlidUp());
    }
}
