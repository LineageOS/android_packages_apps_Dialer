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
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.support.annotation.Nullable;
import android.support.v13.app.FragmentCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Recycler;
import android.support.v7.widget.RecyclerView.State;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnScrollChangeListener;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.preference.ContactsPreferences.ChangeListener;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.widget.EmptyContentView;
import com.android.dialer.widget.EmptyContentView.OnEmptyViewActionButtonClickedListener;
import java.util.Arrays;

/** Fragment containing a list of all contacts. */
public class ContactsFragment extends Fragment
    implements LoaderCallbacks<Cursor>,
        OnScrollChangeListener,
        OnEmptyViewActionButtonClickedListener,
        ChangeListener {

  public static final int READ_CONTACTS_PERMISSION_REQUEST_CODE = 1;

  private FastScroller fastScroller;
  private TextView anchoredHeader;
  private RecyclerView recyclerView;
  private LinearLayoutManager manager;
  private ContactsAdapter adapter;
  private EmptyContentView emptyContentView;

  private ContactsPreferences contactsPrefs;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    contactsPrefs = new ContactsPreferences(getContext());
    contactsPrefs.registerChangeListener(this);
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_contacts, container, false);
    fastScroller = view.findViewById(R.id.fast_scroller);
    anchoredHeader = view.findViewById(R.id.header);
    recyclerView = view.findViewById(R.id.recycler_view);

    emptyContentView = view.findViewById(R.id.empty_list_view);
    emptyContentView.setImage(R.drawable.empty_contacts);
    emptyContentView.setActionClickedListener(this);

    if (PermissionsUtil.hasContactsReadPermissions(getContext())) {
      getLoaderManager().initLoader(0, null, this);
    } else {
      emptyContentView.setDescription(R.string.permission_no_contacts);
      emptyContentView.setActionLabel(R.string.permission_single_turn_on);
      emptyContentView.setVisibility(View.VISIBLE);
    }

    return view;
  }

  @Override
  public void onChange() {
    if (getActivity() != null && isAdded()) {
      getLoaderManager().restartLoader(0, null, this);
    }
  }

  /** @return a loader according to sort order and display order. */
  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    boolean sortOrderPrimary =
        (contactsPrefs.getSortOrder() == ContactsPreferences.SORT_ORDER_PRIMARY);
    boolean displayOrderPrimary =
        (contactsPrefs.getDisplayOrder() == ContactsPreferences.DISPLAY_ORDER_PRIMARY);

    String sortKey = sortOrderPrimary ? Contacts.SORT_KEY_PRIMARY : Contacts.SORT_KEY_ALTERNATIVE;
    return displayOrderPrimary
        ? ContactsCursorLoader.createInstanceDisplayNamePrimary(getContext(), sortKey)
        : ContactsCursorLoader.createInstanceDisplayNameAlternative(getContext(), sortKey);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    if (cursor.getCount() == 0) {
      emptyContentView.setDescription(R.string.all_contacts_empty);
      emptyContentView.setActionLabel(R.string.all_contacts_empty_add_contact_action);
      emptyContentView.setVisibility(View.VISIBLE);
      recyclerView.setVisibility(View.GONE);
    } else {
      emptyContentView.setVisibility(View.GONE);
      recyclerView.setVisibility(View.VISIBLE);
      adapter = new ContactsAdapter(getContext(), cursor);
      manager =
          new LinearLayoutManager(getContext()) {
            @Override
            public void onLayoutChildren(Recycler recycler, State state) {
              super.onLayoutChildren(recycler, state);
              int itemsShown = findLastVisibleItemPosition() - findFirstVisibleItemPosition() + 1;
              if (adapter.getItemCount() > itemsShown) {
                fastScroller.setVisibility(View.VISIBLE);
                recyclerView.setOnScrollChangeListener(ContactsFragment.this);
              } else {
                fastScroller.setVisibility(View.GONE);
              }
            }
          };

      recyclerView.setLayoutManager(manager);
      recyclerView.setAdapter(adapter);
      PerformanceReport.logOnScrollStateChange(recyclerView);
      fastScroller.setup(adapter, manager);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    recyclerView.setAdapter(null);
    recyclerView.setOnScrollChangeListener(null);
    adapter = null;
    contactsPrefs.unregisterChangeListener();
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
    fastScroller.updateContainerAndScrollBarPosition(recyclerView);
    int firstVisibleItem = manager.findFirstVisibleItemPosition();
    int firstCompletelyVisible = manager.findFirstCompletelyVisibleItemPosition();
    if (firstCompletelyVisible == RecyclerView.NO_POSITION) {
      // No items are visible, so there are no headers to update.
      return;
    }
    String anchoredHeaderString = adapter.getHeaderString(firstCompletelyVisible);

    // If the user swipes to the top of the list very quickly, there is some strange behavior
    // between this method updating headers and adapter#onBindViewHolder updating headers.
    // To overcome this, we refresh the headers to ensure they are correct.
    if (firstVisibleItem == firstCompletelyVisible && firstVisibleItem == 0) {
      adapter.refreshHeaders();
      anchoredHeader.setVisibility(View.INVISIBLE);
    } else if (firstVisibleItem != 0) { // skip the add contact row
      if (adapter.getHeaderString(firstVisibleItem).equals(anchoredHeaderString)) {
        anchoredHeader.setText(anchoredHeaderString);
        anchoredHeader.setVisibility(View.VISIBLE);
        getContactHolder(firstVisibleItem).getHeaderView().setVisibility(View.INVISIBLE);
        getContactHolder(firstCompletelyVisible).getHeaderView().setVisibility(View.INVISIBLE);
      } else {
        anchoredHeader.setVisibility(View.INVISIBLE);
        getContactHolder(firstVisibleItem).getHeaderView().setVisibility(View.VISIBLE);
        getContactHolder(firstCompletelyVisible).getHeaderView().setVisibility(View.VISIBLE);
      }
    }
  }

  private ContactViewHolder getContactHolder(int position) {
    return ((ContactViewHolder) recyclerView.findViewHolderForAdapterPosition(position));
  }

  @Override
  public void onEmptyViewActionButtonClicked() {
    if (emptyContentView.getActionLabel() == R.string.permission_single_turn_on) {
      String[] deniedPermissions =
          PermissionsUtil.getPermissionsCurrentlyDenied(
              getContext(), PermissionsUtil.allContactsGroupPermissionsUsedInDialer);
      if (deniedPermissions.length > 0) {
        LogUtil.i(
            "ContactsFragment.onEmptyViewActionButtonClicked",
            "Requesting permissions: " + Arrays.toString(deniedPermissions));
        FragmentCompat.requestPermissions(
            this, deniedPermissions, READ_CONTACTS_PERMISSION_REQUEST_CODE);
      }

    } else if (emptyContentView.getActionLabel()
        == R.string.all_contacts_empty_add_contact_action) {
      // Add new contact
      DialerUtils.startActivityWithErrorToast(
          getContext(), IntentUtil.getNewContactIntent(), R.string.add_contact_not_available);
    } else {
      throw Assert.createIllegalStateFailException("Invalid empty content view action label.");
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == READ_CONTACTS_PERMISSION_REQUEST_CODE) {
      if (grantResults.length >= 1 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
        // Force a refresh of the data since we were missing the permission before this.
        emptyContentView.setVisibility(View.GONE);
        getLoaderManager().initLoader(0, null, this);
      }
    }
  }
}
