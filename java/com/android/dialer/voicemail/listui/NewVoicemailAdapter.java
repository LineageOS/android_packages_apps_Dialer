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
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.time.Clock;
import com.android.dialer.voicemail.listui.NewVoicemailViewHolder.NewVoicemailViewHolderListener;
import com.android.dialer.voicemail.model.VoicemailEntry;
import java.util.Objects;
import java.util.Set;

/** {@link RecyclerView.Adapter} for the new voicemail call log fragment. */
final class NewVoicemailAdapter extends RecyclerView.Adapter<NewVoicemailViewHolder>
    implements NewVoicemailViewHolderListener {

  private final Cursor cursor;
  private final Clock clock;
  private final FragmentManager fragmentManager;
  /** A valid id for {@link VoicemailEntry} is greater than 0 */
  private int currentlyExpandedViewHolderId = -1;

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
  private final ArrayMap<Integer, NewVoicemailViewHolder> newVoicemailViewHolderArrayMap =
      new ArrayMap<>();

  // A single instance of a media player re-used across the expanded view holders.
  private final NewVoicemailMediaPlayer mediaPlayer =
      new NewVoicemailMediaPlayer(new MediaPlayer());

  /** @param cursor whose projection is {@link VoicemailCursorLoader.VOICEMAIL_COLUMNS} */
  NewVoicemailAdapter(Cursor cursor, Clock clock, FragmentManager fragmentManager) {
    LogUtil.enterBlock("NewVoicemailAdapter");
    this.cursor = cursor;
    this.clock = clock;
    this.fragmentManager = fragmentManager;
    initializeMediaPlayerListeners();
  }

  private void initializeMediaPlayerListeners() {
    mediaPlayer.setOnCompletionListener(onCompletionListener);
    mediaPlayer.setOnPreparedListener(onPreparedListener);
    mediaPlayer.setOnErrorListener(onErrorListener);
  }

  @Override
  public NewVoicemailViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
    View view = inflater.inflate(R.layout.new_voicemail_entry, viewGroup, false);
    NewVoicemailViewHolder newVoicemailViewHolder = new NewVoicemailViewHolder(view, clock, this);
    newVoicemailViewHolderSet.add(newVoicemailViewHolder);
    return newVoicemailViewHolder;
  }

  @Override
  public void onBindViewHolder(NewVoicemailViewHolder viewHolder, int position) {
    // Remove if the viewholder is being recycled.
    if (newVoicemailViewHolderArrayMap.containsKey(viewHolder.getViewHolderId())) {
      // TODO(uabdullah): Remove the logging, only here for debugging during development.
      LogUtil.i(
          "NewVoicemailAdapter.onBindViewHolder",
          "Removing from hashset:%d, hashsetSize:%d",
          viewHolder.getViewHolderId(),
          newVoicemailViewHolderArrayMap.size());

      newVoicemailViewHolderArrayMap.remove(viewHolder.getViewHolderId());
    }

    viewHolder.reset();
    cursor.moveToPosition(position);
    viewHolder.bindViewHolderValuesFromAdapter(
        cursor, fragmentManager, mediaPlayer, position, currentlyExpandedViewHolderId);

    // Need this to ensure correct getCurrentlyExpandedViewHolder() value
    newVoicemailViewHolderArrayMap.put(viewHolder.getViewHolderId(), viewHolder);

    // If the viewholder is playing the voicemail, keep updating its media player view (seekbar,
    // duration etc.)
    if (viewHolder.isViewHolderExpanded() && mediaPlayer.isPlaying()) {
      Assert.checkArgument(
          viewHolder
              .getViewHolderVoicemailUri()
              .equals(mediaPlayer.getLastPlayedOrPlayingVoicemailUri()),
          "only the expanded view holder can be playing.");
      Assert.isNotNull(getCurrentlyExpandedViewHolder());
      Assert.checkArgument(
          getCurrentlyExpandedViewHolder()
              .getViewHolderVoicemailUri()
              .equals(mediaPlayer.getLastPlayedOrPlayingVoicemailUri()));

      recursivelyUpdateMediaPlayerViewOfExpandedViewHolder(viewHolder);
    }
    // Updates the hashmap with the most up-to-date state of the viewholder.
    newVoicemailViewHolderArrayMap.put(viewHolder.getViewHolderId(), viewHolder);
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
      // TODO(uabdullah): Remove this, here for debugging during development.
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
          "no view holder found in newVoicemailViewHolderArrayMap size:%d for %d",
          newVoicemailViewHolderArrayMap.size(),
          currentlyExpandedViewHolderId);
      return null;
    }
  }

  @Override
  public int getItemCount() {
    return cursor.getCount();
  }
}
