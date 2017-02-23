/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.app.calllog;

import android.app.Activity;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.VoicemailContract;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.app.R;
import com.android.dialer.app.list.ListsFragment;
import com.android.dialer.app.voicemail.VoicemailAudioManager;
import com.android.dialer.app.voicemail.VoicemailErrorManager;
import com.android.dialer.app.voicemail.VoicemailPlaybackPresenter;
import com.android.dialer.common.LogUtil;

public class VisualVoicemailCallLogFragment extends CallLogFragment {

  private final ContentObserver mVoicemailStatusObserver = new CustomContentObserver();
  private VoicemailPlaybackPresenter mVoicemailPlaybackPresenter;

  private VoicemailErrorManager mVoicemailAlertManager;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    mCallTypeFilter = CallLog.Calls.VOICEMAIL_TYPE;
    mVoicemailPlaybackPresenter = VoicemailPlaybackPresenter.getInstance(getActivity(), state);
    getActivity()
        .getContentResolver()
        .registerContentObserver(
            VoicemailContract.Status.CONTENT_URI, true, mVoicemailStatusObserver);
  }

  @Override
  protected VoicemailPlaybackPresenter getVoicemailPlaybackPresenter() {
    return mVoicemailPlaybackPresenter;
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    mVoicemailAlertManager =
        new VoicemailErrorManager(getContext(), getAdapter().getAlertManager(), mModalAlertManager);
    getActivity()
        .getContentResolver()
        .registerContentObserver(
            VoicemailContract.Status.CONTENT_URI,
            true,
            mVoicemailAlertManager.getContentObserver());
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
    View view = inflater.inflate(R.layout.call_log_fragment, container, false);
    setupView(view);
    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
    mVoicemailPlaybackPresenter.onResume();
    mVoicemailAlertManager.onResume();
  }

  @Override
  public void onPause() {
    mVoicemailPlaybackPresenter.onPause();
    mVoicemailAlertManager.onPause();
    super.onPause();
  }

  @Override
  public void onDestroy() {
    getActivity()
        .getContentResolver()
        .unregisterContentObserver(mVoicemailAlertManager.getContentObserver());
    mVoicemailPlaybackPresenter.onDestroy();
    getActivity().getContentResolver().unregisterContentObserver(mVoicemailStatusObserver);
    super.onDestroy();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mVoicemailPlaybackPresenter.onSaveInstanceState(outState);
  }

  @Override
  public void fetchCalls() {
    super.fetchCalls();
    ((ListsFragment) getParentFragment()).updateTabUnreadCounts();
  }

  @Override
  public void onPageResume(@Nullable Activity activity) {
    LogUtil.d("VisualVoicemailCallLogFragment.onPageResume", null);
    super.onPageResume(activity);
    if (activity != null) {
      activity.setVolumeControlStream(VoicemailAudioManager.PLAYBACK_STREAM);
    }
  }

  @Override
  public void onPagePause(@Nullable Activity activity) {
    LogUtil.d("VisualVoicemailCallLogFragment.onPagePause", null);
    super.onPagePause(activity);
    if (activity != null) {
      activity.setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
    }
  }
}
