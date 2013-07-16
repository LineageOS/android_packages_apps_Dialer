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

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;

import com.android.contacts.common.util.ViewUtil;
import com.android.dialer.R;
import com.android.dialer.list.PhoneFavoriteDragAndDropListeners.PhoneFavoriteDragListener;
import com.android.dialer.list.PhoneFavoriteDragAndDropListeners.PhoneFavoriteGestureListener;

import com.android.dialer.list.PhoneFavoritesTileAdapter.ContactTileRow;


public class PhoneFavoriteRegularRowView extends PhoneFavoriteTileView {
    private static final String TAG = PhoneFavoriteRegularRowView.class.getSimpleName();
    private static final boolean DEBUG = false;

    public PhoneFavoriteRegularRowView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mFavoriteContactCard = findViewById(R.id.contact_favorite_card);
        mRemovalDialogue = findViewById(R.id.favorite_remove_dialogue);
        mUndoRemovalButton = findViewById(R.id.favorite_remove_undo_button);

        mGestureDetector = new GestureDetector(getContext(),
                new PhoneFavoriteGestureListener(this));
    }

    @Override
    protected void onAttachedToWindow() {
        mParentRow = (ContactTileRow) getParent();
        mParentRow.setOnDragListener(new PhoneFavoriteDragListener(mParentRow,
                mParentRow.getTileAdapter()));
    }

    @Override
    protected boolean isDarkTheme() {
        return false;
    }

    @Override
    protected int getApproximateImageSize() {
        return ViewUtil.getConstantPreLayoutWidth(getQuickContact());
    }
}
