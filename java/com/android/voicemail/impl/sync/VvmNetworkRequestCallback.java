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
package com.android.voicemail.impl.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.InetAddresses;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;

import androidx.annotation.CallSuper;

import com.android.dialer.common.Assert;
import com.android.voicemail.impl.OmtpEvents;
import com.android.voicemail.impl.OmtpVvmCarrierConfigHelper;
import com.android.voicemail.impl.VoicemailStatus;
import com.android.voicemail.impl.VvmLog;

/**
 * Base class for network request call backs for visual voicemail syncing with the Imap server. This
 * handles retries and network requests.
 */
public abstract class VvmNetworkRequestCallback extends ConnectivityManager.NetworkCallback {

  private static final String TAG = "VvmNetworkRequest";

  // Timeout used to call ConnectivityManager.requestNetwork
  private static final int NETWORK_REQUEST_TIMEOUT_MILLIS = 60 * 1000;

  public static final String NETWORK_REQUEST_FAILED_TIMEOUT = "timeout";
  public static final String NETWORK_REQUEST_FAILED_LOST = "lost";

  protected Context context;
  protected PhoneAccountHandle phoneAccount;
  protected NetworkRequest networkRequest;
  private ConnectivityManager connectivityManager;
  private final OmtpVvmCarrierConfigHelper carrierConfigHelper;
  private final VoicemailStatus.Editor status;
  private boolean requestSent = false;
  private boolean resultReceived = false;
  private boolean released = false;

  public VvmNetworkRequestCallback(
      Context context, PhoneAccountHandle phoneAccount, VoicemailStatus.Editor status) {
    this.context = context;
    this.phoneAccount = phoneAccount;
    this.status = status;
    carrierConfigHelper = new OmtpVvmCarrierConfigHelper(context, this.phoneAccount);
    networkRequest = createNetworkRequest();
  }

  public VvmNetworkRequestCallback(
      OmtpVvmCarrierConfigHelper config,
      PhoneAccountHandle phoneAccount,
      VoicemailStatus.Editor status) {
    context = config.getContext();
    this.phoneAccount = phoneAccount;
    this.status = status;
    carrierConfigHelper = config;
    networkRequest = createNetworkRequest();
  }

  public VoicemailStatus.Editor getVoicemailStatusEditor() {
    return status;
  }

  /**
   * @return NetworkRequest for a proper transport type. Use only cellular network if the carrier
   *     requires it. Otherwise use whatever available.
   */
  private NetworkRequest createNetworkRequest() {

    NetworkRequest.Builder builder =
        new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

    TelephonyManager telephonyManager =
        context.getSystemService(TelephonyManager.class).createForPhoneAccountHandle(phoneAccount);
    // At this point mPhoneAccount should always be valid and telephonyManager will never be null
    Assert.isNotNull(telephonyManager);
    if (carrierConfigHelper.isCellularDataRequired()) {
      VvmLog.d(TAG, "Transport type: CELLULAR");
      builder
          .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
          .setNetworkSpecifier(telephonyManager.getNetworkSpecifier());
    } else {
      VvmLog.d(TAG, "Transport type: ANY");
    }
    return builder.build();
  }

  public NetworkRequest getNetworkRequest() {
    return networkRequest;
  }

  @Override
  @CallSuper
  public void onLost(Network network) {
    VvmLog.i(TAG, "onLost");
    resultReceived = true;
    onFailed(NETWORK_REQUEST_FAILED_LOST);
  }

  @Override
  @CallSuper
  public void onAvailable(Network network) {
    super.onAvailable(network);
    resultReceived = true;
  }

  private static final int DEFAULT_IPV4_WAIT_DELAY_MS = 500; // in milliseconds
  private final ConditionVariable mWaitV4Cv = new ConditionVariable();
  @Override
  @CallSuper
  public void onLinkPropertiesChanged(Network network, LinkProperties lp) {
    boolean hasIPv4 = (lp != null) &&
            (lp.isReachable(InetAddresses.parseNumericAddress("8.8.8.8")));
    if(hasIPv4) {
        mWaitV4Cv.open();
    }
  }
  public void waitForIpv4() {
    VvmLog.w(TAG, "Waiting for IPV4 address...");
    mWaitV4Cv.block(DEFAULT_IPV4_WAIT_DELAY_MS);
  }

  @CallSuper
  public void onUnavailable() {
    VvmLog.i(TAG, "onUnavailable");
    resultReceived = true;
    onFailed(NETWORK_REQUEST_FAILED_TIMEOUT);
  }

  public void requestNetwork() {
    if (requestSent == true) {
      VvmLog.e(TAG, "requestNetwork() called twice");
      return;
    }
    requestSent = true;
    getConnectivityManager().requestNetwork(getNetworkRequest(), this);
    /**
     * Somehow requestNetwork() with timeout doesn't work, and it's a hidden method. Implement our
     * own timeout mechanism instead.
     */
    Handler handler = new Handler(Looper.getMainLooper());
    handler.postDelayed(
        new Runnable() {
          @Override
          public void run() {
            if (resultReceived == false) {
              onFailed(NETWORK_REQUEST_FAILED_TIMEOUT);
            }
          }
        },
        NETWORK_REQUEST_TIMEOUT_MILLIS);
  }

  public void releaseNetwork() {
    VvmLog.i(TAG, "releaseNetwork");
    if (!released) {
      getConnectivityManager().unregisterNetworkCallback(this);
      released = true;
    } else {
      VvmLog.w(TAG, "already released");
    }
  }

  public ConnectivityManager getConnectivityManager() {
    if (connectivityManager == null) {
      connectivityManager =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }
    return connectivityManager;
  }

  @CallSuper
  public void onFailed(String reason) {
    VvmLog.i(TAG, "onFailed: " + reason);
    if (carrierConfigHelper.isCellularDataRequired()) {
      carrierConfigHelper.handleEvent(status, OmtpEvents.DATA_NO_CONNECTION_CELLULAR_REQUIRED);
    } else {
      carrierConfigHelper.handleEvent(status, OmtpEvents.DATA_NO_CONNECTION);
    }
    releaseNetwork();
  }
}
