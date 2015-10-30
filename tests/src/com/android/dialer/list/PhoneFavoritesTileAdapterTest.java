package com.android.dialer.list;

import com.google.common.collect.Lists;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.ContactsContract.PinnedPositions;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.common.ContactTileLoaderFactory;
import com.android.contacts.common.list.ContactEntry;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.dialer.list.PhoneFavoritesTileAdapter.OnDataSetChangedForAnimationListener;

import junit.framework.Assert;

import java.util.ArrayList;

@SmallTest
public class PhoneFavoritesTileAdapterTest extends AndroidTestCase {

    private static final OnDataSetChangedForAnimationListener NOOP_ANIMATION_LISTENER =
            new OnDataSetChangedForAnimationListener() {
                @Override
                public void onDataSetChangedForAnimation(long... idsInPlace) {}

                @Override
                public void cacheOffsetsForDatasetChange() {}
            };

    private PhoneFavoritesTileAdapter mAdapter;

    @Override
    public void setUp() {
        this.mAdapter = new PhoneFavoritesTileAdapter(getContext(), null, NOOP_ANIMATION_LISTENER);
    }

    /**
     * For all arrangeContactsByPinnedPosition tests, the id for a particular ContactEntry
     * represents the index at which it should be located after calling
     * arrangeContactsByPinnedPosition
     */

    public void testArrangeContactsByPinnedPosition_NoPinned() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(0),
                getTestContactEntry(1), getTestContactEntry(2));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 3);
    }

    public void testArrangeContactsByPinnedPosition_NoPinned_RemoveDemoted() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(0),
                getTestContactEntry(-1, PinnedPositions.DEMOTED), getTestContactEntry(1));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 2);
    }

    public void testArrangeContactsByPinnedPosition_OnePinned_Beginning() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(1),
                getTestContactEntry(0, 1), getTestContactEntry(2));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 3);
    }

    public void testArrangeContactsByPinnedPosition_OnePinned_Middle() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(0),
                getTestContactEntry(1, 2), getTestContactEntry(2));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 3);
    }

    public void testArrangeContactsByPinnedPosition_OnePinned_End() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(0),
                getTestContactEntry(2, 3), getTestContactEntry(1));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 3);
    }

    public void testArrangeContactsByPinnedPosition_OnePinned_Outside() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(0),
                getTestContactEntry(2, 5), getTestContactEntry(1));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 3);
    }

    public void testArrangeContactsByPinnedPosition_OnePinned_RemoveDemoted() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(1, 2),
                getTestContactEntry(-1, PinnedPositions.DEMOTED), getTestContactEntry(0));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 2);
    }

    public void testArrangeContactsByPinnedPosition_TwoPinned_Split() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(0, 1),
                getTestContactEntry(1), getTestContactEntry(2, 3));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 3);
    }

    public void testArrangeContactsByPinnedPosition_TwoPinned_Adjacent() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(1, 2),
                getTestContactEntry(0), getTestContactEntry(2, 3));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 3);
    }

    public void testArrangeContactsByPinnedPosition_TwoPinned_Conflict_UnpinnedBefore() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(1, 2),
                getTestContactEntry(0), getTestContactEntry(2, 2));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 3);
    }

    public void testArrangeContactsByPinnedPosition_TwoPinned_Conflict_UnpinnedAfter() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(0, 1),
                getTestContactEntry(2), getTestContactEntry(1, 1));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 3);
    }

    public void testArrangeContactsByPinnedPosition_TwoPinned_Conflict_RemoveDemoted() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(1, 2),
                getTestContactEntry(-1, PinnedPositions.DEMOTED), getTestContactEntry(0, 2));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 2);
    }

    public void testArrangeContactsByPinnedPosition_AllPinned() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(1, 2),
                getTestContactEntry(0, 1), getTestContactEntry(2, 3));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 3);
    }

    public void testArrangeContactsByPinnedPosition_AllPinned_TwoConflicts_ConflictsFirst() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(1, 2),
                getTestContactEntry(0, 2), getTestContactEntry(2, 3));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 3);
    }

    public void testArrangeContactsByPinnedPosition_AllPinned_TwoConflicts_ConflictsLast() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(0, 2),
                getTestContactEntry(1, 3), getTestContactEntry(2, 3));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 3);
    }

    public void testArrangeContactsByPinnedPosition_AllPinned_AllConflicts() {
        ArrayList<ContactEntry> toArrange = Lists.newArrayList(getTestContactEntry(2, 3),
                getTestContactEntry(1, 3), getTestContactEntry(0, 3));
        mAdapter.arrangeContactsByPinnedPosition(toArrange);

        assertContactEntryListPositionsMatchId(toArrange, 3);
    }

    public void testArrangeContactsByPinnedPosition_All_Pinned_AllConflicts_SortNameAlternative() {
        Context context  = getContext();
        context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).edit()
                .putInt(ContactsPreferences.SORT_ORDER_KEY,
                        ContactsPreferences.SORT_ORDER_ALTERNATIVE)
                .commit();
        ArrayList<ContactEntry> actual = Lists.newArrayList(
                getTestContactEntry(1, 3, "2", "1"),
                getTestContactEntry(2, 3, "0", "2"),
                getTestContactEntry(0, 3, "1", "0")
        );
        mAdapter.arrangeContactsByPinnedPosition(actual);

        assertContactEntryListPositionsMatchId(actual, 3);
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

    public void testSetContactCursor_DisplayNameOrder_Primary() {
        setNameDisplayOrder(getContext(), ContactsPreferences.DISPLAY_ORDER_PRIMARY);
        Cursor testCursor = getCursorForTest(1, 0);
        mAdapter.setContactCursor(testCursor);
        Assert.assertEquals(1, mAdapter.mContactEntries.size());
        Assert.assertEquals(ContactsPreferences.DISPLAY_ORDER_PRIMARY,
                mAdapter.mContactEntries.get(0).nameDisplayOrder);
    }

    public void testSetContactCursor_DisplayNameOrder_Alternative() {
        setNameDisplayOrder(getContext(), ContactsPreferences.DISPLAY_ORDER_ALTERNATIVE);
        Cursor testCursor = getCursorForTest(1, 0);
        mAdapter.setContactCursor(testCursor);
        Assert.assertEquals(1, mAdapter.mContactEntries.size());
        Assert.assertEquals(ContactsPreferences.DISPLAY_ORDER_ALTERNATIVE,
                mAdapter.mContactEntries.get(0).nameDisplayOrder);
    }

    public void testSetContactCursor_DisplayNameOrder_Changed() {
        setNameDisplayOrder(getContext(), ContactsPreferences.DISPLAY_ORDER_PRIMARY);
        Cursor testCursor = getCursorForTest(1, 0);
        mAdapter.setContactCursor(testCursor);
        Assert.assertEquals(1, mAdapter.mContactEntries.size());
        Assert.assertEquals(ContactsPreferences.DISPLAY_ORDER_PRIMARY,
                mAdapter.mContactEntries.get(0).nameDisplayOrder);

        setNameDisplayOrder(getContext(), ContactsPreferences.DISPLAY_ORDER_ALTERNATIVE);
        mAdapter.refreshContactsPreferences();
        mAdapter.setContactCursor(testCursor);
        Assert.assertEquals(1, mAdapter.mContactEntries.size());
        Assert.assertEquals(ContactsPreferences.DISPLAY_ORDER_ALTERNATIVE,
                mAdapter.mContactEntries.get(0).nameDisplayOrder);
    }

    private void setNameDisplayOrder(Context context, int displayOrder) {
        context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).edit().putInt(
                ContactsPreferences.DISPLAY_ORDER_KEY, displayOrder).commit();
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
                    PinnedPositions.UNPINNED, countId, null});
            countId++;
        }

        // Add frequent contact entries. These entries have the starred field set to 0 (false).
        for (int i = 0; i < numFrequents; i++) {
            c.addRow(new Object[] {countId, null, 0, null, null, 0, 0, null, 0,
                    PinnedPositions.UNPINNED, countId, null});
            countId++;
        }
        return c;
    }

    private ContactEntry getTestContactEntry(int id) {
        return getTestContactEntry(id, PinnedPositions.UNPINNED);
    }

    private ContactEntry getTestContactEntry(int id, int pinned) {
        return getTestContactEntry(id, pinned, String.valueOf(id), String.valueOf(id));
    }

    private ContactEntry getTestContactEntry(int id, int pinned, String namePrimaryAppend,
            String nameAlternativeAppend) {
        ContactEntry contactEntry = new ContactEntry();
        contactEntry.id = id;
        contactEntry.pinned = pinned;
        contactEntry.namePrimary = namePrimaryAppend;
        contactEntry.nameAlternative = nameAlternativeAppend;
        return contactEntry;
    }

    private void assertContactEntryListPositionsMatchId(ArrayList<ContactEntry> contactEntries,
            int expectedSize) {
        Assert.assertEquals(expectedSize, contactEntries.size());
        for (int i = 0; i < expectedSize; ++i) {
            Assert.assertEquals(i, contactEntries.get(i).id);
        }
    }
}
