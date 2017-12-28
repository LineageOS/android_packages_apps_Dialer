/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.incallui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ScreenEvent;
import com.android.incallui.ConferenceManagerPresenter.ConferenceManagerUi;
import com.android.incallui.baseui.BaseFragment;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import java.util.List;

/** Fragment that allows the user to manage a conference call. */
public class ConferenceManagerFragment
    extends BaseFragment<ConferenceManagerPresenter, ConferenceManagerUi>
    implements ConferenceManagerPresenter.ConferenceManagerUi {

  private ListView conferenceParticipantList;
  private ContactPhotoManager contactPhotoManager;
  private ConferenceParticipantListAdapter conferenceParticipantListAdapter;

  @Override
  public ConferenceManagerPresenter createPresenter() {
    return new ConferenceManagerPresenter();
  }

  @Override
  public ConferenceManagerPresenter.ConferenceManagerUi getUi() {
    return this;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (savedInstanceState != null) {
      Logger.get(getContext()).logScreenView(ScreenEvent.Type.CONFERENCE_MANAGEMENT, getActivity());
    }
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    final View parent = inflater.inflate(R.layout.conference_manager_fragment, container, false);

    conferenceParticipantList = (ListView) parent.findViewById(R.id.participantList);
    contactPhotoManager = ContactPhotoManager.getInstance(getActivity().getApplicationContext());

    return parent;
  }

  @Override
  public void onResume() {
    super.onResume();
    final CallList calls = CallList.getInstance();
    getPresenter().init(calls);
    // Request focus on the list of participants for accessibility purposes.  This ensures
    // that once the list of participants is shown, the first participant is announced.
    conferenceParticipantList.requestFocus();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
  }

  @Override
  public boolean isFragmentVisible() {
    return isVisible();
  }

  @Override
  public void update(List<DialerCall> participants, boolean parentCanSeparate) {
    if (conferenceParticipantListAdapter == null) {
      conferenceParticipantListAdapter =
          new ConferenceParticipantListAdapter(conferenceParticipantList, contactPhotoManager);

      conferenceParticipantList.setAdapter(conferenceParticipantListAdapter);
    }
    conferenceParticipantListAdapter.updateParticipants(participants, parentCanSeparate);
  }

  @Override
  public void refreshCall(DialerCall call) {
    conferenceParticipantListAdapter.refreshCall(call);
  }
}
