/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.animation.ObjectAnimator;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PinnedPositions;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactTileLoaderFactory;
import com.android.contacts.common.R;
import com.android.contacts.common.list.ContactEntry;
import com.android.contacts.common.list.ContactTileAdapter.DisplayType;
import com.android.contacts.common.list.ContactTileView;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ComparisonChain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Also allows for a configurable number of columns as well as a maximum row of tiled contacts.
 *
 * This adapter has been rewritten to only support a maximum of one row for favorites.
 *
 */
public class PhoneFavoritesTileAdapter extends BaseAdapter {
    private static final String TAG = PhoneFavoritesTileAdapter.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final int ROW_LIMIT_DEFAULT = 1;

    /** Time period for an animation. */
    private static final int ANIMATION_LENGTH = 300;

    private final ObjectAnimator mTranslateHorizontalAnimation;
    private final ObjectAnimator mTranslateVerticalAnimation;
    private final ObjectAnimator mAlphaAnimation;

    private ContactTileView.Listener mListener;
    private Context mContext;
    private Resources mResources;

    /** Contact data stored in cache. This is used to populate the associated view. */
    protected ArrayList<ContactEntry> mContactEntries = null;
    /** Back up of the temporarily removed Contact during dragging. */
    private ContactEntry mDraggedEntry = null;
    /** Position of the temporarily removed contact in the cache. */
    private int mDraggedEntryIndex = -1;
    /** New position of the temporarily removed contact in the cache. */
    private int mDropEntryIndex = -1;
    /** Position of the contact pending removal. */
    private int mPotentialRemoveEntryIndex = -1;

    private ContactPhotoManager mPhotoManager;
    protected int mNumFrequents;
    protected int mNumStarred;

    protected int mColumnCount;
    private int mMaxTiledRows = ROW_LIMIT_DEFAULT;
    private int mStarredIndex;

    protected int mIdIndex;
    protected int mLookupIndex;
    protected int mPhotoUriIndex;
    protected int mNameIndex;
    protected int mPresenceIndex;
    protected int mStatusIndex;

    /**
     * Only valid when {@link DisplayType#STREQUENT_PHONE_ONLY} is true
     */
    private int mPhoneNumberIndex;
    private int mPhoneNumberTypeIndex;
    private int mPhoneNumberLabelIndex;
    protected int mPinnedIndex;
    protected int mContactIdForFrequentIndex;

    private final int mPaddingInPixels;

    /** Indicates whether a drag is in process. */
    private boolean mInDragging = false;

    private static final int PIN_LIMIT = 20;

    final Comparator<ContactEntry> mContactEntryComparator = new Comparator<ContactEntry>() {
        @Override
        public int compare(ContactEntry lhs, ContactEntry rhs) {
            return ComparisonChain.start()
                    .compare(lhs.pinned, rhs.pinned)
                    .compare(lhs.name, rhs.name)
                    .result();
        }
    };

    public PhoneFavoritesTileAdapter(Context context, ContactTileView.Listener listener,
            int numCols) {
        this(context, listener, numCols, ROW_LIMIT_DEFAULT);
    }

    public PhoneFavoritesTileAdapter(Context context, ContactTileView.Listener listener,
            int numCols, int maxTiledRows) {
        mListener = listener;
        mContext = context;
        mResources = context.getResources();
        mColumnCount = numCols;
        mNumFrequents = 0;
        mMaxTiledRows = maxTiledRows;
        mContactEntries = new ArrayList<ContactEntry>();
        // Converting padding in dips to padding in pixels
        mPaddingInPixels = mContext.getResources()
                .getDimensionPixelSize(R.dimen.contact_tile_divider_padding);

        // Initiates all animations.
        mAlphaAnimation = ObjectAnimator.ofFloat(null, "alpha", 1.f).setDuration(ANIMATION_LENGTH);

        mTranslateHorizontalAnimation = ObjectAnimator.ofFloat(null, "translationX", 0.f).
                setDuration(ANIMATION_LENGTH);

        mTranslateVerticalAnimation = ObjectAnimator.ofFloat(null, "translationY", 0.f).setDuration(
                ANIMATION_LENGTH);

        bindColumnIndices();
    }

    public void setPhotoLoader(ContactPhotoManager photoLoader) {
        mPhotoManager = photoLoader;
    }

    public void setMaxRowCount(int maxRows) {
        mMaxTiledRows = maxRows;
    }

    public void setColumnCount(int columnCount) {
        mColumnCount = columnCount;
    }

    /**
     * Indicates whether a drag is in process.
     *
     * @param inDragging Boolean variable indicating whether there is a drag in process.
     */
    public void setInDragging(boolean inDragging) {
        mInDragging = inDragging;
    }

    /** Gets whether the drag is in process. */
    public boolean getInDragging() {
        return mInDragging;
    }

    /**
     * Sets the column indices for expected {@link Cursor}
     * based on {@link DisplayType}.
     */
    protected void bindColumnIndices() {
        mIdIndex = ContactTileLoaderFactory.CONTACT_ID;
        mLookupIndex = ContactTileLoaderFactory.LOOKUP_KEY;
        mPhotoUriIndex = ContactTileLoaderFactory.PHOTO_URI;
        mNameIndex = ContactTileLoaderFactory.DISPLAY_NAME;
        mStarredIndex = ContactTileLoaderFactory.STARRED;
        mPresenceIndex = ContactTileLoaderFactory.CONTACT_PRESENCE;
        mStatusIndex = ContactTileLoaderFactory.CONTACT_STATUS;

        mPhoneNumberIndex = ContactTileLoaderFactory.PHONE_NUMBER;
        mPhoneNumberTypeIndex = ContactTileLoaderFactory.PHONE_NUMBER_TYPE;
        mPhoneNumberLabelIndex = ContactTileLoaderFactory.PHONE_NUMBER_LABEL;
        mPinnedIndex = ContactTileLoaderFactory.PINNED;
        mContactIdForFrequentIndex = ContactTileLoaderFactory.CONTACT_ID_FOR_FREQUENT;
    }

    /**
     * Gets the number of frequents from the passed in cursor.
     *
     * This methods is needed so the GroupMemberTileAdapter can override this.
     *
     * @param cursor The cursor to get number of frequents from.
     */
    protected void saveNumFrequentsFromCursor(Cursor cursor) {
        mNumFrequents = cursor.getCount() - mNumStarred;
    }

    /**
     * Creates {@link ContactTileView}s for each item in {@link Cursor}.
     *
     * Else use {@link ContactTileLoaderFactory}
     */
    public void setContactCursor(Cursor cursor) {
        if (cursor != null && !cursor.isClosed()) {
            mNumStarred = getNumStarredContacts(cursor);
            saveNumFrequentsFromCursor(cursor);
            saveCursorToCache(cursor);

            // cause a refresh of any views that rely on this data
            notifyDataSetChanged();
        }
    }

    /**
     * Saves the cursor data to the cache, to speed up UI changes.
     *
     * @param cursor Returned cursor with data to populate the view.
     */
    private void saveCursorToCache(Cursor cursor) {
        mContactEntries.clear();

        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            final long id = cursor.getLong(mIdIndex);

            final String photoUri = cursor.getString(mPhotoUriIndex);
            final String lookupKey = cursor.getString(mLookupIndex);

            final ContactEntry contact = new ContactEntry();

            final int pinned = cursor.getInt(mPinnedIndex);
            final int starred = cursor.getInt(mStarredIndex);

            final String name = cursor.getString(mNameIndex);

            if (starred > 0) {
                contact.id = id;
            } else {
                // The contact id for frequent contacts is stored in the .contact_id field rather
                // than the _id field
                contact.id = cursor.getLong(mContactIdForFrequentIndex);
            }
            contact.name = (name != null) ? name : mResources.getString(R.string.missing_name);
            contact.status = cursor.getString(mStatusIndex);
            contact.photoUri = (photoUri != null ? Uri.parse(photoUri) : null);
            contact.lookupKey = ContentUris.withAppendedId(
                    Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey), id);

            // Set phone number and label
            final int phoneNumberType = cursor.getInt(mPhoneNumberTypeIndex);
            final String phoneNumberCustomLabel = cursor.getString(mPhoneNumberLabelIndex);
            contact.phoneLabel = (String) Phone.getTypeLabel(mResources, phoneNumberType,
                    phoneNumberCustomLabel);
            contact.phoneNumber = cursor.getString(mPhoneNumberIndex);

            contact.pinned = pinned;
            mContactEntries.add(contact);
        }

        arrangeContactsByPinnedPosition(mContactEntries);

        notifyDataSetChanged();
    }

    /**
     * Iterates over the {@link Cursor}
     * Returns position of the first NON Starred Contact
     * Returns -1 if {@link DisplayType#STARRED_ONLY}
     * Returns 0 if {@link DisplayType#FREQUENT_ONLY}
     */
    protected int getNumStarredContacts(Cursor cursor) {
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            if (cursor.getInt(mStarredIndex) == 0) {
                return cursor.getPosition();
            }
        }

        // There are not NON Starred contacts in cursor
        // Set divider positon to end
        return cursor.getCount();
    }

    /**
     * Loads a contact from the cached list.
     *
     * @param position Position of the Contact.
     * @return Contact at the requested position.
     */
    protected ContactEntry getContactEntryFromCache(int position) {
        if (mContactEntries.size() <= position) return null;
        return mContactEntries.get(position);
    }

    /**
     * Returns the number of frequents that will be displayed in the list.
     */
    public int getNumFrequents() {
        return mNumFrequents;
    }

    @Override
    public int getCount() {
        if (mContactEntries == null || mContactEntries.isEmpty()) {
            return 0;
        }

        int total = mContactEntries.size();
        // The number of contacts that don't show up as tiles
        final int nonTiledRows = Math.max(0, total - getMaxContactsInTiles());
        // The number of tiled rows
        final int tiledRows = getRowCount(total - nonTiledRows);
        return nonTiledRows + tiledRows;
    }

    public int getMaxTiledRows() {
        return mMaxTiledRows;
    }

    /**
     * Returns the number of rows required to show the provided number of entries
     * with the current number of columns.
     */
    protected int getRowCount(int entryCount) {
        if (entryCount == 0) return 0;
        final int nonLimitedRows = ((entryCount - 1) / mColumnCount) + 1;
        return Math.min(mMaxTiledRows, nonLimitedRows);
    }

    private int getMaxContactsInTiles() {
        return mColumnCount * mMaxTiledRows;
    }

    protected int getRowIndex(int entryIndex) {
        if (entryIndex < mMaxTiledRows * mColumnCount) {
            return entryIndex / mColumnCount;
        } else {
            return entryIndex - mMaxTiledRows * mColumnCount + mMaxTiledRows;
        }
    }

    public int getColumnCount() {
        return mColumnCount;
    }

    /**
     * Returns an ArrayList of the {@link ContactEntry}s that are to appear
     * on the row for the given position.
     */
    @Override
    public ArrayList<ContactEntry> getItem(int position) {
        ArrayList<ContactEntry> resultList = new ArrayList<ContactEntry>(mColumnCount);
        int contactIndex = position * mColumnCount;
        final int maxContactsInTiles = getMaxContactsInTiles();
        if (position < getRowCount(maxContactsInTiles)) {
            // Contacts that appear as tiles
            for (int columnCounter = 0; columnCounter < mColumnCount &&
                    contactIndex != maxContactsInTiles; columnCounter++) {
                resultList.add(getContactEntryFromCache(contactIndex));
                contactIndex++;
            }
        } else {
            // Contacts that appear as rows
            // The actual position of the contact in the cursor is simply total the number of
            // tiled contacts + the given position
            contactIndex = maxContactsInTiles + position - 1;
            resultList.add(getContactEntryFromCache(contactIndex));
        }

        return resultList;
    }

    @Override
    public long getItemId(int position) {
        // As we show several selectable items for each ListView row,
        // we can not determine a stable id. But as we don't rely on ListView's selection,
        // this should not be a problem.
        return position;
    }

    @Override
    public boolean areAllItemsEnabled() {
        // No dividers, so all items are enabled.
        return true;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public void notifyDataSetChanged() {
        if (DEBUG) {
            Log.v(TAG, "notifyDataSetChanged");
        }
        super.notifyDataSetChanged();
    }

    /**
     * Configures the animation for each view.
     *
     * @param contactTileRowView The row to be animated.
     * @param position The position of the row.
     * @param itemViewType The type of the row.
     */
    private void configureAnimationToView(ContactTileRow contactTileRowView, int position,
            int itemViewType) {
        // No need to animate anything if we are just entering a drag, because the blank
        // entry takes the place of the dragged entry anyway.
        if (mInDragging) return;
        if (mDropEntryIndex != -1) {
            // If one item is dropped in front the row, animate all following rows to shift down.
            // If the item is a favorite tile, animate it to appear from left.
            if (position >= getRowIndex(mDropEntryIndex)) {
                if (itemViewType == ViewTypes.FREQUENT) {
                    if (position == getRowIndex(mDropEntryIndex) || position == mMaxTiledRows) {
                        contactTileRowView.setVisibility(View.VISIBLE);
                        mAlphaAnimation.setTarget(contactTileRowView);
                        mAlphaAnimation.clone().start();
                    } else {
                        mTranslateVerticalAnimation.setTarget(contactTileRowView);
                        mTranslateVerticalAnimation.setFloatValues(-contactTileRowView.getHeight(),
                                0);
                        mTranslateVerticalAnimation.clone().start();
                    }
                } else {
                    contactTileRowView.animateTilesAppearRight(mDropEntryIndex + 1 -
                            position * mColumnCount);
                }
            }
        } else if (mPotentialRemoveEntryIndex != -1) {
            // If one item is to be removed above this row, animate the row to shift up. If it is
            // a favorite contact tile, animate it to appear from right.
            if (position >= getRowIndex(mPotentialRemoveEntryIndex)) {
                if (itemViewType == ViewTypes.FREQUENT) {
                    mTranslateVerticalAnimation.setTarget(contactTileRowView);
                    mTranslateVerticalAnimation.setFloatValues(contactTileRowView.getHeight(), 0);
                    mTranslateVerticalAnimation.clone().start();
                } else {
                    contactTileRowView.animateTilesAppearLeft(
                            mPotentialRemoveEntryIndex - position * mColumnCount);
                }
            }
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (DEBUG) {
            Log.v(TAG, "get view for " + String.valueOf(position));
        }
        int itemViewType = getItemViewType(position);

        ContactTileRow contactTileRowView  = (ContactTileRow) convertView;

        ArrayList<ContactEntry> contactList = getItem(position);

        if (contactTileRowView == null) {
            // Creating new row if needed
            contactTileRowView = new ContactTileRow(mContext, itemViewType, position);
        }

        contactTileRowView.configureRow(contactList, position, position == getCount() - 1);

        configureAnimationToView(contactTileRowView, position, itemViewType);

        return contactTileRowView;
    }

    private int getLayoutResourceId(int viewType) {
        switch (viewType) {
            case ViewTypes.FREQUENT:
                return R.layout.phone_favorite_regular_row_view;
            case ViewTypes.TOP:
                return R.layout.phone_favorite_tile_view;
            default:
                throw new IllegalArgumentException("Unrecognized viewType " + viewType);
        }
    }
    @Override
    public int getViewTypeCount() {
        return ViewTypes.COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        if (position < getRowCount(getMaxContactsInTiles())) {
            return ViewTypes.TOP;
        } else {
            return ViewTypes.FREQUENT;
        }
    }

    /**
     * Returns the "frequent header" position. Only available when STREQUENT or
     * STREQUENT_PHONE_ONLY is used for its display type.
     *
     * TODO krelease: We shouldn't need this method once we get rid of the frequent header
     * in the merged adapter
     */
    public int getFrequentHeaderPosition() {
        return getRowCount(mNumStarred);
    }

    /**
     * Temporarily removes a contact from the list for UI refresh. Stores data for this contact
     * in the back-up variable.
     *
     * @param index Position of the contact to be removed.
     */
    public void popContactEntry(int index) {
        if (index >= 0 && index < mContactEntries.size()) {
            mDraggedEntry = mContactEntries.get(index);
            mContactEntries.set(index, ContactEntry.BLANK_ENTRY);
            mDraggedEntryIndex = index;
            notifyDataSetChanged();
        }
    }

    /**
     * Drops the temporarily removed contact to the desired location in the list.
     *
     * @param index Location where the contact will be dropped.
     */
    public void dropContactEntry(int index) {
        boolean changed = false;
        if (mDraggedEntry != null) {
            if (index >= 0 && index <= mContactEntries.size()) {
                // Don't add the ContactEntry here (to prevent a double animation from occuring).
                // When we receive a new cursor the list of contact entries will automatically be
                // populated with the dragged ContactEntry at the correct spot.
                mDropEntryIndex = index;
                changed = true;
            } else if (mDraggedEntryIndex >= 0 && mDraggedEntryIndex <= mContactEntries.size()) {
                /** If the index is invalid, falls back to the original position of the contact. */
                mContactEntries.set(mDraggedEntryIndex, mDraggedEntry);
                mDropEntryIndex = mDraggedEntryIndex;
                notifyDataSetChanged();
            }

            if (changed && mDropEntryIndex < PIN_LIMIT) {
                final ContentValues cv = getReflowedPinnedPositions(mContactEntries, mDraggedEntry,
                        mDraggedEntryIndex, mDropEntryIndex);
                final Uri pinUri = PinnedPositions.UPDATE_URI.buildUpon().appendQueryParameter(
                            PinnedPositions.STAR_WHEN_PINNING, "true").build();
                // update the database here with the new pinned positions
                mContext.getContentResolver().update(pinUri, cv, null, null);
            }
            mDraggedEntry = null;
        }
    }

    /**
     * Invoked when the dragged item is dropped to unsupported location. We will then move the
     * contact back to where it was dragged from.
     */
    public void dropToUnsupportedView() {
        dropContactEntry(-1);
    }

    /**
     * Sets an item to for pending removal. If the user does not click the undo button, the item
     * will be removed at the next interaction.
     *
     * @param index Index of the item to be removed.
     */
    public void setPotentialRemoveEntryIndex(int index) {
        mPotentialRemoveEntryIndex = index;
    }

    /**
     * Removes a contact entry from the list.
     *
     * @return True is an item is removed. False is there is no item to be removed.
     */
    public boolean removeContactEntry() {
        if (mPotentialRemoveEntryIndex >= 0 && mPotentialRemoveEntryIndex < mContactEntries.size()) {
            final ContactEntry entry = mContactEntries.get(mPotentialRemoveEntryIndex);
            unstarAndUnpinContact(entry.lookupKey);
            return true;
        }
        return false;
    }

    /**
     * Resets the item for pending removal.
     */
    public void undoPotentialRemoveEntryIndex() {
        mPotentialRemoveEntryIndex = -1;
    }

    /**
     * Clears all temporary variables at a new interaction.
     */
    public void cleanTempVariables() {
        mDraggedEntryIndex = -1;
        mDropEntryIndex = -1;
        mDraggedEntry = null;
        mPotentialRemoveEntryIndex = -1;
    }

    /**
     * Acts as a row item composed of {@link ContactTileView}
     *
     */
    public class ContactTileRow extends FrameLayout {
        private int mItemViewType;
        private int mLayoutResId;
        private final int mRowPaddingStart;
        private final int mRowPaddingEnd;
        private final int mRowPaddingTop;
        private final int mRowPaddingBottom;
        private int mPosition;

        public ContactTileRow(Context context, int itemViewType, int position) {
            super(context);
            mItemViewType = itemViewType;
            mLayoutResId = getLayoutResourceId(mItemViewType);
            mPosition = position;

            final Resources resources = mContext.getResources();
            mRowPaddingStart = resources.getDimensionPixelSize(
                    R.dimen.favorites_row_start_padding);
            mRowPaddingEnd = resources.getDimensionPixelSize(
                    R.dimen.favorites_row_end_padding);
            mRowPaddingTop = resources.getDimensionPixelSize(
                    R.dimen.favorites_row_top_padding);
            mRowPaddingBottom = resources.getDimensionPixelSize(
                    R.dimen.favorites_row_bottom_padding);

            setBackgroundResource(R.drawable.bottom_border_background);

            setPaddingRelative(mRowPaddingStart, mRowPaddingTop, mRowPaddingEnd,
                    mRowPaddingBottom);

            // Remove row (but not children) from accessibility node tree.
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        /**
         * Configures the row to add {@link ContactEntry}s information to the views
         */
        public void configureRow(ArrayList<ContactEntry> list, int position, boolean isLastRow) {
            int columnCount = mItemViewType == ViewTypes.FREQUENT ? 1 : mColumnCount;
            mPosition = position;

            // Adding tiles to row and filling in contact information
            for (int columnCounter = 0; columnCounter < columnCount; columnCounter++) {
                ContactEntry entry =
                        columnCounter < list.size() ? list.get(columnCounter) : null;
                addTileFromEntry(entry, columnCounter, isLastRow);
            }
            setPressed(false);
            getBackground().setAlpha(255);
        }

        private void addTileFromEntry(ContactEntry entry, int childIndex, boolean isLastRow) {
            final PhoneFavoriteTileView contactTile;

            if (getChildCount() <= childIndex) {

                contactTile = (PhoneFavoriteTileView) inflate(mContext, mLayoutResId, null);
                // Note: the layoutparam set here is only actually used for FREQUENT.
                // We override onMeasure() for STARRED and we don't care the layout param there.
                final Resources resources = mContext.getResources();
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);

                params.setMargins(
                        resources.getDimensionPixelSize(R.dimen.detail_item_side_margin), 0,
                        resources.getDimensionPixelSize(R.dimen.detail_item_side_margin), 0);
                contactTile.setLayoutParams(params);
                contactTile.setPhotoManager(mPhotoManager);
                contactTile.setListener(mListener);
                addView(contactTile);
            } else {
                contactTile = (PhoneFavoriteTileView) getChildAt(childIndex);
            }
            contactTile.loadFromContact(entry);
            contactTile.setId(childIndex);
            switch (mItemViewType) {
                case ViewTypes.TOP:
                    // Setting divider visibilities
                    contactTile.setPaddingRelative(0, 0,
                            childIndex >= mColumnCount - 1 ? 0 : mPaddingInPixels, 0);
                    break;
                case ViewTypes.FREQUENT:
                    contactTile.setHorizontalDividerVisibility(
                            isLastRow ? View.GONE : View.VISIBLE);
                    break;
                default:
                    break;
            }
            contactTile.setupFavoriteContactCard();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            switch (mItemViewType) {
                case ViewTypes.TOP:
                    onLayoutForTiles();
                    return;
                default:
                    super.onLayout(changed, left, top, right, bottom);
                    return;
            }
        }

        private void onLayoutForTiles() {
            final int count = getChildCount();

            // Just line up children horizontally.
            int childLeft = getPaddingStart();
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);

                // Note MeasuredWidth includes the padding.
                final int childWidth = child.getMeasuredWidth();
                child.layout(childLeft, 0, childLeft + childWidth, child.getMeasuredHeight());
                childLeft += childWidth;
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            switch (mItemViewType) {
                case ViewTypes.TOP:
                    onMeasureForTiles(widthMeasureSpec);
                    return;
                default:
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    return;
            }
        }

        private void onMeasureForTiles(int widthMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);

            final int childCount = getChildCount();
            if (childCount == 0) {
                // Just in case...
                setMeasuredDimension(width, 0);
                return;
            }

            // 1. Calculate image size.
            //      = ([total width] - [total padding]) / [child count]
            //
            // 2. Set it to width/height of each children.
            //    If we have a remainder, some tiles will have 1 pixel larger width than its height.
            //
            // 3. Set the dimensions of itself.
            //    Let width = given width.
            //    Let height = image size + bottom paddding.

            final int totalPaddingsInPixels = (mColumnCount - 1) * mPaddingInPixels
                    + mRowPaddingStart + mRowPaddingEnd;

            // Preferred width / height for images (excluding the padding).
            // The actual width may be 1 pixel larger than this if we have a remainder.
            final int imageSize = (width - totalPaddingsInPixels) / mColumnCount;
            final int remainder = width - (imageSize * mColumnCount) - totalPaddingsInPixels;

            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                final int childWidth = imageSize + child.getPaddingRight()
                        // Compensate for the remainder
                        + (i < remainder ? 1 : 0);
                final int childHeight = imageSize;
                child.measure(
                        MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                        );
            }
            setMeasuredDimension(width, imageSize + getPaddingTop() + getPaddingBottom());
        }

        /**
         * Gets the index of the item at the specified coordinates.
         *
         * @param itemX X-coordinate of the selected item.
         * @param itemY Y-coordinate of the selected item.
         * @return Index of the selected item in the cached array.
         */
        public int getItemIndex(float itemX, float itemY) {
            if (mPosition < mMaxTiledRows) {
                final Rect childRect = new Rect();
                if (DEBUG) {
                    Log.v(TAG, String.valueOf(itemX) + " " + String.valueOf(itemY));
                }
                for (int i = 0; i < getChildCount(); ++i) {
                    /** If the row contains multiple tiles, checks each tile to see if the point
                     * is contained in the tile. */
                    getChildAt(i).getHitRect(childRect);
                    if (DEBUG) {
                        Log.v(TAG, childRect.toString());
                    }
                    if (childRect.contains((int)itemX, (int)itemY)) {
                        /** If the point is contained in the rectangle, computes the index of the
                         * item in the cached array. */
                        return i + (mPosition) * mColumnCount;
                    }
                }
            } else {
                /** If the selected item is one of the rows, compute the index. */
                return (mPosition - mMaxTiledRows) + mColumnCount * mMaxTiledRows;
            }
            return -1;
        }

        public PhoneFavoritesTileAdapter getTileAdapter() {
            return PhoneFavoritesTileAdapter.this;
        }

        public void animateTilesAppearLeft(int index) {
            for (int i = index; i < getChildCount(); ++i) {
                View childView = getChildAt(i);
                mTranslateHorizontalAnimation.setTarget(childView);
                mTranslateHorizontalAnimation.setFloatValues(childView.getWidth(), 0);
                mTranslateHorizontalAnimation.clone().start();
            }
        }

        public void animateTilesAppearRight(int index) {
            for (int i = index; i < getChildCount(); ++i) {
                View childView = getChildAt(i);
                mTranslateHorizontalAnimation.setTarget(childView);
                mTranslateHorizontalAnimation.setFloatValues(-childView.getWidth(), 0);
                mTranslateHorizontalAnimation.clone().start();
            }
        }

        public int getPosition() {
            return mPosition;
        }
    }

    /**
     * Used when a contact is swiped away. This will both unstar and set pinned position of the
     * contact to PinnedPosition.DEMOTED so that it doesn't show up anymore in the favorites list.
     */
    private void unstarAndUnpinContact(Uri contactUri) {
        final ContentValues values = new ContentValues(2);
        values.put(Contacts.STARRED, false);
        values.put(Contacts.PINNED, PinnedPositions.DEMOTED);
        mContext.getContentResolver().update(contactUri, values, null, null);
    }

    /**
     * Given a list of contacts that each have pinned positions, rearrange the list (destructive)
     * such that all pinned contacts are in their defined pinned positions, and unpinned contacts
     * take the spaces between those pinned contacts. Demoted contacts should not appear in the
     * resulting list.
     *
     * This method also updates the pinned positions of pinned contacts so that they are all
     * unique positive integers within range from 0 to toArrange.size() - 1. This is because
     * when the contact entries are read from the database, it is possible for them to have
     * overlapping pin positions due to sync or modifications by third party apps.
     */
    @VisibleForTesting
    /* package */ void arrangeContactsByPinnedPosition(ArrayList<ContactEntry> toArrange) {
        final PriorityQueue<ContactEntry> pinnedQueue =
                new PriorityQueue<ContactEntry>(PIN_LIMIT, mContactEntryComparator);

        final List<ContactEntry> unpinnedContacts = new LinkedList<ContactEntry>();

        final int length = toArrange.size();
        for (int i = 0; i < length; i++) {
            final ContactEntry contact = toArrange.get(i);
            // Decide whether the contact is hidden(demoted), pinned, or unpinned
            if (contact.pinned > PIN_LIMIT) {
                unpinnedContacts.add(contact);
            } else if (contact.pinned > PinnedPositions.DEMOTED) {
                // Demoted or contacts with negative pinned positions are ignored.
                // Pinned contacts go into a priority queue where they are ranked by pinned
                // position. This is required because the contacts provider does not return
                // contacts ordered by pinned position.
                pinnedQueue.add(contact);
            }
        }

        final int maxToPin = Math.min(PIN_LIMIT, pinnedQueue.size() + unpinnedContacts.size());

        toArrange.clear();
        for (int i = 0; i < maxToPin; i++) {
            if (!pinnedQueue.isEmpty() && pinnedQueue.peek().pinned <= i) {
                final ContactEntry toPin = pinnedQueue.poll();
                toPin.pinned = i;
                toArrange.add(toPin);
            } else if (!unpinnedContacts.isEmpty()) {
                toArrange.add(unpinnedContacts.remove(0));
            }
        }

        // If there are still contacts in pinnedContacts at this point, it means that the pinned
        // positions of these pinned contacts exceed the actual number of contacts in the list.
        // For example, the user had 10 frequents, starred and pinned one of them at the last spot,
        // and then cleared frequents. Contacts in this situation should become unpinned.
        while (!pinnedQueue.isEmpty()) {
            final ContactEntry entry = pinnedQueue.poll();
            entry.pinned = PinnedPositions.UNPINNED;
            toArrange.add(entry);
        }

        // Any remaining unpinned contacts that weren't in the gaps between the pinned contacts
        // now just get appended to the end of the list.
        toArrange.addAll(unpinnedContacts);
    }

    /**
     * Given an existing list of contact entries and a single entry that is to be pinned at a
     * particular position, return a ContentValues object that contains new pinned positions for
     * all contacts that are forced to be pinned at new positions, trying as much as possible to
     * keep pinned contacts at their original location.
     *
     * At this point in time the pinned position of each contact in the list has already been
     * updated by {@link #arrangeContactsByPinnedPosition}, so we can assume that all pinned
     * positions(within {@link #PIN_LIMIT} are unique positive integers.
     */
    @VisibleForTesting
    /* package */ ContentValues getReflowedPinnedPositions(ArrayList<ContactEntry> list,
            ContactEntry entryToPin, int oldPos, int newPinPos) {

        final ContentValues cv = new ContentValues();

        // Add the dragged contact at the user-requested spot.
        cv.put(String.valueOf(entryToPin.id), newPinPos);

        final int listSize = list.size();
        if (oldPos < newPinPos && list.get(listSize - 1).pinned == (listSize - 1)) {
            // The only time we should get here is it we are completely full - i.e. starting
            // from the newly pinned contact to the end of the list, every single contact
            // thereafter is pinned, and a contact is being shifted to the right by the user.
            // Instead of trying to make room to the right, we should thus try to shift contacts
            // to the left instead, working backwards through the list, starting from the contact
            // which just got bumped.
            for (int i = newPinPos; i >= 0; i--) {
                final ContactEntry entry = list.get(i);
                // Once we find an unpinned spot(or a blank entry), we can stop pushing contacts
                // to the left.
                if (entry.pinned > PIN_LIMIT) break;
                cv.put(String.valueOf(entry.id), entry.pinned - 1);
            }
        } else {
            // Shift any pinned contacts to the right as necessary, until an unpinned
            // spot is found
            for (int i = newPinPos; i < PIN_LIMIT && i < list.size(); i++) {
                final ContactEntry entry = list.get(i);
                if (entry.pinned > PIN_LIMIT) break;
                cv.put(String.valueOf(entry.id), entry.pinned + 1);
            }
        }
        return cv;
    }

    protected static class ViewTypes {
        public static final int COUNT = 2;
        public static final int FREQUENT = 0;
        public static final int TOP = 1;
    }
}
