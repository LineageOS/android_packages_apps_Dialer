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

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.SupportUiListener;
import com.android.dialer.precall.PreCall;
import com.android.dialer.speeddial.FavoritesViewHolder.FavoriteContactsListener;
import com.android.dialer.speeddial.HeaderViewHolder.SpeedDialHeaderListener;
import com.android.dialer.speeddial.SuggestionViewHolder.SuggestedContactsListener;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.android.dialer.speeddial.loader.SpeedDialUiItem;
import com.android.dialer.speeddial.loader.UiItemLoaderComponent;
import com.google.common.collect.ImmutableList;
import java.util.List;

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

  private final SpeedDialHeaderListener headerListener = new SpeedDialFragmentHeaderListener();
  private final FavoriteContactsListener favoritesListener = new SpeedDialFavoritesListener();
  private final SuggestedContactsListener suggestedListener = new SpeedDialSuggestedListener();

  private SpeedDialAdapter adapter;
  private SupportUiListener<ImmutableList<SpeedDialUiItem>> speedDialLoaderListener;

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

    speedDialLoaderListener =
        DialerExecutorComponent.get(getContext())
            .createUiListener(getChildFragmentManager(), "speed_dial_loader_listener");
    return view;
  }

  public boolean hasFrequents() {
    // TODO(calderwoodra)
    return false;
  }

  @Override
  public void onResume() {
    super.onResume();
    speedDialLoaderListener.listen(
        getContext(),
        UiItemLoaderComponent.get(getContext()).speedDialUiItemLoader().loadSpeedDialUiItems(),
        speedDialUiItems -> {
          adapter.setSpeedDialUiItems(speedDialUiItems);
          adapter.notifyDataSetChanged();
        },
        throwable -> {
          throw new RuntimeException(throwable);
        });
  }

  private class SpeedDialFragmentHeaderListener implements SpeedDialHeaderListener {

    @Override
    public void onAddFavoriteClicked() {
      startActivity(new Intent(getContext(), AddFavoriteActivity.class));
    }
  }

  private class SpeedDialFavoritesListener implements FavoriteContactsListener {

    @Override
    public void onAmbiguousContactClicked(List<Channel> channels) {
      DisambigDialog.show(channels, getChildFragmentManager());
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
}
