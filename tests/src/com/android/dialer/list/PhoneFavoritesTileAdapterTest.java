package com.android.dialer.list;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.ContactsContract.PinnedPositions;
import android.test.AndroidTestCase;

import com.android.contacts.common.ContactTileLoaderFactory;
import com.android.contacts.common.list.ContactEntry;
import com.android.dialer.list.PhoneFavoritesTileAdapter.OnDataSetChangedForAnimationListener;

import java.util.ArrayList;

public class PhoneFavoritesTileAdapterTest extends AndroidTestCase {
    private PhoneFavoritesTileAdapter mAdapter;
    private static final OnDataSetChangedForAnimationListener
            sOnDataSetChangedForAnimationListener = new OnDataSetChangedForAnimationListener() {
                @Override
                public void onDataSetChangedForAnimation(long... idsInPlace) {}

                @Override
                public void cacheOffsetsForDatasetChange() {}
            };

    /**
     * TODO: Add tests
     *
     * Test cases (various combinations of):
     * No pinned contacts
     * One pinned contact
     * Multiple pinned contacts with differing pinned positions
     * Multiple pinned contacts with conflicting pinned positions
     * Pinned contacts with pinned positions at the start, middle, end, and outside the list
     */
    public void testArrangeContactsByPinnedPosition() {

    }

    /**
     * TODO: Add tests
     *
     * This method assumes that contacts have already been reordered by
     * arrangeContactsByPinnedPosition, so we can test it with a less expansive set of test data.
     *
     * Test cases:
     * Pin a single contact at the start, middle and end of a completely unpinned list
     * Pin a single contact at the start, middle and end of a list with various numbers of
     * pinned contacts
     * Pin a single contact at the start, middle and end of a list where all contacts are pinned
     * such that contacts are forced to the left as necessary.
     */
    public void testGetReflowedPinnedPositions() {

    }

    public void testGetRowIndex_NoRowLimit() {
        mAdapter = getAdapterForTest(2, PhoneFavoritesTileAdapter.NO_ROW_LIMIT);
        assertEquals(0, mAdapter.getRowCount(0));
        assertEquals(1, mAdapter.getRowCount(1));
        assertEquals(1, mAdapter.getRowCount(2));
        assertEquals(2, mAdapter.getRowCount(4));
        assertEquals(4, mAdapter.getRowCount(7));
        assertEquals(100, mAdapter.getRowCount(199));

        mAdapter = getAdapterForTest(5, PhoneFavoritesTileAdapter.NO_ROW_LIMIT);
        assertEquals(0, mAdapter.getRowCount(0));
        assertEquals(1, mAdapter.getRowCount(1));
        assertEquals(1, mAdapter.getRowCount(3));
        assertEquals(1, mAdapter.getRowCount(5));
        assertEquals(2, mAdapter.getRowCount(7));
        assertEquals(2, mAdapter.getRowCount(10));
        assertEquals(40, mAdapter.getRowCount(199));
    }

    public void testGetItemId_NoRowLimit() {
        mAdapter = getAdapterForTest(2, PhoneFavoritesTileAdapter.NO_ROW_LIMIT);
        assertEquals(0, mAdapter.getItemId(0));
        assertEquals(1, mAdapter.getItemId(1));
        assertEquals(5, mAdapter.getItemId(5));
        assertEquals(10, mAdapter.getItemId(10));
    }

    public void testGetAdjustedItemId_NoRowLimit() {
        mAdapter = getAdapterForTest(2, PhoneFavoritesTileAdapter.NO_ROW_LIMIT);
        assertEquals(0, mAdapter.getAdjustedItemId(0));
        assertEquals(1, mAdapter.getAdjustedItemId(1));
        assertEquals(5, mAdapter.getAdjustedItemId(5));
        assertEquals(10, mAdapter.getAdjustedItemId(10));
    }

    public void testGetItem_NoRowLimit() {
        mAdapter = getAdapterForTest(2, PhoneFavoritesTileAdapter.NO_ROW_LIMIT);
        mAdapter.setContactCursor(getCursorForTest(5, 5));

        final ArrayList<ContactEntry> row1 = new ArrayList<ContactEntry> ();
        row1.add(getTestContactEntry(0, true));
        row1.add(getTestContactEntry(1, true));
        assertContactEntryRowsEqual(row1, mAdapter.getItem(0));

        final ArrayList<ContactEntry> row3 = new ArrayList<ContactEntry> ();
        row3.add(getTestContactEntry(4, true));
        row3.add(getTestContactEntry(5, false));
        assertContactEntryRowsEqual(row3, mAdapter.getItem(2));

        final ArrayList<ContactEntry> row5 = new ArrayList<ContactEntry> ();
        row5.add(getTestContactEntry(8, false));
        row5.add(getTestContactEntry(9, false));
        assertContactEntryRowsEqual(row5, mAdapter.getItem(4));
    }

    /**
     * Ensures that PhoneFavoritesTileAdapter returns true for hasStableIds. This is needed for
     * animation purposes.
     */
    public void testHasStableIds() {
        mAdapter = new PhoneFavoritesTileAdapter(getContext(), null, null, 2, 2);
        assertTrue(mAdapter.hasStableIds());
    }

    private PhoneFavoritesTileAdapter getAdapterForTest(int numCols, int numRows) {
        return new PhoneFavoritesTileAdapter(getContext(), null,
                sOnDataSetChangedForAnimationListener, numCols, numRows);
    }

    /**
     * Returns a cursor containing starred and frequent contacts for test purposes.
     *
     * @param numStarred Number of starred contacts in the cursor. Cannot be a negative number.
     * @param numFrequents Number of frequent contacts in the cursor. Cannot be a negative number.
     * @return Cursor containing the required number of rows, each representing one ContactEntry
     */
    private Cursor getCursorForTest(int numStarred, int numFrequents) {
        assertTrue(numStarred >= 0);
        assertTrue(numFrequents >= 0);
        final MatrixCursor c = new MatrixCursor(ContactTileLoaderFactory.COLUMNS_PHONE_ONLY);
        int countId = 0;

        // Add starred contact entries. These entries have the starred field set to 1 (true).
        // The only field that really matters for testing is the contact id.
        for (int i = 0; i < numStarred; i++) {
            c.addRow(new Object[] {countId, null, 1, null, null, 0, 0, null, 0,
                    PinnedPositions.UNPINNED, countId});
            countId++;
        }

        // Add frequent contact entries. These entries have the starred field set to 0 (false).
        for (int i = 0; i < numFrequents; i++) {
            c.addRow(new Object[] {countId, null, 0, null, null, 0, 0, null, 0,
                    PinnedPositions.UNPINNED, countId});
            countId++;
        }
        return c;
    }

    /**
     * Returns a ContactEntry with test data corresponding to the provided contact Id
     *
     * @param id Non-negative id
     * @return ContactEntry item used for testing
     */
    private ContactEntry getTestContactEntry(int id, boolean isFavorite) {
        ContactEntry contactEntry = new ContactEntry();
        contactEntry.id = id;
        contactEntry.isFavorite = isFavorite;
        return contactEntry;
    }

    private void assertContactEntryRowsEqual(ArrayList<ContactEntry> expected,
            ArrayList<ContactEntry> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < actual.size(); i++) {
            assertEquals(expected.get(i).id, actual.get(i).id);
            assertEquals(expected.get(i).isFavorite, actual.get(i).isFavorite);
        }
    }
}
