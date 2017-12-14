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

package com.android.dialer.speeddial;

import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.precall.PreCall;
import com.android.dialer.speeddial.FavoritesViewHolder.FavoriteContactsListener;
import com.android.dialer.speeddial.HeaderViewHolder.SpeedDialHeaderListener;
import com.android.dialer.speeddial.SuggestionViewHolder.SuggestedContactsListener;

/**
 * Fragment for displaying:
 *
 * <ul>
 *   <li>Favorite/Starred contacts
 *   <li>Suggested contacts
 * </ul>
 *
 * <p>Suggested contacts built from {@link android.provider.ContactsContract#STREQUENT_PHONE_ONLY}.
 */
public class SpeedDialFragment extends Fragment {

  private static final int STREQUENT_CONTACTS_LOADER_ID = 1;

  private final SpeedDialHeaderListener headerListener = new SpeedDialFragmentHeaderListener();
  private final FavoriteContactsListener favoritesListener = new SpeedDialFavoritesListener();
  private final SuggestedContactsListener suggestedListener = new SpeedDialSuggestedListener();
  private final SpeedDialFragmentLoaderCallback loaderCallback =
      new SpeedDialFragmentLoaderCallback();

  private SpeedDialAdapter adapter;

  public static SpeedDialFragment newInstance() {
    return new SpeedDialFragment();
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_speed_dial, container, false);
    RecyclerView recyclerView = view.findViewById(R.id.speed_dial_recycler_view);

    adapter =
        new SpeedDialAdapter(getContext(), favoritesListener, suggestedListener, headerListener);
    recyclerView.setLayoutManager(adapter.getLayoutManager(getContext()));
    recyclerView.setAdapter(adapter);
    getLoaderManager().initLoader(STREQUENT_CONTACTS_LOADER_ID, null /* args */, loaderCallback);
    return view;
  }

  public boolean hasFrequents() {
    // TODO(calderwoodra)
    return false;
  }

  @Override
  public void onResume() {
    super.onResume();
    getLoaderManager().restartLoader(STREQUENT_CONTACTS_LOADER_ID, null, loaderCallback);
  }

  private class SpeedDialFragmentHeaderListener implements SpeedDialHeaderListener {

    @Override
    public void onAddFavoriteClicked() {
      startActivity(new Intent(getContext(), AddFavoriteActivity.class));
    }
  }

  private class SpeedDialFavoritesListener implements FavoriteContactsListener {

    @Override
    public void onAmbiguousContactClicked(String lookupKey) {
      DisambigDialog.show(lookupKey, getFragmentManager());
    }

    @Override
    public void onClick(String number, boolean isVideoCall) {
      // TODO(calderwoodra): add logic for duo video calls
      PreCall.start(
          getContext(),
          new CallIntentBuilder(number, CallInitiationType.Type.SPEED_DIAL)
              .setIsVideoCall(isVideoCall));
    }

    @Override
    public void onLongClick(String number) {
      // TODO(calderwoodra): show favorite contact floating context menu
    }
  }

  private class SpeedDialSuggestedListener implements SuggestedContactsListener {

    @Override
    public void onOverFlowMenuClicked(String number) {
      // TODO(calderwoodra) show overflow menu for suggested contacts
    }

    @Override
    public void onRowClicked(String number) {
      PreCall.start(
          getContext(), new CallIntentBuilder(number, CallInitiationType.Type.SPEED_DIAL));
    }
  }

  /**
   * Loader callback that registers a content observer. {@link #unregisterContentObserver()} needs
   * to be called during tear down of the fragment.
   */
  private class SpeedDialFragmentLoaderCallback implements LoaderCallbacks<Cursor> {

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
      if (id == STREQUENT_CONTACTS_LOADER_ID) {
        return new StrequentContactsCursorLoader(getContext());
      }
      throw Assert.createIllegalStateFailException("Invalid loader id: " + id);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
      adapter.setCursor((SpeedDialCursor) data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
      adapter.setCursor(null);
    }
  }
}
