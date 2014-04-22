package com.android.dialer.list;

import android.animation.LayoutTransition;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.android.contacts.common.GeoUtil;
import com.android.dialer.DialtactsActivity;
import android.view.View.OnClickListener;

import com.android.dialer.R;
import com.android.dialer.calllog.CallLogAdapter;
import com.android.dialer.calllog.CallLogFragment;
import com.android.dialer.calllog.CallLogQuery;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialerbind.ObjectFactory;

/**
 * Fragment that is used as the main screen of the Dialer.
 *
 * Contains a ViewPager that contains various contact lists like the Speed Dial list and the
 * All Contacts list. This will also eventually contain the logic that allows sliding the
 * ViewPager containing the lists up above the shortcut cards and pin it against the top of the
 * screen.
 */
public class ListsFragment extends Fragment implements CallLogQueryHandler.Listener,
        CallLogAdapter.CallFetcher {

    private static final int TAB_INDEX_SPEED_DIAL = 0;
    private static final int TAB_INDEX_RECENTS = 1;
    private static final int TAB_INDEX_ALL_CONTACTS = 2;

    private static final int TAB_INDEX_COUNT = 3;

    private static final int MAX_RECENTS_ENTRIES = 20;
    // Oldest recents entry to display is 2 weeks old.
    private static final long OLDEST_RECENTS_DATE = 1000L * 60 * 60 * 24 * 14;

    private static final String KEY_LAST_DISMISSED_CALL_SHORTCUT_DATE =
            "key_last_dismissed_call_shortcut_date";

    // Used with LoaderManager
    private static int MISSED_CALL_LOADER = 1;

    public interface HostInterface {
        public void showCallHistory();
    }

    private ViewPager mViewPager;
    private ViewPagerAdapter mViewPagerAdapter;
    private PhoneFavoriteFragment mSpeedDialFragment;
    private CallLogFragment mRecentsFragment;
    private AllContactsFragment mAllContactsFragment;

    private String[] mTabTitles;

    private PhoneFavoriteMergedAdapter mMergedAdapter;
    private CallLogAdapter mCallLogAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;

    /**
     * Call shortcuts older than this date (persisted in shared preferences) will not show up in
     * at the top of the screen
     */
    private long mLastCallShortcutDate = 0;

    /**
     * The date of the current call shortcut that is showing on screen.
     */
    private long mCurrentCallShortcutDate = 0;

    private class MissedCallLogLoaderListener implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            final Uri uri = CallLog.Calls.CONTENT_URI;
            final String[] projection = new String[] {CallLog.Calls.TYPE};
            final String selection = CallLog.Calls.TYPE + " = " + CallLog.Calls.MISSED_TYPE +
                    " AND " + CallLog.Calls.IS_READ + " = 0";
            return new CursorLoader(getActivity(), uri, projection, selection, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor data) {
            mCallLogAdapter.setMissedCalls(data);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> cursorLoader) {
        }
    }

    public class ViewPagerAdapter extends FragmentPagerAdapter {
        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case TAB_INDEX_SPEED_DIAL:
                    mSpeedDialFragment = new PhoneFavoriteFragment();
                    return mSpeedDialFragment;
                case TAB_INDEX_RECENTS:
                    mRecentsFragment = new CallLogFragment(CallLogQueryHandler.CALL_TYPE_ALL,
                            MAX_RECENTS_ENTRIES, System.currentTimeMillis() - OLDEST_RECENTS_DATE);

                    /*
                     * Provide mViewPager as a parent viewgroup for the inflation of the footer,
                     * to ensure that the footer view is inflated with the correct LayoutParams.
                     * If root is null in
                     * inflate(XmlPullParser parser, ViewGroup root, boolean attachToRoot),
                     * the layout parameters specified in R.layout.recents_list_footer are not
                     * correctly applied. The footer view is ultimately not attached to mViewPager.
                     */
                    final View viewFullHistoryFooter = getActivity().getLayoutInflater().inflate(
                            R.layout.recents_list_footer, mViewPager, false);
                    viewFullHistoryFooter.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ((HostInterface) getActivity()).showCallHistory();
                        }
                    });
                    mRecentsFragment.setFooterView(viewFullHistoryFooter);
                    return mRecentsFragment;
                case TAB_INDEX_ALL_CONTACTS:
                    mAllContactsFragment = new AllContactsFragment();
                    return mAllContactsFragment;
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public int getCount() {
            return TAB_INDEX_COUNT;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mTabTitles[position];
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCallLogQueryHandler = new CallLogQueryHandler(getActivity().getContentResolver(),
                this, 1);
        final String currentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());
        mCallLogAdapter = ObjectFactory.newCallLogAdapter(getActivity(), this,
                new ContactInfoHelper(getActivity(), currentCountryIso), false, false);

        mMergedAdapter = new PhoneFavoriteMergedAdapter(getActivity(), this, null,
                mCallLogAdapter, null, null, null);
    }

    @Override
    public void onStart() {
        super.onStart();
        getLoaderManager().initLoader(MISSED_CALL_LOADER, null, new MissedCallLogLoaderListener());
    }

    @Override
    public void onResume() {
        super.onResume();
        final SharedPreferences prefs = getActivity().getSharedPreferences(
                DialtactsActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        mLastCallShortcutDate = prefs.getLong(KEY_LAST_DISMISSED_CALL_SHORTCUT_DATE, 0);

        fetchCalls();
        mCallLogAdapter.setLoading(true);
    }

    @Override
    public void onPause() {
        // Wipe the cache to refresh the call shortcut item. This is not that expensive because
        // it only contains one item.
        mCallLogAdapter.invalidateCache();
        super.onPause();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View parentView = inflater.inflate(R.layout.lists_fragment, container, false);
        mViewPager = (ViewPager) parentView.findViewById(R.id.lists_pager);
        mViewPagerAdapter = new ViewPagerAdapter(getChildFragmentManager());
        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.setOffscreenPageLimit(2);

        mTabTitles = new String[TAB_INDEX_COUNT];
        mTabTitles[TAB_INDEX_SPEED_DIAL] = getResources().getString(R.string.tab_speed_dial);
        mTabTitles[TAB_INDEX_RECENTS] = getResources().getString(R.string.tab_recents);
        mTabTitles[TAB_INDEX_ALL_CONTACTS] = getResources().getString(R.string.tab_all_contacts);

        ViewPagerTabs tabs = (ViewPagerTabs) parentView.findViewById(R.id.lists_pager_header);
        tabs.setViewPager(mViewPager);

        final ListView shortcutCardsListView =
                (ListView) parentView.findViewById(R.id.shortcut_card_list);
        shortcutCardsListView.setAdapter(mMergedAdapter);

        LayoutTransition transition = ((LinearLayout) parentView).getLayoutTransition();
        // Turns on animations for all types of layout changes so that they occur for
        // height changes.
        transition.enableTransitionType(LayoutTransition.CHANGING);
        return parentView;
    }

    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        // no-op
    }

    @Override
    public void onCallsFetched(Cursor cursor) {
        mCallLogAdapter.setLoading(false);

        // Save the date of the most recent call log item
        if (cursor != null && cursor.moveToFirst()) {
            mCurrentCallShortcutDate = cursor.getLong(CallLogQuery.DATE);
        }

        mCallLogAdapter.changeCursor(cursor);
        mMergedAdapter.notifyDataSetChanged();
    }

    @Override
    public void fetchCalls() {
        mCallLogQueryHandler.fetchCalls(CallLogQueryHandler.CALL_TYPE_ALL, mLastCallShortcutDate);
    }

    public void dismissShortcut(View view) {
        mLastCallShortcutDate = mCurrentCallShortcutDate;
        final SharedPreferences prefs = view.getContext().getSharedPreferences(
                DialtactsActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_DISMISSED_CALL_SHORTCUT_DATE, mLastCallShortcutDate)
                .apply();
        fetchCalls();
    }
}
