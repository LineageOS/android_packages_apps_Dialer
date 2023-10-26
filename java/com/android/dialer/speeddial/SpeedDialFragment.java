/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

import static android.app.Activity.RESULT_OK;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.android.dialer.R;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DefaultFutureCallback;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.SupportUiListener;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.historyitemactions.DividerModule;
import com.android.dialer.historyitemactions.HistoryItemActionBottomSheet;
import com.android.dialer.historyitemactions.HistoryItemActionModule;
import com.android.dialer.historyitemactions.HistoryItemBottomSheetHeaderInfo;
import com.android.dialer.historyitemactions.IntentModule;
import com.android.dialer.precall.PreCall;
import com.android.dialer.shortcuts.ShortcutRefresher;
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
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.widget.EmptyContentView;
import com.android.dialer.widget.EmptyContentView.OnEmptyViewActionButtonClickedListener;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;

import java.util.ArrayList;
import java.util.Arrays;
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

  /** Listen for changes to the strequents content observer. */
  private final ContentObserver strequentsContentObserver =
      new ContentObserver(ThreadUtil.getUiThreadHandler()) {
        @Override
        public void onChange(boolean selfChange) {
          super.onChange(selfChange);
          loadContacts();
        }
      };

  private final SpeedDialHeaderListener headerListener = new SpeedDialFragmentHeaderListener();
  private final SpeedDialSuggestedListener suggestedListener = new SpeedDialSuggestedListener();

  private SpeedDialAdapter adapter;
  private SupportUiListener<ImmutableList<SpeedDialUiItem>> speedDialLoaderListener;
  private SpeedDialFavoritesListener favoritesListener;

  private EmptyContentView emptyContentView;

  /**
   * We update the UI every time the fragment is resumed. This boolean suppresses that functionality
   * once per onResume call.
   */
  private boolean updateSpeedDialItemsOnResume = true;

  private final ActivityResultLauncher<String[]> contactPermissionLauncher =
          registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                  grantResults -> {
                    if (grantResults.size() >= 1 && grantResults.values().iterator().next()) {
                      PermissionsUtil.notifyPermissionGranted(getContext(),
                              Manifest.permission.READ_CONTACTS);
                      loadContacts();
                    }
                  });

  private final ActivityResultLauncher<Intent> addFavoriteLauncher = registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent data = result.getData();
            if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null) {
              updateSpeedDialItemsOnResume = false;
              speedDialLoaderListener.listen(
                      getContext(),
                      UiItemLoaderComponent.get(requireContext())
                              .speedDialUiItemMutator()
                              .starContact(data.getData()),
                      this::onSpeedDialUiItemListLoaded,
                      throwable -> {
                        throw new RuntimeException(throwable);
                      });
            }
          });

  public static SpeedDialFragment newInstance() {
    return new SpeedDialFragment();
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    LogUtil.enterBlock("SpeedDialFragment.onCreateView");
    View rootLayout = inflater.inflate(R.layout.fragment_speed_dial, container, false);
    emptyContentView = rootLayout.findViewById(R.id.speed_dial_empty_content_view);
    emptyContentView.setImage(R.drawable.empty_speed_dial);

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
            layoutManager,
            new UpdateSpeedDialAdapterListener(),
            speedDialLoaderListener);
    adapter =
        new SpeedDialAdapter(
            getContext(),
            favoritesListener,
            suggestedListener,
            headerListener,
            FragmentUtils.getParentUnsafe(this, HostInterface.class));
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
    loadContacts();
  }

  @Override
  public void onPause() {
    super.onPause();
    favoritesListener.hideMenu();
    suggestedListener.onPause();
    onHidden();
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    if (hidden) {
      onHidden();
    } else {
      loadContacts();
    }
  }

  private void onHidden() {
    if (!PermissionsUtil.hasContactsReadPermissions(getContext())) {
      return;
    }

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
    ShortcutRefresher.refresh(
        getContext(),
        ShortcutRefresher.speedDialUiItemsToContactEntries(adapter.getSpeedDialUiItems()));
  }

  private void loadContacts() {
    if (!updateSpeedDialItemsOnResume) {
      updateSpeedDialItemsOnResume = true;
      return;
    }

    if (showContactsPermissionEmptyContentView()) {
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

  private void onSpeedDialUiItemListLoaded(ImmutableList<SpeedDialUiItem> speedDialUiItems) {
    LogUtil.enterBlock("SpeedDialFragment.onSpeedDialUiItemListLoaded");
    // TODO(calderwoodra): Use DiffUtil to properly update and animate the change
    adapter.setSpeedDialUiItems(speedDialUiItems);
    adapter.notifyDataSetChanged();
    maybeShowNoContactsEmptyContentView();

    if (getActivity() != null) {
      FragmentUtils.getParentUnsafe(this, HostInterface.class)
          .setHasFrequents(adapter.hasFrequents());
    }
  }

  /** Returns true if the empty content view was shown. */
  private boolean showContactsPermissionEmptyContentView() {
    if (PermissionsUtil.hasContactsReadPermissions(getContext())) {
      emptyContentView.setVisibility(View.GONE);
      return false;
    }

    emptyContentView.setVisibility(View.VISIBLE);
    emptyContentView.setActionLabel(R.string.speed_dial_turn_on_contacts_permission);
    emptyContentView.setDescription(R.string.speed_dial_contacts_permission_description);
    emptyContentView.setActionClickedListener(
        new SpeedDialContactPermissionEmptyViewListener(getContext(), this,
                contactPermissionLauncher));
    return true;
  }

  private void maybeShowNoContactsEmptyContentView() {
    if (adapter.getItemCount() != 0) {
      emptyContentView.setVisibility(View.GONE);
      return;
    }

    emptyContentView.setVisibility(View.VISIBLE);
    emptyContentView.setActionLabel(R.string.speed_dial_no_contacts_action_text);
    emptyContentView.setDescription(R.string.speed_dial_no_contacts_description);
    emptyContentView.setActionClickedListener(
            new SpeedDialNoContactsEmptyViewListener(addFavoriteLauncher));
  }

  @Override
  public void onStart() {
    super.onStart();
    PermissionsUtil.registerPermissionReceiver(
        getActivity(), readContactsPermissionGrantedReceiver, Manifest.permission.READ_CONTACTS);
    if (PermissionsUtil.hasContactsReadPermissions(getContext())) {
      getContext()
          .getContentResolver()
          .registerContentObserver(Contacts.CONTENT_STREQUENT_URI, true, strequentsContentObserver);
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    PermissionsUtil.unregisterPermissionReceiver(
        getContext(), readContactsPermissionGrantedReceiver);
    getContext().getContentResolver().unregisterContentObserver(strequentsContentObserver);
  }

  private class SpeedDialFragmentHeaderListener implements SpeedDialHeaderListener {

    @Override
    public void onAddFavoriteClicked() {
      Intent intent = new Intent(Intent.ACTION_PICK, Phone.CONTENT_URI);
      addFavoriteLauncher.launch(intent);
    }
  }

  private static final class SpeedDialFavoritesListener implements FavoriteContactsListener {

    private final FragmentActivity activity;
    private final FragmentManager childFragmentManager;
    private final SpeedDialLayoutManager layoutManager;
    private final UpdateSpeedDialAdapterListener updateAdapterListener;
    private final SupportUiListener<ImmutableList<SpeedDialUiItem>> speedDialLoaderListener;

    private final SpeedDialContextMenuItemListener speedDialContextMenuItemListener =
        new SpeedDialContextMenuItemListener();

    private ContextMenu contextMenu;

    SpeedDialFavoritesListener(
        FragmentActivity activity,
        FragmentManager childFragmentManager,
        SpeedDialLayoutManager layoutManager,
        UpdateSpeedDialAdapterListener updateAdapterListener,
        SupportUiListener<ImmutableList<SpeedDialUiItem>> speedDialLoaderListener) {
      this.activity = activity;
      this.childFragmentManager = childFragmentManager;
      this.layoutManager = layoutManager;
      this.updateAdapterListener = updateAdapterListener;
      this.speedDialLoaderListener = speedDialLoaderListener;
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
      PreCall.start(
          activity,
          new CallIntentBuilder(channel.number(), CallInitiationType.Type.SPEED_DIAL)
              .setAllowAssistedDial(true)
              .setIsVideoCall(channel.isVideoTechnology()));
    }

    @Override
    public void showContextMenu(View view, SpeedDialUiItem speedDialUiItem) {
      layoutManager.setScrollEnabled(false);
      contextMenu =
          ContextMenu.show(activity, view, speedDialContextMenuItemListener, speedDialUiItem);
    }

    @Override
    public void onTouchFinished(boolean closeContextMenu) {
      layoutManager.setScrollEnabled(true);

      if (closeContextMenu) {
        contextMenu.hide();
        contextMenu = null;
      }
    }

    @Override
    public void onRequestRemove(SpeedDialUiItem speedDialUiItem) {
      speedDialContextMenuItemListener.removeFavoriteContact(speedDialUiItem);
    }

    void hideMenu() {
      if (contextMenu != null) {
        contextMenu.hide();
        contextMenu = null;
      }
    }

    public SpeedDialContextMenuItemListener getSpeedDialContextMenuItemListener() {
      return speedDialContextMenuItemListener;
    }

    class SpeedDialContextMenuItemListener implements ContextMenuItemListener {

      @Override
      public void placeCall(Channel channel) {
        PreCall.start(
            activity,
            new CallIntentBuilder(channel.number(), CallInitiationType.Type.SPEED_DIAL)
                .setAllowAssistedDial(true)
                .setIsVideoCall(channel.isVideoTechnology()));
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
              R.drawable.quantum_ic_person_vd_theme_24));

      bottomSheet = HistoryItemActionBottomSheet.show(getContext(), headerInfo, modules);
    }

    @Override
    public void onRowClicked(Channel channel) {
      PreCall.start(
          getContext(),
          new CallIntentBuilder(channel.number(), CallInitiationType.Type.SPEED_DIAL)
              .setAllowAssistedDial(true)
              .setIsVideoCall(channel.isVideoTechnology()));
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

  private static final class SpeedDialContactPermissionEmptyViewListener
      implements OnEmptyViewActionButtonClickedListener {

    private final Context context;
    private final Fragment fragment;

    private final ActivityResultLauncher<String[]> contactPermissionLauncher;

    private SpeedDialContactPermissionEmptyViewListener(Context context, Fragment fragment,
                                                        ActivityResultLauncher<String[]>
                                                                contactPermissionLauncher) {
      this.context = context;
      this.fragment = fragment;
      this.contactPermissionLauncher = contactPermissionLauncher;
    }

    @Override
    public void onEmptyViewActionButtonClicked() {
      String[] deniedPermissions =
          PermissionsUtil.getPermissionsCurrentlyDenied(
              context, PermissionsUtil.allContactsGroupPermissionsUsedInDialer);
      Assert.checkArgument(deniedPermissions.length > 0);
      LogUtil.i(
          "OldSpeedDialFragment.onEmptyViewActionButtonClicked",
          "Requesting permissions: " + Arrays.toString(deniedPermissions));
      contactPermissionLauncher.launch(deniedPermissions);
    }
  }

  private static final class SpeedDialNoContactsEmptyViewListener
      implements OnEmptyViewActionButtonClickedListener {

    private final ActivityResultLauncher<Intent> addFavoriteLauncher;

    SpeedDialNoContactsEmptyViewListener(ActivityResultLauncher<Intent> addFavoriteLauncher) {
      this.addFavoriteLauncher = addFavoriteLauncher;
    }

    @Override
    public void onEmptyViewActionButtonClicked() {
      Intent intent = new Intent(Intent.ACTION_PICK, Phone.CONTENT_URI);
      addFavoriteLauncher.launch(intent);
    }
  }

  /** Listener for when a SpeedDialUiItem is updated. */
  class UpdateSpeedDialAdapterListener {

    void updateAdapter(ImmutableList<SpeedDialUiItem> speedDialUiItems) {
      onSpeedDialUiItemListLoaded(speedDialUiItems);
    }
  }

  /** Interface for {@link SpeedDialFragment} to communicate with its host/parent. */
  public interface HostInterface {

    void setHasFrequents(boolean hasFrequents);

    void dragFavorite(boolean start);
  }
}
