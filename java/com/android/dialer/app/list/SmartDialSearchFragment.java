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

import static android.Manifest.permission.CALL_PHONE;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v13.app.FragmentCompat;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.dialer.app.R;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.common.LogUtil;
import com.android.dialer.database.DialerDatabaseHelper;
import com.android.dialer.smartdial.SmartDialCursorLoader;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.widget.EmptyContentView;
import java.util.Arrays;

/** Implements a fragment to load and display SmartDial search results. */
public class SmartDialSearchFragment extends SearchFragment
    implements EmptyContentView.OnEmptyViewActionButtonClickedListener,
        FragmentCompat.OnRequestPermissionsResultCallback {

  private static final int CALL_PHONE_PERMISSION_REQUEST_CODE = 1;

  private final BroadcastReceiver smartDialUpdatedReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          LogUtil.i("SmartDialSearchFragment.onReceive", "smart dial update broadcast received");
          reloadData();
        }
      };

  /** Creates a SmartDialListAdapter to display and operate on search results. */
  @Override
  protected ContactEntryListAdapter createListAdapter() {
    SmartDialNumberListAdapter adapter = new SmartDialNumberListAdapter(getActivity());
    adapter.setUseCallableUri(super.usesCallableUri());
    adapter.setQuickContactEnabled(true);
    adapter.setShowEmptyListForNullQuery(getShowEmptyListForNullQuery());
    // Set adapter's query string to restore previous instance state.
    adapter.setQueryString(getQueryString());
    adapter.setListener(this);
    return adapter;
  }

  /** Creates a SmartDialCursorLoader object to load query results. */
  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    // Smart dialing does not support Directory Load, falls back to normal search instead.
    if (id == getDirectoryLoaderId()) {
      return super.onCreateLoader(id, args);
    } else {
      final SmartDialNumberListAdapter adapter = (SmartDialNumberListAdapter) getAdapter();
      SmartDialCursorLoader loader = new SmartDialCursorLoader(super.getContext());
      loader.setShowEmptyListForNullQuery(getShowEmptyListForNullQuery());
      adapter.configureLoader(loader);
      return loader;
    }
  }

  @Override
  public boolean getShowEmptyListForNullQuery() {
    return true;
  }

  @Override
  protected void setupEmptyView() {
    if (emptyView != null && getActivity() != null) {
      if (!PermissionsUtil.hasPermission(getActivity(), CALL_PHONE)) {
        emptyView.setImage(R.drawable.empty_contacts);
        emptyView.setActionLabel(R.string.permission_single_turn_on);
        emptyView.setDescription(R.string.permission_place_call);
        emptyView.setActionClickedListener(this);
      } else {
        emptyView.setImage(EmptyContentView.NO_IMAGE);
        emptyView.setActionLabel(EmptyContentView.NO_LABEL);
        emptyView.setDescription(EmptyContentView.NO_LABEL);
      }
    }
  }

  @Override
  public void onStart() {
    super.onStart();

    LogUtil.i("SmartDialSearchFragment.onStart", "registering smart dial update receiver");

    getActivity()
        .registerReceiver(
            smartDialUpdatedReceiver,
            new IntentFilter(DialerDatabaseHelper.ACTION_SMART_DIAL_UPDATED));
  }

  @Override
  public void onStop() {
    super.onStop();

    LogUtil.i("SmartDialSearchFragment.onStop", "unregistering smart dial update receiver");

    getActivity().unregisterReceiver(smartDialUpdatedReceiver);
  }

  @Override
  public void onEmptyViewActionButtonClicked() {
    final Activity activity = getActivity();
    if (activity == null) {
      return;
    }

    String[] deniedPermissions =
        PermissionsUtil.getPermissionsCurrentlyDenied(
            getContext(), PermissionsUtil.allPhoneGroupPermissionsUsedInDialer);
    if (deniedPermissions.length > 0) {
      LogUtil.i(
          "SmartDialSearchFragment.onEmptyViewActionButtonClicked",
          "Requesting permissions: " + Arrays.toString(deniedPermissions));
      FragmentCompat.requestPermissions(
          this, deniedPermissions, CALL_PHONE_PERMISSION_REQUEST_CODE);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == CALL_PHONE_PERMISSION_REQUEST_CODE) {
      setupEmptyView();
    }
  }

  @Override
  protected CallInitiationType.Type getCallInitiationType(boolean isRemoteDirectory) {
    return CallInitiationType.Type.SMART_DIAL;
  }

  public boolean isShowingPermissionRequest() {
    return emptyView != null && emptyView.isShowingContent();
  }

  @Override
  public void setShowEmptyListForNullQuery(boolean show) {
    if (getAdapter() != null) {
      ((SmartDialNumberListAdapter) getAdapter()).setShowEmptyListForNullQuery(show);
    }
    super.setShowEmptyListForNullQuery(show);
  }
}
