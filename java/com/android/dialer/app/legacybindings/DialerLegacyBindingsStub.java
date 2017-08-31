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
 * limitations under the License
 */

package com.android.dialer.app.legacybindings;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.view.ViewGroup;
import com.android.dialer.app.calllog.CallLogAdapter;
import com.android.dialer.app.calllog.calllogcache.CallLogCache;
import com.android.dialer.app.contactinfo.ContactInfoCache;
import com.android.dialer.app.list.RegularSearchFragment;
import com.android.dialer.app.voicemail.VoicemailPlaybackPresenter;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;

/** Default implementation for dialer legacy bindings. */
public class DialerLegacyBindingsStub implements DialerLegacyBindings {

  @Override
  public CallLogAdapter newCallLogAdapter(
      Activity activity,
      ViewGroup alertContainer,
      CallLogAdapter.CallFetcher callFetcher,
      CallLogAdapter.MultiSelectRemoveView multiSelectRemoveView,
      CallLogAdapter.OnActionModeStateChangedListener actionModeStateChangedListener,
      CallLogCache callLogCache,
      ContactInfoCache contactInfoCache,
      VoicemailPlaybackPresenter voicemailPlaybackPresenter,
      @NonNull FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler,
      int activityType) {
    return new CallLogAdapter(
        activity,
        alertContainer,
        callFetcher,
        multiSelectRemoveView,
        actionModeStateChangedListener,
        callLogCache,
        contactInfoCache,
        voicemailPlaybackPresenter,
        filteredNumberAsyncQueryHandler,
        activityType);
  }

  @Override
  public RegularSearchFragment newRegularSearchFragment() {
    return new RegularSearchFragment();
  }
}
