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

package com.android.dialer.searchfragment;

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

/** Fragment used for searching contacts. */
public final class NewSearchFragment extends Fragment implements LoaderCallbacks<Cursor> {

  private RecyclerView recyclerView;
  private SearchAdapter adapter;
  private String query;

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle bundle) {
    getLoaderManager().initLoader(0, null, this);
    View view = inflater.inflate(R.layout.fragment_search, parent, false);
    recyclerView = view.findViewById(R.id.recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

    getLoaderManager().initLoader(0, null, this);
    return view;
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
    // TODO add more loaders
    return new SearchContactsCursorLoader(getContext());
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    if (adapter == null) {
      adapter = new SearchAdapter(getContext());
    }
    if (loader instanceof SearchContactsCursorLoader) {
      adapter.setContactsCursor(new SearchContactCursor(cursor, query));
    }
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    if (adapter != null) {
      adapter.clear();
      adapter = null;
    }
    recyclerView.setAdapter(null);
  }

  public void setQuery(String query) {
    this.query = query;
    if (adapter != null) {
      adapter.setQuery(query);
    }
  }
}
