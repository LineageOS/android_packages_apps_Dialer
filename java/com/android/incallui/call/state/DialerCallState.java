/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.incallui.call.state;

/** Defines different states of {@link com.android.incallui.call.DialerCall} */
public class DialerCallState {

  public static final int INVALID = 0;
  public static final int NEW = 1; /* The call is new. */
  public static final int IDLE = 2; /* The call is idle.  Nothing active */
  public static final int ACTIVE = 3; /* There is an active call */
  public static final int INCOMING = 4; /* A normal incoming phone call */
  public static final int CALL_WAITING = 5; /* Incoming call while another is active */
  public static final int DIALING = 6; /* An outgoing call during dial phase */
  public static final int REDIALING = 7; /* Subsequent dialing attempt after a failure */
  public static final int ONHOLD = 8; /* An active phone call placed on hold */
  public static final int DISCONNECTING = 9; /* A call is being ended. */
  public static final int DISCONNECTED = 10; /* State after a call disconnects */
  public static final int CONFERENCED = 11; /* DialerCall part of a conference call */
  public static final int SELECT_PHONE_ACCOUNT = 12; /* Waiting for account selection */
  public static final int CONNECTING = 13; /* Waiting for Telecom broadcast to finish */
  public static final int BLOCKED = 14; /* The number was found on the block list */
  public static final int PULLING = 15; /* An external call being pulled to the device */
  public static final int CALL_PENDING = 16; /* A call is pending on a long process to finish */

  public static boolean isConnectingOrConnected(int state) {
    switch (state) {
      case ACTIVE:
      case INCOMING:
      case CALL_WAITING:
      case CONNECTING:
      case DIALING:
      case PULLING:
      case REDIALING:
      case ONHOLD:
      case CONFERENCED:
        return true;
      default:
        return false;
    }
  }

  public static boolean isDialing(int state) {
    return state == DIALING || state == PULLING || state == REDIALING;
  }

  public static String toString(int state) {
    switch (state) {
      case INVALID:
        return "INVALID";
      case NEW:
        return "NEW";
      case IDLE:
        return "IDLE";
      case ACTIVE:
        return "ACTIVE";
      case INCOMING:
        return "INCOMING";
      case CALL_WAITING:
        return "CALL_WAITING";
      case DIALING:
        return "DIALING";
      case PULLING:
        return "PULLING";
      case REDIALING:
        return "REDIALING";
      case ONHOLD:
        return "ONHOLD";
      case DISCONNECTING:
        return "DISCONNECTING";
      case DISCONNECTED:
        return "DISCONNECTED";
      case CONFERENCED:
        return "CONFERENCED";
      case SELECT_PHONE_ACCOUNT:
        return "SELECT_PHONE_ACCOUNT";
      case CONNECTING:
        return "CONNECTING";
      case BLOCKED:
        return "BLOCKED";
      default:
        return "UNKNOWN";
    }
  }
}
