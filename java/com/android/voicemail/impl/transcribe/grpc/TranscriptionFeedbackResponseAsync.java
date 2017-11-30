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

import android.support.annotation.VisibleForTesting;
import com.android.dialer.common.Assert;
import com.google.internal.communications.voicemailtranscription.v1.SendTranscriptionFeedbackResponse;
import io.grpc.Status;

/** Container for response and status objects for an asynchronous transcription feedback request */
public class TranscriptionFeedbackResponseAsync extends TranscriptionResponse {

  @VisibleForTesting
  public TranscriptionFeedbackResponseAsync(SendTranscriptionFeedbackResponse response) {
    Assert.checkArgument(response != null);
  }

  @VisibleForTesting
  public TranscriptionFeedbackResponseAsync(Status status) {
    super(status);
  }
}
