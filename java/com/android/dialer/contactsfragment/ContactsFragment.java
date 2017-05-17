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
 * limitations under the License.
 */

package com.android.dialer.contactsfragment;

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
import android.view.View.OnScrollChangeListener;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.dialer.util.PermissionsUtil;

/** Fragment containing a list of all contacts. */
public class ContactsFragment extends Fragment
    implements LoaderCallbacks<Cursor>, OnScrollChangeListener {

  private TextView anchoredHeader;
  private RecyclerView recyclerView;
  private LinearLayoutManager manager;
  private ContactsAdapter adapter;

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_contacts, container, false);
    anchoredHeader = (TextView) view.findViewById(R.id.header);
    manager = new LinearLayoutManager(getContext());

    // TODO: Handle contacts permission denied view
    // TODO: Handle 0 contacts layout
    recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
    recyclerView.setLayoutManager(manager);
    getLoaderManager().initLoader(0, null, this);

    if (PermissionsUtil.hasContactsReadPermissions(getContext())) {
      getLoaderManager().initLoader(0, null, this);
    }

    return view;
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new ContactsCursorLoader(getContext());
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    // TODO setup fast scroller.
    adapter = new ContactsAdapter(getContext(), cursor);
    recyclerView.setAdapter(adapter);
    if (adapter.getItemCount() > 1) {
      recyclerView.setOnScrollChangeListener(this);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    recyclerView.setAdapter(null);
    recyclerView.setOnScrollChangeListener(null);
    adapter = null;
  }

  /*
   * When our recycler view updates, we need to ensure that our row headers and anchored header
   * are in the correct state.
   *
   * The general rule is, when the row headers are shown, our anchored header is hidden. When the
   * recycler view is scrolling through a sublist that has more than one element, we want to show
   * out anchored header, to create the illusion that our row header has been anchored. In all
   * other situations, we want to hide the anchor because that means we are transitioning between
   * two sublists.
   */
  @Override
  public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
    int firstVisibleItem = manager.findFirstVisibleItemPosition();
    int firstCompletelyVisible = manager.findFirstCompletelyVisibleItemPosition();

    // If the user swipes to the top of the list very quickly, there is some strange behavior
    // between this method updating headers and adapter#onBindViewHolder updating headers.
    // To overcome this, we refresh the headers to ensure they are correct.
    if (firstVisibleItem == firstCompletelyVisible && firstVisibleItem == 0) {
      adapter.refreshHeaders();
      anchoredHeader.setVisibility(View.INVISIBLE);
    } else {
      boolean showAnchor =
          adapter.getHeader(firstVisibleItem).equals(adapter.getHeader(firstCompletelyVisible));
      anchoredHeader.setText(adapter.getHeader(firstCompletelyVisible));
      anchoredHeader.setVisibility(showAnchor ? View.VISIBLE : View.INVISIBLE);

      int rowHeaderVisibility = showAnchor ? View.INVISIBLE : View.VISIBLE;
      adapter.setHeaderVisibility(firstVisibleItem, rowHeaderVisibility);
      adapter.setHeaderVisibility(firstCompletelyVisible, rowHeaderVisibility);
    }
  }
}
