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

package com.android.dialer.enrichedcall;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.multimedia.MultimediaData;

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
  public void onCapabilitiesReceived(
      @NonNull String number, @NonNull EnrichedCallCapabilities capabilities) {}

  @Override
  public void registerStateChangedListener(@NonNull StateChangedListener listener) {}

  @Nullable
  @Override
  public Session getSession(@NonNull String number) {
    return null;
  }

  @Nullable
  @Override
  public Session getSession(long sessionId) {
    return null;
  }

  @Override
  public void unregisterStateChangedListener(@NonNull StateChangedListener listener) {}

  @Override
  public void onSessionStatusUpdate(long sessionId, @NonNull String number, int state) {}

  @Override
  public void onMessageUpdate(long sessionId, @NonNull String messageId, int state) {}

  @Override
  public void onIncomingCallComposerData(long sessionId, @NonNull MultimediaData multimediaData) {}
}
