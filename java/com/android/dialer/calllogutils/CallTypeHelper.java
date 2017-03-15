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
import com.android.dialer.compat.AppCompatConstants;

/** Helper class to perform operations related to call types. */
public class CallTypeHelper {

  /** Name used to identify incoming calls. */
  private final CharSequence mIncomingName;
  /** Name used to identify incoming calls which were transferred to another device. */
  private final CharSequence mIncomingPulledName;
  /** Name used to identify outgoing calls. */
  private final CharSequence mOutgoingName;
  /** Name used to identify outgoing calls which were transferred to another device. */
  private final CharSequence mOutgoingPulledName;
  /** Name used to identify missed calls. */
  private final CharSequence mMissedName;
  /** Name used to identify incoming video calls. */
  private final CharSequence mIncomingVideoName;
  /** Name used to identify incoming video calls which were transferred to another device. */
  private final CharSequence mIncomingVideoPulledName;
  /** Name used to identify outgoing video calls. */
  private final CharSequence mOutgoingVideoName;
  /** Name used to identify outgoing video calls which were transferred to another device. */
  private final CharSequence mOutgoingVideoPulledName;
  /** Name used to identify missed video calls. */
  private final CharSequence mMissedVideoName;
  /** Name used to identify voicemail calls. */
  private final CharSequence mVoicemailName;
  /** Name used to identify rejected calls. */
  private final CharSequence mRejectedName;
  /** Name used to identify blocked calls. */
  private final CharSequence mBlockedName;
  /** Name used to identify calls which were answered on another device. */
  private final CharSequence mAnsweredElsewhereName;

  public CallTypeHelper(Resources resources) {
    // Cache these values so that we do not need to look them up each time.
    mIncomingName = resources.getString(R.string.type_incoming);
    mIncomingPulledName = resources.getString(R.string.type_incoming_pulled);
    mOutgoingName = resources.getString(R.string.type_outgoing);
    mOutgoingPulledName = resources.getString(R.string.type_outgoing_pulled);
    mMissedName = resources.getString(R.string.type_missed);
    mIncomingVideoName = resources.getString(R.string.type_incoming_video);
    mIncomingVideoPulledName = resources.getString(R.string.type_incoming_video_pulled);
    mOutgoingVideoName = resources.getString(R.string.type_outgoing_video);
    mOutgoingVideoPulledName = resources.getString(R.string.type_outgoing_video_pulled);
    mMissedVideoName = resources.getString(R.string.type_missed_video);
    mVoicemailName = resources.getString(R.string.type_voicemail);
    mRejectedName = resources.getString(R.string.type_rejected);
    mBlockedName = resources.getString(R.string.type_blocked);
    mAnsweredElsewhereName = resources.getString(R.string.type_answered_elsewhere);
  }

  public static boolean isMissedCallType(int callType) {
    return (callType != AppCompatConstants.CALLS_INCOMING_TYPE
        && callType != AppCompatConstants.CALLS_OUTGOING_TYPE
        && callType != AppCompatConstants.CALLS_VOICEMAIL_TYPE
        && callType != AppCompatConstants.CALLS_ANSWERED_EXTERNALLY_TYPE);
  }

  /** Returns the text used to represent the given call type. */
  public CharSequence getCallTypeText(int callType, boolean isVideoCall, boolean isPulledCall) {
    switch (callType) {
      case AppCompatConstants.CALLS_INCOMING_TYPE:
        if (isVideoCall) {
          if (isPulledCall) {
            return mIncomingVideoPulledName;
          } else {
            return mIncomingVideoName;
          }
        } else {
          if (isPulledCall) {
            return mIncomingPulledName;
          } else {
            return mIncomingName;
          }
        }

      case AppCompatConstants.CALLS_OUTGOING_TYPE:
        if (isVideoCall) {
          if (isPulledCall) {
            return mOutgoingVideoPulledName;
          } else {
            return mOutgoingVideoName;
          }
        } else {
          if (isPulledCall) {
            return mOutgoingPulledName;
          } else {
            return mOutgoingName;
          }
        }

      case AppCompatConstants.CALLS_MISSED_TYPE:
        if (isVideoCall) {
          return mMissedVideoName;
        } else {
          return mMissedName;
        }

      case AppCompatConstants.CALLS_VOICEMAIL_TYPE:
        return mVoicemailName;

      case AppCompatConstants.CALLS_REJECTED_TYPE:
        return mRejectedName;

      case AppCompatConstants.CALLS_BLOCKED_TYPE:
        return mBlockedName;

      case AppCompatConstants.CALLS_ANSWERED_EXTERNALLY_TYPE:
        return mAnsweredElsewhereName;

      default:
        return mMissedName;
    }
  }
}
