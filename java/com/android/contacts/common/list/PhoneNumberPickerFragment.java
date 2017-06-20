/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.common.list;

import android.content.ComponentName;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.ArraySet;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import com.android.contacts.common.R;
import com.android.contacts.common.list.PhoneNumberListAdapter.Listener;
import com.android.contacts.common.util.AccountFilterUtil;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallInitiationType.Type;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.enrichedcall.EnrichedCallManager;
import com.android.dialer.lightbringer.LightbringerComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.protos.ProtoParsers;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

/** Fragment containing a phone number list for picking. */
public class PhoneNumberPickerFragment extends ContactEntryListFragment<ContactEntryListAdapter>
    implements PhoneNumberListAdapter.Listener, EnrichedCallManager.CapabilitiesListener {

  private static final String KEY_FILTER = "filter";
  private OnPhoneNumberPickerActionListener mListener;
  private ContactListFilter mFilter;
  private View mAccountFilterHeader;
  /**
   * Lives as ListView's header and is shown when {@link #mAccountFilterHeader} is set to View.GONE.
   */
  private View mPaddingView;
  /** true if the loader has started at least once. */
  private boolean mLoaderStarted;

  private boolean mUseCallableUri;

  private final Set<OnLoadFinishedListener> mLoadFinishedListeners = new ArraySet<>();

  private CursorReranker mCursorReranker;

  public PhoneNumberPickerFragment() {
    setQuickContactEnabled(false);
    setPhotoLoaderEnabled(true);
    setSectionHeaderDisplayEnabled(false);
    setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_NONE);

    // Show nothing instead of letting caller Activity show something.
    setHasOptionsMenu(true);
  }

  /**
   * Handles a click on the video call icon for a row in the list.
   *
   * @param position The position in the list where the click ocurred.
   */
  @Override
  public void onVideoCallIconClicked(int position) {
    Logger.get(getContext()).logImpression(DialerImpression.Type.IMS_VIDEO_REQUESTED_FROM_SEARCH);
    callNumber(position, true /* isVideoCall */);
  }

  @Override
  public void onLightbringerIconClicked(int position) {
    PerformanceReport.stopRecording();
    String phoneNumber = getPhoneNumber(position);
    Intent intent =
        LightbringerComponent.get(getContext())
            .getLightbringer()
            .getIntent(getContext(), phoneNumber);
    // DialtactsActivity.ACTIVITY_REQUEST_CODE_LIGHTBRINGER
    // Cannot reference because of cyclic dependencies
    Logger.get(getContext())
        .logImpression(DialerImpression.Type.LIGHTBRINGER_VIDEO_REQUESTED_FROM_SEARCH);
    int dialactsActivityRequestCode = 3;
    getActivity().startActivityForResult(intent, dialactsActivityRequestCode);
  }

  @Override
  public void onCallAndShareIconClicked(int position) {
    // Required because of cyclic dependencies of everything depending on contacts/common.
    String componentName = "com.android.dialer.callcomposer.CallComposerActivity";
    Intent intent = new Intent();
    intent.setComponent(new ComponentName(getContext(), componentName));
    DialerContact contact = ((PhoneNumberListAdapter) getAdapter()).getDialerContact(position);
    ProtoParsers.put(intent, "CALL_COMPOSER_CONTACT", contact);
    startActivity(intent);
  }

  public void setDirectorySearchEnabled(boolean flag) {
    setDirectorySearchMode(
        flag ? DirectoryListLoader.SEARCH_MODE_DEFAULT : DirectoryListLoader.SEARCH_MODE_NONE);
  }

  public void setOnPhoneNumberPickerActionListener(OnPhoneNumberPickerActionListener listener) {
    this.mListener = listener;
  }

  public OnPhoneNumberPickerActionListener getOnPhoneNumberPickerListener() {
    return mListener;
  }

  @Override
  protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
    super.onCreateView(inflater, container);

    View paddingView = inflater.inflate(R.layout.contact_detail_list_padding, null, false);
    mPaddingView = paddingView.findViewById(R.id.contact_detail_list_padding);
    getListView().addHeaderView(paddingView);

    mAccountFilterHeader = getView().findViewById(R.id.account_filter_header_container);
    updateFilterHeaderView();

    setVisibleScrollbarEnabled(getVisibleScrollbarEnabled());
  }

  @Override
  public void onPause() {
    super.onPause();
    EnrichedCallComponent.get(getContext())
        .getEnrichedCallManager()
        .unregisterCapabilitiesListener(this);
  }

  @Override
  public void onResume() {
    super.onResume();
    EnrichedCallComponent.get(getContext())
        .getEnrichedCallManager()
        .registerCapabilitiesListener(this);
  }

  protected boolean getVisibleScrollbarEnabled() {
    return true;
  }

  @Override
  protected void setSearchMode(boolean flag) {
    super.setSearchMode(flag);
    updateFilterHeaderView();
  }

  private void updateFilterHeaderView() {
    final ContactListFilter filter = getFilter();
    if (mAccountFilterHeader == null || filter == null) {
      return;
    }
    final boolean shouldShowHeader =
        !isSearchMode()
            && AccountFilterUtil.updateAccountFilterTitleForPhone(
                mAccountFilterHeader, filter, false);
    if (shouldShowHeader) {
      mPaddingView.setVisibility(View.GONE);
      mAccountFilterHeader.setVisibility(View.VISIBLE);
    } else {
      mPaddingView.setVisibility(View.VISIBLE);
      mAccountFilterHeader.setVisibility(View.GONE);
    }
  }

  @Override
  public void restoreSavedState(Bundle savedState) {
    super.restoreSavedState(savedState);

    if (savedState == null) {
      return;
    }

    mFilter = savedState.getParcelable(KEY_FILTER);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putParcelable(KEY_FILTER, mFilter);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    final int itemId = item.getItemId();
    if (itemId == android.R.id.home) { // See ActionBar#setDisplayHomeAsUpEnabled()
      if (mListener != null) {
        mListener.onHomeInActionBarSelected();
      }
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onItemClick(int position, long id) {
    callNumber(position, false /* isVideoCall */);
  }

  /**
   * Initiates a call to the number at the specified position.
   *
   * @param position The position.
   * @param isVideoCall {@code true} if the call should be initiated as a video call, {@code false}
   *     otherwise.
   */
  private void callNumber(int position, boolean isVideoCall) {
    final String number = getPhoneNumber(position);
    if (!TextUtils.isEmpty(number)) {
      cacheContactInfo(position);
      CallSpecificAppData callSpecificAppData =
          CallSpecificAppData.newBuilder()
              .setCallInitiationType(getCallInitiationType(true /* isRemoteDirectory */))
              .setPositionOfSelectedSearchResult(position)
              .setCharactersInSearchString(getQueryString() == null ? 0 : getQueryString().length())
              .build();
      mListener.onPickPhoneNumber(number, isVideoCall, callSpecificAppData);
    } else {
      LogUtil.i(
          "PhoneNumberPickerFragment.callNumber",
          "item at %d was clicked before adapter is ready, ignoring",
          position);
    }

    // Get the lookup key and track any analytics
    final String lookupKey = getLookupKey(position);
    if (!TextUtils.isEmpty(lookupKey)) {
      maybeTrackAnalytics(lookupKey);
    }
  }

  protected void cacheContactInfo(int position) {
    // Not implemented. Hook for child classes
  }

  protected String getPhoneNumber(int position) {
    final PhoneNumberListAdapter adapter = (PhoneNumberListAdapter) getAdapter();
    return adapter.getPhoneNumber(position);
  }

  protected String getLookupKey(int position) {
    final PhoneNumberListAdapter adapter = (PhoneNumberListAdapter) getAdapter();
    return adapter.getLookupKey(position);
  }

  @Override
  protected void startLoading() {
    mLoaderStarted = true;
    super.startLoading();
  }

  @Override
  @MainThread
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    Assert.isMainThread();
    // TODO: define and verify behavior for "Nearby places", corp directories,
    // and dividers listed in UI between these categories
    if (mCursorReranker != null
        && data != null
        && !data.isClosed()
        && data.getCount() > 0
        && loader.getId() == 0) { // only re-rank if a suggestions loader with id of 0.
      data = mCursorReranker.rerankCursor(data);
    }
    super.onLoadFinished(loader, data);

    // disable scroll bar if there is no data
    setVisibleScrollbarEnabled(data != null && !data.isClosed() && data.getCount() > 0);

    if (data != null) {
      notifyListeners();
    }
  }

  /** Ranks cursor data rows and returns reference to new cursor object with reordered data. */
  public interface CursorReranker {
    @MainThread
    Cursor rerankCursor(Cursor data);
  }

  @MainThread
  public void setReranker(@Nullable CursorReranker reranker) {
    Assert.isMainThread();
    mCursorReranker = reranker;
  }

  /** Listener that is notified when cursor has finished loading data. */
  public interface OnLoadFinishedListener {
    void onLoadFinished();
  }

  @MainThread
  public void addOnLoadFinishedListener(OnLoadFinishedListener listener) {
    Assert.isMainThread();
    mLoadFinishedListeners.add(listener);
  }

  @MainThread
  public void removeOnLoadFinishedListener(OnLoadFinishedListener listener) {
    Assert.isMainThread();
    mLoadFinishedListeners.remove(listener);
  }

  @MainThread
  protected void notifyListeners() {
    Assert.isMainThread();
    for (OnLoadFinishedListener listener : mLoadFinishedListeners) {
      listener.onLoadFinished();
    }
  }

  @Override
  public void onCapabilitiesUpdated() {
    if (getAdapter() != null) {
      EnrichedCallManager manager =
          EnrichedCallComponent.get(getContext()).getEnrichedCallManager();
      Listener listener = ((PhoneNumberListAdapter) getAdapter()).getListener();

      for (int i = 0; i < getListView().getChildCount(); i++) {
        if (!(getListView().getChildAt(i) instanceof ContactListItemView)) {
          continue;
        }

        // Since call and share is the lowest priority call to action, if any others are set,
        // do not reset the call to action. Also do not set the call and share call to action if
        // the number doesn't support call composer.
        ContactListItemView view = (ContactListItemView) getListView().getChildAt(i);
        if (view.getCallToAction() != ContactListItemView.NONE
            || view.getPhoneNumber() == null
            || manager.getCapabilities(view.getPhoneNumber()) == null
            || !manager.getCapabilities(view.getPhoneNumber()).supportsCallComposer()) {
          continue;
        }
        view.setCallToAction(ContactListItemView.CALL_AND_SHARE, listener, view.getPosition());
      }
    }
  }

  @MainThread
  @Override
  public void onDetach() {
    Assert.isMainThread();
    mLoadFinishedListeners.clear();
    super.onDetach();
  }

  public void setUseCallableUri(boolean useCallableUri) {
    mUseCallableUri = useCallableUri;
  }

  public boolean usesCallableUri() {
    return mUseCallableUri;
  }

  @Override
  protected ContactEntryListAdapter createListAdapter() {
    PhoneNumberListAdapter adapter = new PhoneNumberListAdapter(getActivity());
    adapter.setDisplayPhotos(true);
    adapter.setUseCallableUri(mUseCallableUri);
    return adapter;
  }

  @Override
  protected void configureAdapter() {
    super.configureAdapter();

    final ContactEntryListAdapter adapter = getAdapter();
    if (adapter == null) {
      return;
    }

    if (!isSearchMode() && mFilter != null) {
      adapter.setFilter(mFilter);
    }
  }

  @Override
  protected View inflateView(LayoutInflater inflater, ViewGroup container) {
    return inflater.inflate(R.layout.contact_list_content, null);
  }

  public ContactListFilter getFilter() {
    return mFilter;
  }

  public void setFilter(ContactListFilter filter) {
    if ((mFilter == null && filter == null) || (mFilter != null && mFilter.equals(filter))) {
      return;
    }

    mFilter = filter;
    if (mLoaderStarted) {
      reloadData();
    }
    updateFilterHeaderView();
  }

  /**
   * @param isRemoteDirectory {@code true} if the call was initiated using a contact/phone number
   *     not in the local contacts database
   */
  protected CallInitiationType.Type getCallInitiationType(boolean isRemoteDirectory) {
    return Type.UNKNOWN_INITIATION;
  }

  /**
   * Where a lookup key contains analytic event information, logs the associated analytics event.
   *
   * @param lookupKey The lookup key JSON object.
   */
  private void maybeTrackAnalytics(String lookupKey) {
    try {
      JSONObject json = new JSONObject(lookupKey);

      String analyticsCategory =
          json.getString(PhoneNumberListAdapter.PhoneQuery.ANALYTICS_CATEGORY);
      String analyticsAction = json.getString(PhoneNumberListAdapter.PhoneQuery.ANALYTICS_ACTION);
      String analyticsValue = json.getString(PhoneNumberListAdapter.PhoneQuery.ANALYTICS_VALUE);

      if (TextUtils.isEmpty(analyticsCategory)
          || TextUtils.isEmpty(analyticsAction)
          || TextUtils.isEmpty(analyticsValue)) {
        return;
      }

      // Assume that the analytic value being tracked could be a float value, but just cast
      // to a long so that the analytic server can handle it.
      long value;
      try {
        float floatValue = Float.parseFloat(analyticsValue);
        value = (long) floatValue;
      } catch (NumberFormatException nfe) {
        return;
      }

      Logger.get(getActivity())
          .sendHitEventAnalytics(analyticsCategory, analyticsAction, "" /* label */, value);
    } catch (JSONException e) {
      // Not an error; just a lookup key that doesn't have the right information.
    }
  }
}
