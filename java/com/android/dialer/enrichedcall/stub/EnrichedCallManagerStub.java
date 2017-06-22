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

package com.android.dialer.enrichedcall.stub;

import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.calldetails.CallDetailsEntries;
import com.android.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.android.dialer.common.Assert;
import com.android.dialer.enrichedcall.EnrichedCallCapabilities;
import com.android.dialer.enrichedcall.EnrichedCallManager;
import com.android.dialer.enrichedcall.Session;
import com.android.dialer.enrichedcall.historyquery.proto.HistoryResult;
import com.android.dialer.enrichedcall.videoshare.VideoShareListener;
import com.android.dialer.multimedia.MultimediaData;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Stub implementation of {@link EnrichedCallManager}. */
public final class EnrichedCallManagerStub implements EnrichedCallManager {

  @Override
  public void registerCapabilitiesListener(@NonNull CapabilitiesListener listener) {}

  @Override
  public void requestCapabilities(@NonNull String number) {}

  @Override
  public void unregisterCapabilitiesListener(@NonNull CapabilitiesListener listener) {}

  @Override
  public EnrichedCallCapabilities getCapabilities(@NonNull String number) {
    return null;
  }

  @Override
  public void clearCachedData() {}

  @Override
  public long startCallComposerSession(@NonNull String number) {
    return Session.NO_SESSION_ID;
  }

  @Override
  public void sendCallComposerData(long sessionId, @NonNull MultimediaData data) {}

  @Override
  public void endCallComposerSession(long sessionId) {}

  @Override
  public void sendPostCallNote(@NonNull String number, @NonNull String message) {}

  @Override
  public void onCapabilitiesReceived(
      @NonNull String number, @NonNull EnrichedCallCapabilities capabilities) {}

  @Override
  public void registerStateChangedListener(@NonNull StateChangedListener listener) {}

  @Nullable
  @Override
  public Session getSession(
      @NonNull String uniqueCallId, @NonNull String number, @Nullable Filter filter) {
    return null;
  }

  @Nullable
  @Override
  public Session getSession(long sessionId) {
    return null;
  }

  @MainThread
  @NonNull
  @Override
  public List<String> getAllSessionsForDisplay() {
    Assert.isMainThread();
    return Collections.emptyList();
  }

  @NonNull
  @Override
  public Filter createIncomingCallComposerFilter() {
    return session -> false;
  }

  @NonNull
  @Override
  public Filter createOutgoingCallComposerFilter() {
    return session -> false;
  }

  @Nullable
  @Override
  @MainThread
  public Map<CallDetailsEntry, List<HistoryResult>> getAllHistoricalData(
      @NonNull String number, @NonNull CallDetailsEntries entries) {
    Assert.isMainThread();
    return null;
  }

  @Override
  public boolean hasStoredData() {
    Assert.isMainThread();
    return false;
  }

  @MainThread
  @Override
  public void requestAllHistoricalData(
      @NonNull String number, @NonNull CallDetailsEntries entries) {
    Assert.isMainThread();
  }

  @Override
  public void unregisterStateChangedListener(@NonNull StateChangedListener listener) {}

  @Override
  public void onSessionStatusUpdate(long sessionId, @NonNull String number, int state) {}

  @Override
  public void onMessageUpdate(long sessionId, @NonNull String messageId, int state) {}

  @Override
  public void onIncomingCallComposerData(long sessionId, @NonNull MultimediaData multimediaData) {}

  @Override
  public void onIncomingPostCallData(long sessionId, @NonNull MultimediaData multimediaData) {}

  @Override
  public void registerVideoShareListener(@NonNull VideoShareListener listener) {}

  @Override
  public void unregisterVideoShareListener(@NonNull VideoShareListener listener) {}

  @Override
  public boolean onIncomingVideoShareInvite(long sessionId, @NonNull String number) {
    return false;
  }

  @Override
  public long startVideoShareSession(String number) {
    return Session.NO_SESSION_ID;
  }

  @Override
  public boolean acceptVideoShareSession(long sessionId) {
    return false;
  }

  @Override
  public long getVideoShareInviteSessionId(@NonNull String number) {
    return Session.NO_SESSION_ID;
  }

  @Override
  public void endVideoShareSession(long sessionId) {}
}
