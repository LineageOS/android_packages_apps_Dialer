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

package com.android.dialer.calldetails;

import android.content.Context;
import android.support.annotation.CallSuper;
import android.support.annotation.MainThread;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.android.dialer.calldetails.CallDetailsEntryViewHolder.CallDetailsEntryListener;
import com.android.dialer.calldetails.CallDetailsFooterViewHolder.DeleteCallDetailsListener;
import com.android.dialer.calldetails.CallDetailsFooterViewHolder.ReportCallIdListener;
import com.android.dialer.calldetails.CallDetailsHeaderViewHolder.CallDetailsHeaderListener;
import com.android.dialer.calllogutils.CallTypeHelper;
import com.android.dialer.calllogutils.CallbackActionHelper;
import com.android.dialer.calllogutils.CallbackActionHelper.CallbackAction;
import com.android.dialer.callrecord.CallRecordingDataStore;
import com.android.dialer.common.Assert;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.glidephotomanager.PhotoInfo;

/**
 * Contains common logic shared between {@link OldCallDetailsAdapter} and {@link
 * CallDetailsAdapter}.
 */
abstract class CallDetailsAdapterCommon extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private static final int HEADER_VIEW_TYPE = 1;
  private static final int CALL_ENTRY_VIEW_TYPE = 2;
  private static final int FOOTER_VIEW_TYPE = 3;

  private final CallDetailsEntryListener callDetailsEntryListener;
  private final CallDetailsHeaderListener callDetailsHeaderListener;
  private final ReportCallIdListener reportCallIdListener;
  private final DeleteCallDetailsListener deleteCallDetailsListener;
  private final CallTypeHelper callTypeHelper;
  private final CallRecordingDataStore callRecordingDataStore;

  private CallDetailsEntries callDetailsEntries;

  protected abstract void bindCallDetailsHeaderViewHolder(
      CallDetailsHeaderViewHolder viewHolder, int position);

  protected abstract CallDetailsHeaderViewHolder createCallDetailsHeaderViewHolder(
      View container, CallDetailsHeaderListener callDetailsHeaderListener);

  /** Returns the phone number of the call details. */
  protected abstract String getNumber();

  /** Returns the primary text shown on call details toolbar, usually contact name or number. */
  protected abstract String getPrimaryText();

  /** Returns {@link PhotoInfo} of the contact. */
  protected abstract PhotoInfo getPhotoInfo();

  CallDetailsAdapterCommon(
      Context context,
      CallDetailsEntries callDetailsEntries,
      CallDetailsEntryListener callDetailsEntryListener,
      CallDetailsHeaderListener callDetailsHeaderListener,
      ReportCallIdListener reportCallIdListener,
      DeleteCallDetailsListener deleteCallDetailsListener,
      CallRecordingDataStore callRecordingDataStore) {
    this.callDetailsEntries = callDetailsEntries;
    this.callDetailsEntryListener = callDetailsEntryListener;
    this.callDetailsHeaderListener = callDetailsHeaderListener;
    this.reportCallIdListener = reportCallIdListener;
    this.deleteCallDetailsListener = deleteCallDetailsListener;
    this.callRecordingDataStore = callRecordingDataStore;
    this.callTypeHelper =
        new CallTypeHelper(context.getResources(), DuoComponent.get(context).getDuo());
  }

  @Override
  @CallSuper
  public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    switch (viewType) {
      case HEADER_VIEW_TYPE:
        return createCallDetailsHeaderViewHolder(
            inflater.inflate(R.layout.contact_container, parent, false), callDetailsHeaderListener);
      case CALL_ENTRY_VIEW_TYPE:
        return new CallDetailsEntryViewHolder(
            inflater.inflate(R.layout.call_details_entry, parent, false), callDetailsEntryListener);
      case FOOTER_VIEW_TYPE:
        return new CallDetailsFooterViewHolder(
            inflater.inflate(R.layout.call_details_footer, parent, false),
            reportCallIdListener,
            deleteCallDetailsListener);
      default:
        throw Assert.createIllegalStateFailException(
            "No ViewHolder available for viewType: " + viewType);
    }
  }

  @Override
  @CallSuper
  public void onBindViewHolder(ViewHolder holder, int position) {
    if (position == 0) { // Header
      bindCallDetailsHeaderViewHolder((CallDetailsHeaderViewHolder) holder, position);
    } else if (position == getItemCount() - 1) {
      ((CallDetailsFooterViewHolder) holder).setPhoneNumber(getNumber());
    } else {
      CallDetailsEntryViewHolder viewHolder = (CallDetailsEntryViewHolder) holder;
      CallDetailsEntry entry = callDetailsEntries.getEntries(position - 1);
      viewHolder.setCallDetails(
          getNumber(),
          getPrimaryText(),
          getPhotoInfo(),
          entry,
          callTypeHelper,
          callRecordingDataStore,
          !entry.getHistoryResultsList().isEmpty() && position != getItemCount() - 2);
    }
  }

  @Override
  @CallSuper
  public int getItemViewType(int position) {
    if (position == 0) { // Header
      return HEADER_VIEW_TYPE;
    } else if (position == getItemCount() - 1) {
      return FOOTER_VIEW_TYPE;
    } else {
      return CALL_ENTRY_VIEW_TYPE;
    }
  }

  @Override
  @CallSuper
  public int getItemCount() {
    return callDetailsEntries.getEntriesCount() == 0
        ? 0
        : callDetailsEntries.getEntriesCount() + 2; // plus header and footer
  }

  final CallDetailsEntries getCallDetailsEntries() {
    return callDetailsEntries;
  }

  @MainThread
  final void updateCallDetailsEntries(CallDetailsEntries entries) {
    Assert.isMainThread();
    callDetailsEntries = entries;
    notifyDataSetChanged();
  }

  final @CallbackAction int getCallbackAction() {
    Assert.checkState(!callDetailsEntries.getEntriesList().isEmpty());

    CallDetailsEntry entry = callDetailsEntries.getEntries(0);
    return CallbackActionHelper.getCallbackAction(
        getNumber(), entry.getFeatures(), entry.getIsDuoCall());
  }
}
