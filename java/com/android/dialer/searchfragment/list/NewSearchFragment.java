/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.dialer.searchfragment.list;

import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.contacts.common.extensions.PhoneDirectoryExtenderAccessor;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.searchfragment.cp2.SearchContactsCursorLoader;
import com.android.dialer.searchfragment.nearbyplaces.NearbyPlacesCursorLoader;

/** Fragment used for searching contacts. */
public final class NewSearchFragment extends Fragment implements LoaderCallbacks<Cursor> {

  // Since some of our queries can generate network requests, we should delay them until the user
  // stops typing to prevent generating too much network traffic.
  private static final int NETWORK_SEARCH_DELAY_MILLIS = 300;

  private static final int CONTACTS_LOADER_ID = 0;
  private static final int NEARBY_PLACES_ID = 1;

  private RecyclerView recyclerView;
  private SearchAdapter adapter;
  private String query;

  private final Runnable loadNearbyPlacesRunnable =
      () -> getLoaderManager().restartLoader(NEARBY_PLACES_ID, null, this);

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle bundle) {
    getLoaderManager().initLoader(0, null, this);
    View view = inflater.inflate(R.layout.fragment_search, parent, false);
    adapter = new SearchAdapter(getContext());
    recyclerView = view.findViewById(R.id.recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    recyclerView.setAdapter(adapter);

    getLoaderManager().initLoader(CONTACTS_LOADER_ID, null, this);
    loadNearbyPlacesCursor();
    return view;
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
    // TODO add enterprise loader
    if (id == CONTACTS_LOADER_ID) {
      return new SearchContactsCursorLoader(getContext());
    } else if (id == NEARBY_PLACES_ID) {
      return new NearbyPlacesCursorLoader(getContext(), query);
    } else {
      throw new IllegalStateException("Invalid loader id: " + id);
    }
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    if (loader instanceof SearchContactsCursorLoader) {
      adapter.setContactsCursor(cursor);
    } else if (loader instanceof NearbyPlacesCursorLoader) {
      adapter.setNearbyPlacesCursor(cursor);
    } else {
      throw new IllegalStateException("Invalid loader: " + loader);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    adapter.clear();
    recyclerView.setAdapter(null);
  }

  public void setQuery(String query) {
    this.query = query;
    if (adapter != null) {
      adapter.setQuery(query);
      loadNearbyPlacesCursor();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    // close adapters
    adapter.setNearbyPlacesCursor(null);
    adapter.setContactsCursor(null);
    ThreadUtil.getUiThreadHandler().removeCallbacks(loadNearbyPlacesRunnable);
  }

  private void loadNearbyPlacesCursor() {
    // Cancel existing load if one exists.
    ThreadUtil.getUiThreadHandler().removeCallbacks(loadNearbyPlacesRunnable);

    // If nearby places is not enabled, do not try to load them.
    if (!PhoneDirectoryExtenderAccessor.get(getContext()).isEnabled(getContext())) {
      return;
    }
    ThreadUtil.getUiThreadHandler()
        .postDelayed(loadNearbyPlacesRunnable, NETWORK_SEARCH_DELAY_MILLIS);
  }
}
