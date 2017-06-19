/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.voicemail.impl.transcribe.grpc;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.voicemail.impl.transcribe.TranscriptionConfigProvider;
import com.google.internal.communications.voicemailtranscription.v1.VoicemailTranscriptionServiceGrpc;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.okhttp.OkHttpChannelBuilder;
import java.security.MessageDigest;

/**
 * Factory for creating grpc clients that talk to the transcription server. This allows all clients
 * to share the same channel, which is relatively expensive to create.
 */
public class TranscriptionClientFactory {
  private static final String DIGEST_ALGORITHM_SHA1 = "SHA1";
  private static final char[] HEX_UPPERCASE = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
  };

  private final TranscriptionConfigProvider configProvider;
  private final ManagedChannel originalChannel;
  private final String packageName;
  private final String cert;

  public TranscriptionClientFactory(Context context, TranscriptionConfigProvider configProvider) {
    this(context, configProvider, getManagedChannel(configProvider));
  }

  public TranscriptionClientFactory(
      Context context, TranscriptionConfigProvider configProvider, ManagedChannel managedChannel) {
    this.configProvider = configProvider;
    this.packageName = context.getPackageName();
    this.cert = getCertificateFingerprint(context);
    originalChannel = managedChannel;
  }

  public TranscriptionClient getClient() {
    LogUtil.enterBlock("TranscriptionClientFactory.getClient");
    Assert.checkState(!originalChannel.isShutdown());
    Channel channel =
        ClientInterceptors.intercept(
            originalChannel,
            new Interceptor(
                packageName, cert, configProvider.getApiKey(), configProvider.getAuthToken()));
    return new TranscriptionClient(VoicemailTranscriptionServiceGrpc.newBlockingStub(channel));
  }

  public void shutdown() {
    LogUtil.enterBlock("TranscriptionClientFactory.shutdown");
    originalChannel.shutdown();
  }

  private static ManagedChannel getManagedChannel(TranscriptionConfigProvider configProvider) {
    ManagedChannelBuilder<OkHttpChannelBuilder> builder =
        OkHttpChannelBuilder.forTarget(configProvider.getServerAddress());
    // Only use plaintext for debugging
    if (configProvider.shouldUsePlaintext()) {
      // Just passing 'false' doesnt have the same effect as not setting this field
      builder.usePlaintext(true);
    }
    return builder.build();
  }

  private static String getCertificateFingerprint(Context context) {
    try {
      PackageInfo packageInfo =
          context
              .getPackageManager()
              .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
      if (packageInfo != null
          && packageInfo.signatures != null
          && packageInfo.signatures.length > 0) {
        MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM_SHA1);
        if (messageDigest == null) {
          LogUtil.w(
              "TranscriptionClientFactory.getCertificateFingerprint", "error getting digest.");
          return null;
        }
        byte[] bytes = messageDigest.digest(packageInfo.signatures[0].toByteArray());
        if (bytes == null) {
          LogUtil.w(
              "TranscriptionClientFactory.getCertificateFingerprint", "empty message digest.");
          return null;
        }

        int length = bytes.length;
        StringBuilder out = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
          out.append(HEX_UPPERCASE[(bytes[i] & 0xf0) >>> 4]);
          out.append(HEX_UPPERCASE[bytes[i] & 0x0f]);
        }
        return out.toString();
      } else {
        LogUtil.w(
            "TranscriptionClientFactory.getCertificateFingerprint",
            "failed to get package signature.");
      }
    } catch (Exception e) {
      LogUtil.e(
          "TranscriptionClientFactory.getCertificateFingerprint",
          "error getting certificate fingerprint.",
          e);
    }

    return null;
  }

  private static final class Interceptor implements ClientInterceptor {
    private final String packageName;
    private final String cert;
    private final String apiKey;
    private final String authToken;

    private static final Metadata.Key<String> API_KEY_HEADER =
        Metadata.Key.of("X-Goog-Api-Key", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> ANDROID_PACKAGE_HEADER =
        Metadata.Key.of("X-Android-Package", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> ANDROID_CERT_HEADER =
        Metadata.Key.of("X-Android-Cert", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    public Interceptor(String packageName, String cert, String apiKey, String authToken) {
      this.packageName = packageName;
      this.cert = cert;
      this.apiKey = apiKey;
      this.authToken = authToken;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
      LogUtil.enterBlock(
          "TranscriptionClientFactory.interceptCall, intercepted " + method.getFullMethodName());
      ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);

      call =
          new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(call) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
              if (!TextUtils.isEmpty(packageName)) {
                LogUtil.i(
                    "TranscriptionClientFactory.interceptCall",
                    "attaching package name: " + packageName);
                headers.put(ANDROID_PACKAGE_HEADER, packageName);
              }
              if (!TextUtils.isEmpty(cert)) {
                LogUtil.i("TranscriptionClientFactory.interceptCall", "attaching android cert");
                headers.put(ANDROID_CERT_HEADER, cert);
              }
              if (!TextUtils.isEmpty(apiKey)) {
                LogUtil.i("TranscriptionClientFactory.interceptCall", "attaching API Key");
                headers.put(API_KEY_HEADER, apiKey);
              }
              if (!TextUtils.isEmpty(authToken)) {
                LogUtil.i("TranscriptionClientFactory.interceptCall", "attaching auth token");
                headers.put(AUTHORIZATION_HEADER, "Bearer " + authToken);
              }
              super.start(responseListener, headers);
            }
          };
      return call;
    }
  }
}
