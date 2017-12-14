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
import com.android.dialer.common.LogUtil;

/** Fragment for Dialer Voicemail Tab. */
public final class NewVoicemailFragment extends Fragment implements LoaderCallbacks<Cursor> {

  private RecyclerView recyclerView;

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
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    LogUtil.enterBlock("NewVoicemailFragment.onLoaderReset");
    recyclerView.setAdapter(null);
  }
}
