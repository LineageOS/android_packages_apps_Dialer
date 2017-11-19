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
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.dialer.common.LogUtil;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.time.Clock;
import com.android.dialer.voicemail.model.VoicemailEntry;

/** {@link RecyclerView.ViewHolder} for the new voicemail tab. */
final class NewVoicemailViewHolder extends RecyclerView.ViewHolder implements OnClickListener {

  private final Context context;
  private final TextView primaryTextView;
  private final TextView secondaryTextView;
  private final TextView transcriptionTextView;
  private final QuickContactBadge quickContactBadge;
  private final NewVoicemailMediaPlayerView mediaPlayerView;
  private final Clock clock;
  private boolean isViewHolderExpanded;
  private int viewHolderId;
  private final NewVoicemailViewHolderListener voicemailViewHolderListener;

  NewVoicemailViewHolder(
      View view, Clock clock, NewVoicemailViewHolderListener newVoicemailViewHolderListener) {
    super(view);
    LogUtil.enterBlock("NewVoicemailViewHolder");
    this.context = view.getContext();
    primaryTextView = view.findViewById(R.id.primary_text);
    secondaryTextView = view.findViewById(R.id.secondary_text);
    transcriptionTextView = view.findViewById(R.id.transcription_text);
    quickContactBadge = view.findViewById(R.id.quick_contact_photo);
    mediaPlayerView = view.findViewById(R.id.new_voicemail_media_player);
    this.clock = clock;
    voicemailViewHolderListener = newVoicemailViewHolderListener;
  }

  void bind(Cursor cursor, FragmentManager fragmentManager) {
    VoicemailEntry voicemailEntry = VoicemailCursorLoader.toVoicemailEntry(cursor);
    viewHolderId = voicemailEntry.id();
    primaryTextView.setText(VoicemailEntryText.buildPrimaryVoicemailText(context, voicemailEntry));
    secondaryTextView.setText(
        VoicemailEntryText.buildSecondaryVoicemailText(context, clock, voicemailEntry));

    String voicemailTranscription = voicemailEntry.transcription();

    if (TextUtils.isEmpty(voicemailTranscription)) {
      transcriptionTextView.setVisibility(View.GONE);
      transcriptionTextView.setText(null);
    } else {
      transcriptionTextView.setVisibility(View.VISIBLE);
      transcriptionTextView.setText(voicemailTranscription);
    }

    itemView.setOnClickListener(this);
    setPhoto(voicemailEntry);
    mediaPlayerView.setVoicemailEntryValues(voicemailEntry);
    mediaPlayerView.setFragmentManager(fragmentManager);
  }

  // TODO(uabdullah): Consider/Implement TYPE (e.g Spam, TYPE_VOICEMAIL)
  private void setPhoto(VoicemailEntry voicemailEntry) {
    ContactPhotoManager.getInstance(context)
        .loadDialerThumbnailOrPhoto(
            quickContactBadge,
            voicemailEntry.lookupUri() == null ? null : Uri.parse(voicemailEntry.lookupUri()),
            voicemailEntry.photoId(),
            voicemailEntry.photoUri() == null ? null : Uri.parse(voicemailEntry.photoUri()),
            voicemailEntry.name(),
            LetterTileDrawable.TYPE_DEFAULT);
  }

  void collapseViewHolder() {
    transcriptionTextView.setMaxLines(1);
    isViewHolderExpanded = false;
    mediaPlayerView.setVisibility(View.GONE);
  }

  void expandViewHolder() {
    LogUtil.i("NewVoicemailViewHolder.expandViewHolder", "voicemail id: %d", viewHolderId);
    transcriptionTextView.setMaxLines(999);
    isViewHolderExpanded = true;
    mediaPlayerView.setVisibility(View.VISIBLE);
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  boolean isViewHolderExpanded() {
    return isViewHolderExpanded;
  }

  public int getViewHolderId() {
    return viewHolderId;
  }

  interface NewVoicemailViewHolderListener {
    void onViewHolderExpanded(NewVoicemailViewHolder expandedViewHolder);

    void onViewHolderCollapsed(NewVoicemailViewHolder expandedViewHolder);
  }

  @Override
  public void onClick(View v) {
    LogUtil.i(
        "NewVoicemailViewHolder.onClick",
        "voicemail id: %d, isViewHolderExpanded:%b",
        viewHolderId,
        isViewHolderExpanded);
    if (isViewHolderExpanded) {
      collapseViewHolder();
      voicemailViewHolderListener.onViewHolderCollapsed(this);
    } else {
      expandViewHolder();
      voicemailViewHolderListener.onViewHolderExpanded(this);
    }
  }
}
