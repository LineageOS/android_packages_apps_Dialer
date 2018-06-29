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

import static android.Manifest.permission.READ_CONTACTS;

import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.IntDef;
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
import android.widget.ImageView;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.widget.EmptyContentView;
import com.android.dialer.widget.EmptyContentView.OnEmptyViewActionButtonClickedListener;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/** Fragment containing a list of all contacts. */
public class ContactsFragment extends Fragment
    implements LoaderCallbacks<Cursor>,
        OnScrollChangeListener,
        OnEmptyViewActionButtonClickedListener {

  /** An enum for the different types of headers that be inserted at position 0 in the list. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({Header.NONE, Header.ADD_CONTACT})
  public @interface Header {
    int NONE = 0;
    /** Header that allows the user to add a new contact. */
    int ADD_CONTACT = 1;
  }

  public static final int READ_CONTACTS_PERMISSION_REQUEST_CODE = 1;

  private static final String EXTRA_HEADER = "extra_header";
  private static final String EXTRA_HAS_PHONE_NUMBERS = "extra_has_phone_numbers";

  /**
   * Listen to broadcast events about permissions in order to be notified if the READ_CONTACTS
   * permission is granted via the UI in another fragment.
   */
  private final BroadcastReceiver readContactsPermissionGrantedReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          loadContacts();
        }
      };

  private FastScroller fastScroller;
  private TextView anchoredHeader;
  private RecyclerView recyclerView;
  private LinearLayoutManager manager;
  private ContactsAdapter adapter;
  private EmptyContentView emptyContentView;

  private @Header int header;

  private boolean hasPhoneNumbers;
  private String query;

  /**
   * Used to get a configured instance of ContactsFragment.
   *
   * <p>Current example of this fragment are the contacts tab and in creating a new favorite
   * contact. For example, the contacts tab we use:
   *
   * <ul>
   *   <li>{@link Header#ADD_CONTACT} to insert a header that allows users to add a contact
   *   <li>Open contact cards on click
   * </ul>
   *
   * And for the add favorite contact screen we might use:
   *
   * <ul>
   *   <li>{@link Header#NONE} so that all rows are contacts (i.e. no header inserted)
   *   <li>Send a selected contact to the parent activity.
   * </ul>
   *
   * @param header determines the type of header inserted at position 0 in the contacts list
   */
  public static ContactsFragment newInstance(@Header int header) {
    ContactsFragment fragment = new ContactsFragment();
    Bundle args = new Bundle();
    args.putInt(EXTRA_HEADER, header);
    fragment.setArguments(args);
    return fragment;
  }

  /**
   * Returns {@link ContactsFragment} with a list of contacts such that:
   *
   * <ul>
   *   <li>Each contact has a phone number
   *   <li>Contacts are filterable via {@link #updateQuery(String)}
   *   <li>There is no list header (i.e. {@link Header#NONE}
   *   <li>Clicking on a contact notifies the parent activity via {@link
   *       OnContactSelectedListener#onContactSelected(ImageView, Uri, long)}.
   * </ul>
   */
  public static ContactsFragment newAddFavoritesInstance() {
    ContactsFragment fragment = new ContactsFragment();
    Bundle args = new Bundle();
    args.putInt(EXTRA_HEADER, Header.NONE);
    args.putBoolean(EXTRA_HAS_PHONE_NUMBERS, true);
    fragment.setArguments(args);
    return fragment;
  }

  @SuppressWarnings("WrongConstant")
  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    header = getArguments().getInt(EXTRA_HEADER);
    hasPhoneNumbers = getArguments().getBoolean(EXTRA_HAS_PHONE_NUMBERS);
    if (savedInstanceState == null) {
      // The onHiddenChanged callback does not get called the first time the fragment is
      // attached, so call it ourselves here.
      onHiddenChanged(false);
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    PermissionsUtil.registerPermissionReceiver(
        getActivity(), readContactsPermissionGrantedReceiver, READ_CONTACTS);
  }

  @Override
  public void onStop() {
    PermissionsUtil.unregisterPermissionReceiver(
        getActivity(), readContactsPermissionGrantedReceiver);
    super.onStop();
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_contacts, container, false);
    fastScroller = view.findViewById(R.id.fast_scroller);
    anchoredHeader = view.findViewById(R.id.header);
    recyclerView = view.findViewById(R.id.recycler_view);
    adapter =
        new ContactsAdapter(
            getContext(), header, FragmentUtils.getParent(this, OnContactSelectedListener.class));
    recyclerView.setAdapter(adapter);
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

    emptyContentView = view.findViewById(R.id.empty_list_view);
    emptyContentView.setImage(R.drawable.empty_contacts);
    emptyContentView.setActionClickedListener(this);

    if (PermissionsUtil.hasContactsReadPermissions(getContext())) {
      loadContacts();
    } else {
      emptyContentView.setDescription(R.string.permission_no_contacts);
      emptyContentView.setActionLabel(R.string.permission_single_turn_on);
      emptyContentView.setVisibility(View.VISIBLE);
      recyclerView.setVisibility(View.GONE);
    }

    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
    if (getActivity() != null
        && isAdded()
        && PermissionsUtil.hasContactsReadPermissions(getContext())) {
      getLoaderManager().restartLoader(0, null, this);
    }
  }

  /** @return a loader according to sort order and display order. */
  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    ContactsCursorLoader cursorLoader = new ContactsCursorLoader(getContext(), hasPhoneNumbers);
    cursorLoader.setQuery(query);
    return cursorLoader;
  }

  public void updateQuery(String query) {
    this.query = query;
    getLoaderManager().restartLoader(0, null, this);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    LogUtil.enterBlock("ContactsFragment.onLoadFinished");
    if (cursor == null || cursor.getCount() == 0) {
      emptyContentView.setDescription(R.string.all_contacts_empty);
      emptyContentView.setActionLabel(R.string.all_contacts_empty_add_contact_action);
      emptyContentView.setVisibility(View.VISIBLE);
      recyclerView.setVisibility(View.GONE);
    } else {
      emptyContentView.setVisibility(View.GONE);
      recyclerView.setVisibility(View.VISIBLE);
      adapter.updateCursor(cursor);

      PerformanceReport.logOnScrollStateChange(recyclerView);
      fastScroller.setup(adapter, manager);
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
    fastScroller.updateContainerAndScrollBarPosition(recyclerView);
    int firstVisibleItem = manager.findFirstVisibleItemPosition();
    int firstCompletelyVisible = manager.findFirstCompletelyVisibleItemPosition();
    if (firstCompletelyVisible == RecyclerView.NO_POSITION) {
      // No items are visible, so there are no headers to update.
      return;
    }
    String anchoredHeaderString = adapter.getHeaderString(firstCompletelyVisible);

    OnContactsListScrolledListener listener =
        FragmentUtils.getParent(this, OnContactsListScrolledListener.class);
    if (listener != null) {
      listener.onContactsListScrolled(
          recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING
              || fastScroller.isDragStarted());
    }

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
        PermissionsUtil.notifyPermissionGranted(getContext(), permissions[0]);
      }
    }
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    OnContactsFragmentHiddenChangedListener listener =
        FragmentUtils.getParent(this, OnContactsFragmentHiddenChangedListener.class);
    if (listener != null) {
      listener.onContactsFragmentHiddenChanged(hidden);
    }
  }

  private void loadContacts() {
    getLoaderManager().initLoader(0, null, this);
    recyclerView.setVisibility(View.VISIBLE);
    emptyContentView.setVisibility(View.GONE);
  }

  /** Listener for contacts list scroll state. */
  public interface OnContactsListScrolledListener {
    void onContactsListScrolled(boolean isDragging);
  }

  /** Listener to notify parents when a contact is selected. */
  public interface OnContactSelectedListener {

    /** Called when a contact is selected in {@link ContactsFragment}. */
    void onContactSelected(ImageView photo, Uri contactUri, long contactId);
  }

  /** Listener for contacts fragment hidden state */
  public interface OnContactsFragmentHiddenChangedListener {
    void onContactsFragmentHiddenChanged(boolean hidden);
  }
}
