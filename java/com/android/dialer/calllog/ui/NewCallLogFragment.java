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

import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.calllog.CallLogComponent;
import com.android.dialer.calllog.CallLogFramework;
import com.android.dialer.calllog.CallLogFramework.CallLogUi;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.CoalescedAnnotatedCallLog;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.DialerExecutorFactory;

/** The "new" call log fragment implementation, which is built on top of the annotated call log. */
public final class NewCallLogFragment extends Fragment
    implements CallLogUi, LoaderCallbacks<Cursor> {

  /*
   * This is a reasonable time that it might take between related call log writes, that also
   * shouldn't slow down single-writes too much. For example, when populating the database using
   * the simulator, using this value results in ~6 refresh cycles (on a release build) to write 120
   * call log entries.
   */
  private static final long WAIT_MILLIS = 100L;

  private DialerExecutor<Boolean> refreshAnnotatedCallLogTask;
  private RecyclerView recyclerView;

  public NewCallLogFragment() {
    LogUtil.enterBlock("NewCallLogFragment.NewCallLogFragment");
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);

    LogUtil.enterBlock("NewCallLogFragment.onCreate");

    CallLogComponent component = CallLogComponent.get(getContext());
    CallLogFramework callLogFramework = component.callLogFramework();
    callLogFramework.attachUi(this);

    DialerExecutorFactory dialerExecutorFactory =
        DialerExecutorComponent.get(getContext()).dialerExecutorFactory();

    refreshAnnotatedCallLogTask =
        dialerExecutorFactory
            .createUiTaskBuilder(
                getFragmentManager(),
                "NewCallLogFragment.refreshAnnotatedCallLog",
                component.getRefreshAnnotatedCallLogWorker())
            .build();
  }

  @Override
  public void onStart() {
    super.onStart();

    LogUtil.enterBlock("NewCallLogFragment.onStart");
  }

  @Override
  public void onResume() {
    super.onResume();

    LogUtil.enterBlock("NewCallLogFragment.onResume");

    CallLogFramework callLogFramework = CallLogComponent.get(getContext()).callLogFramework();
    callLogFramework.attachUi(this);

    // TODO: Consider doing this when fragment becomes visible.
    checkAnnotatedCallLogDirtyAndRefreshIfNecessary();
  }

  @Override
  public void onPause() {
    super.onPause();

    LogUtil.enterBlock("NewCallLogFragment.onPause");

    CallLogFramework callLogFramework = CallLogComponent.get(getContext()).callLogFramework();
    callLogFramework.detachUi();
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    LogUtil.enterBlock("NewCallLogFragment.onCreateView");

    View view = inflater.inflate(R.layout.new_call_log_fragment, container, false);
    recyclerView = view.findViewById(R.id.new_call_log_recycler_view);

    getLoaderManager().restartLoader(0, null, this);

    return view;
  }

  private void checkAnnotatedCallLogDirtyAndRefreshIfNecessary() {
    LogUtil.enterBlock("NewCallLogFragment.checkAnnotatedCallLogDirtyAndRefreshIfNecessary");
    refreshAnnotatedCallLogTask.executeSerialWithWait(false /* skipDirtyCheck */, WAIT_MILLIS);
  }

  @Override
  public void invalidateUi() {
    LogUtil.enterBlock("NewCallLogFragment.invalidateUi");
    refreshAnnotatedCallLogTask.executeSerialWithWait(true /* skipDirtyCheck */, WAIT_MILLIS);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    LogUtil.enterBlock("NewCallLogFragment.onCreateLoader");
    // CoalescedAnnotatedCallLog requires that all params be null.
    return new CursorLoader(
        getContext(), CoalescedAnnotatedCallLog.CONTENT_URI, null, null, null, null);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor newCursor) {
    LogUtil.enterBlock("NewCallLogFragment.onLoadFinished");

    // TODO: Handle empty cursor by showing empty view.
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    recyclerView.setAdapter(new NewCallLogAdapter(newCursor));
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    LogUtil.enterBlock("NewCallLogFragment.onLoaderReset");
    recyclerView.setAdapter(null);
  }
}
