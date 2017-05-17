/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.voicemail.impl.sms;

import android.app.PendingIntent;
import android.content.Context;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telephony.SmsManager;
import com.android.voicemail.impl.OmtpConstants;
import com.android.voicemail.impl.TelephonyMangerCompat;

/**
 * Send client originated OMTP messages to the OMTP server.
 *
 * <p>Uses {@link PendingIntent} instead of a call back to notify when the message is sent. This is
 * primarily to keep the implementation simple and reuse what the underlying {@link SmsManager}
 * interface provides.
 *
 * <p>Provides simple APIs to send different types of mobile originated OMTP SMS to the VVM server.
 */
public abstract class OmtpMessageSender {
  protected static final String TAG = "OmtpMessageSender";
  protected final Context mContext;
  protected final PhoneAccountHandle mPhoneAccountHandle;
  protected final short mApplicationPort;
  protected final String mDestinationNumber;

  public OmtpMessageSender(
      Context context,
      PhoneAccountHandle phoneAccountHandle,
      short applicationPort,
      String destinationNumber) {
    mContext = context;
    mPhoneAccountHandle = phoneAccountHandle;
    mApplicationPort = applicationPort;
    mDestinationNumber = destinationNumber;
  }

  /**
   * Sends a request to the VVM server to activate VVM for the current subscriber.
   *
   * @param sentIntent If not NULL this PendingIntent is broadcast when the message is successfully
   *     sent, or failed.
   */
  public void requestVvmActivation(@Nullable PendingIntent sentIntent) {}

  /**
   * Sends a request to the VVM server to deactivate VVM for the current subscriber.
   *
   * @param sentIntent If not NULL this PendingIntent is broadcast when the message is successfully
   *     sent, or failed.
   */
  public void requestVvmDeactivation(@Nullable PendingIntent sentIntent) {}

  /**
   * Send a request to the VVM server to get account status of the current subscriber.
   *
   * @param sentIntent If not NULL this PendingIntent is broadcast when the message is successfully
   *     sent, or failed.
   */
  public void requestVvmStatus(@Nullable PendingIntent sentIntent) {}

  protected void sendSms(String text, PendingIntent sentIntent) {
    TelephonyMangerCompat.sendVisualVoicemailSms(
        mContext, mPhoneAccountHandle, mDestinationNumber, mApplicationPort, text, sentIntent);
  }

  protected void appendField(StringBuilder sb, String field, Object value) {
    sb.append(field).append(OmtpConstants.SMS_KEY_VALUE_SEPARATOR).append(value);
  }
}
