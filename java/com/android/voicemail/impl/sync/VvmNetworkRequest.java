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

package com.android.voicemail.impl.sync;

import android.annotation.TargetApi;
import android.net.Network;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.telecom.PhoneAccountHandle;
import com.android.voicemail.impl.OmtpVvmCarrierConfigHelper;
import com.android.voicemail.impl.VoicemailStatus;
import com.android.voicemail.impl.VvmLog;
import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Class to retrieve a {@link Network} synchronously. {@link #getNetwork(OmtpVvmCarrierConfigHelper,
 * PhoneAccountHandle)} will block until a suitable network is retrieved or it has failed.
 */
@SuppressWarnings("AndroidApiChecker") /* CompletableFuture is java8*/
@TargetApi(VERSION_CODES.O)
public class VvmNetworkRequest {

  private static final String TAG = "VvmNetworkRequest";

  /**
   * A wrapper around a Network returned by a {@link VvmNetworkRequestCallback}, which should be
   * closed once not needed anymore.
   */
  public static class NetworkWrapper implements Closeable {

    private final Network mNetwork;
    private final VvmNetworkRequestCallback mCallback;

    private NetworkWrapper(Network network, VvmNetworkRequestCallback callback) {
      mNetwork = network;
      mCallback = callback;
    }

    public Network get() {
      return mNetwork;
    }

    @Override
    public void close() {
      mCallback.releaseNetwork();
    }
  }

  public static class RequestFailedException extends Exception {

    private RequestFailedException(Throwable cause) {
      super(cause);
    }
  }

  @NonNull
  public static NetworkWrapper getNetwork(
      OmtpVvmCarrierConfigHelper config, PhoneAccountHandle handle, VoicemailStatus.Editor status)
      throws RequestFailedException {
    FutureNetworkRequestCallback callback =
        new FutureNetworkRequestCallback(config, handle, status);
    callback.requestNetwork();
    try {
      return callback.getFuture().get();
    } catch (InterruptedException | ExecutionException e) {
      callback.releaseNetwork();
      VvmLog.e(TAG, "can't get future network", e);
      throw new RequestFailedException(e);
    }
  }

  private static class FutureNetworkRequestCallback extends VvmNetworkRequestCallback {

    /**
     * {@link CompletableFuture#get()} will block until {@link CompletableFuture# complete(Object) }
     * has been called on the other thread.
     */
    private final CompletableFuture<NetworkWrapper> mFuture = new CompletableFuture<>();

    public FutureNetworkRequestCallback(
        OmtpVvmCarrierConfigHelper config,
        PhoneAccountHandle phoneAccount,
        VoicemailStatus.Editor status) {
      super(config, phoneAccount, status);
    }

    public Future<NetworkWrapper> getFuture() {
      return mFuture;
    }

    @Override
    public void onAvailable(Network network) {
      super.onAvailable(network);
      mFuture.complete(new NetworkWrapper(network, this));
    }

    @Override
    public void onFailed(String reason) {
      super.onFailed(reason);
      mFuture.complete(null);
    }
  }
}
