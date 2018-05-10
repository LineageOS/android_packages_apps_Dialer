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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DefaultFutureCallback;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.SupportUiListener;
import com.android.dialer.constants.ActivityRequestCodes;
import com.android.dialer.historyitemactions.DividerModule;
import com.android.dialer.historyitemactions.HistoryItemActionBottomSheet;
import com.android.dialer.historyitemactions.HistoryItemActionModule;
import com.android.dialer.historyitemactions.HistoryItemBottomSheetHeaderInfo;
import com.android.dialer.historyitemactions.IntentModule;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.precall.PreCall;
import com.android.dialer.speeddial.ContextMenu.ContextMenuItemListener;
import com.android.dialer.speeddial.FavoritesViewHolder.FavoriteContactsListener;
import com.android.dialer.speeddial.HeaderViewHolder.SpeedDialHeaderListener;
import com.android.dialer.speeddial.SuggestionViewHolder.SuggestedContactsListener;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.android.dialer.speeddial.draghelper.SpeedDialItemTouchHelperCallback;
import com.android.dialer.speeddial.draghelper.SpeedDialLayoutManager;
import com.android.dialer.speeddial.loader.SpeedDialUiItem;
import com.android.dialer.speeddial.loader.UiItemLoaderComponent;
import com.android.dialer.util.IntentUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
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
  private final SpeedDialSuggestedListener suggestedListener = new SpeedDialSuggestedListener();

  private SpeedDialAdapter adapter;
  private SupportUiListener<ImmutableList<SpeedDialUiItem>> speedDialLoaderListener;
  private SpeedDialFavoritesListener favoritesListener;

  /**
   * We update the UI every time the fragment is resumed. This boolean suppresses that functionality
   * once per onResume call.
   */
  private boolean updateSpeedDialItemsOnResume = true;

  public static SpeedDialFragment newInstance() {
    return new SpeedDialFragment();
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    LogUtil.enterBlock("SpeedDialFragment.onCreateView");
    View rootLayout = inflater.inflate(R.layout.fragment_speed_dial, container, false);

    speedDialLoaderListener =
        DialerExecutorComponent.get(getContext())
            .createUiListener(getChildFragmentManager(), "speed_dial_loader_listener");

    // Setup our RecyclerView
    SpeedDialLayoutManager layoutManager =
        new SpeedDialLayoutManager(getContext(), 3 /* spanCount */);
    favoritesListener =
        new SpeedDialFavoritesListener(
            getActivity(),
            getChildFragmentManager(),
            new SpeedDialContextMenuItemListener(
                getActivity(), new UpdateSpeedDialAdapterListener(), speedDialLoaderListener),
            layoutManager);
    adapter =
        new SpeedDialAdapter(getContext(), favoritesListener, suggestedListener, headerListener);
    layoutManager.setSpanSizeLookup(adapter.getSpanSizeLookup());
    RecyclerView recyclerView = rootLayout.findViewById(R.id.speed_dial_recycler_view);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(adapter);

    // Setup drag and drop touch helper
    ItemTouchHelper.Callback callback = new SpeedDialItemTouchHelperCallback(adapter);
    ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
    touchHelper.attachToRecyclerView(recyclerView);
    adapter.setItemTouchHelper(touchHelper);
    return rootLayout;
  }

  @Override
  public void onResume() {
    super.onResume();
    if (!updateSpeedDialItemsOnResume) {
      updateSpeedDialItemsOnResume = true;
      return;
    }

    speedDialLoaderListener.listen(
        getContext(),
        UiItemLoaderComponent.get(getContext()).speedDialUiItemMutator().loadSpeedDialUiItems(),
        this::onSpeedDialUiItemListLoaded,
        throwable -> {
          throw new RuntimeException(throwable);
        });
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == ActivityRequestCodes.SPEED_DIAL_ADD_FAVORITE) {
      if (resultCode == AppCompatActivity.RESULT_OK && data.getData() != null) {
        updateSpeedDialItemsOnResume = false;
        speedDialLoaderListener.listen(
            getContext(),
            UiItemLoaderComponent.get(getContext())
                .speedDialUiItemMutator()
                .starContact(data.getData()),
            this::onSpeedDialUiItemListLoaded,
            throwable -> {
              throw new RuntimeException(throwable);
            });
      }
    }
  }

  private void onSpeedDialUiItemListLoaded(ImmutableList<SpeedDialUiItem> speedDialUiItems) {
    LogUtil.enterBlock("SpeedDialFragment.onSpeedDialUiItemListLoaded");
    // TODO(calderwoodra): Use DiffUtil to properly update and animate the change
    adapter.setSpeedDialUiItems(
        UiItemLoaderComponent.get(getContext())
            .speedDialUiItemMutator()
            .insertDuoChannels(getContext(), speedDialUiItems));
    adapter.notifyDataSetChanged();
    if (getActivity() != null) {
      FragmentUtils.getParentUnsafe(this, HostInterface.class)
          .setHasFrequents(adapter.hasFrequents());
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    favoritesListener.hideMenu();
    Futures.addCallback(
        DialerExecutorComponent.get(getContext())
            .backgroundExecutor()
            .submit(
                () -> {
                  UiItemLoaderComponent.get(getContext())
                      .speedDialUiItemMutator()
                      .updatePinnedPosition(adapter.getSpeedDialUiItems());
                  return null;
                }),
        new DefaultFutureCallback<>(),
        DialerExecutorComponent.get(getContext()).backgroundExecutor());
    suggestedListener.onPause();
  }

  private class SpeedDialFragmentHeaderListener implements SpeedDialHeaderListener {

    @Override
    public void onAddFavoriteClicked() {
      Intent intent = new Intent(Intent.ACTION_PICK, Phone.CONTENT_URI);
      startActivityForResult(intent, ActivityRequestCodes.SPEED_DIAL_ADD_FAVORITE);
    }
  }

  private static final class SpeedDialFavoritesListener implements FavoriteContactsListener {

    private final FragmentActivity activity;
    private final FragmentManager childFragmentManager;
    private final ContextMenuItemListener contextMenuListener;
    private final SpeedDialLayoutManager layoutManager;

    private ContextMenu contextMenu;

    SpeedDialFavoritesListener(
        FragmentActivity activity,
        FragmentManager childFragmentManager,
        ContextMenuItemListener contextMenuListener,
        SpeedDialLayoutManager layoutManager) {
      this.activity = activity;
      this.childFragmentManager = childFragmentManager;
      this.contextMenuListener = contextMenuListener;
      this.layoutManager = layoutManager;
    }

    @Override
    public void onAmbiguousContactClicked(SpeedDialUiItem speedDialUiItem) {
      // If there is only one channel, skip the menu and place a call directly
      if (speedDialUiItem.channels().size() == 1) {
        onClick(speedDialUiItem.channels().get(0));
        return;
      }

      DisambigDialog.show(speedDialUiItem, childFragmentManager);
    }

    @Override
    public void onClick(Channel channel) {
      if (channel.technology() == Channel.DUO) {
        Logger.get(activity)
            .logImpression(DialerImpression.Type.LIGHTBRINGER_VIDEO_REQUESTED_FOR_FAVORITE_CONTACT);
      }

      PreCall.start(
          activity,
          new CallIntentBuilder(channel.number(), CallInitiationType.Type.SPEED_DIAL)
              .setIsVideoCall(channel.isVideoTechnology())
              .setIsDuoCall(channel.technology() == Channel.DUO));
    }

    @Override
    public void showContextMenu(View view, SpeedDialUiItem speedDialUiItem) {
      layoutManager.setScrollEnabled(false);
      contextMenu = ContextMenu.show(activity, view, contextMenuListener, speedDialUiItem);
    }

    @Override
    public void onTouchFinished(boolean closeContextMenu) {
      layoutManager.setScrollEnabled(true);

      if (closeContextMenu) {
        contextMenu.hide();
        contextMenu = null;
      }
    }

    public void hideMenu() {
      if (contextMenu != null) {
        contextMenu.hide();
        contextMenu = null;
      }
    }
  }

  private final class SpeedDialSuggestedListener implements SuggestedContactsListener {

    private HistoryItemActionBottomSheet bottomSheet;

    @Override
    public void onOverFlowMenuClicked(
        SpeedDialUiItem speedDialUiItem, HistoryItemBottomSheetHeaderInfo headerInfo) {
      List<HistoryItemActionModule> modules = new ArrayList<>();
      Channel defaultChannel = speedDialUiItem.defaultChannel();

      // Add voice call module
      Channel voiceChannel = speedDialUiItem.getDefaultVoiceChannel();
      if (voiceChannel != null) {
        modules.add(
            IntentModule.newCallModule(
                getContext(),
                new CallIntentBuilder(voiceChannel.number(), CallInitiationType.Type.SPEED_DIAL)
                    .setAllowAssistedDial(true)));
      }

      // Add video if we can determine the correct channel
      Channel videoChannel = speedDialUiItem.getDefaultVideoChannel();
      if (videoChannel != null) {
        modules.add(
            IntentModule.newCallModule(
                getContext(),
                new CallIntentBuilder(videoChannel.number(), CallInitiationType.Type.SPEED_DIAL)
                    .setIsVideoCall(true)
                    .setAllowAssistedDial(true)));
      }

      // Add sms module
      if (!TextUtils.isEmpty(defaultChannel.number())) {
        modules.add(
            IntentModule.newModuleForSendingTextMessage(getContext(), defaultChannel.number()));
      }

      modules.add(new DividerModule());

      modules.add(new StarContactModule(speedDialUiItem));
      // TODO(calderwoodra): remove from strequent module

      // Contact info module
      modules.add(
          new ContactInfoModule(
              getContext(),
              new Intent(
                  Intent.ACTION_VIEW,
                  Uri.withAppendedPath(
                      Contacts.CONTENT_URI, String.valueOf(speedDialUiItem.contactId()))),
              R.string.contact_menu_contact_info,
              R.drawable.context_menu_contact_icon));

      bottomSheet = HistoryItemActionBottomSheet.show(getContext(), headerInfo, modules);
    }

    @Override
    public void onRowClicked(Channel channel) {
      if (channel.technology() == Channel.DUO) {
        Logger.get(getContext())
            .logImpression(
                DialerImpression.Type.LIGHTBRINGER_VIDEO_REQUESTED_FOR_SUGGESTED_CONTACT);
      }
      PreCall.start(
          getContext(),
          new CallIntentBuilder(channel.number(), CallInitiationType.Type.SPEED_DIAL)
              .setIsVideoCall(channel.isVideoTechnology())
              .setIsDuoCall(channel.technology() == Channel.DUO));
    }

    private final class StarContactModule implements HistoryItemActionModule {

      private final SpeedDialUiItem speedDialUiItem;

      StarContactModule(SpeedDialUiItem speedDialUiItem) {
        this.speedDialUiItem = speedDialUiItem;
      }

      @Override
      public int getStringId() {
        return R.string.suggested_contact_bottom_sheet_add_favorite_option;
      }

      @Override
      public int getDrawableId() {
        return R.drawable.quantum_ic_star_vd_theme_24;
      }

      @Override
      public boolean onClick() {
        speedDialLoaderListener.listen(
            getContext(),
            UiItemLoaderComponent.get(getContext())
                .speedDialUiItemMutator()
                .starContact(
                    Uri.withAppendedPath(
                        Phone.CONTENT_FILTER_URI, speedDialUiItem.defaultChannel().number())),
            SpeedDialFragment.this::onSpeedDialUiItemListLoaded,
            throwable -> {
              throw new RuntimeException(throwable);
            });
        return true;
      }
    }

    private final class ContactInfoModule extends IntentModule {

      public ContactInfoModule(Context context, Intent intent, int text, int image) {
        super(context, intent, text, image);
      }

      @Override
      public boolean tintDrawable() {
        return false;
      }
    }

    public void onPause() {
      if (bottomSheet != null && bottomSheet.isShowing()) {
        bottomSheet.dismiss();
      }
    }
  }

  private static final class SpeedDialContextMenuItemListener implements ContextMenuItemListener {

    private final FragmentActivity activity;
    private final SupportUiListener<ImmutableList<SpeedDialUiItem>> speedDialLoaderListener;
    private final UpdateSpeedDialAdapterListener updateAdapterListener;

    SpeedDialContextMenuItemListener(
        FragmentActivity activity,
        UpdateSpeedDialAdapterListener updateAdapterListener,
        SupportUiListener<ImmutableList<SpeedDialUiItem>> speedDialLoaderListener) {
      this.activity = activity;
      this.updateAdapterListener = updateAdapterListener;
      this.speedDialLoaderListener = speedDialLoaderListener;
    }

    @Override
    public void placeCall(Channel channel) {
      if (channel.technology() == Channel.DUO) {
        Logger.get(activity)
            .logImpression(DialerImpression.Type.LIGHTBRINGER_VIDEO_REQUESTED_FOR_FAVORITE_CONTACT);
      }
      PreCall.start(
          activity,
          new CallIntentBuilder(channel.number(), CallInitiationType.Type.SPEED_DIAL)
              .setIsVideoCall(channel.isVideoTechnology())
              .setIsDuoCall(channel.technology() == Channel.DUO));
    }

    @Override
    public void openSmsConversation(String number) {
      activity.startActivity(IntentUtil.getSendSmsIntent(number));
    }

    @Override
    public void removeFavoriteContact(SpeedDialUiItem speedDialUiItem) {
      speedDialLoaderListener.listen(
          activity,
          UiItemLoaderComponent.get(activity)
              .speedDialUiItemMutator()
              .removeSpeedDialUiItem(speedDialUiItem),
          updateAdapterListener::updateAdapter,
          throwable -> {
            throw new RuntimeException(throwable);
          });
    }

    @Override
    public void openContactInfo(SpeedDialUiItem speedDialUiItem) {
      activity.startActivity(
          new Intent(
              Intent.ACTION_VIEW,
              Uri.withAppendedPath(
                  Contacts.CONTENT_URI, String.valueOf(speedDialUiItem.contactId()))));
    }
  }

  /** Listener for when a SpeedDialUiItem is updated. */
  private class UpdateSpeedDialAdapterListener {

    void updateAdapter(ImmutableList<SpeedDialUiItem> speedDialUiItems) {
      onSpeedDialUiItemListLoaded(speedDialUiItems);
    }
  }

  /** Interface for {@link SpeedDialFragment} to communicate with its host/parent. */
  public interface HostInterface {

    void setHasFrequents(boolean hasFrequents);
  }
}
