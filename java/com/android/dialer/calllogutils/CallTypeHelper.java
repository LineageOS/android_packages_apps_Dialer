/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.calllogutils;

import android.content.res.Resources;
import android.provider.CallLog.Calls;

/** Helper class to perform operations related to call types. */
public class CallTypeHelper {

  /** Name used to identify incoming calls. */
  private final CharSequence incomingName;
  /** Name used to identify incoming calls which were transferred to another device. */
  private final CharSequence incomingPulledName;
  /** Name used to identify outgoing calls. */
  private final CharSequence outgoingName;
  /** Name used to identify outgoing calls which were transferred to another device. */
  private final CharSequence outgoingPulledName;
  /** Name used to identify missed calls. */
  private final CharSequence missedName;
  /** Name used to identify incoming video calls. */
  private final CharSequence incomingVideoName;
  /** Name used to identify incoming video calls which were transferred to another device. */
  private final CharSequence incomingVideoPulledName;
  /** Name used to identify outgoing video calls. */
  private final CharSequence outgoingVideoName;
  /** Name used to identify outgoing video calls which were transferred to another device. */
  private final CharSequence outgoingVideoPulledName;
  /** Name used to identify missed video calls. */
  private final CharSequence missedVideoName;
  /** Name used to identify voicemail calls. */
  private final CharSequence voicemailName;
  /** Name used to identify rejected calls. */
  private final CharSequence rejectedName;
  /** Name used to identify blocked calls. */
  private final CharSequence blockedName;
  /** Name used to identify calls which were answered on another device. */
  private final CharSequence answeredElsewhereName;
  /** Name used to identify incoming Duo calls. */

  public CallTypeHelper(Resources resources) {
    // Cache these values so that we do not need to look them up each time.
    incomingName = resources.getString(R.string.type_incoming);
    incomingPulledName = resources.getString(R.string.type_incoming_pulled);
    outgoingName = resources.getString(R.string.type_outgoing);
    outgoingPulledName = resources.getString(R.string.type_outgoing_pulled);
    missedName = resources.getString(R.string.type_missed);
    incomingVideoName = resources.getString(R.string.type_incoming_video);
    incomingVideoPulledName = resources.getString(R.string.type_incoming_video_pulled);
    outgoingVideoName = resources.getString(R.string.type_outgoing_video);
    outgoingVideoPulledName = resources.getString(R.string.type_outgoing_video_pulled);
    missedVideoName = resources.getString(R.string.type_missed_video);
    voicemailName = resources.getString(R.string.type_voicemail);
    rejectedName = resources.getString(R.string.type_rejected);
    blockedName = resources.getString(R.string.type_blocked);
    answeredElsewhereName = resources.getString(R.string.type_answered_elsewhere);
  }

  public static boolean isMissedCallType(int callType) {
    return (callType != Calls.INCOMING_TYPE
        && callType != Calls.OUTGOING_TYPE
        && callType != Calls.VOICEMAIL_TYPE
        && callType != Calls.ANSWERED_EXTERNALLY_TYPE);
  }

  /** Returns the text used to represent the given call type. */
  public CharSequence getCallTypeText(
      int callType, boolean isVideoCall, boolean isPulledCall) {
    switch (callType) {
      case Calls.INCOMING_TYPE:
        if (isVideoCall) {
          if (isPulledCall) {
            return incomingVideoPulledName;
          } else {
            return incomingVideoName;
          }
        } else {
          if (isPulledCall) {
            return incomingPulledName;
          } else {
            return incomingName;
          }
        }

      case Calls.OUTGOING_TYPE:
        if (isVideoCall) {
          if (isPulledCall) {
            return outgoingVideoPulledName;
          } else {
            return outgoingVideoName;
          }
        } else {
          if (isPulledCall) {
            return outgoingPulledName;
          } else {
            return outgoingName;
          }
        }

      case Calls.MISSED_TYPE:
        if (isVideoCall) {
          return missedVideoName;
        } else {
          return missedName;
        }

      case Calls.VOICEMAIL_TYPE:
        return voicemailName;

      case Calls.REJECTED_TYPE:
        return rejectedName;

      case Calls.BLOCKED_TYPE:
        return blockedName;

      case Calls.ANSWERED_EXTERNALLY_TYPE:
        return answeredElsewhereName;

      default:
        return missedName;
    }
  }
}
