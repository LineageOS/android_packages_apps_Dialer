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
package com.android.voicemail.impl.transcribe;

/**
 * Provide access to new API constants before they're publicly available
 *
 * <p>Copied from android.provider.VoicemailContract.Voicemails. These should become public in O-MR1
 * and these constants can be removed then.
 */
public class VoicemailCompat {

  /**
   * The state of the voicemail transcription.
   *
   * <p>Possible values: {@link #TRANSCRIPTION_NOT_STARTED}, {@link #TRANSCRIPTION_IN_PROGRESS},
   * {@link #TRANSCRIPTION_FAILED}, {@link #TRANSCRIPTION_AVAILABLE}.
   *
   * <p>Type: INTEGER
   */
  public static final String TRANSCRIPTION_STATE = "transcription_state";

  /**
   * Value of {@link #TRANSCRIPTION_STATE} when the voicemail transcription has not yet been
   * attempted.
   */
  public static final int TRANSCRIPTION_NOT_STARTED = 0;

  /**
   * Value of {@link #TRANSCRIPTION_STATE} when the voicemail transcription has begun but is not yet
   * complete.
   */
  public static final int TRANSCRIPTION_IN_PROGRESS = 1;

  /**
   * Value of {@link #TRANSCRIPTION_STATE} when the voicemail transcription has been attempted and
   * failed.
   */
  public static final int TRANSCRIPTION_FAILED = 2;

  /**
   * Value of {@link #TRANSCRIPTION_STATE} when the voicemail transcription has completed and the
   * result has been stored in the {@link #TRANSCRIPTION} column.
   */
  public static final int TRANSCRIPTION_AVAILABLE = 3;
}
