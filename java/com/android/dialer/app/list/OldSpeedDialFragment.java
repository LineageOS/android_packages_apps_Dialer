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

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Trace;
import android.support.v13.app.FragmentCompat;
import android.support.v4.util.LongSparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.ListView;
import com.android.contacts.common.ContactTileLoaderFactory;
import com.android.contacts.common.list.ContactTileView;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.dialer.app.R;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.common.LogUtil;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.util.ViewUtil;
import com.android.dialer.widget.EmptyContentView;
import java.util.ArrayList;
import java.util.Arrays;

/** This fragment displays the user's favorite/frequent contacts in a grid. */
public class OldSpeedDialFragment extends Fragment
    implements OnItemClickListener,
        PhoneFavoritesTileAdapter.OnDataSetChangedForAnimationListener,
        EmptyContentView.OnEmptyViewActionButtonClickedListener,
        FragmentCompat.OnRequestPermissionsResultCallback {

  private static final int READ_CONTACTS_PERMISSION_REQUEST_CODE = 1;

  /**
   * By default, the animation code assumes that all items in a list view are of the same height
   * when animating new list items into view (e.g. from the bottom of the screen into view). This
   * can cause incorrect translation offsets when a item that is larger or smaller than other list
   * item is removed from the list. This key is used to provide the actual height of the removed
   * object so that the actual translation appears correct to the user.
   */
  private static final long KEY_REMOVED_ITEM_HEIGHT = Long.MAX_VALUE;

  private static final String TAG = "OldSpeedDialFragment";
  private static final boolean DEBUG = false;
  /** Used with LoaderManager. */
  private static final int LOADER_ID_CONTACT_TILE = 1;

  private final LongSparseArray<Integer> itemIdTopMap = new LongSparseArray<>();
  private final LongSparseArray<Integer> itemIdLeftMap = new LongSparseArray<>();
  private final ContactTileView.Listener contactTileAdapterListener =
      new ContactTileAdapterListener();
  private final LoaderManager.LoaderCallbacks<Cursor> contactTileLoaderListener =
      new ContactTileLoaderListener();
  private final ScrollListener scrollListener = new ScrollListener();
  private int animationDuration;
  private OnPhoneNumberPickerActionListener phoneNumberPickerActionListener;
  private OnListFragmentScrolledListener activityScrollListener;
  private PhoneFavoritesTileAdapter contactTileAdapter;
  private View parentView;
  private PhoneFavoriteListView listView;
  private View contactTileFrame;
  /** Layout used when there are no favorites. */
  private EmptyContentView emptyView;

  @Override
  public void onCreate(Bundle savedState) {
    if (DEBUG) {
      LogUtil.d("OldSpeedDialFragment.onCreate", null);
    }
    Trace.beginSection(TAG + " onCreate");
    super.onCreate(savedState);

    // Construct two base adapters which will become part of PhoneFavoriteMergedAdapter.
    // We don't construct the resultant adapter at this moment since it requires LayoutInflater
    // that will be available on onCreateView().
    contactTileAdapter =
        new PhoneFavoritesTileAdapter(getActivity(), contactTileAdapterListener, this);
    contactTileAdapter.setPhotoLoader(ContactPhotoManager.getInstance(getActivity()));
    animationDuration = getResources().getInteger(R.integer.fade_duration);
    Trace.endSection();
  }

  @Override
  public void onResume() {
    Trace.beginSection(TAG + " onResume");
    super.onResume();
    if (contactTileAdapter != null) {
      contactTileAdapter.refreshContactsPreferences();
    }
    if (PermissionsUtil.hasContactsReadPermissions(getActivity())) {
      if (getLoaderManager().getLoader(LOADER_ID_CONTACT_TILE) == null) {
        getLoaderManager().initLoader(LOADER_ID_CONTACT_TILE, null, contactTileLoaderListener);

      } else {
        getLoaderManager().getLoader(LOADER_ID_CONTACT_TILE).forceLoad();
      }

      emptyView.setDescription(R.string.speed_dial_empty);
      emptyView.setActionLabel(R.string.speed_dial_empty_add_favorite_action);
    } else {
      emptyView.setDescription(R.string.permission_no_speeddial);
      emptyView.setActionLabel(R.string.permission_single_turn_on);
    }
    Trace.endSection();
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    Trace.beginSection(TAG + " onCreateView");
    parentView = inflater.inflate(R.layout.speed_dial_fragment, container, false);

    listView = (PhoneFavoriteListView) parentView.findViewById(R.id.contact_tile_list);
    listView.setOnItemClickListener(this);
    listView.setVerticalScrollBarEnabled(false);
    listView.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);
    listView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);
    listView.getDragDropController().addOnDragDropListener(contactTileAdapter);

    final ImageView dragShadowOverlay =
        (ImageView) getActivity().findViewById(R.id.contact_tile_drag_shadow_overlay);
    listView.setDragShadowOverlay(dragShadowOverlay);

    emptyView = (EmptyContentView) parentView.findViewById(R.id.empty_list_view);
    emptyView.setImage(R.drawable.empty_speed_dial);
    emptyView.setActionClickedListener(this);

    contactTileFrame = parentView.findViewById(R.id.contact_tile_frame);

    final LayoutAnimationController controller =
        new LayoutAnimationController(
            AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in));
    controller.setDelay(0);
    listView.setLayoutAnimation(controller);
    listView.setAdapter(contactTileAdapter);

    listView.setOnScrollListener(scrollListener);
    listView.setFastScrollEnabled(false);
    listView.setFastScrollAlwaysVisible(false);

    // prevent content changes of the list from firing accessibility events.
    listView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
    ContentChangedFilter.addToParent(listView);

    Trace.endSection();
    return parentView;
  }

  public boolean hasFrequents() {
    if (contactTileAdapter == null) {
      return false;
    }
    return contactTileAdapter.getNumFrequents() > 0;
  }

  /* package */ void setEmptyViewVisibility(final boolean visible) {
    final int previousVisibility = emptyView.getVisibility();
    final int emptyViewVisibility = visible ? View.VISIBLE : View.GONE;
    final int listViewVisibility = visible ? View.GONE : View.VISIBLE;

    if (previousVisibility != emptyViewVisibility) {
      final FrameLayout.LayoutParams params = (LayoutParams) contactTileFrame.getLayoutParams();
      params.height = visible ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT;
      contactTileFrame.setLayoutParams(params);
      emptyView.setVisibility(emptyViewVisibility);
      listView.setVisibility(listViewVisibility);
    }
  }

  @Override
  public void onStart() {
    super.onStart();

    final Activity activity = getActivity();

    try {
      activityScrollListener = (OnListFragmentScrolledListener) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(
          activity.toString() + " must implement OnListFragmentScrolledListener");
    }

    try {
      OnDragDropListener listener = (OnDragDropListener) activity;
      listView.getDragDropController().addOnDragDropListener(listener);
      ((HostInterface) activity).setDragDropController(listView.getDragDropController());
    } catch (ClassCastException e) {
      throw new ClassCastException(
          activity.toString() + " must implement OnDragDropListener and HostInterface");
    }

    try {
      phoneNumberPickerActionListener = (OnPhoneNumberPickerActionListener) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(
          activity.toString() + " must implement PhoneFavoritesFragment.listener");
    }

    // Use initLoader() instead of restartLoader() to refraining unnecessary reload.
    // This method call implicitly assures ContactTileLoaderListener's onLoadFinished() will
    // be called, on which we'll check if "all" contacts should be reloaded again or not.
    if (PermissionsUtil.hasContactsReadPermissions(activity)) {
      getLoaderManager().initLoader(LOADER_ID_CONTACT_TILE, null, contactTileLoaderListener);
    } else {
      setEmptyViewVisibility(true);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>This is only effective for elements provided by {@link #contactTileAdapter}. {@link
   * #contactTileAdapter} has its own logic for click events.
   */
  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    final int contactTileAdapterCount = contactTileAdapter.getCount();
    if (position <= contactTileAdapterCount) {
      LogUtil.e(
          "OldSpeedDialFragment.onItemClick",
          "event for unexpected position. The position "
              + position
              + " is before \"all\" section. Ignored.");
    }
  }

  /**
   * Cache the current view offsets into memory. Once a relayout of views in the ListView has
   * happened due to a dataset change, the cached offsets are used to create animations that slide
   * views from their previous positions to their new ones, to give the appearance that the views
   * are sliding into their new positions.
   */
  private void saveOffsets(int removedItemHeight) {
    final int firstVisiblePosition = listView.getFirstVisiblePosition();
    if (DEBUG) {
      LogUtil.d("OldSpeedDialFragment.saveOffsets", "Child count : " + listView.getChildCount());
    }
    for (int i = 0; i < listView.getChildCount(); i++) {
      final View child = listView.getChildAt(i);
      final int position = firstVisiblePosition + i;
      // Since we are getting the position from mListView and then querying
      // mContactTileAdapter, its very possible that things are out of sync
      // and we might index out of bounds.  Let's make sure that this doesn't happen.
      if (!contactTileAdapter.isIndexInBound(position)) {
        continue;
      }
      final long itemId = contactTileAdapter.getItemId(position);
      if (DEBUG) {
        LogUtil.d(
            "OldSpeedDialFragment.saveOffsets",
            "Saving itemId: " + itemId + " for listview child " + i + " Top: " + child.getTop());
      }
      itemIdTopMap.put(itemId, child.getTop());
      itemIdLeftMap.put(itemId, child.getLeft());
    }
    itemIdTopMap.put(KEY_REMOVED_ITEM_HEIGHT, removedItemHeight);
  }

  /*
   * Performs animations for the gridView
   */
  private void animateGridView(final long... idsInPlace) {
    if (itemIdTopMap.size() == 0) {
      // Don't do animations if the database is being queried for the first time and
      // the previous item offsets have not been cached, or the user hasn't done anything
      // (dragging, swiping etc) that requires an animation.
      return;
    }

    ViewUtil.doOnPreDraw(
        listView,
        true,
        new Runnable() {
          @Override
          public void run() {

            final int firstVisiblePosition = listView.getFirstVisiblePosition();
            final AnimatorSet animSet = new AnimatorSet();
            final ArrayList<Animator> animators = new ArrayList<Animator>();
            for (int i = 0; i < listView.getChildCount(); i++) {
              final View child = listView.getChildAt(i);
              int position = firstVisiblePosition + i;

              // Since we are getting the position from mListView and then querying
              // mContactTileAdapter, its very possible that things are out of sync
              // and we might index out of bounds.  Let's make sure that this doesn't happen.
              if (!contactTileAdapter.isIndexInBound(position)) {
                continue;
              }

              final long itemId = contactTileAdapter.getItemId(position);

              if (containsId(idsInPlace, itemId)) {
                animators.add(ObjectAnimator.ofFloat(child, "alpha", 0.0f, 1.0f));
                break;
              } else {
                Integer startTop = itemIdTopMap.get(itemId);
                Integer startLeft = itemIdLeftMap.get(itemId);
                final int top = child.getTop();
                final int left = child.getLeft();
                int deltaX = 0;
                int deltaY = 0;

                if (startLeft != null) {
                  if (startLeft != left) {
                    deltaX = startLeft - left;
                    animators.add(ObjectAnimator.ofFloat(child, "translationX", deltaX, 0.0f));
                  }
                }

                if (startTop != null) {
                  if (startTop != top) {
                    deltaY = startTop - top;
                    animators.add(ObjectAnimator.ofFloat(child, "translationY", deltaY, 0.0f));
                  }
                }

                if (DEBUG) {
                  LogUtil.d(
                      "OldSpeedDialFragment.onPreDraw",
                      "Found itemId: "
                          + itemId
                          + " for listview child "
                          + i
                          + " Top: "
                          + top
                          + " Delta: "
                          + deltaY);
                }
              }
            }

            if (animators.size() > 0) {
              animSet.setDuration(animationDuration).playTogether(animators);
              animSet.start();
            }

            itemIdTopMap.clear();
            itemIdLeftMap.clear();
          }
        });
  }

  private boolean containsId(long[] ids, long target) {
    // Linear search on array is fine because this is typically only 0-1 elements long
    for (int i = 0; i < ids.length; i++) {
      if (ids[i] == target) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void onDataSetChangedForAnimation(long... idsInPlace) {
    animateGridView(idsInPlace);
  }

  @Override
  public void cacheOffsetsForDatasetChange() {
    saveOffsets(0);
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
          "OldSpeedDialFragment.onEmptyViewActionButtonClicked",
          "Requesting permissions: " + Arrays.toString(deniedPermissions));
      FragmentCompat.requestPermissions(
          this, deniedPermissions, READ_CONTACTS_PERMISSION_REQUEST_CODE);
    } else {
      // Switch tabs
      ((HostInterface) activity).showAllContactsTab();
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == READ_CONTACTS_PERMISSION_REQUEST_CODE) {
      if (grantResults.length == 1 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
        PermissionsUtil.notifyPermissionGranted(getActivity(), READ_CONTACTS);
      }
    }
  }

  public interface HostInterface {

    void setDragDropController(DragDropController controller);

    void showAllContactsTab();
  }

  class ContactTileLoaderListener implements LoaderManager.LoaderCallbacks<Cursor> {

    @Override
    public CursorLoader onCreateLoader(int id, Bundle args) {
      if (DEBUG) {
        LogUtil.d("ContactTileLoaderListener.onCreateLoader", null);
      }
      return ContactTileLoaderFactory.createStrequentPhoneOnlyLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
      if (DEBUG) {
        LogUtil.d("ContactTileLoaderListener.onLoadFinished", null);
      }
      contactTileAdapter.setContactCursor(data);
      setEmptyViewVisibility(contactTileAdapter.getCount() == 0);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
      if (DEBUG) {
        LogUtil.d("ContactTileLoaderListener.onLoaderReset", null);
      }
    }
  }

  private class ContactTileAdapterListener implements ContactTileView.Listener {

    @Override
    public void onContactSelected(
        Uri contactUri, Rect targetRect, CallSpecificAppData callSpecificAppData) {
      if (phoneNumberPickerActionListener != null) {
        phoneNumberPickerActionListener.onPickDataUri(
            contactUri, false /* isVideoCall */, callSpecificAppData);
      }
    }

    @Override
    public void onCallNumberDirectly(String phoneNumber, CallSpecificAppData callSpecificAppData) {
      if (phoneNumberPickerActionListener != null) {
        phoneNumberPickerActionListener.onPickPhoneNumber(
            phoneNumber, false /* isVideoCall */, callSpecificAppData);
      }
    }
  }

  private class ScrollListener implements ListView.OnScrollListener {

    @Override
    public void onScroll(
        AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
      if (activityScrollListener != null) {
        activityScrollListener.onListFragmentScroll(
            firstVisibleItem, visibleItemCount, totalItemCount);
      }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
      activityScrollListener.onListFragmentScrollStateChange(scrollState);
    }
  }
}
