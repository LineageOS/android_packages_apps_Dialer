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

import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.calldetails.CallDetailsEntries;
import com.android.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.android.dialer.enrichedcall.historyquery.proto.HistoryResult;
import com.android.dialer.enrichedcall.videoshare.VideoShareListener;
import com.android.dialer.multimedia.MultimediaData;
import java.util.List;
import java.util.Map;

/** Performs all enriched calling logic. */
public interface EnrichedCallManager {

  int POST_CALL_NOTE_MAX_CHAR = 60;

  /** Receives updates when enriched call capabilities are ready. */
  interface CapabilitiesListener {

    /** Callback fired when the capabilities are updated. */
    @MainThread
    void onCapabilitiesUpdated();
  }

  /**
   * Registers the given {@link CapabilitiesListener}.
   *
   * <p>As a result of this method, the listener will receive a call to {@link
   * CapabilitiesListener#onCapabilitiesUpdated()} after a call to {@link
   * #requestCapabilities(String)}.
   */
  @MainThread
  void registerCapabilitiesListener(@NonNull CapabilitiesListener listener);

  /**
   * Starts an asynchronous process to get enriched call capabilities of the given number.
   *
   * <p>Registered listeners will receive a call to {@link
   * CapabilitiesListener#onCapabilitiesUpdated()} on completion.
   *
   * @param number the remote number in any format
   */
  @MainThread
  void requestCapabilities(@NonNull String number);

  /**
   * Unregisters the given {@link CapabilitiesListener}.
   *
   * <p>As a result of this method, the listener will not receive capabilities of the given number.
   */
  @MainThread
  void unregisterCapabilitiesListener(@NonNull CapabilitiesListener listener);

  /** Gets the cached capabilities for the given number, else null */
  @MainThread
  @Nullable
  EnrichedCallCapabilities getCapabilities(@NonNull String number);

  /** Clears any cached data, such as capabilities. */
  @MainThread
  void clearCachedData();

  /**
   * Starts a call composer session with the given remote number.
   *
   * @param number the remote number in any format
   * @return the id for the started session, or {@link Session#NO_SESSION_ID} if the session fails
   */
  @MainThread
  long startCallComposerSession(@NonNull String number);

  /**
   * Sends the given information through an open enriched call session. As per the enriched calling
   * spec, up to two messages are sent: the first is an enriched call data message that optionally
   * includes the subject and the second is the optional image data message.
   *
   * @param sessionId the id for the session. See {@link #startCallComposerSession(String)}
   * @param data the {@link MultimediaData}
   * @throws IllegalArgumentException if there's no open session with the given number
   * @throws IllegalStateException if the session isn't in the {@link Session#STATE_STARTED} state
   */
  @MainThread
  void sendCallComposerData(long sessionId, @NonNull MultimediaData data);

  /**
   * Ends the given call composer session. Ending a session means that the call composer session
   * will be closed.
   *
   * @param sessionId the id of the session to end
   */
  @MainThread
  void endCallComposerSession(long sessionId);

  /**
   * Sends a post call note to the given number.
   *
   * @throws IllegalArgumentException if message is longer than {@link #POST_CALL_NOTE_MAX_CHAR}
   *     characters
   */
  @MainThread
  void sendPostCallNote(@NonNull String number, @NonNull String message);

  /**
   * Called once the capabilities are available for a corresponding call to {@link
   * #requestCapabilities(String)}.
   *
   * @param number the remote number in any format
   * @param capabilities the supported capabilities
   */
  @MainThread
  void onCapabilitiesReceived(
      @NonNull String number, @NonNull EnrichedCallCapabilities capabilities);

  /** Receives updates when the state of an enriched call changes. */
  interface StateChangedListener {

    /**
     * Callback fired when state changes. Listeners should call {@link #getSession(long)} or {@link
     * #getSession(String, String, Filter)} to retrieve the new state.
     */
    void onEnrichedCallStateChanged();
  }

  /**
   * Registers the given {@link StateChangedListener}.
   *
   * <p>As a result of this method, the listener will receive updates when the state of any enriched
   * call changes.
   */
  @MainThread
  void registerStateChangedListener(@NonNull StateChangedListener listener);

  /**
   * Returns the {@link Session} for the given unique call id, falling back to the number. If a
   * filter is provided, it will be applied to both the uniqueCalId and number lookups.
   */
  @MainThread
  @Nullable
  Session getSession(@NonNull String uniqueCallId, @NonNull String number, @Nullable Filter filter);

  /** Returns the {@link Session} for the given sessionId, or {@code null} if no session exists. */
  @MainThread
  @Nullable
  Session getSession(long sessionId);

  /**
   * Returns a list containing viewable string representations of all existing sessions.
   *
   * <p>Intended for debug display purposes only.
   */
  @MainThread
  @NonNull
  List<String> getAllSessionsForDisplay();

  @NonNull
  Filter createIncomingCallComposerFilter();

  @NonNull
  Filter createOutgoingCallComposerFilter();

  /**
   * Starts an asynchronous process to get all historical data for the given number and set of
   * {@link CallDetailsEntries}.
   */
  @MainThread
  void requestAllHistoricalData(@NonNull String number, @NonNull CallDetailsEntries entries);

  /**
   * Returns a mapping of enriched call data for all of the given {@link CallDetailsEntries}, which
   * should not be modified. A {@code null} return indicates that clients should call {@link
   * #requestAllHistoricalData(String, CallDetailsEntries)}.
   *
   * <p>The mapping is created by finding the HistoryResults whose timestamps occurred during or
   * close after a CallDetailsEntry. A CallDetailsEntry can have multiple HistoryResults in the
   * event that both a CallComposer message and PostCall message were sent for the same call.
   */
  @Nullable
  @MainThread
  Map<CallDetailsEntry, List<HistoryResult>> getAllHistoricalData(
      @NonNull String number, @NonNull CallDetailsEntries entries);

  /** Returns true if any enriched calls have been made or received. */
  @MainThread
  boolean hasStoredData();

  /**
   * Unregisters the given {@link StateChangedListener}.
   *
   * <p>As a result of this method, the listener will not receive updates when the state of enriched
   * calls changes.
   */
  @MainThread
  void unregisterStateChangedListener(@NonNull StateChangedListener listener);

  /**
   * Called when the status of an enriched call session changes.
   *
   *
   * @throws IllegalArgumentException if the state is invalid
   */
  @MainThread
  void onSessionStatusUpdate(long sessionId, @NonNull String number, int state);

  /**
   * Called when the status of an enriched call message updates.
   *
   *
   * @throws IllegalArgumentException if the state is invalid
   * @throws IllegalStateException if there's no session for the given id
   */
  @MainThread
  void onMessageUpdate(long sessionId, @NonNull String messageId, int state);

  /**
   * Called when call composer data arrives for the given session.
   *
   * @throws IllegalStateException if there's no session for the given id
   */
  @MainThread
  void onIncomingCallComposerData(long sessionId, @NonNull MultimediaData multimediaData);

  /**
   * Called when post call data arrives for the given session.
   *
   * @throws IllegalStateException if there's no session for the given id
   */
  @MainThread
  void onIncomingPostCallData(long sessionId, @NonNull MultimediaData multimediaData);

  /**
   * Registers the given {@link VideoShareListener}.
   *
   * <p>As a result of this method, the listener will receive updates when any video share state
   * changes.
   */
  @MainThread
  void registerVideoShareListener(@NonNull VideoShareListener listener);

  /**
   * Unregisters the given {@link VideoShareListener}.
   *
   * <p>As a result of this method, the listener will not receive updates when any video share state
   * changes.
   */
  @MainThread
  void unregisterVideoShareListener(@NonNull VideoShareListener listener);

  /**
   * Called when an incoming video share invite is received.
   *
   * @return whether or not the invite was accepted by the manager (rejected when disabled)
   */
  @MainThread
  boolean onIncomingVideoShareInvite(long sessionId, @NonNull String number);

  /**
   * Starts a video share session with the given remote number.
   *
   * @param number the remote number in any format
   * @return the id for the started session, or {@link Session#NO_SESSION_ID} if the session fails
   */
  @MainThread
  long startVideoShareSession(@NonNull String number);

  /**
   * Accepts a video share session invite.
   *
   * @param sessionId the session to accept
   * @return whether or not accepting the session succeeded
   */
  @MainThread
  boolean acceptVideoShareSession(long sessionId);

  /**
   * Retrieve the session id for an incoming video share invite.
   *
   * @param number the remote number in any format
   * @return the id for the session invite, or {@link Session#NO_SESSION_ID} if there is no invite
   */
  @MainThread
  long getVideoShareInviteSessionId(@NonNull String number);

  /**
   * Ends the given video share session.
   *
   * @param sessionId the id of the session to end
   */
  @MainThread
  void endVideoShareSession(long sessionId);

  /** Interface for filtering sessions (compatible with Predicate from Java 8) */
  interface Filter {
    boolean test(Session session);
  }
}
