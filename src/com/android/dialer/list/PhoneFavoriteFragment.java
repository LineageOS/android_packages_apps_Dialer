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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import com.android.contacts.common.list.ContactTileView;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.R;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.calllog.CallLogAdapter;
import com.android.dialer.calllog.CallLogQueryHandler;

/**
 * Fragment for Phone UI's favorite screen.
 *
 * This fragment contains three kinds of contacts in one screen: "starred", "frequent", and "all"
 * contacts. To show them at once, this merges results from {@link com.android.contacts.common.list.ContactTileAdapter} and
 * {@link com.android.contacts.common.list.PhoneNumberListAdapter} into one unified list using {@link PhoneFavoriteMergedAdapter}.
 * A contact filter header is also inserted between those adapters' results.
 */
public class PhoneFavoriteFragment extends Fragment implements OnItemClickListener,
        CallLogQueryHandler.Listener, CallLogAdapter.CallFetcher {
    private static final String TAG = PhoneFavoriteFragment.class.getSimpleName();
    private static final boolean DEBUG = false;

    /**
     * Used with LoaderManager.
     */
    private static int LOADER_ID_CONTACT_TILE = 1;

    public interface OnPhoneFavoriteFragmentStartedListener {
        public void onPhoneFavoriteFragmentStarted();
    }

    public interface OnShowAllContactsListener {
        public void onShowAllContacts();
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
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onLoaderReset. ");
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

    private Listener mListener;

    private OnListFragmentScrolledListener mActivityScrollListener;
    private OnShowAllContactsListener mShowAllContactsListener;
    private PhoneFavoriteMergedAdapter mAdapter;
    private PhoneFavoritesTileAdapter mContactTileAdapter;

    private CallLogAdapter mCallLogAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;

    private TextView mEmptyView;
    private ListView mListView;
    private View mShowAllContactsButton;

    /**
     * Layout used when contacts load is slower than expected and thus "loading" view should be
     * shown.
     */
    private View mLoadingView;

    private final ContactTileView.Listener mContactTileAdapterListener =
            new ContactTileAdapterListener();
    private final LoaderManager.LoaderCallbacks<Cursor> mContactTileLoaderListener =
            new ContactTileLoaderListener();
    private final ScrollListener mScrollListener = new ScrollListener();

    @Override
    public void onAttach(Activity activity) {
        if (DEBUG) Log.d(TAG, "onAttach()");
        super.onAttach(activity);

        // Construct two base adapters which will become part of PhoneFavoriteMergedAdapter.
        // We don't construct the resultant adapter at this moment since it requires LayoutInflater
        // that will be available on onCreateView().

        mContactTileAdapter = new PhoneFavoritesTileAdapter(activity, mContactTileAdapterListener,
                getResources().getInteger(R.integer.contact_tile_column_count_in_favorites_new),
                1);
        mContactTileAdapter.setPhotoLoader(ContactPhotoManager.getInstance(activity));
    }

    @Override
    public void onCreate(Bundle savedState) {
        if (DEBUG) Log.d(TAG, "onCreate()");
        super.onCreate(savedState);

        mCallLogQueryHandler = new CallLogQueryHandler(getActivity().getContentResolver(),
                this, 1);
        final String currentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());
        mCallLogAdapter = new CallLogAdapter(getActivity(), this,
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
                R.layout.phone_favorites_fragment, container, false);

        mListView = (ListView) listLayout.findViewById(R.id.contact_tile_list);
        mListView.setItemsCanFocus(true);
        mListView.setOnItemClickListener(this);
        mListView.setVerticalScrollBarEnabled(false);
        mListView.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);

        mLoadingView = inflater.inflate(R.layout.phone_loading_contacts, mListView, false);
        mShowAllContactsButton = inflater.inflate(R.layout.show_all_contact_button, mListView,
                false);
        mShowAllContactsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                showAllContacts();
            }
        });

        mAdapter = new PhoneFavoriteMergedAdapter(getActivity(), mContactTileAdapter,
                mCallLogAdapter, mLoadingView, mShowAllContactsButton);

        mListView.setAdapter(mAdapter);

        mListView.setOnScrollListener(mScrollListener);
        mListView.setFastScrollEnabled(false);
        mListView.setFastScrollAlwaysVisible(false);

        mEmptyView = (TextView) listLayout.findViewById(R.id.contact_tile_list_empty);
        mEmptyView.setText(getString(R.string.listTotalAllContactsZero));
        mListView.setEmptyView(mEmptyView);

        return listLayout;
    }

    public boolean hasFrequents() {
        if (mContactTileAdapter == null) return false;
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

        try {
            mShowAllContactsListener = (OnShowAllContactsListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnShowAllContactsListener");
        }

        // Use initLoader() instead of restartLoader() to refraining unnecessary reload.
        // This method call implicitly assures ContactTileLoaderListener's onLoadFinished() will
        // be called, on which we'll check if "all" contacts should be reloaded again or not.
        getLoaderManager().initLoader(LOADER_ID_CONTACT_TILE, null, mContactTileLoaderListener);
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
        }
    }

    /**
     * Gets called when user click on the show all contacts button.
     */
    private void showAllContacts() {
        mShowAllContactsListener.onShowAllContacts();
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
