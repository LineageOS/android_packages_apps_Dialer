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
import android.support.v7.widget.RecyclerView;
import android.util.ArraySet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.common.LogUtil;
import com.android.dialer.time.Clock;
import com.android.dialer.voicemail.listui.NewVoicemailViewHolder.NewVoicemailViewHolderListener;
import com.android.dialer.voicemail.model.VoicemailEntry;
import java.util.Set;

/** {@link RecyclerView.Adapter} for the new voicemail call log fragment. */
final class NewVoicemailAdapter extends RecyclerView.Adapter<NewVoicemailViewHolder>
    implements NewVoicemailViewHolderListener {

  private final Cursor cursor;
  private final Clock clock;
  private final FragmentManager fragmentManager;
  /** A valid id for {@link VoicemailEntry} is greater than 0 */
  private int currentlyExpandedViewHolderId = -1;

  // A set of (re-usable) view holders being used by the recycler view to display voicemails
  private final Set<NewVoicemailViewHolder> newVoicemailViewHolderSet = new ArraySet<>();

  /** @param cursor whose projection is {@link VoicemailCursorLoader.VOICEMAIL_COLUMNS} */
  NewVoicemailAdapter(Cursor cursor, Clock clock, FragmentManager fragmentManager) {
    LogUtil.enterBlock("NewVoicemailAdapter");
    this.cursor = cursor;
    this.clock = clock;
    this.fragmentManager = fragmentManager;
  }

  @Override
  public NewVoicemailViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
    LogUtil.enterBlock("NewVoicemailAdapter.onCreateViewHolder");
    LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
    View view = inflater.inflate(R.layout.new_voicemail_entry, viewGroup, false);
    NewVoicemailViewHolder newVoicemailViewHolder = new NewVoicemailViewHolder(view, clock, this);
    newVoicemailViewHolderSet.add(newVoicemailViewHolder);
    return newVoicemailViewHolder;
  }

  @Override
  public void onBindViewHolder(NewVoicemailViewHolder viewHolder, int position) {
    cursor.moveToPosition(position);
    viewHolder.bind(cursor, fragmentManager);
    expandOrCollapseViewHolder(viewHolder);
  }

  /**
   * Ensures a voicemail {@link NewVoicemailViewHolder} that was expanded and scrolled out of view,
   * doesn't have it's corresponding recycled view also expanded. It also ensures than when the
   * expanded voicemail is scrolled back into view, it still remains expanded.
   *
   * @param viewHolder an {@link NewVoicemailViewHolder} that is either expanded or collapsed
   */
  private void expandOrCollapseViewHolder(NewVoicemailViewHolder viewHolder) {
    if (viewHolder.getViewHolderId() == currentlyExpandedViewHolderId) {
      viewHolder.expandViewHolder();
    } else {
      viewHolder.collapseViewHolder();
    }
  }

  @Override
  public int getItemCount() {
    return cursor.getCount();
  }

  /**
   * We can only have one expanded voicemail view holder. This allows us to ensure that except for
   * the currently expanded view holder, all the other view holders visible on the screen are
   * collapsed.
   *
   * @param expandedViewHolder is the view holder that is currently expanded.
   */
  @Override
  public void onViewHolderExpanded(NewVoicemailViewHolder expandedViewHolder) {
    currentlyExpandedViewHolderId = expandedViewHolder.getViewHolderId();
    for (NewVoicemailViewHolder viewHolder : newVoicemailViewHolderSet) {
      if (!viewHolder.equals(expandedViewHolder)) {
        viewHolder.collapseViewHolder();
      }
    }
  }
}
