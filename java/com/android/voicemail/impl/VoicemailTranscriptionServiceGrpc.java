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

package com.google.internal.communications.voicemailtranscription.v1;

import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;

/**
 * <pre>
 * RPC service for transcribing voicemails.
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.0.3)",
    comments = "Source: voicemail_transcription.proto")
public class VoicemailTranscriptionServiceGrpc {

  private VoicemailTranscriptionServiceGrpc() {}

  public static final String SERVICE_NAME = "google.internal.communications.voicemailtranscription.v1.VoicemailTranscriptionService";

  // Static method descriptors that strictly reflect the proto.
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailRequest,
      com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailResponse> METHOD_TRANSCRIBE_VOICEMAIL =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "google.internal.communications.voicemailtranscription.v1.VoicemailTranscriptionService", "TranscribeVoicemail"),
          io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailRequest.getDefaultInstance()),
          io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailResponse.getDefaultInstance()));
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncRequest,
      com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncResponse> METHOD_TRANSCRIBE_VOICEMAIL_ASYNC =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "google.internal.communications.voicemailtranscription.v1.VoicemailTranscriptionService", "TranscribeVoicemailAsync"),
          io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncRequest.getDefaultInstance()),
          io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncResponse.getDefaultInstance()));
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<com.google.internal.communications.voicemailtranscription.v1.GetTranscriptRequest,
      com.google.internal.communications.voicemailtranscription.v1.GetTranscriptResponse> METHOD_GET_TRANSCRIPT =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "google.internal.communications.voicemailtranscription.v1.VoicemailTranscriptionService", "GetTranscript"),
          io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(com.google.internal.communications.voicemailtranscription.v1.GetTranscriptRequest.getDefaultInstance()),
          io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(com.google.internal.communications.voicemailtranscription.v1.GetTranscriptResponse.getDefaultInstance()));
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static final io.grpc.MethodDescriptor<com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackRequest,
      com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackResponse> METHOD_SEND_TRANSCRIPTION_FEEDBACK =
      io.grpc.MethodDescriptor.create(
          io.grpc.MethodDescriptor.MethodType.UNARY,
          generateFullMethodName(
              "google.internal.communications.voicemailtranscription.v1.VoicemailTranscriptionService", "SendTranscriptionFeedback"),
          io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackRequest.getDefaultInstance()),
          io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackResponse.getDefaultInstance()));

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static VoicemailTranscriptionServiceStub newStub(io.grpc.Channel channel) {
    return new VoicemailTranscriptionServiceStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static VoicemailTranscriptionServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new VoicemailTranscriptionServiceBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary and streaming output calls on the service
   */
  public static VoicemailTranscriptionServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new VoicemailTranscriptionServiceFutureStub(channel);
  }

  /**
   * <pre>
   * RPC service for transcribing voicemails.
   * </pre>
   */
  public static abstract class VoicemailTranscriptionServiceImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Returns a transcript of the given voicemail.
     * </pre>
     */
    public void transcribeVoicemail(com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailRequest request,
        io.grpc.stub.StreamObserver<com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailResponse> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_TRANSCRIBE_VOICEMAIL, responseObserver);
    }

    /**
     * <pre>
     * Schedules a transcription of the given voicemail. The transcript can be
     * retrieved using the returned ID.
     * </pre>
     */
    public void transcribeVoicemailAsync(com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncRequest request,
        io.grpc.stub.StreamObserver<com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncResponse> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_TRANSCRIBE_VOICEMAIL_ASYNC, responseObserver);
    }

    /**
     * <pre>
     * Returns the transcript corresponding to the given ID, which was returned
     * by TranscribeVoicemailAsync.
     * </pre>
     */
    public void getTranscript(com.google.internal.communications.voicemailtranscription.v1.GetTranscriptRequest request,
        io.grpc.stub.StreamObserver<com.google.internal.communications.voicemailtranscription.v1.GetTranscriptResponse> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_GET_TRANSCRIPT, responseObserver);
    }

    /**
     * <pre>
     * Uploads user's transcription feedback. Feedback will only be collected from
     * user's who have consented to donate their voicemails.
     * </pre>
     */
    public void sendTranscriptionFeedback(com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackRequest request,
        io.grpc.stub.StreamObserver<com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackResponse> responseObserver) {
      asyncUnimplementedUnaryCall(METHOD_SEND_TRANSCRIPTION_FEEDBACK, responseObserver);
    }

    @java.lang.Override public io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            METHOD_TRANSCRIBE_VOICEMAIL,
            asyncUnaryCall(
              new MethodHandlers<
                com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailRequest,
                com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailResponse>(
                  this, METHODID_TRANSCRIBE_VOICEMAIL)))
          .addMethod(
            METHOD_TRANSCRIBE_VOICEMAIL_ASYNC,
            asyncUnaryCall(
              new MethodHandlers<
                com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncRequest,
                com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncResponse>(
                  this, METHODID_TRANSCRIBE_VOICEMAIL_ASYNC)))
          .addMethod(
            METHOD_GET_TRANSCRIPT,
            asyncUnaryCall(
              new MethodHandlers<
                com.google.internal.communications.voicemailtranscription.v1.GetTranscriptRequest,
                com.google.internal.communications.voicemailtranscription.v1.GetTranscriptResponse>(
                  this, METHODID_GET_TRANSCRIPT)))
          .addMethod(
            METHOD_SEND_TRANSCRIPTION_FEEDBACK,
            asyncUnaryCall(
              new MethodHandlers<
                com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackRequest,
                com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackResponse>(
                  this, METHODID_SEND_TRANSCRIPTION_FEEDBACK)))
          .build();
    }
  }

  /**
   * <pre>
   * RPC service for transcribing voicemails.
   * </pre>
   */
  public static final class VoicemailTranscriptionServiceStub extends io.grpc.stub.AbstractStub<VoicemailTranscriptionServiceStub> {
    private VoicemailTranscriptionServiceStub(io.grpc.Channel channel) {
      super(channel);
    }

    private VoicemailTranscriptionServiceStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected VoicemailTranscriptionServiceStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new VoicemailTranscriptionServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Returns a transcript of the given voicemail.
     * </pre>
     */
    public void transcribeVoicemail(com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailRequest request,
        io.grpc.stub.StreamObserver<com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_TRANSCRIBE_VOICEMAIL, getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Schedules a transcription of the given voicemail. The transcript can be
     * retrieved using the returned ID.
     * </pre>
     */
    public void transcribeVoicemailAsync(com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncRequest request,
        io.grpc.stub.StreamObserver<com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_TRANSCRIBE_VOICEMAIL_ASYNC, getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Returns the transcript corresponding to the given ID, which was returned
     * by TranscribeVoicemailAsync.
     * </pre>
     */
    public void getTranscript(com.google.internal.communications.voicemailtranscription.v1.GetTranscriptRequest request,
        io.grpc.stub.StreamObserver<com.google.internal.communications.voicemailtranscription.v1.GetTranscriptResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_GET_TRANSCRIPT, getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Uploads user's transcription feedback. Feedback will only be collected from
     * user's who have consented to donate their voicemails.
     * </pre>
     */
    public void sendTranscriptionFeedback(com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackRequest request,
        io.grpc.stub.StreamObserver<com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(METHOD_SEND_TRANSCRIPTION_FEEDBACK, getCallOptions()), request, responseObserver);
    }
  }

  /**
   * <pre>
   * RPC service for transcribing voicemails.
   * </pre>
   */
  public static final class VoicemailTranscriptionServiceBlockingStub extends io.grpc.stub.AbstractStub<VoicemailTranscriptionServiceBlockingStub> {
    private VoicemailTranscriptionServiceBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private VoicemailTranscriptionServiceBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected VoicemailTranscriptionServiceBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new VoicemailTranscriptionServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Returns a transcript of the given voicemail.
     * </pre>
     */
    public com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailResponse transcribeVoicemail(com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailRequest request) {
      return blockingUnaryCall(
          getChannel(), METHOD_TRANSCRIBE_VOICEMAIL, getCallOptions(), request);
    }

    /**
     * <pre>
     * Schedules a transcription of the given voicemail. The transcript can be
     * retrieved using the returned ID.
     * </pre>
     */
    public com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncResponse transcribeVoicemailAsync(com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncRequest request) {
      return blockingUnaryCall(
          getChannel(), METHOD_TRANSCRIBE_VOICEMAIL_ASYNC, getCallOptions(), request);
    }

    /**
     * <pre>
     * Returns the transcript corresponding to the given ID, which was returned
     * by TranscribeVoicemailAsync.
     * </pre>
     */
    public com.google.internal.communications.voicemailtranscription.v1.GetTranscriptResponse getTranscript(com.google.internal.communications.voicemailtranscription.v1.GetTranscriptRequest request) {
      return blockingUnaryCall(
          getChannel(), METHOD_GET_TRANSCRIPT, getCallOptions(), request);
    }

    /**
     * <pre>
     * Uploads user's transcription feedback. Feedback will only be collected from
     * user's who have consented to donate their voicemails.
     * </pre>
     */
    public com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackResponse sendTranscriptionFeedback(com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackRequest request) {
      return blockingUnaryCall(
          getChannel(), METHOD_SEND_TRANSCRIPTION_FEEDBACK, getCallOptions(), request);
    }
  }

  /**
   * <pre>
   * RPC service for transcribing voicemails.
   * </pre>
   */
  public static final class VoicemailTranscriptionServiceFutureStub extends io.grpc.stub.AbstractStub<VoicemailTranscriptionServiceFutureStub> {
    private VoicemailTranscriptionServiceFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private VoicemailTranscriptionServiceFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected VoicemailTranscriptionServiceFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new VoicemailTranscriptionServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Returns a transcript of the given voicemail.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailResponse> transcribeVoicemail(
        com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailRequest request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_TRANSCRIBE_VOICEMAIL, getCallOptions()), request);
    }

    /**
     * <pre>
     * Schedules a transcription of the given voicemail. The transcript can be
     * retrieved using the returned ID.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncResponse> transcribeVoicemailAsync(
        com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncRequest request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_TRANSCRIBE_VOICEMAIL_ASYNC, getCallOptions()), request);
    }

    /**
     * <pre>
     * Returns the transcript corresponding to the given ID, which was returned
     * by TranscribeVoicemailAsync.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.internal.communications.voicemailtranscription.v1.GetTranscriptResponse> getTranscript(
        com.google.internal.communications.voicemailtranscription.v1.GetTranscriptRequest request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_GET_TRANSCRIPT, getCallOptions()), request);
    }

    /**
     * <pre>
     * Uploads user's transcription feedback. Feedback will only be collected from
     * user's who have consented to donate their voicemails.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackResponse> sendTranscriptionFeedback(
        com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackRequest request) {
      return futureUnaryCall(
          getChannel().newCall(METHOD_SEND_TRANSCRIPTION_FEEDBACK, getCallOptions()), request);
    }
  }

  private static final int METHODID_TRANSCRIBE_VOICEMAIL = 0;
  private static final int METHODID_TRANSCRIBE_VOICEMAIL_ASYNC = 1;
  private static final int METHODID_GET_TRANSCRIPT = 2;
  private static final int METHODID_SEND_TRANSCRIPTION_FEEDBACK = 3;

  private static class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final VoicemailTranscriptionServiceImplBase serviceImpl;
    private final int methodId;

    public MethodHandlers(VoicemailTranscriptionServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_TRANSCRIBE_VOICEMAIL:
          serviceImpl.transcribeVoicemail((com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailRequest) request,
              (io.grpc.stub.StreamObserver<com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailResponse>) responseObserver);
          break;
        case METHODID_TRANSCRIBE_VOICEMAIL_ASYNC:
          serviceImpl.transcribeVoicemailAsync((com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncRequest) request,
              (io.grpc.stub.StreamObserver<com.google.internal.communications.voicemailtranscription.v1.TranscribeVoicemailAsyncResponse>) responseObserver);
          break;
        case METHODID_GET_TRANSCRIPT:
          serviceImpl.getTranscript((com.google.internal.communications.voicemailtranscription.v1.GetTranscriptRequest) request,
              (io.grpc.stub.StreamObserver<com.google.internal.communications.voicemailtranscription.v1.GetTranscriptResponse>) responseObserver);
          break;
        case METHODID_SEND_TRANSCRIPTION_FEEDBACK:
          serviceImpl.sendTranscriptionFeedback((com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackRequest) request,
              (io.grpc.stub.StreamObserver<com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    return new io.grpc.ServiceDescriptor(SERVICE_NAME,
        METHOD_TRANSCRIBE_VOICEMAIL,
        METHOD_TRANSCRIBE_VOICEMAIL_ASYNC,
        METHOD_GET_TRANSCRIPT,
        METHOD_SEND_TRANSCRIPTION_FEEDBACK);
  }

}
