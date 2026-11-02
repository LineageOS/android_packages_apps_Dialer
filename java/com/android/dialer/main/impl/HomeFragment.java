/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.main.impl;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.dialer.R;
import com.android.dialer.app.calllog.CallLogFragment;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.SupportUiListener;
import com.android.dialer.database.CallLogQueryHandler;
import com.android.dialer.glidephotomanager.GlidePhotoManagerComponent;
import com.android.dialer.precall.PreCall;
import com.android.dialer.speeddial.DisambigDialog;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.android.dialer.speeddial.loader.SpeedDialUiItem;
import com.android.dialer.speeddial.loader.UiItemLoaderComponent;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.PermissionsUtil;

import com.google.android.material.chip.ChipGroup;
import com.google.common.collect.ImmutableList;

public final class HomeFragment extends Fragment {

  private static final String CALL_LOG_TAG = "home_call_log";

  private View favorites;
  private LinearLayout favoritesRow;
  private SupportUiListener<ImmutableList<SpeedDialUiItem>> favoritesListener;

  private final ActivityResultLauncher<Intent> addFavoriteLauncher =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            Intent data = result.getData();
            if (result.getResultCode() == Activity.RESULT_OK
                && data != null
                && data.getData() != null) {
              favoritesListener.listen(
                  requireContext(),
                  UiItemLoaderComponent.get(requireContext())
                      .speedDialUiItemMutator()
                      .starContact(data.getData()),
                  this::bindFavorites,
                  throwable -> {});
            }
          });

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
    View view = inflater.inflate(R.layout.home_fragment, container, false);
    favorites = view.findViewById(R.id.home_favorites);
    favoritesRow = view.findViewById(R.id.home_favorites_row);
    favoritesListener =
        DialerExecutorComponent.get(requireContext())
            .createUiListener(getChildFragmentManager(), "home_favorites_listener");

    ChipGroup filters = view.findViewById(R.id.home_filters);
    filters.setOnCheckedStateChangeListener(
        (group, checkedIds) -> {
          Fragment callLog = getChildFragmentManager().findFragmentByTag(CALL_LOG_TAG);
          if (callLog instanceof CallLogFragment) {
            ((CallLogFragment) callLog).setCallTypeFilter(getFilter(group.getCheckedChipId()));
          }
        });

    TextView header = view.findViewById(R.id.home_favorites_header);
    header.setOnClickListener(
        v -> {
          boolean show = favorites.getVisibility() != View.VISIBLE;
          favorites.setVisibility(show ? View.VISIBLE : View.GONE);
          header.setCompoundDrawablesRelativeWithIntrinsicBounds(
              0, 0, show ? R.drawable.ic_expand_less_24 : R.drawable.ic_expand_more_24, 0);
        });

    view.findViewById(R.id.home_view_contacts)
        .setOnClickListener(
            v ->
                DialerUtils.startActivityWithErrorToast(
                    requireContext(), new Intent(Intent.ACTION_VIEW, Contacts.CONTENT_URI)));
    view.findViewById(R.id.home_add_favorite)
        .setOnClickListener(
            v -> addFavoriteLauncher.launch(new Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)));
    return view;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
    super.onViewCreated(view, state);
    if (getChildFragmentManager().findFragmentByTag(CALL_LOG_TAG) == null) {
      getChildFragmentManager()
          .beginTransaction()
          .add(R.id.home_call_log_container, new CallLogFragment(), CALL_LOG_TAG)
          .commit();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    loadFavorites();
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    if (!hidden) {
      loadFavorites();
    }
  }

  private void loadFavorites() {
    if (favoritesListener == null
        || !PermissionsUtil.hasContactsReadPermissions(requireContext())) {
      return;
    }
    favoritesListener.listen(
        requireContext(),
        UiItemLoaderComponent.get(requireContext()).speedDialUiItemMutator().loadSpeedDialUiItems(),
        this::bindFavorites,
        throwable -> {});
  }

  private void bindFavorites(ImmutableList<SpeedDialUiItem> items) {
    Context context = getContext();
    if (context == null || favoritesRow == null) {
      return;
    }
    favoritesRow.removeViews(0, favoritesRow.getChildCount() - 1);
    LayoutInflater inflater = LayoutInflater.from(context);
    for (SpeedDialUiItem item : items) {
      if (!item.isStarred()) {
        continue;
      }
      View view = inflater.inflate(R.layout.home_favorite_item, favoritesRow, false);
      GlidePhotoManagerComponent.get(context)
          .glidePhotoManager()
          .loadContactPhoto(view.findViewById(R.id.home_favorite_photo), item.getPhotoInfo());
      ((TextView) view.findViewById(R.id.home_favorite_name)).setText(item.name());
      view.setOnClickListener(v -> callFavorite(item));
      view.setOnLongClickListener(
          v -> {
            DialerUtils.startActivityWithErrorToast(
                context,
                new Intent(
                    Intent.ACTION_VIEW,
                    Uri.withAppendedPath(Contacts.CONTENT_URI, String.valueOf(item.contactId()))));
            return true;
          });
      favoritesRow.addView(view, favoritesRow.getChildCount() - 1);
    }
  }

  private void callFavorite(SpeedDialUiItem item) {
    Channel channel = item.defaultChannel();
    if (channel == null && item.channels().size() == 1) {
      channel = item.channels().get(0);
    }
    if (channel == null) {
      DisambigDialog.show(item, getChildFragmentManager());
      return;
    }
    PreCall.start(
        requireContext(),
        new CallIntentBuilder(channel.number(), CallInitiationType.Type.SPEED_DIAL)
            .setAllowAssistedDial(true)
            .setIsVideoCall(channel.isVideoTechnology()));
  }

  private static int getFilter(int checkedId) {
    if (checkedId == R.id.home_filter_missed) {
      return Calls.MISSED_TYPE;
    } else if (checkedId == R.id.home_filter_contacts) {
      return CallLogQueryHandler.CALL_TYPE_CONTACTS;
    } else if (checkedId == R.id.home_filter_incoming) {
      return Calls.INCOMING_TYPE;
    } else if (checkedId == R.id.home_filter_outgoing) {
      return Calls.OUTGOING_TYPE;
    }
    return CallLogQueryHandler.CALL_TYPE_ALL;
  }
}
