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
import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import com.android.dialer.calllog.CallLogComponent;
import com.android.dialer.calllog.CallLogFramework;
import com.android.dialer.calllog.CallLogFramework.CallLogUi;
import com.android.dialer.calllog.database.AnnotatedCallLog.Columns;
import com.android.dialer.common.LogUtil;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** The "new" call log fragment implementation, which is built on top of the annotated call log. */
public final class NewCallLogFragment extends Fragment
    implements CallLogUi, LoaderCallbacks<Cursor> {

  private CursorAdapter cursorAdapter;

  public NewCallLogFragment() {
    LogUtil.enterBlock("NewCallLogFragment.NewCallLogFragment");
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);

    LogUtil.enterBlock("NewCallLogFragment.onCreate");

    CallLogFramework callLogFramework = CallLogComponent.get(getContext()).callLogFramework();
    callLogFramework.attachUi(this);
  }

  @Override
  public void onResume() {
    super.onResume();

    LogUtil.enterBlock("NewCallLogFragment.onResume");

    CallLogFramework callLogFramework = CallLogComponent.get(getContext()).callLogFramework();
    callLogFramework.attachUi(this);
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
    ListView listView = (ListView) view.findViewById(R.id.list);

    this.cursorAdapter =
        new MyCursorAdapter(
            getContext(),
            R.layout.new_call_log_entry,
            null /* cursor */,
            new String[] {Columns.TIMESTAMP, Columns.CONTACT_NAME},
            new int[] {R.id.timestamp, R.id.contact_name},
            0);
    listView.setAdapter(cursorAdapter);

    getLoaderManager().initLoader(0, null, this);

    return view;
  }

  @Override
  public void invalidateUi() {
    LogUtil.enterBlock("NewCallLogFragment.invalidateUi");
    // TODO: Implementation.
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    // TODO: This is sort of weird, do we need to implement a content provider?
    return new AnnotatedCallLogCursorLoader(getContext());
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor newCursor) {
    cursorAdapter.swapCursor(newCursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    cursorAdapter.swapCursor(null);
  }

  private static class MyCursorAdapter extends SimpleCursorAdapter {

    MyCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
      super(context, layout, c, from, to, flags);
    }

    @Override
    public void setViewText(TextView view, String text) {
      if (view.getId() == R.id.timestamp) {
        text = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Long.valueOf(text));
      }
      view.setText(text);
    }
  }
}
