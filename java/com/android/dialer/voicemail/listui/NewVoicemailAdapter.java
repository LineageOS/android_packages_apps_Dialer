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
 * limitations under the License
 */
package com.android.dialer.voicemail.listui;

import android.app.FragmentManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import com.android.dialer.calllogutils.CallLogDates;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.time.Clock;
import com.android.dialer.voicemail.listui.NewVoicemailViewHolder.NewVoicemailViewHolderListener;
import com.android.dialer.voicemail.listui.error.VoicemailErrorMessage;
import com.android.dialer.voicemail.listui.error.VoicemailErrorMessageCreator;
import com.android.dialer.voicemail.listui.error.VoicemailStatus;
import com.android.dialer.voicemail.model.VoicemailEntry;
import com.android.voicemail.VoicemailClient;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** {@link RecyclerView.Adapter} for the new voicemail call log fragment. */
final class NewVoicemailAdapter extends RecyclerView.Adapter<ViewHolder>
    implements NewVoicemailViewHolderListener {

  private static final int VOICEMAIL_DELETE_DELAY_MS = 3000;

  /** IntDef for the different types of rows that can be shown in the call log. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({RowType.HEADER, RowType.VOICEMAIL_ENTRY, RowType.VOICEMAIL_ALERT})
  @interface RowType {
    /** A row representing a voicemail alert. */
    int VOICEMAIL_ALERT = 1;
    /** Header that displays "Today", "Yesterday" or "Older". */
    int HEADER = 2;
    /** A row representing a voicemail entry. */
    int VOICEMAIL_ENTRY = 3;
  }

  private Cursor cursor;
  private final Clock clock;

  /** {@link Integer#MAX_VALUE} when the "Today" header should not be displayed. */
  private int todayHeaderPosition = Integer.MAX_VALUE;
  /** {@link Integer#MAX_VALUE} when the "Yesterday" header should not be displayed. */
  private int yesterdayHeaderPosition = Integer.MAX_VALUE;
  /** {@link Integer#MAX_VALUE} when the "Older" header should not be displayed. */
  private int olderHeaderPosition = Integer.MAX_VALUE;
  /** {@link Integer#MAX_VALUE} when the voicemail alert message should not be displayed. */
  private int voicemailAlertPosition = Integer.MAX_VALUE;

  private final FragmentManager fragmentManager;
  /** A valid id for {@link VoicemailEntry} is greater than 0 */
  private long currentlyExpandedViewHolderId = -1;

  private VoicemailErrorMessage voicemailErrorMessage;

  /**
   * It takes time to delete voicemails from the server, so we "remove" them and remember the
   * positions we removed until a new cursor is ready.
   */
  Set<Integer> deletedVoicemailPosition = new ArraySet<>();

  /**
   * A set of (re-usable) view holders being used by the recycler view to display voicemails. This
   * set may include multiple view holder with the same ID and shouldn't be used to lookup a
   * specific viewholder based on this value, instead use newVoicemailViewHolderArrayMap for that
   * purpose.
   */
  private final Set<NewVoicemailViewHolder> newVoicemailViewHolderSet = new ArraySet<>();
  /**
   * This allows us to retrieve the view holder corresponding to a particular view holder id, and
   * will always ensure there is only (up-to-date) view holder corresponding to a view holder id,
   * unlike the newVoicemailViewHolderSet.
   */
  private final ArrayMap<Long, NewVoicemailViewHolder> newVoicemailViewHolderArrayMap =
      new ArrayMap<>();

  // A single instance of a media player re-used across the expanded view holders.
  private final NewVoicemailMediaPlayer mediaPlayer =
      new NewVoicemailMediaPlayer(new MediaPlayer());

  /** @param cursor whose projection is {@link VoicemailCursorLoader#VOICEMAIL_COLUMNS} */
  NewVoicemailAdapter(Cursor cursor, Clock clock, FragmentManager fragmentManager) {
    LogUtil.enterBlock("NewVoicemailAdapter");
    this.cursor = cursor;
    this.clock = clock;
    this.fragmentManager = fragmentManager;
    initializeMediaPlayerListeners();
    updateHeaderPositions();
  }

  private void updateHeaderPositions() {
    LogUtil.i(
        "NewVoicemailAdapter.updateHeaderPositions",
        "before updating todayPos:%d, yestPos:%d, olderPos:%d, alertPos:%d",
        todayHeaderPosition,
        yesterdayHeaderPosition,
        olderHeaderPosition,
        voicemailAlertPosition);

    // If there are no rows to display, set all header positions to MAX_VALUE.
    if (!cursor.moveToFirst()) {
      todayHeaderPosition = Integer.MAX_VALUE;
      yesterdayHeaderPosition = Integer.MAX_VALUE;
      olderHeaderPosition = Integer.MAX_VALUE;
      return;
    }

    long currentTimeMillis = clock.currentTimeMillis();

    int numItemsInToday = 0;
    int numItemsInYesterday = 0;

    do {
      long timestamp = VoicemailCursorLoader.getTimestamp(cursor);
      long dayDifference = CallLogDates.getDayDifference(currentTimeMillis, timestamp);
      if (dayDifference == 0) {
        numItemsInToday++;
      } else if (dayDifference == 1) {
        numItemsInYesterday++;
      } else {
        break;
      }
    } while (cursor.moveToNext());

    if (numItemsInToday > 0) {
      numItemsInToday++; // including the "Today" header;
    }
    if (numItemsInYesterday > 0) {
      numItemsInYesterday++; // including the "Yesterday" header;
    }

    int alertOffSet = 0;
    if (voicemailAlertPosition != Integer.MAX_VALUE) {
      Assert.checkArgument(
          voicemailAlertPosition == 0, "voicemail alert can only be 0, when showing");
      alertOffSet = 1;
    }

    // Set all header positions.
    // A header position will be MAX_VALUE if there is no item to be displayed under that header.
    todayHeaderPosition = numItemsInToday > 0 ? alertOffSet : Integer.MAX_VALUE;
    yesterdayHeaderPosition =
        numItemsInYesterday > 0 ? numItemsInToday + alertOffSet : Integer.MAX_VALUE;
    olderHeaderPosition =
        !cursor.isAfterLast()
            ? numItemsInToday + numItemsInYesterday + alertOffSet
            : Integer.MAX_VALUE;

    LogUtil.i(
        "NewVoicemailAdapter.updateHeaderPositions",
        "after updating todayPos:%d, yestPos:%d, olderPos:%d, alertOffSet:%d, alertPos:%d",
        todayHeaderPosition,
        yesterdayHeaderPosition,
        olderHeaderPosition,
        alertOffSet,
        voicemailAlertPosition);
  }

  private void initializeMediaPlayerListeners() {
    mediaPlayer.setOnCompletionListener(onCompletionListener);
    mediaPlayer.setOnPreparedListener(onPreparedListener);
    mediaPlayer.setOnErrorListener(onErrorListener);
  }

  public void updateCursor(Cursor updatedCursor) {
    LogUtil.enterBlock("NewVoicemailAdapter.updateCursor");
    deletedVoicemailPosition.clear();
    this.cursor = updatedCursor;
    updateHeaderPositions();
    notifyDataSetChanged();
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup viewGroup, @RowType int viewType) {
    LogUtil.enterBlock("NewVoicemailAdapter.onCreateViewHolder");
    LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
    View view;
    switch (viewType) {
      case RowType.VOICEMAIL_ALERT:
        view = inflater.inflate(R.layout.new_voicemail_entry_alert, viewGroup, false);
        return new NewVoicemailAlertViewHolder(view);
      case RowType.HEADER:
        view = inflater.inflate(R.layout.new_voicemail_entry_header, viewGroup, false);
        return new NewVoicemailHeaderViewHolder(view);
      case NewVoicemailAdapter.RowType.VOICEMAIL_ENTRY:
        view = inflater.inflate(R.layout.new_voicemail_entry, viewGroup, false);
        NewVoicemailViewHolder newVoicemailViewHolder =
            new NewVoicemailViewHolder(view, clock, this);
        newVoicemailViewHolderSet.add(newVoicemailViewHolder);
        return newVoicemailViewHolder;
      default:
        throw Assert.createUnsupportedOperationFailException("Unsupported view type: " + viewType);
    }
  }

  // TODO(uabdullah): a bug - Clean up logging in this function, here for debugging during
  // development.
  @Override
  public void onBindViewHolder(ViewHolder viewHolder, int position) {
    LogUtil.enterBlock("NewVoicemailAdapter.onBindViewHolder, pos:" + position);
    // Re-request a bind when a viewholder is deleted to ensure correct position
    if (deletedVoicemailPosition.contains(position)) {
      LogUtil.i(
          "NewVoicemailAdapter.onBindViewHolder",
          "pos:%d contains deleted voicemail, re-bind. #of deleted voicemail positions: %d",
          position,
          deletedVoicemailPosition.size());
      // TODO(uabdullah): This should be removed when we support multi-select delete
      Assert.checkArgument(
          deletedVoicemailPosition.size() == 1, "multi-deletes not currently supported");
      onBindViewHolder(viewHolder, ++position);
      return;
    }

    // TODO(uabdullah): a bug Remove logging, temporarily here for debugging.
    printHashSet();
    // TODO(uabdullah): a bug Remove logging, temporarily here for debugging.
    printArrayMap();

    if (viewHolder instanceof NewVoicemailHeaderViewHolder) {
      LogUtil.i(
          "NewVoicemailAdapter.onBindViewHolder", "view holder at pos:%d is a header", position);
      onBindHeaderViewHolder(viewHolder, position);
      return;
    }

    if (viewHolder instanceof NewVoicemailAlertViewHolder) {
      LogUtil.i(
          "NewVoicemailAdapter.onBindViewHolder", "view holder at pos:%d is a alert", position);
      onBindAlertViewHolder(viewHolder, position);
      return;
    }

    LogUtil.i(
        "NewVoicemailAdapter.onBindViewHolder",
        "view holder at pos:%d is a not a header or an alert",
        position);

    NewVoicemailViewHolder newVoicemailViewHolder = (NewVoicemailViewHolder) viewHolder;
    int nonVoicemailEntryHeaders = getHeaderCountAtPosition(position);

    LogUtil.i(
        "NewVoicemailAdapter.onBindViewHolder",
        "view holder at pos:%d, nonVoicemailEntryHeaders:%d",
        position,
        nonVoicemailEntryHeaders);

    // Remove if the viewholder is being recycled.
    if (newVoicemailViewHolderArrayMap.containsKey(newVoicemailViewHolder.getViewHolderId())) {
      // TODO(uabdullah): a bug Remove logging, temporarily here for debugging.
      LogUtil.i(
          "NewVoicemailAdapter.onBindViewHolder",
          "Removing from hashset:%d, hashsetSize:%d, currExpanded:%d",
          newVoicemailViewHolder.getViewHolderId(),
          newVoicemailViewHolderArrayMap.size(),
          currentlyExpandedViewHolderId);

      newVoicemailViewHolderArrayMap.remove(newVoicemailViewHolder.getViewHolderId());
      printHashSet();
      printArrayMap();
    }

    newVoicemailViewHolder.reset();
    cursor.moveToPosition(position - nonVoicemailEntryHeaders);
    newVoicemailViewHolder.bindViewHolderValuesFromAdapter(
        cursor, fragmentManager, mediaPlayer, position, currentlyExpandedViewHolderId);

    // TODO(uabdullah): a bug Remove logging, temporarily here for debugging.
    LogUtil.i(
        "NewVoicemailAdapter.onBindViewHolder",
        "Adding to hashset:%d, hashsetSize:%d, pos:%d, currExpanded:%d",
        newVoicemailViewHolder.getViewHolderId(),
        newVoicemailViewHolderArrayMap.size(),
        position,
        currentlyExpandedViewHolderId);

    // Need this to ensure correct getCurrentlyExpandedViewHolder() value
    newVoicemailViewHolderArrayMap.put(
        newVoicemailViewHolder.getViewHolderId(), newVoicemailViewHolder);

    // TODO(uabdullah): a bug Remove logging, temporarily here for debugging.
    printHashSet();
    // TODO(uabdullah): a bug Remove logging, temporarily here for debugging.
    printArrayMap();

    // If the viewholder is playing the voicemail, keep updating its media player view (seekbar,
    // duration etc.)
    if (newVoicemailViewHolder.isViewHolderExpanded() && mediaPlayer.isPlaying()) {
      LogUtil.i(
          "NewVoicemailAdapter.onBindViewHolder",
          "Adding to hashset:%d, hashsetSize:%d, pos:%d, currExpanded:%d",
          newVoicemailViewHolderSet.size(),
          newVoicemailViewHolderArrayMap.size(),
          position,
          currentlyExpandedViewHolderId);

      Assert.checkArgument(
          newVoicemailViewHolder
              .getViewHolderVoicemailUri()
              .equals(mediaPlayer.getLastPlayedOrPlayingVoicemailUri()),
          "only the expanded view holder can be playing.");
      Assert.isNotNull(getCurrentlyExpandedViewHolder());
      Assert.checkArgument(
          getCurrentlyExpandedViewHolder()
              .getViewHolderVoicemailUri()
              .equals(mediaPlayer.getLastPlayedOrPlayingVoicemailUri()));

      recursivelyUpdateMediaPlayerViewOfExpandedViewHolder(newVoicemailViewHolder);
    }
    // Updates the hashmap with the most up-to-date state of the viewholder.
    newVoicemailViewHolderArrayMap.put(
        newVoicemailViewHolder.getViewHolderId(), newVoicemailViewHolder);

    // TODO(uabdullah): a bug Remove logging, temporarily here for debugging.
    printHashSet();
    // TODO(uabdullah): a bug Remove logging, temporarily here for debugging.
    printArrayMap();
  }

  private int getHeaderCountAtPosition(int position) {
    int previousHeaders = 0;
    if (voicemailAlertPosition != Integer.MAX_VALUE && position > voicemailAlertPosition) {
      previousHeaders++;
    }
    if (todayHeaderPosition != Integer.MAX_VALUE && position > todayHeaderPosition) {
      previousHeaders++;
    }
    if (yesterdayHeaderPosition != Integer.MAX_VALUE && position > yesterdayHeaderPosition) {
      previousHeaders++;
    }
    if (olderHeaderPosition != Integer.MAX_VALUE && position > olderHeaderPosition) {
      previousHeaders++;
    }
    return previousHeaders;
  }

  private void onBindAlertViewHolder(ViewHolder viewHolder, int position) {
    LogUtil.i(
        "NewVoicemailAdapter.onBindAlertViewHolder",
        "pos:%d, voicemailAlertPosition:%d",
        position,
        voicemailAlertPosition);

    NewVoicemailAlertViewHolder alertViewHolder = (NewVoicemailAlertViewHolder) viewHolder;
    @RowType int viewType = getItemViewType(position);

    Assert.checkArgument(position == 0, "position is not 0");
    Assert.checkArgument(
        position == voicemailAlertPosition,
        String.format(
            Locale.US,
            "position:%d and voicemailAlertPosition:%d are different",
            position,
            voicemailAlertPosition));
    Assert.checkArgument(viewType == RowType.VOICEMAIL_ALERT, "Invalid row type: " + viewType);
    Assert.checkArgument(
        voicemailErrorMessage.getActions().size() <= 2,
        "Too many actions: " + voicemailErrorMessage.getActions().size());

    alertViewHolder.setTitle(voicemailErrorMessage.getTitle());
    alertViewHolder.setDescription(voicemailErrorMessage.getDescription());

    if (!voicemailErrorMessage.getActions().isEmpty()) {
      alertViewHolder.setPrimaryButton(voicemailErrorMessage.getActions().get(0));
    }
    if (voicemailErrorMessage.getActions().size() > 1) {
      alertViewHolder.setSecondaryButton(voicemailErrorMessage.getActions().get(1));
    }
  }

  private void onBindHeaderViewHolder(ViewHolder viewHolder, int position) {
    NewVoicemailHeaderViewHolder headerViewHolder = (NewVoicemailHeaderViewHolder) viewHolder;
    @RowType int viewType = getItemViewType(position);
    if (position == todayHeaderPosition) {
      headerViewHolder.setHeader(R.string.new_voicemail_header_today);
    } else if (position == yesterdayHeaderPosition) {
      headerViewHolder.setHeader(R.string.new_voicemail_header_yesterday);
    } else if (position == olderHeaderPosition) {
      headerViewHolder.setHeader(R.string.new_voicemail_header_older);
    } else {
      throw Assert.createIllegalStateFailException(
          "Unexpected view type " + viewType + " at position: " + position);
    }
  }

  private void printArrayMap() {
    LogUtil.i(
        "NewVoicemailAdapter.printArrayMap",
        "hashMapSize: %d, currentlyExpandedViewHolderId:%d",
        newVoicemailViewHolderArrayMap.size(),
        currentlyExpandedViewHolderId);

    if (!newVoicemailViewHolderArrayMap.isEmpty()) {
      String ids = "";
      for (long id : newVoicemailViewHolderArrayMap.keySet()) {
        ids = ids + id + " ";
      }
      LogUtil.i("NewVoicemailAdapter.printArrayMap", "ids are " + ids);
    }
  }

  private void printHashSet() {
    LogUtil.i(
        "NewVoicemailAdapter.printHashSet",
        "hashSetSize: %d, currentlyExpandedViewHolderId:%d",
        newVoicemailViewHolderSet.size(),
        currentlyExpandedViewHolderId);

    if (!newVoicemailViewHolderSet.isEmpty()) {
      String viewHolderID = "";
      for (NewVoicemailViewHolder vh : newVoicemailViewHolderSet) {
        viewHolderID = viewHolderID + vh.getViewHolderId() + " ";
      }
      LogUtil.i("NewVoicemailAdapter.printHashSet", "ids are " + viewHolderID);
    }
  }

  /**
   * The {@link NewVoicemailAdapter} needs to keep track of {@link NewVoicemailViewHolder} that has
   * been expanded. This is so that the adapter can ensure the correct {@link
   * NewVoicemailMediaPlayerView} and {@link NewVoicemailViewHolder} states are maintained
   * (playing/paused/reset) for the expanded viewholder, especially when views are recycled in
   * {@link RecyclerView}. Since we can only have one expanded voicemail view holder, this method
   * ensures that except for the currently expanded view holder, all the other view holders visible
   * on the screen are collapsed.
   *
   * <p>The {@link NewVoicemailMediaPlayer} is also reset, if there is an existing playing
   * voicemail.
   *
   * <p>This is the function that is responsible of keeping track of the expanded viewholder in the
   * {@link NewVoicemailAdapter}
   *
   * <p>This is the first function called in the adapter when a viewholder has been expanded.
   *
   * <p>This is the function that is responsible of keeping track of the expanded viewholder in the
   * {@link NewVoicemailAdapter}
   *
   * @param viewHolderRequestedToExpand is the view holder that is currently expanded.
   * @param voicemailEntryOfViewHolder
   */
  @Override
  public void expandViewHolderFirstTimeAndCollapseAllOtherVisibleViewHolders(
      NewVoicemailViewHolder viewHolderRequestedToExpand,
      VoicemailEntry voicemailEntryOfViewHolder,
      NewVoicemailViewHolderListener listener) {

    LogUtil.i(
        "NewVoicemailAdapter.expandViewHolderFirstTimeAndCollapseAllOtherVisibleViewHolders",
        "viewholder id:%d being request to expand, isExpanded:%b, size of our view holder "
            + "dataset:%d, hashmap size:%d",
        viewHolderRequestedToExpand.getViewHolderId(),
        viewHolderRequestedToExpand.isViewHolderExpanded(),
        newVoicemailViewHolderSet.size(),
        newVoicemailViewHolderArrayMap.size());

    currentlyExpandedViewHolderId = viewHolderRequestedToExpand.getViewHolderId();

    for (NewVoicemailViewHolder viewHolder : newVoicemailViewHolderSet) {
      if (viewHolder.getViewHolderId() != viewHolderRequestedToExpand.getViewHolderId()) {
        viewHolder.collapseViewHolder();
      }
    }

    // If the media player is playing and we expand something other than the currently playing one
    // we should stop playing the media player
    if (mediaPlayer.isPlaying()
        && !Objects.equals(
            mediaPlayer.getLastPlayedOrPlayingVoicemailUri(),
            viewHolderRequestedToExpand.getViewHolderVoicemailUri())) {
      LogUtil.i(
          "NewVoicemailAdapter.expandViewHolderFirstTimeAndCollapseAllOtherVisibleViewHolders",
          "Reset the media player since we expanded something other that the playing "
              + "voicemail, MP was playing:%s, viewholderExpanded:%d, MP.isPlaying():%b",
          String.valueOf(mediaPlayer.getLastPlayedOrPlayingVoicemailUri()),
          viewHolderRequestedToExpand.getViewHolderId(),
          mediaPlayer.isPlaying());
      mediaPlayer.reset();
    }

    // If the media player is paused and we expand something other than the currently paused one
    // we should stop playing the media player
    if (mediaPlayer.isPaused()
        && !Objects.equals(
            mediaPlayer.getLastPausedVoicemailUri(),
            viewHolderRequestedToExpand.getViewHolderVoicemailUri())) {
      LogUtil.i(
          "NewVoicemailAdapter.expandViewHolderFirstTimeAndCollapseAllOtherVisibleViewHolders",
          "There was an existing paused viewholder, the media player should reset since we "
              + "expanded something other that the paused voicemail, MP.paused:%s",
          String.valueOf(mediaPlayer.getLastPausedVoicemailUri()));
      mediaPlayer.reset();
    }

    Assert.checkArgument(
        !viewHolderRequestedToExpand.isViewHolderExpanded(),
        "cannot expand a voicemail that is not collapsed");

    viewHolderRequestedToExpand.expandAndBindViewHolderAndMediaPlayerViewWithAdapterValues(
        voicemailEntryOfViewHolder, fragmentManager, mediaPlayer, listener);

    // There should be nothing playing when we expand a viewholder for the first time
    Assert.checkArgument(!mediaPlayer.isPlaying());
  }

  /**
   * Ensures that when we collapse the expanded view, we don't expand it again when we are recycling
   * the viewholders. If we collapse an existing playing voicemail viewholder, we should stop
   * playing it.
   *
   * @param collapseViewHolder is the view holder that is currently collapsed.
   */
  @Override
  public void collapseExpandedViewHolder(NewVoicemailViewHolder collapseViewHolder) {
    Assert.checkArgument(collapseViewHolder.getViewHolderId() == currentlyExpandedViewHolderId);
    collapseViewHolder.collapseViewHolder();
    currentlyExpandedViewHolderId = -1;

    // If the view holder is currently playing, then we should stop playing it.
    if (mediaPlayer.isPlaying()) {
      Assert.checkArgument(
          Objects.equals(
              mediaPlayer.getLastPlayedOrPlayingVoicemailUri(),
              collapseViewHolder.getViewHolderVoicemailUri()),
          "the voicemail being played should have been of the recently collapsed view holder.");
      mediaPlayer.reset();
    }
  }

  @Override
  public void pauseViewHolder(NewVoicemailViewHolder expandedViewHolder) {
    Assert.isNotNull(
        getCurrentlyExpandedViewHolder(),
        "cannot have pressed pause if the viewholder wasn't expanded");
    Assert.checkArgument(
        getCurrentlyExpandedViewHolder()
            .getViewHolderVoicemailUri()
            .equals(expandedViewHolder.getViewHolderVoicemailUri()),
        "view holder whose pause button was pressed has to have been the expanded "
            + "viewholder being tracked by the adapter.");
    mediaPlayer.pauseMediaPlayer(expandedViewHolder.getViewHolderVoicemailUri());
    expandedViewHolder.setPausedStateOfMediaPlayerView(
        expandedViewHolder.getViewHolderVoicemailUri(), mediaPlayer);
  }

  @Override
  public void resumePausedViewHolder(NewVoicemailViewHolder expandedViewHolder) {
    Assert.isNotNull(
        getCurrentlyExpandedViewHolder(),
        "cannot have pressed pause if the viewholder wasn't expanded");
    Assert.checkArgument(
        getCurrentlyExpandedViewHolder()
            .getViewHolderVoicemailUri()
            .equals(expandedViewHolder.getViewHolderVoicemailUri()),
        "view holder whose play button was pressed has to have been the expanded "
            + "viewholder being tracked by the adapter.");
    Assert.isNotNull(
        mediaPlayer.getLastPausedVoicemailUri(), "there should be be an pausedUri to resume");
    Assert.checkArgument(
        mediaPlayer
            .getLastPlayedOrPlayingVoicemailUri()
            .equals(expandedViewHolder.getViewHolderVoicemailUri()),
        "only the last playing uri can be resumed");
    Assert.checkArgument(
        mediaPlayer
            .getLastPreparedOrPreparingToPlayVoicemailUri()
            .equals(expandedViewHolder.getViewHolderVoicemailUri()),
        "only the last prepared uri can be resumed");
    Assert.checkArgument(
        mediaPlayer
            .getLastPreparedOrPreparingToPlayVoicemailUri()
            .equals(mediaPlayer.getLastPlayedOrPlayingVoicemailUri()),
        "the last prepared and playing voicemails have to be the same when resuming");

    onPreparedListener.onPrepared(mediaPlayer.getMediaPlayer());
  }

  @Override
  public void deleteViewHolder(
      Context context,
      FragmentManager fragmentManager,
      NewVoicemailViewHolder expandedViewHolder,
      Uri voicemailUri) {
    LogUtil.i(
        "NewVoicemailAdapter.deleteViewHolder",
        "deleting adapter position %d, id:%d, uri:%s ",
        expandedViewHolder.getAdapterPosition(),
        expandedViewHolder.getViewHolderId(),
        String.valueOf(voicemailUri));

    deletedVoicemailPosition.add(expandedViewHolder.getAdapterPosition());

    Assert.checkArgument(expandedViewHolder.getViewHolderVoicemailUri().equals(voicemailUri));

    Assert.checkArgument(currentlyExpandedViewHolderId == expandedViewHolder.getViewHolderId());

    collapseExpandedViewHolder(expandedViewHolder);

    showUndoSnackbar(
        context,
        expandedViewHolder.getMediaPlayerView(),
        expandedViewHolder.getAdapterPosition(),
        voicemailUri);
  }

  private void showUndoSnackbar(
      Context context, View newVoicemailMediaPlayerView, int position, Uri voicemailUri) {
    LogUtil.i(
        "NewVoicemailAdapter.showUndoSnackbar",
        "position:%d and uri:%s",
        position,
        String.valueOf(voicemailUri));
    Snackbar undoSnackbar =
        Snackbar.make(
            newVoicemailMediaPlayerView,
            R.string.snackbar_voicemail_deleted,
            VOICEMAIL_DELETE_DELAY_MS);
    undoSnackbar.addCallback(
        new Snackbar.Callback() {
          @Override
          public void onShown(Snackbar sb) {
            notifyItemRemoved(position);
            LogUtil.i(
                "NewVoicemailAdapter.showUndoSnackbar",
                "onShown for position:%d and uri:%s",
                position,
                voicemailUri);
            super.onShown(sb);
          }

          @Override
          public void onDismissed(Snackbar transientBottomBar, int event) {
            LogUtil.i(
                "NewVoicemailAdapter.showUndoSnackbar",
                "onDismissed for event:%d, position:%d and uri:%s",
                event,
                position,
                String.valueOf(voicemailUri));

            switch (event) {
              case DISMISS_EVENT_SWIPE:
              case DISMISS_EVENT_ACTION:
              case DISMISS_EVENT_MANUAL:
                LogUtil.i(
                    "NewVoicemailAdapter.showUndoSnackbar",
                    "Not proceeding with deleting the voicemail");
                deletedVoicemailPosition.remove(position);
                notifyItemChanged(position);
                break;
              case DISMISS_EVENT_TIMEOUT:
              case DISMISS_EVENT_CONSECUTIVE:
                LogUtil.i(
                    "NewVoicemailAdapter.showUndoSnackbar", "Proceeding with deleting voicemail");

                DialerExecutorComponent.get(context)
                    .dialerExecutorFactory()
                    .createNonUiTaskBuilder(this::deleteVoicemail)
                    .build()
                    .executeSerial(new Pair<>(context, voicemailUri));
                break;
              default:
                Assert.checkArgument(event <= 4 && event >= 0, "unknown event");
            }
          }

          @WorkerThread
          private Void deleteVoicemail(Pair<Context, Uri> contextUriPair) {
            Assert.isWorkerThread();
            Context context = contextUriPair.first;
            Uri uri = contextUriPair.second;
            LogUtil.i(
                "NewVoicemailAdapter.deleteVoicemail", "deleting uri:%s", String.valueOf(uri));
            ContentValues values = new ContentValues();
            values.put(Voicemails.DELETED, "1");

            int numRowsUpdated = context.getContentResolver().update(uri, values, null, null);

            LogUtil.i("NewVoicemailAdapter.deleteVoicemail", "return value:%d", numRowsUpdated);
            Assert.checkArgument(numRowsUpdated == 1, "voicemail delete was not successful");

            Intent intent = new Intent(VoicemailClient.ACTION_UPLOAD);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
            return null;
          }
        });

    undoSnackbar
        .setAction(
            R.string.snackbar_undo,
            new OnClickListener() {
              @Override
              public void onClick(View v) {
                // does nothing, but needed for the undo button to show
              }
            })
        .setActionTextColor(
            context.getResources().getColor(R.color.dialer_snackbar_action_text_color))
        .show();
  }

  /**
   * This function is called recursively to update the seekbar, duration, play/pause buttons of the
   * expanded view holder if its playing.
   *
   * <p>Since this function is called at 30 frames/second, its possible (and eventually will happen)
   * that between each update the playing voicemail state could have changed, in which case this
   * method should stop calling itself. These conditions are:
   *
   * <ul>
   *   <li>The user scrolled the playing voicemail out of view.
   *   <li>Another view holder was expanded.
   *   <li>The playing voicemail was paused.
   *   <li>The media player returned {@link MediaPlayer#isPlaying()} to be true but had its {@link
   *       MediaPlayer#getCurrentPosition()} > {@link MediaPlayer#getDuration()}.
   *   <li>The {@link MediaPlayer} stopped playing.
   * </ul>
   *
   * <p>Note: Since the update happens at 30 frames/second, it's also possible that the viewholder
   * was recycled when scrolling the playing voicemail out of view.
   *
   * @param expandedViewHolderPossiblyPlaying the view holder that was expanded and could or could
   *     not be playing. This viewholder can be recycled.
   */
  private void recursivelyUpdateMediaPlayerViewOfExpandedViewHolder(
      NewVoicemailViewHolder expandedViewHolderPossiblyPlaying) {
    // TODO(uabdullah): a bug Remove logging, temporarily here for debugging.
    LogUtil.i(
        "NewVoicemailAdapter.recursivelyUpdateMediaPlayerViewOfExpandedViewHolder",
        "currentlyExpanded:%d",
        currentlyExpandedViewHolderId);

    // It's possible that by the time this is run, the expanded view holder has been
    // scrolled out of view (and possibly recycled)
    if (getCurrentlyExpandedViewHolder() == null) {
      LogUtil.i(
          "NewVoicemailAdapter.recursivelyUpdateMediaPlayerViewOfExpandedViewHolder",
          "viewholder:%d media player view, no longer on screen, no need to update",
          expandedViewHolderPossiblyPlaying.getViewHolderId());
      return;
    }

    // Another viewholder was expanded, no need to update
    if (!getCurrentlyExpandedViewHolder().equals(expandedViewHolderPossiblyPlaying)) {
      LogUtil.i(
          "NewVoicemailAdapter.recursivelyUpdateMediaPlayerViewOfExpandedViewHolder",
          "currentlyExpandedViewHolderId:%d and the one we are attempting to update:%d "
              + "aren't the same.",
          currentlyExpandedViewHolderId,
          expandedViewHolderPossiblyPlaying.getViewHolderId());
      return;
    }

    Assert.checkArgument(expandedViewHolderPossiblyPlaying.isViewHolderExpanded());
    Assert.checkArgument(
        expandedViewHolderPossiblyPlaying.getViewHolderId()
            == getCurrentlyExpandedViewHolder().getViewHolderId());

    // If the viewholder was paused, there is no need to update the media player view
    if (mediaPlayer.isPaused()) {
      Assert.checkArgument(
          expandedViewHolderPossiblyPlaying
              .getViewHolderVoicemailUri()
              .equals(mediaPlayer.getLastPausedVoicemailUri()),
          "only the expanded viewholder can be paused.");

      LogUtil.i(
          "NewVoicemailAdapter.recursivelyUpdateMediaPlayerViewOfExpandedViewHolder",
          "set the media player to a paused state");
      expandedViewHolderPossiblyPlaying.setPausedStateOfMediaPlayerView(
          expandedViewHolderPossiblyPlaying.getViewHolderVoicemailUri(), mediaPlayer);
      return;
    }

    // In some weird corner cases a media player could return isPlaying() as true but would
    // have getCurrentPosition > getDuration(). We consider that as the voicemail has finished
    // playing.
    if (mediaPlayer.isPlaying() && mediaPlayer.getCurrentPosition() < mediaPlayer.getDuration()) {

      Assert.checkArgument(
          mediaPlayer
              .getLastPlayedOrPlayingVoicemailUri()
              .equals(getCurrentlyExpandedViewHolder().getViewHolderVoicemailUri()));
      // TODO(uabdullah): a bug Remove logging, temporarily here for debugging.
      LogUtil.i(
          "NewVoicemailAdapter.recursivelyUpdateMediaPlayerViewOfExpandedViewHolder",
          "recursely update the player, currentlyExpanded:%d",
          expandedViewHolderPossiblyPlaying.getViewHolderId());

      Assert.checkArgument(
          expandedViewHolderPossiblyPlaying
              .getViewHolderVoicemailUri()
              .equals(getCurrentlyExpandedViewHolder().getViewHolderVoicemailUri()));

      expandedViewHolderPossiblyPlaying.updateMediaPlayerViewWithPlayingState(
          expandedViewHolderPossiblyPlaying, mediaPlayer);

      ThreadUtil.postDelayedOnUiThread(
          new Runnable() {
            @Override
            public void run() {
              recursivelyUpdateMediaPlayerViewOfExpandedViewHolder(
                  expandedViewHolderPossiblyPlaying);
            }
          },
          1000 / 30 /*30 FPS*/);
      return;
    }

    if (!mediaPlayer.isPlaying()
        || (mediaPlayer.isPlaying()
            && mediaPlayer.getCurrentPosition() > mediaPlayer.getDuration())) {
      LogUtil.i(
          "NewVoicemailAdapter.recursivelyUpdateMediaPlayerViewOfExpandedViewHolder",
          "resetting the player, currentlyExpanded:%d, MPPlaying:%b",
          getCurrentlyExpandedViewHolder().getViewHolderId(),
          mediaPlayer.isPlaying());
      mediaPlayer.reset();
      Assert.checkArgument(
          expandedViewHolderPossiblyPlaying
              .getViewHolderVoicemailUri()
              .equals(getCurrentlyExpandedViewHolder().getViewHolderVoicemailUri()));
      expandedViewHolderPossiblyPlaying.setMediaPlayerViewToResetState(
          expandedViewHolderPossiblyPlaying, mediaPlayer);
      return;
    }

    String error =
        String.format(
            "expandedViewHolderPossiblyPlaying:%d, expanded:%b, CurrentExpanded:%d, uri:%s, "
                + "MPPlaying:%b, MPPaused:%b, MPPreparedUri:%s, MPPausedUri:%s",
            expandedViewHolderPossiblyPlaying.getViewHolderId(),
            expandedViewHolderPossiblyPlaying.isViewHolderExpanded(),
            currentlyExpandedViewHolderId,
            String.valueOf(expandedViewHolderPossiblyPlaying.getViewHolderVoicemailUri()),
            mediaPlayer.isPlaying(),
            mediaPlayer.isPaused(),
            String.valueOf(mediaPlayer.getLastPreparedOrPreparingToPlayVoicemailUri()),
            String.valueOf(mediaPlayer.getLastPreparedOrPreparingToPlayVoicemailUri()));

    throw Assert.createAssertionFailException(
        "All cases should have been handled before. Error " + error);
  }

  // When a voicemail has finished playing.
  OnCompletionListener onCompletionListener =
      new OnCompletionListener() {

        @Override
        public void onCompletion(MediaPlayer mp) {
          Assert.checkArgument(
              mediaPlayer
                  .getLastPlayedOrPlayingVoicemailUri()
                  .equals(mediaPlayer.getLastPreparedOrPreparingToPlayVoicemailUri()));
          Assert.checkArgument(!mediaPlayer.isPlaying());

          LogUtil.i(
              "NewVoicemailAdapter.onCompletionListener",
              "completed playing voicemailUri: %s, expanded viewholder is %d, visibility :%b",
              mediaPlayer.getLastPlayedOrPlayingVoicemailUri().toString(),
              currentlyExpandedViewHolderId,
              isCurrentlyExpandedViewHolderInViewHolderSet());

          Assert.checkArgument(
              currentlyExpandedViewHolderId != -1,
              "a voicemail that was never expanded, should never be playing.");
          mediaPlayer.reset();
        }
      };

  // When a voicemail has been prepared and can be played
  private final OnPreparedListener onPreparedListener =
      new OnPreparedListener() {

        /**
         * When a user pressed the play button, this listener should be called immediately. The
         * asserts ensures that is the case. This function starts playing the voicemail and updates
         * the UI.
         */
        @Override
        public void onPrepared(MediaPlayer mp) {
          LogUtil.i(
              "NewVoicemailAdapter.onPrepared",
              "MPPreparedUri: %s, currentlyExpandedViewHolderId:%d, and its visibility on "
                  + "the screen is:%b",
              String.valueOf(mediaPlayer.getLastPreparedOrPreparingToPlayVoicemailUri()),
              currentlyExpandedViewHolderId,
              isCurrentlyExpandedViewHolderInViewHolderSet());

          NewVoicemailViewHolder currentlyExpandedViewHolder = getCurrentlyExpandedViewHolder();
          Assert.checkArgument(currentlyExpandedViewHolder != null);
          Assert.checkArgument(
              currentlyExpandedViewHolder
                  .getViewHolderVoicemailUri()
                  .equals(mediaPlayer.getLastPreparedOrPreparingToPlayVoicemailUri()),
              "should only have prepared the last expanded view holder.");

          mediaPlayer.start(mediaPlayer.getLastPreparedOrPreparingToPlayVoicemailUri());

          recursivelyUpdateMediaPlayerViewOfExpandedViewHolder(currentlyExpandedViewHolder);

          Assert.checkArgument(mediaPlayer.isPlaying());
          LogUtil.i("NewVoicemailAdapter.onPrepared", "voicemail should be playing");
        }
      };

  // TODO(uabdullah): when playing the voicemail results in an error
  // we must update the viewholder and mention there was an error playing the voicemail, and reset
  // the media player and the media player view
  private final OnErrorListener onErrorListener =
      new OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
          LogUtil.e("NewVoicemailAdapter.onError", "onError, what:%d, extra:%d", what, extra);
          Assert.checkArgument(
              mediaPlayer.getMediaPlayer().equals(mp),
              "there should always only be one instance of the media player");
          Assert.checkArgument(
              mediaPlayer
                  .getLastPlayedOrPlayingVoicemailUri()
                  .equals(mediaPlayer.getLastPreparedOrPreparingToPlayVoicemailUri()));
          LogUtil.i(
              "NewVoicemailAdapter.onErrorListener",
              "error playing voicemailUri: %s",
              mediaPlayer.getLastPlayedOrPlayingVoicemailUri().toString());
          return false;
        }
      };

  private boolean isCurrentlyExpandedViewHolderInViewHolderSet() {
    for (NewVoicemailViewHolder viewHolder : newVoicemailViewHolderSet) {
      if (viewHolder.getViewHolderId() == currentlyExpandedViewHolderId) {
        return true;
      }
    }
    return false;
  }

  /**
   * The expanded view holder may or may not be visible on the screen. Since the {@link
   * NewVoicemailViewHolder} may be recycled, it's possible that the expanded view holder is
   * recycled for a non-expanded view holder when the expanded view holder is scrolled out of view.
   *
   * @return the expanded view holder if it is amongst the recycled views on the screen, otherwise
   *     null.
   */
  @Nullable
  private NewVoicemailViewHolder getCurrentlyExpandedViewHolder() {
    if (newVoicemailViewHolderArrayMap.containsKey(currentlyExpandedViewHolderId)) {
      Assert.checkArgument(
          newVoicemailViewHolderArrayMap.get(currentlyExpandedViewHolderId).getViewHolderId()
              == currentlyExpandedViewHolderId);
      return newVoicemailViewHolderArrayMap.get(currentlyExpandedViewHolderId);
    } else {
      // returned when currentlyExpandedViewHolderId = -1 (viewholder was collapsed)
      LogUtil.i(
          "NewVoicemailAdapter.getCurrentlyExpandedViewHolder",
          "no view holder found in hashmap size:%d for %d",
          newVoicemailViewHolderArrayMap.size(),
          currentlyExpandedViewHolderId);
      // TODO(uabdullah): a bug Remove logging, temporarily here for debugging.
      printHashSet();
      printArrayMap();
      return null;
    }
  }

  @Override
  public int getItemCount() {
    // TODO(uabdullah): a bug Remove logging, temporarily here for debugging.
    LogUtil.enterBlock("NewVoicemailAdapter.getItemCount");
    int numberOfHeaders = 0;
    if (voicemailAlertPosition != Integer.MAX_VALUE) {
      numberOfHeaders++;
    }
    if (todayHeaderPosition != Integer.MAX_VALUE) {
      numberOfHeaders++;
    }
    if (yesterdayHeaderPosition != Integer.MAX_VALUE) {
      numberOfHeaders++;
    }
    if (olderHeaderPosition != Integer.MAX_VALUE) {
      numberOfHeaders++;
    }
    // TODO(uabdullah): a bug Remove logging, temporarily here for debugging.
    LogUtil.i(
        "NewVoicemailAdapter.getItemCount",
        "cursor cnt:%d, num of headers:%d, delete size:%d",
        cursor.getCount(),
        numberOfHeaders,
        deletedVoicemailPosition.size());
    return cursor.getCount() + numberOfHeaders - deletedVoicemailPosition.size();
  }

  @RowType
  @Override
  public int getItemViewType(int position) {
    LogUtil.enterBlock("NewVoicemailAdapter.getItemViewType");
    if (voicemailAlertPosition != Integer.MAX_VALUE && position == voicemailAlertPosition) {
      return RowType.VOICEMAIL_ALERT;
    }
    if (todayHeaderPosition != Integer.MAX_VALUE && position == todayHeaderPosition) {
      return RowType.HEADER;
    }
    if (yesterdayHeaderPosition != Integer.MAX_VALUE && position == yesterdayHeaderPosition) {
      return RowType.HEADER;
    }
    if (olderHeaderPosition != Integer.MAX_VALUE && position == olderHeaderPosition) {
      return RowType.HEADER;
    }
    return RowType.VOICEMAIL_ENTRY;
  }

  /**
   * This will be called once the voicemail that was attempted to be played (and was not locally
   * available) was downloaded from the server. However it is possible that by the time the download
   * was completed, the view holder was collapsed. In that case we shouldn't play the voicemail.
   */
  public void checkAndPlayVoicemail() {
    LogUtil.i(
        "NewVoicemailAdapter.checkAndPlayVoicemail",
        "expandedViewHolder:%d, inViewHolderSet:%b, MPRequestToDownload:%s",
        currentlyExpandedViewHolderId,
        isCurrentlyExpandedViewHolderInViewHolderSet(),
        String.valueOf(mediaPlayer.getVoicemailRequestedToDownload()));

    NewVoicemailViewHolder currentlyExpandedViewHolder = getCurrentlyExpandedViewHolder();
    if (currentlyExpandedViewHolderId != -1
        && isCurrentlyExpandedViewHolderInViewHolderSet()
        && currentlyExpandedViewHolder != null
        // Used to differentiate underlying table changes from voicemail downloads and other changes
        // (e.g delete)
        && mediaPlayer.getVoicemailRequestedToDownload() != null
        && (mediaPlayer
            .getVoicemailRequestedToDownload()
            .equals(currentlyExpandedViewHolder.getViewHolderVoicemailUri()))) {
      currentlyExpandedViewHolder.clickPlayButtonOfViewHoldersMediaPlayerView(
          currentlyExpandedViewHolder);
    } else {
      LogUtil.i("NewVoicemailAdapter.checkAndPlayVoicemail", "not playing downloaded voicemail");
    }
  }

  /**
   * Updates the voicemail alert message to reflect the state of the {@link VoicemailStatus} table.
   * TODO(uabdullah): Handle ToS properly (a bug)
   */
  public void updateVoicemailAlertWithMostRecentStatus(
      Context context, ImmutableList<VoicemailStatus> voicemailStatuses) {

    if (voicemailStatuses.isEmpty()) {
      LogUtil.i(
          "NewVoicemailAdapter.updateVoicemailAlertWithMostRecentStatus",
          "voicemailStatuses was empty");
      return;
    }

    voicemailErrorMessage = null;
    VoicemailErrorMessageCreator messageCreator = new VoicemailErrorMessageCreator();

    for (VoicemailStatus status : voicemailStatuses) {
      voicemailErrorMessage = messageCreator.create(context, status, null);
      if (voicemailErrorMessage != null) {
        break;
      }
    }

    if (voicemailErrorMessage != null) {
      LogUtil.i("NewVoicemailAdapter.updateVoicemailAlertWithMostRecentStatus", "showing alert");
      voicemailAlertPosition = 0;
      updateHeaderPositions();
      notifyItemChanged(0);
    }
  }
}
