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

import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.calllog.CallLogComponent;
import com.android.dialer.calllog.CallLogFramework;
import com.android.dialer.calllog.CallLogFramework.CallLogUi;
import com.android.dialer.calllog.RefreshAnnotatedCallLogWorker;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.common.concurrent.UiListener;
import com.android.dialer.database.CallLogQueryHandler;
import com.android.dialer.database.CallLogQueryHandler.Listener;
import com.android.dialer.glidephotomanager.GlidePhotoManagerComponent;
import com.google.common.util.concurrent.ListenableFuture;

// TODO(uabdullah): Register content observer for VoicemailContract.Status.CONTENT_URI in onStart
/** Fragment for Dialer Voicemail Tab. */
public final class NewVoicemailFragment extends Fragment
    implements LoaderCallbacks<Cursor>, CallLogUi {

  /*
   * This is a reasonable time that it might take between related call log writes, that also
   * shouldn't slow down single-writes too much. For example, when populating the database using
   * the simulator, using this value results in ~6 refresh cycles (on a release build) to write 120
   * call log entries.
   */
  private static final long WAIT_MILLIS = 100L;

  private RefreshAnnotatedCallLogWorker refreshAnnotatedCallLogWorker;
  private UiListener<Void> refreshAnnotatedCallLogListener;
  @Nullable private Runnable refreshAnnotatedCallLogRunnable;

  private RecyclerView recyclerView;
  private CallLogQueryHandler callLogQueryHandler;

  public NewVoicemailFragment() {
    LogUtil.enterBlock("NewVoicemailFragment.NewVoicemailFragment");
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    LogUtil.enterBlock("NewVoicemailFragment.onActivityCreated");

    CallLogComponent component = CallLogComponent.get(getContext());
    CallLogFramework callLogFramework = component.callLogFramework();
    callLogFramework.attachUi(this);

    // TODO(zachh): Use support fragment manager and add support for them in executors library.
    refreshAnnotatedCallLogListener =
        DialerExecutorComponent.get(getContext())
            .createUiListener(
                getActivity().getFragmentManager(), "NewVoicemailFragment.refreshAnnotatedCallLog");
    refreshAnnotatedCallLogWorker = component.getRefreshAnnotatedCallLogWorker();
  }

  @Override
  public void onStart() {
    super.onStart();
    LogUtil.enterBlock("NewVoicemailFragment.onStart");
  }

  @Override
  public void onResume() {
    super.onResume();

    LogUtil.enterBlock("NewCallLogFragment.onResume");

    CallLogFramework callLogFramework = CallLogComponent.get(getContext()).callLogFramework();
    callLogFramework.attachUi(this);

    // TODO(zachh): Consider doing this when fragment becomes visible.
    refreshAnnotatedCallLog(true /* checkDirty */);
  }

  @Override
  public void onPause() {
    super.onPause();

    LogUtil.enterBlock("NewVoicemailFragment.onPause");

    // This is pending work that we don't actually need to follow through with.
    ThreadUtil.getUiThreadHandler().removeCallbacks(refreshAnnotatedCallLogRunnable);

    CallLogFramework callLogFramework = CallLogComponent.get(getContext()).callLogFramework();
    callLogFramework.detachUi();
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    LogUtil.enterBlock("NewVoicemailFragment.onCreateView");
    View view = inflater.inflate(R.layout.new_voicemail_call_log_fragment, container, false);
    recyclerView = view.findViewById(R.id.new_voicemail_call_log_recycler_view);
    getLoaderManager().restartLoader(0, null, this);
    return view;
  }

  private void refreshAnnotatedCallLog(boolean checkDirty) {
    LogUtil.enterBlock("NewVoicemailFragment.refreshAnnotatedCallLog");

    // If we already scheduled a refresh, cancel it and schedule a new one so that repeated requests
    // in quick succession don't result in too much work. For example, if we get 10 requests in
    // 10ms, and a complete refresh takes a constant 200ms, the refresh will take 300ms (100ms wait
    // and 1 iteration @200ms) instead of 2 seconds (10 iterations @ 200ms) since the work requests
    // are serialized in RefreshAnnotatedCallLogWorker.
    //
    // We might get many requests in quick succession, for example, when the simulator inserts
    // hundreds of rows into the system call log, or when the data for a new call is incrementally
    // written to different columns as it becomes available.
    ThreadUtil.getUiThreadHandler().removeCallbacks(refreshAnnotatedCallLogRunnable);

    refreshAnnotatedCallLogRunnable =
        () -> {
          ListenableFuture<Void> future =
              checkDirty
                  ? refreshAnnotatedCallLogWorker.refreshWithDirtyCheck()
                  : refreshAnnotatedCallLogWorker.refreshWithoutDirtyCheck();
          refreshAnnotatedCallLogListener.listen(
              getContext(),
              future,
              unused -> {},
              throwable -> {
                throw new RuntimeException(throwable);
              });
        };
    ThreadUtil.getUiThreadHandler().postDelayed(refreshAnnotatedCallLogRunnable, WAIT_MILLIS);
  }

  @Override
  public void invalidateUi() {
    LogUtil.enterBlock("NewVoicemailFragment.invalidateUi");
    refreshAnnotatedCallLog(false /* checkDirty */);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    LogUtil.enterBlock("NewVoicemailFragment.onCreateLoader");
    return new VoicemailCursorLoader(getContext());
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    LogUtil.i("NewVoicemailFragment.onLoadFinished", "cursor size is %d", data.getCount());
    if (recyclerView.getAdapter() == null) {
      recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
      // TODO(uabdullah): Replace getActivity().getFragmentManager() with getChildFragment()
      recyclerView.setAdapter(
          new NewVoicemailAdapter(
              data,
              System::currentTimeMillis,
              getActivity().getFragmentManager(),
              GlidePhotoManagerComponent.get(getContext()).glidePhotoManager()));
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
    }
    callLogQueryHandler =
        new CallLogQueryHandler(
            getContext(), getContext().getContentResolver(), new NewVoicemailFragmentListener());
    callLogQueryHandler.fetchVoicemailStatus();
  }

  private final class NewVoicemailFragmentListener implements Listener {

    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
      LogUtil.enterBlock("NewVoicemailFragmentListener.onVoicemailStatusFetched");
      ((NewVoicemailAdapter) recyclerView.getAdapter()).setVoicemailStatusCursor(statusCursor);
      ((NewVoicemailAdapter) recyclerView.getAdapter()).updateAlert(getContext());
    }

    @Override
    public void onVoicemailUnreadCountFetched(Cursor cursor) {}

    @Override
    public void onMissedCallsUnreadCountFetched(Cursor cursor) {}

    @Override
    public boolean onCallsFetched(Cursor combinedCursor) {
      return false;
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    LogUtil.enterBlock("NewVoicemailFragment.onLoaderReset");
    recyclerView.setAdapter(null);
  }
}
