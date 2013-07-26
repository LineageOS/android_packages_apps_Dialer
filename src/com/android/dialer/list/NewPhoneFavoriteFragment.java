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

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract.Directory;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactTileLoaderFactory;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.ContactTileView;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.dialer.NewDialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.calllog.NewCallLogAdapter;
import com.android.dialer.calllog.CallLogQueryHandler;

/**
 * Fragment for Phone UI's favorite screen.
 *
 * This fragment contains three kinds of contacts in one screen: "starred", "frequent", and "all"
 * contacts. To show them at once, this merges results from {@link com.android.contacts.common.list.ContactTileAdapter} and
 * {@link com.android.contacts.common.list.PhoneNumberListAdapter} into one unified list using {@link PhoneFavoriteMergedAdapter}.
 * A contact filter header is also inserted between those adapters' results.
 */
public class NewPhoneFavoriteFragment extends Fragment implements OnItemClickListener,
        CallLogQueryHandler.Listener, NewCallLogAdapter.CallFetcher {
    private static final String TAG = NewPhoneFavoriteFragment.class.getSimpleName();
    private static final boolean DEBUG = false;

    /**
     * Used with LoaderManager.
     */
    private static int LOADER_ID_CONTACT_TILE = 1;
    private static int LOADER_ID_ALL_CONTACTS = 2;

    public interface OnPhoneFavoriteFragmentStartedListener {
        public void onPhoneFavoriteFragmentStarted();
    }

    public interface Listener {
        public void onContactSelected(Uri contactUri);
        public void onCallNumberDirectly(String phoneNumber);
    }

    private class ContactTileLoaderListener implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onCreateLoader.");
            return ContactTileLoaderFactory.createStrequentPhoneOnlyLoader(getActivity());
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onLoadFinished");
            mContactTileAdapter.setContactCursor(data);

            if (mAllContactsForceReload) {
                mAllContactsAdapter.onDataReload();
                // Use restartLoader() to make LoaderManager to load the section again.
                getLoaderManager().restartLoader(
                        LOADER_ID_ALL_CONTACTS, null, mAllContactsLoaderListener);
            } else if (!mAllContactsLoaderStarted) {
                // Load "all" contacts if not loaded yet.
                getLoaderManager().initLoader(
                        LOADER_ID_ALL_CONTACTS, null, mAllContactsLoaderListener);
            }
            mAllContactsForceReload = false;
            mAllContactsLoaderStarted = true;

            // Show the filter header with "loading" state.
            mAccountFilterHeader.setVisibility(View.VISIBLE);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onLoaderReset. ");
        }
    }

    private class AllContactsLoaderListener implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            if (DEBUG) Log.d(TAG, "AllContactsLoaderListener#onCreateLoader");
            CursorLoader loader = new CursorLoader(getActivity(), null, null, null, null, null);
            mAllContactsAdapter.configureLoader(loader, Directory.DEFAULT);
            return loader;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (DEBUG) Log.d(TAG, "AllContactsLoaderListener#onLoadFinished");
            mAllContactsAdapter.changeCursor(0, data);
            mHandler.removeMessages(MESSAGE_SHOW_LOADING_EFFECT);
            mLoadingView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (DEBUG) Log.d(TAG, "AllContactsLoaderListener#onLoaderReset. ");
        }
    }

    private class ContactTileAdapterListener implements ContactTileView.Listener {
        @Override
        public void onContactSelected(Uri contactUri, Rect targetRect) {
            if (mListener != null) {
                mListener.onContactSelected(contactUri);
            }
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            if (mListener != null) {
                mListener.onCallNumberDirectly(phoneNumber);
            }
        }

        @Override
        public int getApproximateTileWidth() {
            return getView().getWidth() / mContactTileAdapter.getColumnCount();
        }
    }

    private class ContactsPreferenceChangeListener
            implements ContactsPreferences.ChangeListener {
        @Override
        public void onChange() {
            if (loadContactsPreferences()) {
                requestReloadAllContacts();
            }
        }
    }

    private class ScrollListener implements ListView.OnScrollListener {
        @Override
        public void onScroll(AbsListView view,
                int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            mActivityScrollListener.onListFragmentScrollStateChange(scrollState);
        }
    }

    private static final int MESSAGE_SHOW_LOADING_EFFECT = 1;
    private static final int LOADING_EFFECT_DELAY = 500;  // ms
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_SHOW_LOADING_EFFECT:
                    mLoadingView.setVisibility(View.VISIBLE);
                    break;
            }
        }
    };

    private Listener mListener;

    private OnListFragmentScrolledListener mActivityScrollListener;
    private NewPhoneFavoriteMergedAdapter mAdapter;
    private PhoneFavoritesTileAdapter mContactTileAdapter;
    private PhoneNumberListAdapter mAllContactsAdapter;

    private NewCallLogAdapter mCallLogAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;

    /**
     * true when the loader for {@link PhoneNumberListAdapter} has started already.
     */
    private boolean mAllContactsLoaderStarted;
    /**
     * true when the loader for {@link PhoneNumberListAdapter} must reload "all" contacts again.
     * It typically happens when {@link ContactsPreferences} has changed its settings
     * (display order and sort order)
     */
    private boolean mAllContactsForceReload;

    private ContactsPreferences mContactsPrefs;

    private TextView mEmptyView;
    private ListView mListView;
    /**
     * Layout containing {@link #mAccountFilterHeader}. Used to limit area being "pressed".
     */
    private FrameLayout mAccountFilterHeaderContainer;
    private View mAccountFilterHeader;

    /**
     * Layout used when contacts load is slower than expected and thus "loading" view should be
     * shown.
     */
    private View mLoadingView;

    private final ContactTileView.Listener mContactTileAdapterListener =
            new ContactTileAdapterListener();
    private final LoaderManager.LoaderCallbacks<Cursor> mContactTileLoaderListener =
            new ContactTileLoaderListener();
    private final LoaderManager.LoaderCallbacks<Cursor> mAllContactsLoaderListener =
            new AllContactsLoaderListener();
    private final ContactsPreferenceChangeListener mContactsPreferenceChangeListener =
            new ContactsPreferenceChangeListener();
    private final ScrollListener mScrollListener = new ScrollListener();

    private boolean mOptionsMenuHasFrequents;

    @Override
    public void onAttach(Activity activity) {
        if (DEBUG) Log.d(TAG, "onAttach()");
        super.onAttach(activity);

        mContactsPrefs = new ContactsPreferences(activity);

        // Construct two base adapters which will become part of PhoneFavoriteMergedAdapter.
        // We don't construct the resultant adapter at this moment since it requires LayoutInflater
        // that will be available on onCreateView().

        mContactTileAdapter = new PhoneFavoritesTileAdapter(activity, mContactTileAdapterListener,
                getResources().getInteger(R.integer.contact_tile_column_count_in_favorites_new),
                1);
        mContactTileAdapter.setPhotoLoader(ContactPhotoManager.getInstance(activity));

        // Setup the "all" adapter manually. See also the setup logic in ContactEntryListFragment.
        mAllContactsAdapter = new PhoneNumberListAdapter(activity);
        mAllContactsAdapter.setDisplayPhotos(true);
        mAllContactsAdapter.setQuickContactEnabled(true);
        mAllContactsAdapter.setSearchMode(false);
        mAllContactsAdapter.setIncludeProfile(false);
        mAllContactsAdapter.setSelectionVisible(false);
        mAllContactsAdapter.setDarkTheme(false);
        mAllContactsAdapter.setPhotoLoader(ContactPhotoManager.getInstance(activity));
        // Disable directory header.
        mAllContactsAdapter.setHasHeader(0, false);
        // Show A-Z section index.
        mAllContactsAdapter.setSectionHeaderDisplayEnabled(true);
        // Disable pinned header. It doesn't work with this fragment.
        mAllContactsAdapter.setPinnedPartitionHeadersEnabled(false);
        // Put photos on START (LEFT in LTR layout direction and RIGHT in RTL layout direction)
        // for consistency with "frequent" contacts section.
        mAllContactsAdapter.setPhotoPosition(ContactListItemView.getDefaultPhotoPosition(
                true /* opposite */ ));

        // Use Callable.CONTENT_URI which will include not only phone numbers but also SIP
        // addresses.
        mAllContactsAdapter.setUseCallableUri(true);

        mAllContactsAdapter.setContactNameDisplayOrder(mContactsPrefs.getDisplayOrder());
        mAllContactsAdapter.setSortOrder(mContactsPrefs.getSortOrder());
    }

    @Override
    public void onCreate(Bundle savedState) {
        if (DEBUG) Log.d(TAG, "onCreate()");
        super.onCreate(savedState);

        mCallLogQueryHandler = new CallLogQueryHandler(getActivity().getContentResolver(),
                this, 1);
        final String currentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());
        mCallLogAdapter = new NewCallLogAdapter(getActivity(), this,
                new ContactInfoHelper(getActivity(), currentCountryIso));
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        mCallLogQueryHandler.fetchCalls(CallLogQueryHandler.CALL_TYPE_ALL);
        mCallLogAdapter.setLoading(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View listLayout = inflater.inflate(
                R.layout.new_phone_favorites_fragment, container, false);

        mListView = (ListView) listLayout.findViewById(R.id.contact_tile_list);
        mListView.setItemsCanFocus(true);
        mListView.setOnItemClickListener(this);
        mListView.setVerticalScrollBarEnabled(false);
        mListView.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);

        // TODO krelease: Don't show this header anymore
        // Create the account filter header but keep it hidden until "all" contacts are loaded.
        mAccountFilterHeaderContainer = new FrameLayout(getActivity(), null);
        mAccountFilterHeader = inflater.inflate(R.layout.account_filter_header_for_phone_favorite,
                mListView, false);
        mAccountFilterHeaderContainer.addView(mAccountFilterHeader);

        mLoadingView = inflater.inflate(R.layout.phone_loading_contacts, mListView, false);

        mAdapter = new NewPhoneFavoriteMergedAdapter(getActivity(),
                mContactTileAdapter, mAccountFilterHeaderContainer, mCallLogAdapter, mLoadingView);

        mListView.setAdapter(mAdapter);

        mListView.setOnScrollListener(mScrollListener);
        mListView.setFastScrollEnabled(false);
        mListView.setFastScrollAlwaysVisible(false);

        mEmptyView = (TextView) listLayout.findViewById(R.id.contact_tile_list_empty);
        mEmptyView.setText(getString(R.string.listTotalAllContactsZero));
        mListView.setEmptyView(mEmptyView);

        return listLayout;
    }

    // TODO krelease: update the options menu when displaying the popup menu instead. We could
    // possibly get rid of this method entirely.
    private boolean isOptionsMenuChanged() {
        return mOptionsMenuHasFrequents != hasFrequents();
    }

    // TODO krelease: Configure the menu items properly. Since the menu items show up as a PopupMenu
    // rather than a normal actionbar menu, the initialization should be done there.
    /*
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        final MenuItem clearFrequents = menu.findItem(R.id.menu_clear_frequents);
        mOptionsMenuHasFrequents = hasFrequents();
        clearFrequents.setVisible(mOptionsMenuHasFrequents);
    }*/

    private boolean hasFrequents() {
        return mContactTileAdapter.getNumFrequents() > 0;
    }

    @Override
    public void onStart() {
        super.onStart();

        final Activity activity = getActivity();

        try {
            ((OnPhoneFavoriteFragmentStartedListener) activity).onPhoneFavoriteFragmentStarted();
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnPhoneFavoriteFragmentStartedListener");
        }

        try {
            mActivityScrollListener = (OnListFragmentScrolledListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnListFragmentScrolledListener");
        }
        mContactsPrefs.registerChangeListener(mContactsPreferenceChangeListener);

        // If ContactsPreferences has changed, we need to reload "all" contacts with the new
        // settings. If mAllContactsFoarceReload is already true, it should be kept.
        if (loadContactsPreferences()) {
            mAllContactsForceReload = true;
        }

        // Use initLoader() instead of restartLoader() to refraining unnecessary reload.
        // This method call implicitly assures ContactTileLoaderListener's onLoadFinished() will
        // be called, on which we'll check if "all" contacts should be reloaded again or not.
        getLoaderManager().initLoader(LOADER_ID_CONTACT_TILE, null, mContactTileLoaderListener);

        // Delay showing "loading" view until certain amount of time so that users won't see
        // instant flash of the view when the contacts load is fast enough.
        // This will be kept shown until both tile and all sections are loaded.
        mLoadingView.setVisibility(View.INVISIBLE);
        mHandler.sendEmptyMessageDelayed(MESSAGE_SHOW_LOADING_EFFECT, LOADING_EFFECT_DELAY);
    }

    @Override
    public void onStop() {
        super.onStop();
        mContactsPrefs.unregisterChangeListener();
    }

    /**
     * {@inheritDoc}
     *
     * This is only effective for elements provided by {@link #mContactTileAdapter}.
     * {@link #mContactTileAdapter} has its own logic for click events.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final int contactTileAdapterCount = mContactTileAdapter.getCount();
        if (position <= contactTileAdapterCount) {
            Log.e(TAG, "onItemClick() event for unexpected position. "
                    + "The position " + position + " is before \"all\" section. Ignored.");
        } else {
            final int localPosition = position - mContactTileAdapter.getCount() - 1;
            if (mListener != null) {
                mListener.onContactSelected(mAllContactsAdapter.getDataUri(localPosition));
            }
        }
    }

    private boolean loadContactsPreferences() {
        if (mContactsPrefs == null || mAllContactsAdapter == null) {
            return false;
        }

        boolean changed = false;
        final int currentDisplayOrder = mContactsPrefs.getDisplayOrder();
        if (mAllContactsAdapter.getContactNameDisplayOrder() != currentDisplayOrder) {
            mAllContactsAdapter.setContactNameDisplayOrder(currentDisplayOrder);
            changed = true;
        }

        final int currentSortOrder = mContactsPrefs.getSortOrder();
        if (mAllContactsAdapter.getSortOrder() != currentSortOrder) {
            mAllContactsAdapter.setSortOrder(currentSortOrder);
            changed = true;
        }

        return changed;
    }

    /**
     * Requests to reload "all" contacts. If the section is already loaded, this method will
     * force reloading it now. If the section isn't loaded yet, the actual load may be done later
     * (on {@link #onStart()}.
     */
    private void requestReloadAllContacts() {
        if (DEBUG) {
            Log.d(TAG, "requestReloadAllContacts()"
                    + " mAllContactsAdapter: " + mAllContactsAdapter
                    + ", mAllContactsLoaderStarted: " + mAllContactsLoaderStarted);
        }

        if (mAllContactsAdapter == null || !mAllContactsLoaderStarted) {
            // Remember this request until next load on onStart().
            mAllContactsForceReload = true;
            return;
        }

        if (DEBUG) Log.d(TAG, "Reload \"all\" contacts now.");

        mAllContactsAdapter.onDataReload();
        // Use restartLoader() to make LoaderManager to load the section again.
        getLoaderManager().restartLoader(LOADER_ID_ALL_CONTACTS, null, mAllContactsLoaderListener);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    // TODO krelease: Implement this
    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
    }

    @Override
    public void onCallsFetched(Cursor cursor) {
        mCallLogAdapter.setLoading(false);
        mCallLogAdapter.changeCursor(cursor);
        mAdapter.notifyDataSetChanged();
    }

    // TODO krelease: Implement this
    @Override
    public void fetchCalls() {
    }
}
