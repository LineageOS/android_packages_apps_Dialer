package com.android.dialer.list;

import android.animation.LayoutTransition;
import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.list.ViewPagerTabs;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogAdapter;
import com.android.dialer.calllog.CallLogFragment;
import com.android.dialer.calllog.CallLogQuery;
import com.android.dialer.calllog.CallLogQueryHandler;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.list.ShortcutCardsAdapter.SwipeableShortcutCard;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.widget.OverlappingPaneLayout;
import com.android.dialer.widget.OverlappingPaneLayout.PanelSlideCallbacks;
import com.android.dialerbind.analytics.AnalyticsFragment;
import com.android.dialerbind.ObjectFactory;

import java.util.ArrayList;

/**
 * Fragment that is used as the main screen of the Dialer.
 *
 * Contains a ViewPager that contains various contact lists like the Speed Dial list and the
 * All Contacts list. This will also eventually contain the logic that allows sliding the
 * ViewPager containing the lists up above the shortcut cards and pin it against the top of the
 * screen.
 */
public class ListsFragment extends AnalyticsFragment implements CallLogQueryHandler.Listener,
        CallLogAdapter.CallFetcher, ViewPager.OnPageChangeListener {

    private static final boolean DEBUG = DialtactsActivity.DEBUG;
    private static final String TAG = "ListsFragment";

    public static final int TAB_INDEX_SPEED_DIAL = 0;
    public static final int TAB_INDEX_RECENTS = 1;
    public static final int TAB_INDEX_ALL_CONTACTS = 2;

    public static final int TAB_INDEX_COUNT = 3;

    private static final int MAX_RECENTS_ENTRIES = 20;
    // Oldest recents entry to display is 2 weeks old.
    private static final long OLDEST_RECENTS_DATE = 1000L * 60 * 60 * 24 * 14;

    private static final String KEY_LAST_DISMISSED_CALL_SHORTCUT_DATE =
            "key_last_dismissed_call_shortcut_date";

    public static final float REMOVE_VIEW_SHOWN_ALPHA = 0.5f;
    public static final float REMOVE_VIEW_HIDDEN_ALPHA = 1;

    public interface HostInterface {
        public void showCallHistory();
        public int getActionBarHeight();
        public void setActionBarHideOffset(int offset);
    }

    private ActionBar mActionBar;
    private ViewPager mViewPager;
    private ViewPagerTabs mViewPagerTabs;
    private ViewPagerAdapter mViewPagerAdapter;
    private ListView mShortcutCardsListView;
    private RemoveView mRemoveView;
    private View mRemoveViewContent;
    private SpeedDialFragment mSpeedDialFragment;
    private CallLogFragment mRecentsFragment;
    private AllContactsFragment mAllContactsFragment;
    private ArrayList<OnPageChangeListener> mOnPageChangeListeners =
            new ArrayList<OnPageChangeListener>();

    private String[] mTabTitles;

    private ShortcutCardsAdapter mMergedAdapter;
    private CallLogAdapter mCallLogAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;

    private boolean mIsPanelOpen = true;

    /**
     * Call shortcuts older than this date (persisted in shared preferences) will not show up in
     * at the top of the screen
     */
    private long mLastCallShortcutDate = 0;

    /**
     * The date of the current call shortcut that is showing on screen.
     */
    private long mCurrentCallShortcutDate = 0;

    private PanelSlideCallbacks mPanelSlideCallbacks = new PanelSlideCallbacks() {
        @Override
        public void onPanelSlide(View panel, float slideOffset) {
            // For every 1 percent that the panel is slid upwards, clip 1 percent off the top
            // edge of the shortcut card, to achieve the animated effect of the shortcut card
            // being pushed out of view when the panel is slid upwards. slideOffset is 1 when
            // the shortcut card is fully exposed, and 0 when completely hidden.
            float ratioCardHidden = (1 - slideOffset);
            if (mShortcutCardsListView.getChildCount() > 0) {
                final SwipeableShortcutCard v =
                        (SwipeableShortcutCard) mShortcutCardsListView.getChildAt(0);
                v.clipCard(ratioCardHidden);
            }

            if (mActionBar != null) {
                // Amount of available space that is not being hidden by the bottom pane
                final int topPaneHeight = (int) (slideOffset * mShortcutCardsListView.getHeight());

                final int availableActionBarHeight =
                        Math.min(mActionBar.getHeight(), topPaneHeight);
                ((HostInterface) getActivity()).setActionBarHideOffset(
                        mActionBar.getHeight() - availableActionBarHeight);

                if (!mActionBar.isShowing()) {
                    mActionBar.show();
                }
            }
        }

        @Override
        public void onPanelOpened(View panel) {
            if (DEBUG) {
                Log.d(TAG, "onPanelOpened");
            }
            mIsPanelOpen = true;
        }

        @Override
        public void onPanelClosed(View panel) {
            if (DEBUG) {
                Log.d(TAG, "onPanelClosed");
            }
            mIsPanelOpen = false;
        }

        @Override
        public void onPanelFlingReachesEdge(int velocityY) {
            if (getCurrentListView() != null) {
                getCurrentListView().fling(velocityY);
            }
        }

        @Override
        public boolean isScrollableChildUnscrolled() {
            final AbsListView listView = getCurrentListView();
            return listView != null && (listView.getChildCount() == 0
                    || listView.getChildAt(0).getTop() == listView.getPaddingTop());
        }
    };

    private AbsListView getCurrentListView() {
        final int position = mViewPager.getCurrentItem();
        switch (getRtlPosition(position)) {
            case TAB_INDEX_SPEED_DIAL:
                return mSpeedDialFragment == null ? null : mSpeedDialFragment.getListView();
            case TAB_INDEX_RECENTS:
                return mRecentsFragment == null ? null : mRecentsFragment.getListView();
            case TAB_INDEX_ALL_CONTACTS:
                return mAllContactsFragment == null ? null : mAllContactsFragment.getListView();
        }
        throw new IllegalStateException("No fragment at position " + position);
    }

    public class ViewPagerAdapter extends FragmentPagerAdapter {
        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public long getItemId(int position) {
            return getRtlPosition(position);
        }

        @Override
        public Fragment getItem(int position) {
            switch (getRtlPosition(position)) {
                case TAB_INDEX_SPEED_DIAL:
                    mSpeedDialFragment = new SpeedDialFragment();
                    return mSpeedDialFragment;
                case TAB_INDEX_RECENTS:
                    mRecentsFragment = new CallLogFragment(CallLogQueryHandler.CALL_TYPE_ALL,
                            MAX_RECENTS_ENTRIES, System.currentTimeMillis() - OLDEST_RECENTS_DATE);
                    mRecentsFragment.setHasFooterView(true);
                    return mRecentsFragment;
                case TAB_INDEX_ALL_CONTACTS:
                    mAllContactsFragment = new AllContactsFragment();
                    return mAllContactsFragment;
            }
            throw new IllegalStateException("No fragment at position " + position);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            // On rotation the FragmentManager handles rotation. Therefore getItem() isn't called.
            // Copy the fragments that the FragmentManager finds so that we can store them in
            // instance variables for later.
            final Fragment fragment =
                    (Fragment) super.instantiateItem(container, position);
            if (fragment instanceof SpeedDialFragment) {
                mSpeedDialFragment = (SpeedDialFragment) fragment;
            } else if (fragment instanceof CallLogFragment) {
                mRecentsFragment = (CallLogFragment) fragment;
            } else if (fragment instanceof AllContactsFragment) {
                mAllContactsFragment = (AllContactsFragment) fragment;
            }
            return fragment;
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
                new ContactInfoHelper(getActivity(), currentCountryIso), null, null, false);

        mMergedAdapter = new ShortcutCardsAdapter(getActivity(), this, mCallLogAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        final SharedPreferences prefs = getActivity().getSharedPreferences(
                DialtactsActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        mLastCallShortcutDate = prefs.getLong(KEY_LAST_DISMISSED_CALL_SHORTCUT_DATE, 0);
        mActionBar = getActivity().getActionBar();
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
    public void onDestroy() {
        mCallLogAdapter.stopRequestProcessing();
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View parentView = inflater.inflate(R.layout.lists_fragment, container, false);
        mViewPager = (ViewPager) parentView.findViewById(R.id.lists_pager);
        mViewPagerAdapter = new ViewPagerAdapter(getChildFragmentManager());
        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.setOffscreenPageLimit(2);
        mViewPager.setOnPageChangeListener(this);
        mViewPager.setCurrentItem(getRtlPosition(TAB_INDEX_SPEED_DIAL));

        mTabTitles = new String[TAB_INDEX_COUNT];
        mTabTitles[TAB_INDEX_SPEED_DIAL] = getResources().getString(R.string.tab_speed_dial);
        mTabTitles[TAB_INDEX_RECENTS] = getResources().getString(R.string.tab_recents);
        mTabTitles[TAB_INDEX_ALL_CONTACTS] = getResources().getString(R.string.tab_all_contacts);

        mViewPagerTabs = (ViewPagerTabs) parentView.findViewById(R.id.lists_pager_header);
        mViewPagerTabs.setViewPager(mViewPager);
        addOnPageChangeListener(mViewPagerTabs);

        mShortcutCardsListView = (ListView) parentView.findViewById(R.id.shortcut_card_list);
        mShortcutCardsListView.setAdapter(mMergedAdapter);

        mRemoveView = (RemoveView) parentView.findViewById(R.id.remove_view);
        mRemoveViewContent = parentView.findViewById(R.id.remove_view_content);

        setupPaneLayout((OverlappingPaneLayout) parentView);

        return parentView;
    }

    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        // no-op
    }

    @Override
    public boolean onCallsFetched(Cursor cursor) {
        mCallLogAdapter.setLoading(false);

        // Save the date of the most recent call log item
        if (cursor != null && cursor.moveToFirst()) {
            mCurrentCallShortcutDate = cursor.getLong(CallLogQuery.DATE);
        }

        mCallLogAdapter.changeCursor(cursor);
        mMergedAdapter.notifyDataSetChanged();
        // Return true; took ownership of cursor
        return true;
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

    public void addOnPageChangeListener(OnPageChangeListener onPageChangeListener) {
        if (!mOnPageChangeListeners.contains(onPageChangeListener)) {
            mOnPageChangeListeners.add(onPageChangeListener);
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageScrolled(position, positionOffset,
                    positionOffsetPixels);
        }
    }

    @Override
    public void onPageSelected(int position) {
        if (position == TAB_INDEX_SPEED_DIAL && mSpeedDialFragment != null) {
            mSpeedDialFragment.sendScreenView();
        } else if (position == TAB_INDEX_RECENTS && mRecentsFragment != null) {
            mRecentsFragment.sendScreenView();
        } else if (position == TAB_INDEX_ALL_CONTACTS && mAllContactsFragment != null) {
            mAllContactsFragment.sendScreenView();
        }
        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageSelected(position);
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        final int count = mOnPageChangeListeners.size();
        for (int i = 0; i < count; i++) {
            mOnPageChangeListeners.get(i).onPageScrollStateChanged(state);
        }
    }

    public void showRemoveView(boolean show) {
        mRemoveViewContent.setVisibility(show ? View.VISIBLE : View.GONE);
        mRemoveView.setAlpha(show ? 0 : 1);
        mRemoveView.animate().alpha(show ? 1 : 0).start();

        if (mShortcutCardsListView.getChildCount() > 0) {
            View v = mShortcutCardsListView.getChildAt(0);
            v.animate().withLayer()
                    .alpha(show ? REMOVE_VIEW_SHOWN_ALPHA : REMOVE_VIEW_HIDDEN_ALPHA)
                    .start();
        }
    }

    public boolean shouldShowActionBar() {
        return mIsPanelOpen && mActionBar != null;
    }

    public boolean isPaneOpen() {
        return mIsPanelOpen;
    }

    private void setupPaneLayout(OverlappingPaneLayout paneLayout) {
        // TODO: Remove the notion of a capturable view. The entire view be slideable, once
        // the framework better supports nested scrolling.
        paneLayout.setCapturableView(mViewPagerTabs);
        paneLayout.openPane();
        paneLayout.setPanelSlideCallbacks(mPanelSlideCallbacks);
        paneLayout.setIntermediatePinnedOffset(
                ((HostInterface) getActivity()).getActionBarHeight());

        LayoutTransition transition = paneLayout.getLayoutTransition();
        // Turns on animations for all types of layout changes so that they occur for
        // height changes.
        transition.enableTransitionType(LayoutTransition.CHANGING);
    }

    public SpeedDialFragment getSpeedDialFragment() {
        return mSpeedDialFragment;
    }

    public RemoveView getRemoveView() {
        return mRemoveView;
    }

    public int getRtlPosition(int position) {
        if (DialerUtils.isRtl()) {
            return TAB_INDEX_COUNT - 1 - position;
        }
        return position;
    }
}
