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

package com.android.dialer.app.list;

import static android.Manifest.permission.READ_CONTACTS;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.QuickContact;
import android.support.v13.app.FragmentCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.dialer.app.R;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.CompatUtils;
import com.android.dialer.logging.InteractionEvent;
import com.android.dialer.logging.Logger;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.widget.EmptyContentView;
import com.android.dialer.widget.EmptyContentView.OnEmptyViewActionButtonClickedListener;
import java.util.Arrays;

/** Fragments to show all contacts with phone numbers. */
public class AllContactsFragment extends ContactEntryListFragment<ContactEntryListAdapter>
    implements OnEmptyViewActionButtonClickedListener,
        FragmentCompat.OnRequestPermissionsResultCallback {

  private static final int READ_CONTACTS_PERMISSION_REQUEST_CODE = 1;

  private EmptyContentView emptyListView;

  /**
   * Listen to broadcast events about permissions in order to be notified if the READ_CONTACTS
   * permission is granted via the UI in another fragment.
   */
  private BroadcastReceiver readContactsPermissionGrantedReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          reloadData();
        }
      };

  public AllContactsFragment() {
    setQuickContactEnabled(false);
    setAdjustSelectionBoundsEnabled(true);
    setPhotoLoaderEnabled(true);
    setSectionHeaderDisplayEnabled(true);
    setDarkTheme(false);
    setVisibleScrollbarEnabled(true);
  }

  @Override
  public void onViewCreated(View view, android.os.Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    emptyListView = (EmptyContentView) view.findViewById(R.id.empty_list_view);
    emptyListView.setImage(R.drawable.empty_contacts);
    emptyListView.setDescription(R.string.all_contacts_empty);
    emptyListView.setActionClickedListener(this);
    getListView().setEmptyView(emptyListView);
    emptyListView.setVisibility(View.GONE);
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

  @Override
  protected void startLoading() {
    if (PermissionsUtil.hasPermission(getActivity(), READ_CONTACTS)) {
      super.startLoading();
      emptyListView.setDescription(R.string.all_contacts_empty);
      emptyListView.setActionLabel(R.string.all_contacts_empty_add_contact_action);
    } else {
      emptyListView.setDescription(R.string.permission_no_contacts);
      emptyListView.setActionLabel(R.string.permission_single_turn_on);
      emptyListView.setVisibility(View.VISIBLE);
    }
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    super.onLoadFinished(loader, data);

    if (data == null || data.getCount() == 0) {
      emptyListView.setVisibility(View.VISIBLE);
    }
  }

  @Override
  protected ContactEntryListAdapter createListAdapter() {
    final DefaultContactListAdapter adapter =
        new DefaultContactListAdapter(getActivity()) {
          @Override
          protected void bindView(View itemView, int partition, Cursor cursor, int position) {
            super.bindView(itemView, partition, cursor, position);
            itemView.setTag(this.getContactUri(partition, cursor));
          }
        };
    adapter.setDisplayPhotos(true);
    adapter.setFilter(
        ContactListFilter.createFilterWithType(ContactListFilter.FILTER_TYPE_DEFAULT));
    adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
    return adapter;
  }

  @Override
  protected View inflateView(LayoutInflater inflater, ViewGroup container) {
    return inflater.inflate(R.layout.all_contacts_fragment, null);
  }

  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    final Uri uri = (Uri) view.getTag();
    if (uri != null) {
      Logger.get(getContext())
          .logInteraction(InteractionEvent.Type.OPEN_QUICK_CONTACT_FROM_ALL_CONTACTS_GENERAL);
      if (CompatUtils.hasPrioritizedMimeType()) {
        QuickContact.showQuickContact(getContext(), view, uri, null, Phone.CONTENT_ITEM_TYPE);
      } else {
        QuickContact.showQuickContact(getActivity(), view, uri, QuickContact.MODE_LARGE, null);
      }
    }
  }

  @Override
  protected void onItemClick(int position, long id) {
    // Do nothing. Implemented to satisfy ContactEntryListFragment.
  }

  @Override
  public void onEmptyViewActionButtonClicked() {
    final Activity activity = getActivity();
    if (activity == null) {
      return;
    }

    String[] deniedPermissions =
        PermissionsUtil.getPermissionsCurrentlyDenied(
            getContext(), PermissionsUtil.allContactsGroupPermissionsUsedInDialer);
    if (deniedPermissions.length > 0) {
      LogUtil.i(
          "AllContactsFragment.onEmptyViewActionButtonClicked",
          "Requesting permissions: " + Arrays.toString(deniedPermissions));
      FragmentCompat.requestPermissions(
          this, deniedPermissions, READ_CONTACTS_PERMISSION_REQUEST_CODE);
    } else {
      // Add new contact
      DialerUtils.startActivityWithErrorToast(
          activity, IntentUtil.getNewContactIntent(), R.string.add_contact_not_available);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == READ_CONTACTS_PERMISSION_REQUEST_CODE) {
      if (grantResults.length >= 1 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
        // Force a refresh of the data since we were missing the permission before this.
        reloadData();
      }
    }
  }
}
