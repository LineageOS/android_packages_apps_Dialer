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
import android.content.pm.PackageManager;
import android.support.v13.app.FragmentCompat;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.PinnedHeaderListView;
import com.android.dialer.app.R;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.common.LogUtil;
import com.android.dialer.phonenumbercache.CachedNumberLookupService;
import com.android.dialer.phonenumbercache.PhoneNumberCache;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.widget.EmptyContentView;
import com.android.dialer.widget.EmptyContentView.OnEmptyViewActionButtonClickedListener;
import java.util.Arrays;

public class RegularSearchFragment extends SearchFragment
    implements OnEmptyViewActionButtonClickedListener,
        FragmentCompat.OnRequestPermissionsResultCallback {

  public static final int PERMISSION_REQUEST_CODE = 1;

  private static final int SEARCH_DIRECTORY_RESULT_LIMIT = 5;
  protected String mPermissionToRequest;

  public RegularSearchFragment() {
    configureDirectorySearch();
  }

  public void configureDirectorySearch() {
    setDirectorySearchEnabled(true);
    setDirectoryResultLimit(SEARCH_DIRECTORY_RESULT_LIMIT);
  }

  @Override
  protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
    super.onCreateView(inflater, container);
    ((PinnedHeaderListView) getListView()).setScrollToSectionOnHeaderTouch(true);
  }

  @Override
  protected ContactEntryListAdapter createListAdapter() {
    RegularSearchListAdapter adapter = new RegularSearchListAdapter(getActivity());
    adapter.setDisplayPhotos(true);
    adapter.setUseCallableUri(usesCallableUri());
    adapter.setListener(this);
    return adapter;
  }

  @Override
  protected void cacheContactInfo(int position) {
    CachedNumberLookupService cachedNumberLookupService =
        PhoneNumberCache.get(getContext()).getCachedNumberLookupService();
    if (cachedNumberLookupService != null) {
      final RegularSearchListAdapter adapter = (RegularSearchListAdapter) getAdapter();
      cachedNumberLookupService.addContact(
          getContext(), adapter.getContactInfo(cachedNumberLookupService, position));
    }
  }

  @Override
  protected void setupEmptyView() {
    if (mEmptyView != null && getActivity() != null) {
      final int imageResource;
      final int actionLabelResource;
      final int descriptionResource;
      final OnEmptyViewActionButtonClickedListener listener;
      if (!PermissionsUtil.hasPermission(getActivity(), READ_CONTACTS)) {
        imageResource = R.drawable.empty_contacts;
        actionLabelResource = R.string.permission_single_turn_on;
        descriptionResource = R.string.permission_no_search;
        listener = this;
        mPermissionToRequest = READ_CONTACTS;
      } else {
        imageResource = EmptyContentView.NO_IMAGE;
        actionLabelResource = EmptyContentView.NO_LABEL;
        descriptionResource = EmptyContentView.NO_LABEL;
        listener = null;
        mPermissionToRequest = null;
      }

      mEmptyView.setImage(imageResource);
      mEmptyView.setActionLabel(actionLabelResource);
      mEmptyView.setDescription(descriptionResource);
      if (listener != null) {
        mEmptyView.setActionClickedListener(listener);
      }
    }
  }

  @Override
  public void onEmptyViewActionButtonClicked() {
    final Activity activity = getActivity();
    if (activity == null) {
      return;
    }

    if (READ_CONTACTS.equals(mPermissionToRequest)) {
      String[] deniedPermissions =
          PermissionsUtil.getPermissionsCurrentlyDenied(
              getContext(), PermissionsUtil.allContactsGroupPermissionsUsedInDialer);
      if (deniedPermissions.length > 0) {
        LogUtil.i(
            "RegularSearchFragment.onEmptyViewActionButtonClicked",
            "Requesting permissions: " + Arrays.toString(deniedPermissions));
        FragmentCompat.requestPermissions(this, deniedPermissions, PERMISSION_REQUEST_CODE);
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == PERMISSION_REQUEST_CODE) {
      setupEmptyView();
      if (grantResults != null
          && grantResults.length == 1
          && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
        PermissionsUtil.notifyPermissionGranted(getActivity(), permissions[0]);
      }
    }
  }

  @Override
  protected CallInitiationType.Type getCallInitiationType(boolean isRemoteDirectory) {
    return isRemoteDirectory
        ? CallInitiationType.Type.REMOTE_DIRECTORY
        : CallInitiationType.Type.REGULAR_SEARCH;
  }

  public interface CapabilityChecker {

    boolean isNearbyPlacesSearchEnabled();
  }
}
