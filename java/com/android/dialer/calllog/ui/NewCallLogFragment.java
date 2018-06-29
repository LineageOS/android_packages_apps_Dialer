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
package com.android.dialer.calllog.ui;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.calllog.CallLogComponent;
import com.android.dialer.calllog.RefreshAnnotatedCallLogReceiver;
import com.android.dialer.calllog.database.CallLogDatabaseComponent;
import com.android.dialer.calllog.database.Coalescer;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DefaultFutureCallback;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.SupportUiListener;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.metrics.Metrics;
import com.android.dialer.metrics.MetricsComponent;
import com.android.dialer.metrics.jank.RecyclerViewJankLogger;
import com.android.dialer.promotion.Promotion.PromotionType;
import com.android.dialer.promotion.PromotionComponent;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.widget.EmptyContentView;
import com.android.dialer.widget.EmptyContentView.OnEmptyViewActionButtonClickedListener;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** The "new" call log fragment implementation, which is built on top of the annotated call log. */
public final class NewCallLogFragment extends Fragment implements LoaderCallbacks<Cursor> {

  private static final int PHONE_PERMISSIONS_REQUEST_CODE = 1;
  private static final int LOADER_ID = 0;

  @VisibleForTesting
  static final long MARK_ALL_CALLS_READ_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(3);

  private RecyclerView recyclerView;
  private EmptyContentView emptyContentView;
  private RefreshAnnotatedCallLogReceiver refreshAnnotatedCallLogReceiver;
  private SupportUiListener<ImmutableList<CoalescedRow>> coalesingAnnotatedCallLogListener;

  private boolean shouldMarkCallsRead = false;
  private final Runnable setShouldMarkCallsReadTrue = () -> shouldMarkCallsRead = true;

  public NewCallLogFragment() {
    LogUtil.enterBlock("NewCallLogFragment.NewCallLogFragment");
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    LogUtil.enterBlock("NewCallLogFragment.onActivityCreated");

    refreshAnnotatedCallLogReceiver = new RefreshAnnotatedCallLogReceiver(getContext());
  }

  @Override
  public void onStart() {
    super.onStart();

    LogUtil.enterBlock("NewCallLogFragment.onStart");
  }

  @Override
  public void onResume() {
    super.onResume();

    boolean isHidden = isHidden();
    LogUtil.i("NewCallLogFragment.onResume", "isHidden = %s", isHidden);

    // As a fragment's onResume() is tied to the containing Activity's onResume(), being resumed is
    // not equivalent to becoming visible.
    // For example, when an activity with a hidden fragment is resumed, the fragment's onResume()
    // will be called but it is not visible.
    if (!isHidden) {
      onFragmentShown();
    }
  }

  @Override
  public void onStop() {
    super.onStop();

    if (recyclerView.getAdapter() != null) {
      ((NewCallLogAdapter) recyclerView.getAdapter()).logMetrics(getContext());
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    LogUtil.enterBlock("NewCallLogFragment.onPause");

    onFragmentHidden();
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    LogUtil.i("NewCallLogFragment.onHiddenChanged", "hidden = %s", hidden);

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
    LoaderManager loaderManager = getLoaderManager();
    if (!PermissionsUtil.hasCallLogReadPermissions(getContext())) {
      recyclerView.setVisibility(View.GONE);
      emptyContentView.setVisibility(View.VISIBLE);
      loaderManager.destroyLoader(LOADER_ID);
      return;
    }

    recyclerView.setVisibility(View.VISIBLE);
    emptyContentView.setVisibility(View.GONE);

    // This can happen if permissions were not enabled when the fragment was created.
    if (loaderManager.getLoader(LOADER_ID) == null) {
      loaderManager.restartLoader(LOADER_ID, null, this);
    }

    registerRefreshAnnotatedCallLogReceiver();

    CallLogComponent.get(getContext())
        .getRefreshAnnotatedCallLogNotifier()
        .notify(/* checkDirty = */ true);

    // There are some types of data that we show in the call log that are not represented in the
    // AnnotatedCallLog. For example, CP2 information for invalid numbers can sometimes only be
    // fetched at display time. Because of this, we need to clear the adapter's cache and update it
    // whenever the user arrives at the call log (rather than relying on changes to the CursorLoader
    // alone).
    if (recyclerView.getAdapter() != null) {
      ((NewCallLogAdapter) recyclerView.getAdapter()).clearCache();
      recyclerView.getAdapter().notifyDataSetChanged();
    }

    // We shouldn't mark the calls as read immediately when the 3 second timer expires because we
    // don't want to disrupt the UI; instead we set a bit indicating to mark them read when the user
    // leaves the fragment (in onPause).
    shouldMarkCallsRead = false;
    ThreadUtil.getUiThreadHandler()
        .postDelayed(setShouldMarkCallsReadTrue, MARK_ALL_CALLS_READ_WAIT_MILLIS);
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
    // This is pending work that we don't actually need to follow through with.
    ThreadUtil.getUiThreadHandler().removeCallbacks(setShouldMarkCallsReadTrue);

    unregisterRefreshAnnotatedCallLogReceiver();

    if (shouldMarkCallsRead) {
      Futures.addCallback(
          CallLogComponent.get(getContext()).getClearMissedCalls().clearAll(),
          new DefaultFutureCallback<>(),
          MoreExecutors.directExecutor());
    }
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    LogUtil.enterBlock("NewCallLogFragment.onCreateView");

    View view = inflater.inflate(R.layout.new_call_log_fragment, container, false);
    recyclerView = view.findViewById(R.id.new_call_log_recycler_view);
    recyclerView.addOnScrollListener(
        new RecyclerViewJankLogger(
            MetricsComponent.get(getContext()).metrics(), Metrics.NEW_CALL_LOG_JANK_EVENT_NAME));

    emptyContentView = view.findViewById(R.id.new_call_log_empty_content_view);
    configureEmptyContentView();

    coalesingAnnotatedCallLogListener =
        DialerExecutorComponent.get(getContext())
            .createUiListener(
                getChildFragmentManager(),
                /* taskId = */ "NewCallLogFragment.coalescingAnnotatedCallLog");

    if (PermissionsUtil.hasCallLogReadPermissions(getContext())) {
      getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    return view;
  }

  private void configureEmptyContentView() {
    emptyContentView.setImage(R.drawable.quantum_ic_query_builder_vd_theme_24);
    emptyContentView.setDescription(R.string.new_call_log_permission_no_calllog);
    emptyContentView.setActionLabel(com.android.dialer.widget.R.string.permission_single_turn_on);
    emptyContentView.setActionClickedListener(new TurnOnPhonePermissions());
  }

  private class TurnOnPhonePermissions implements OnEmptyViewActionButtonClickedListener {

    @Override
    public void onEmptyViewActionButtonClicked() {
      if (getContext() == null) {
        LogUtil.w("TurnOnPhonePermissions.onEmptyViewActionButtonClicked", "no context");
        return;
      }
      String[] deniedPermissions =
          PermissionsUtil.getPermissionsCurrentlyDenied(
              getContext(), PermissionsUtil.allPhoneGroupPermissionsUsedInDialer);
      if (deniedPermissions.length > 0) {
        LogUtil.i(
            "TurnOnPhonePermissions.onEmptyViewActionButtonClicked",
            "requesting permissions: %s",
            Arrays.toString(deniedPermissions));
        // Don't implement onRequestPermissionsResult; instead rely on views being updated in
        // #onFragmentShown.
        requestPermissions(deniedPermissions, PHONE_PERMISSIONS_REQUEST_CODE);
      }
    }
  }

  private void registerRefreshAnnotatedCallLogReceiver() {
    LogUtil.enterBlock("NewCallLogFragment.registerRefreshAnnotatedCallLogReceiver");

    LocalBroadcastManager.getInstance(getContext())
        .registerReceiver(
            refreshAnnotatedCallLogReceiver, RefreshAnnotatedCallLogReceiver.getIntentFilter());
  }

  private void unregisterRefreshAnnotatedCallLogReceiver() {
    LogUtil.enterBlock("NewCallLogFragment.unregisterRefreshAnnotatedCallLogReceiver");

    // Cancel pending work as we don't need it any more.
    CallLogComponent.get(getContext()).getRefreshAnnotatedCallLogNotifier().cancel();

    LocalBroadcastManager.getInstance(getContext())
        .unregisterReceiver(refreshAnnotatedCallLogReceiver);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    LogUtil.enterBlock("NewCallLogFragment.onCreateLoader");
    return new AnnotatedCallLogCursorLoader(Assert.isNotNull(getContext()));
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor newCursor) {
    LogUtil.enterBlock("NewCallLogFragment.onLoadFinished");

    if (newCursor == null) {
      // This might be possible when the annotated call log hasn't been created but we're trying
      // to show the call log.
      LogUtil.w("NewCallLogFragment.onLoadFinished", "null cursor");
      return;
    }

    // Start combining adjacent rows which should be collapsed for display purposes.
    // This is a time-consuming process so we will do it in the background.
    ListenableFuture<ImmutableList<CoalescedRow>> coalescedRowsFuture =
        CallLogDatabaseComponent.get(getContext()).coalescer().coalesce(newCursor);

    coalesingAnnotatedCallLogListener.listen(
        getContext(),
        coalescedRowsFuture,
        coalescedRows -> {
          LogUtil.i("NewCallLogFragment.onLoadFinished", "coalescing succeeded");

          // TODO(zachh): Handle empty cursor by showing empty view.
          if (recyclerView.getAdapter() == null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            // Note: It's not clear if this callback can be invoked when there's no associated
            // activity, but if crashes are observed here it may be possible to use getContext()
            // instead.
            Activity activity = Assert.isNotNull(getActivity());
            recyclerView.setAdapter(
                new NewCallLogAdapter(
                    activity,
                    coalescedRows,
                    System::currentTimeMillis,
                    PromotionComponent.get(getContext())
                        .promotionManager()
                        .getHighestPriorityPromotion(PromotionType.CARD)
                        .orElse(null)));
          } else {
            ((NewCallLogAdapter) recyclerView.getAdapter()).updateRows(coalescedRows);
          }
        },
        throwable -> {
          // Coalescing can fail if the cursor passed to Coalescer is closed by the loader while
          // the work is still in progress.
          // This can happen when the loader restarts and finishes loading data before the
          // coalescing work is completed.
          // This failure is identified by ExpectedCoalescerException and doesn't need to be
          // thrown as coalescing will be restarted on the latest data obtained by the loader.
          if (!(throwable instanceof Coalescer.ExpectedCoalescerException)) {
            throw new AssertionError(throwable);
          }
        });
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    LogUtil.enterBlock("NewCallLogFragment.onLoaderReset");
    recyclerView.setAdapter(null);
  }
}
