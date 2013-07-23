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

import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactTileLoaderFactory;
import com.android.contacts.common.R;
import com.android.contacts.common.list.ContactEntry;
import com.android.contacts.common.list.ContactTileAdapter;
import com.android.contacts.common.list.ContactTileView;

import java.util.ArrayList;

/**
 * Also allows for a configurable number of columns as well as a maximum row of tiled contacts.
 *
 * This adapter has been rewritten to only support a maximum of one row for favorites.
 *
 */
public class PhoneFavoritesTileAdapter extends BaseAdapter {
    private static final String TAG = ContactTileAdapter.class.getSimpleName();

    public static final int NO_ROW_LIMIT = -1;

    private ContactTileView.Listener mListener;
    private Context mContext;
    private Resources mResources;
    protected Cursor mContactCursor = null;
    private ContactPhotoManager mPhotoManager;
    protected int mNumFrequents;

    /**
     * Index of the first NON starred contact in the {@link Cursor}
     * Only valid when {@link DisplayType#STREQUENT} is true
     */
    private int mDividerPosition;
    protected int mColumnCount;
    private int mMaxTiledRows = NO_ROW_LIMIT;
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

    private boolean mIsQuickContactEnabled = false;
    private final int mPaddingInPixels;

    public PhoneFavoritesTileAdapter(Context context, ContactTileView.Listener listener, int numCols) {
        this(context, listener, numCols, NO_ROW_LIMIT);
    }

    public PhoneFavoritesTileAdapter(Context context, ContactTileView.Listener listener, int numCols,
            int maxTiledRows) {
        mListener = listener;
        mContext = context;
        mResources = context.getResources();
        mColumnCount = numCols;
        mNumFrequents = 0;
        mMaxTiledRows = maxTiledRows;

        // Converting padding in dips to padding in pixels
        mPaddingInPixels = mContext.getResources()
                .getDimensionPixelSize(R.dimen.phone_contact_tile_divider_padding);
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

    public void enableQuickContact(boolean enableQuickContact) {
        mIsQuickContactEnabled = enableQuickContact;
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
    }

    /**
     * Gets the number of frequents from the passed in cursor.
     *
     * This methods is needed so the GroupMemberTileAdapter can override this.
     *
     * @param cursor The cursor to get number of frequents from.
     */
    protected void saveNumFrequentsFromCursor(Cursor cursor) {
        mNumFrequents = cursor.getCount() - mDividerPosition;
    }

    /**
     * Creates {@link ContactTileView}s for each item in {@link Cursor}.
     *
     * Else use {@link ContactTileLoaderFactory}
     */
    public void setContactCursor(Cursor cursor) {
        mContactCursor = cursor;
        mDividerPosition = getDividerPosition(cursor);

        saveNumFrequentsFromCursor(cursor);

        // cause a refresh of any views that rely on this data
        notifyDataSetChanged();
    }

    /**
     * Iterates over the {@link Cursor}
     * Returns position of the first NON Starred Contact
     * Returns -1 if {@link DisplayType#STARRED_ONLY}
     * Returns 0 if {@link DisplayType#FREQUENT_ONLY}
     */
    protected int getDividerPosition(Cursor cursor) {
        if (cursor == null || cursor.isClosed()) {
            throw new IllegalStateException("Unable to access cursor");
        }

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

    protected ContactEntry createContactEntryFromCursor(Cursor cursor, int position) {
        // If the loader was canceled we will be given a null cursor.
        // In that case, show an empty list of contacts.
        if (cursor == null || cursor.isClosed() || cursor.getCount() <= position) return null;

        cursor.moveToPosition(position);
        long id = cursor.getLong(mIdIndex);
        String photoUri = cursor.getString(mPhotoUriIndex);
        String lookupKey = cursor.getString(mLookupIndex);

        ContactEntry contact = new ContactEntry();
        String name = cursor.getString(mNameIndex);
        contact.name = (name != null) ? name : mResources.getString(R.string.missing_name);
        contact.status = cursor.getString(mStatusIndex);
        contact.photoUri = (photoUri != null ? Uri.parse(photoUri) : null);
        contact.lookupKey = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey), id);

        // Set phone number and label
        int phoneNumberType = cursor.getInt(mPhoneNumberTypeIndex);
        String phoneNumberCustomLabel = cursor.getString(mPhoneNumberLabelIndex);
        contact.phoneLabel = (String) Phone.getTypeLabel(mResources, phoneNumberType,
                phoneNumberCustomLabel);
        contact.phoneNumber = cursor.getString(mPhoneNumberIndex);

        return contact;
    }

    /**
     * Returns the number of frequents that will be displayed in the list.
     */
    public int getNumFrequents() {
        return mNumFrequents;
    }

    @Override
    public int getCount() {
        if (mContactCursor == null || mContactCursor.isClosed()) {
            return 0;
        }

        // Takes numbers of rows the Starred Contacts Occupy
        int starredRowCount = getRowCount(mDividerPosition) +
                (mMaxTiledRows == NO_ROW_LIMIT ? 0 : Math.max(0, mDividerPosition -
                        mMaxTiledRows * mColumnCount));

        // Compute the frequent row count which is 1 plus the number of frequents
        // (to account for the divider) or 0 if there are no frequents.
        int frequentRowCount = mNumFrequents == 0 ? 0 : mNumFrequents;

        // Return the number of starred plus frequent rows
        return starredRowCount + frequentRowCount;
    }

    /**
     * Returns the number of rows required to show the provided number of entries
     * with the current number of columns.
     */
    protected int getRowCount(int entryCount) {
        if (entryCount == 0) return 0;
        final int nonLimitedRows = ((entryCount - 1) / mColumnCount) + 1;
        return mMaxTiledRows == NO_ROW_LIMIT ? nonLimitedRows : Math.min(mMaxTiledRows,
                nonLimitedRows);
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
        if (position < getRowCount(mDividerPosition)) {
            for (int columnCounter = 0; columnCounter < mColumnCount &&
                    contactIndex != mDividerPosition; columnCounter++) {
                resultList.add(createContactEntryFromCursor(mContactCursor, contactIndex));
                contactIndex++;
            }
        } else {
            /*
             * Current position minus how many rows are before the divider and Minus 1 for the
             * divider itself provides the relative index of the frequent contact being displayed.
             * Then add the dividerPostion to give the offset into the contacts cursor to get the
             * absolute index.
             */
            final int rowCount = getRowCount(mDividerPosition);
            contactIndex = position - rowCount + Math.min(mDividerPosition,
                    rowCount * mColumnCount);
            resultList.add(createContactEntryFromCursor(mContactCursor, contactIndex));
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
        return position != getRowCount(mDividerPosition);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int itemViewType = getItemViewType(position);

        ContactTileRow contactTileRowView = (ContactTileRow) convertView;
        ArrayList<ContactEntry> contactList = getItem(position);

        if (contactTileRowView == null) {
            // Creating new row if needed
            contactTileRowView = new ContactTileRow(mContext, itemViewType);
        }

        contactTileRowView.configureRow(contactList, position == getCount() - 1);
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
        if (position < getRowCount(mDividerPosition)) {
            return ViewTypes.TOP;
        } else {
            return ViewTypes.FREQUENT;
        }
    }

    /**
     * Returns the "frequent header" position. Only available when STREQUENT or
     * STREQUENT_PHONE_ONLY is used for its display type.
     */
    public int getFrequentHeaderPosition() {
        return getRowCount(mDividerPosition);
    }

    /**
     * Acts as a row item composed of {@link ContactTileView}
     *
     * TODO: FREQUENT doesn't really need it.  Just let {@link #getView} return
     */
    private class ContactTileRow extends FrameLayout {
        private int mItemViewType;
        private int mLayoutResId;
        private final int mRowPaddingStart;
        private final int mRowPaddingEnd;
        private final int mRowPaddingTop;
        private final int mRowPaddingBottom;

        public ContactTileRow(Context context, int itemViewType) {
            super(context);
            mItemViewType = itemViewType;
            mLayoutResId = getLayoutResourceId(mItemViewType);

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
        public void configureRow(ArrayList<ContactEntry> list, boolean isLastRow) {
            int columnCount = mItemViewType == ViewTypes.FREQUENT ? 1 : mColumnCount;

            // Adding tiles to row and filling in contact information
            for (int columnCounter = 0; columnCounter < columnCount; columnCounter++) {
                ContactEntry entry =
                        columnCounter < list.size() ? list.get(columnCounter) : null;
                addTileFromEntry(entry, columnCounter, isLastRow);
            }
        }

        private void addTileFromEntry(ContactEntry entry, int childIndex, boolean isLastRow) {
            final ContactTileView contactTile;

            if (getChildCount() <= childIndex) {

                contactTile = (ContactTileView) inflate(mContext, mLayoutResId, null);
                // Note: the layoutparam set here is only actually used for FREQUENT.
                // We override onMeasure() for STARRED and we don't care the layout param there.
                final Resources resources = mContext.getResources();
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);

                params.setMargins(
                        resources.getDimensionPixelSize(R.dimen.detail_item_side_margin),
                        0,
                        resources.getDimensionPixelSize(R.dimen.detail_item_side_margin),
                        0);
                contactTile.setLayoutParams(params);
                contactTile.setPhotoManager(mPhotoManager);
                contactTile.setListener(mListener);
                addView(contactTile);
            } else {
                contactTile = (ContactTileView) getChildAt(childIndex);
            }
            contactTile.loadFromContact(entry);
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
    }

    protected static class ViewTypes {
        public static final int COUNT = 2;
        public static final int FREQUENT = 0;
        public static final int TOP = 1;
    }
}
