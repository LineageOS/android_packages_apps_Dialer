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
