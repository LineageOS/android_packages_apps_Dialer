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

import android.app.KeyguardManager;
import android.content.Intent;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.VoicemailContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.app.R;
import com.android.dialer.app.list.ListsFragment;
import com.android.dialer.app.voicemail.VoicemailAudioManager;
import com.android.dialer.app.voicemail.VoicemailErrorManager;
import com.android.dialer.app.voicemail.VoicemailPlaybackPresenter;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.util.PermissionsUtil;

public class VisualVoicemailCallLogFragment extends CallLogFragment {

  private final ContentObserver mVoicemailStatusObserver = new CustomContentObserver();
  private VoicemailPlaybackPresenter mVoicemailPlaybackPresenter;

  private VoicemailErrorManager mVoicemailErrorManager;

  public VisualVoicemailCallLogFragment() {
    super(CallLog.Calls.VOICEMAIL_TYPE);
  }

  @Override
  protected VoicemailPlaybackPresenter getVoicemailPlaybackPresenter() {
    return mVoicemailPlaybackPresenter;
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    mVoicemailPlaybackPresenter =
        VoicemailPlaybackPresenter.getInstance(getActivity(), savedInstanceState);

    if (PermissionsUtil.hasReadVoicemailPermissions(getContext())
        && PermissionsUtil.hasAddVoicemailPermissions(getContext())) {
      getActivity()
          .getContentResolver()
          .registerContentObserver(
              VoicemailContract.Status.CONTENT_URI, true, mVoicemailStatusObserver);
    } else {
      LogUtil.w(
          "VisualVoicemailCallLogFragment.onActivityCreated",
          "read voicemail permission unavailable.");
    }
    super.onActivityCreated(savedInstanceState);
    mVoicemailErrorManager =
        new VoicemailErrorManager(getContext(), getAdapter().getAlertManager(), mModalAlertManager);

    if (PermissionsUtil.hasReadVoicemailPermissions(getContext())
        && PermissionsUtil.hasAddVoicemailPermissions(getContext())) {
      getActivity()
          .getContentResolver()
          .registerContentObserver(
              VoicemailContract.Status.CONTENT_URI,
              true,
              mVoicemailErrorManager.getContentObserver());
    } else {
      LogUtil.w(
          "VisualVoicemailCallLogFragment.onActivityCreated",
          "read voicemail permission unavailable.");
    }
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
    mVoicemailErrorManager.onResume();
  }

  @Override
  public void onPause() {
    mVoicemailPlaybackPresenter.onPause();
    mVoicemailErrorManager.onPause();
    super.onPause();
  }

  @Override
  public void onDestroy() {
    getActivity()
        .getContentResolver()
        .unregisterContentObserver(mVoicemailErrorManager.getContentObserver());
    mVoicemailPlaybackPresenter.onDestroy();
    mVoicemailErrorManager.onDestroy();
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
  public void onVisible() {
    LogUtil.enterBlock("VisualVoicemailCallLogFragment.onVisible");
    super.onVisible();
    if (getActivity() != null) {
      Intent intent = new Intent(VoicemailContract.ACTION_SYNC_VOICEMAIL);
      intent.setPackage(getActivity().getPackageName());
      getActivity().sendBroadcast(intent);
      Logger.get(getActivity()).logImpression(DialerImpression.Type.VVM_TAB_VIEWED);
      getActivity().setVolumeControlStream(VoicemailAudioManager.PLAYBACK_STREAM);
    }
  }

  @Override
  public void onNotVisible() {
    LogUtil.enterBlock("VisualVoicemailCallLogFragment.onNotVisible");
    super.onNotVisible();
    if (getActivity() != null) {
      getActivity().setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);
      // onNotVisible will be called in the lock screen when the call ends
      if (!getActivity().getSystemService(KeyguardManager.class).inKeyguardRestrictedInputMode()) {
        LogUtil.i("VisualVoicemailCallLogFragment.onNotVisible", "clearing all new voicemails");
        CallLogNotificationsService.markAllNewVoicemailsAsOld(getActivity());
      }
    }
  }
}
