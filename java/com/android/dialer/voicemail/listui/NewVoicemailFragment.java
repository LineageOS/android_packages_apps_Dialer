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

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.VoicemailContract.Status;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.android.dialer.calllog.CallLogComponent;
import com.android.dialer.calllog.RefreshAnnotatedCallLogReceiver;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.UiListener;
import com.android.dialer.voicemail.listui.error.VoicemailStatus;
import com.android.dialer.voicemailstatus.VoicemailStatusQuery;
import com.android.dialer.widget.EmptyContentView;
import com.android.voicemail.VoicemailComponent;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;

// TODO(uabdullah): Register content observer for VoicemailContract.Status.CONTENT_URI in onStart
/** Fragment for Dialer Voicemail Tab. */
public final class NewVoicemailFragment extends Fragment implements LoaderCallbacks<Cursor> {

  private RecyclerView recyclerView;
  private RefreshAnnotatedCallLogReceiver refreshAnnotatedCallLogReceiver;
  private UiListener<ImmutableList<VoicemailStatus>> queryVoicemailStatusTableListener;

  // View required to show/hide recycler and empty views
  FrameLayout fragmentRootFrameLayout;

  private EmptyContentView emptyContentView;

  public NewVoicemailFragment() {
    LogUtil.enterBlock("NewVoicemailFragment.NewVoicemailFragment");
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    LogUtil.enterBlock("NewVoicemailFragment.onActivityCreated");

    refreshAnnotatedCallLogReceiver = new RefreshAnnotatedCallLogReceiver(getContext());
    queryVoicemailStatusTableListener =
        DialerExecutorComponent.get(getContext())
            .createUiListener(
                getActivity().getFragmentManager(),
                "NewVoicemailFragment.queryVoicemailStatusTable");
  }

  @Override
  public void onStart() {
    super.onStart();
    LogUtil.enterBlock("NewVoicemailFragment.onStart");
  }

  @Override
  public void onResume() {
    super.onResume();

    boolean isHidden = isHidden();
    LogUtil.i("NewVoicemailFragment.onResume", "isHidden = %s", isHidden);

    // As a fragment's onResume() is tied to the containing Activity's onResume(), being resumed is
    // not equivalent to becoming visible.
    // For example, when an activity with a hidden fragment is resumed, the fragment's onResume()
    // will be called but it is not visible.
    if (!isHidden) {
      onFragmentShown();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    LogUtil.enterBlock("NewVoicemailFragment.onPause");

    onFragmentHidden();
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    LogUtil.i("NewVoicemailFragment.onHiddenChanged", "hidden = %s", hidden);

    if (hidden) {
      onFragmentHidden();
    } else {
      onFragmentShown();
    }
  }

  /**
   * To be called when the fragment becomes visible.
   *
   * <p>Note that for a fragment, being resumed is not equivalent to becoming visible.
   *
   * <p>For example, when an activity with a hidden fragment is resumed, the fragment's onResume()
   * will be called but it is not visible.
   */
  private void onFragmentShown() {
    registerRefreshAnnotatedCallLogReceiver();

    CallLogComponent.get(getContext())
        .getRefreshAnnotatedCallLogNotifier()
        .notify(/* checkDirty = */ true);
  }

  /**
   * To be called when the fragment becomes hidden.
   *
   * <p>This can happen in the following two cases:
   *
   * <ul>
   *   <li>hide the fragment but keep the parent activity visible (e.g., calling {@link
   *       android.support.v4.app.FragmentTransaction#hide(Fragment)} in an activity, or
   *   <li>the parent activity is paused.
   * </ul>
   */
  private void onFragmentHidden() {
    unregisterRefreshAnnotatedCallLogReceiver();
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    LogUtil.enterBlock("NewVoicemailFragment.onCreateView");

    fragmentRootFrameLayout =
        (FrameLayout) inflater.inflate(R.layout.new_voicemail_call_log_fragment, container, false);
    recyclerView = fragmentRootFrameLayout.findViewById(R.id.new_voicemail_call_log_recycler_view);

    emptyContentView = fragmentRootFrameLayout.findViewById(R.id.empty_content_view);
    getLoaderManager().restartLoader(0, null, this);
    return fragmentRootFrameLayout;
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    LogUtil.enterBlock("NewVoicemailFragment.onCreateLoader");
    return new VoicemailCursorLoader(getContext());
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    LogUtil.i("NewVoicemailFragment.onLoadFinished", "cursor size is %d", data.getCount());
    if (data.getCount() == 0) {
      showEmptyVoicemailFragmentView();
      return;
    }
    showView(recyclerView);

    if (recyclerView.getAdapter() == null) {
      recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
      // TODO(uabdullah): Replace getActivity().getFragmentManager() with getChildFragment()
      recyclerView.setAdapter(
          new NewVoicemailAdapter(
              data, System::currentTimeMillis, getActivity().getFragmentManager()));
    } else {
      // This would only be called in cases such as when voicemail has been fetched from the server
      // or a changed occurred in the annotated table changed (e.g deletes). To check if the change
      // was due to a voicemail download,
      // NewVoicemailAdapter.mediaPlayer.getVoicemailRequestedToDownload() is called.
      LogUtil.i(
          "NewVoicemailFragment.onLoadFinished",
          "adapter: %s was not null, checking and playing the voicemail if conditions met",
          recyclerView.getAdapter());
      ((NewVoicemailAdapter) recyclerView.getAdapter()).updateCursor(data);
      ((NewVoicemailAdapter) recyclerView.getAdapter()).checkAndPlayVoicemail();
      queryAndUpdateVoicemailStatusAlert();
    }
  }

  /** Shows the view when there are no voicemails to be displayed * */
  private void showEmptyVoicemailFragmentView() {
    LogUtil.enterBlock("NewVoicemailFragment.showEmptyVoicemailFragmentView");

    showView(emptyContentView);

    emptyContentView.setDescription((R.string.empty_voicemail_tab_text));
    emptyContentView.setImage(R.drawable.quantum_ic_voicemail_vd_theme_24);
  }

  private void showView(View view) {
    LogUtil.i("NewVoicemailFragment.showView", "Showing view: " + view);
    emptyContentView.setVisibility(view == emptyContentView ? View.VISIBLE : View.GONE);
    recyclerView.setVisibility(view == recyclerView ? View.VISIBLE : View.GONE);
  }

  private void registerRefreshAnnotatedCallLogReceiver() {
    LogUtil.enterBlock("NewVoicemailFragment.registerRefreshAnnotatedCallLogReceiver");

    LocalBroadcastManager.getInstance(getContext())
        .registerReceiver(
            refreshAnnotatedCallLogReceiver, RefreshAnnotatedCallLogReceiver.getIntentFilter());
  }

  private void unregisterRefreshAnnotatedCallLogReceiver() {
    LogUtil.enterBlock("NewVoicemailFragment.unregisterRefreshAnnotatedCallLogReceiver");

    // Cancel pending work as we don't need it any more.
    CallLogComponent.get(getContext()).getRefreshAnnotatedCallLogNotifier().cancel();

    LocalBroadcastManager.getInstance(getContext())
        .unregisterReceiver(refreshAnnotatedCallLogReceiver);
  }

  private void queryAndUpdateVoicemailStatusAlert() {
    queryVoicemailStatusTableListener.listen(
        getContext(),
        queryVoicemailStatus(getContext()),
        this::updateVoicemailStatusAlert,
        throwable -> {
          throw new RuntimeException(throwable);
        });
  }

  private ListenableFuture<ImmutableList<VoicemailStatus>> queryVoicemailStatus(Context context) {
    return DialerExecutorComponent.get(context)
        .backgroundExecutor()
        .submit(
            () -> {
              StringBuilder where = new StringBuilder();
              List<String> selectionArgs = new ArrayList<>();

              VoicemailComponent.get(context)
                  .getVoicemailClient()
                  .appendOmtpVoicemailStatusSelectionClause(context, where, selectionArgs);

              ImmutableList.Builder<VoicemailStatus> statuses = ImmutableList.builder();

              try (Cursor cursor =
                  context
                      .getContentResolver()
                      .query(
                          Status.CONTENT_URI,
                          VoicemailStatusQuery.getProjection(),
                          where.toString(),
                          selectionArgs.toArray(new String[selectionArgs.size()]),
                          null)) {
                if (cursor == null) {
                  LogUtil.e(
                      "NewVoicemailFragment.queryVoicemailStatus", "query failed. Null cursor.");
                  return statuses.build();
                }

                LogUtil.i(
                    "NewVoicemailFragment.queryVoicemailStatus",
                    "cursor size:%d ",
                    cursor.getCount());

                while (cursor.moveToNext()) {
                  VoicemailStatus status = new VoicemailStatus(context, cursor);
                  if (status.isActive(context)) {
                    LogUtil.i(
                        "NewVoicemailFragment.queryVoicemailStatus", "inactive source ignored");
                    statuses.add(status);
                    // TODO(a bug): Handle Service State Listeners
                  }
                }
              }
              LogUtil.i(
                  "NewVoicemailFragment.queryVoicemailStatus",
                  "query returned %d results",
                  statuses.build().size());
              return statuses.build();
            });
  }

  private void updateVoicemailStatusAlert(ImmutableList<VoicemailStatus> voicemailStatuses) {
    ((NewVoicemailAdapter) recyclerView.getAdapter())
        .updateVoicemailAlertWithMostRecentStatus(getContext(), voicemailStatuses);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    LogUtil.enterBlock("NewVoicemailFragment.onLoaderReset");
    recyclerView.setAdapter(null);
  }
}
