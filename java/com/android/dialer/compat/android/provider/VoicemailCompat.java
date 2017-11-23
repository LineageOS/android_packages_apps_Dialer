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
package com.android.dialer.compat.android.provider;

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
   * failed for an unspecified reason.
   */
  public static final int TRANSCRIPTION_FAILED = 2;

  /**
   * Value of {@link #TRANSCRIPTION_STATE} when the voicemail transcription has completed and the
   * result has been stored in the {@link #TRANSCRIPTION} column.
   */
  public static final int TRANSCRIPTION_AVAILABLE = 3;

  /**
   * Value of {@link #TRANSCRIPTION_STATE} when the voicemail transcription has been attempted and
   * failed because no speech was detected.
   *
   * <p>Internal dialer use only, not part of the public SDK.
   */
  public static final int TRANSCRIPTION_FAILED_NO_SPEECH_DETECTED = -1;

  /**
   * Value of {@link #TRANSCRIPTION_STATE} when the voicemail transcription has been attempted and
   * failed because the language was not supported.
   *
   * <p>Internal dialer use only, not part of the public SDK.
   */
  public static final int TRANSCRIPTION_FAILED_LANGUAGE_NOT_SUPPORTED = -2;

  /**
   * Value of {@link #TRANSCRIPTION_STATE} when the voicemail transcription has completed and the
   * result has been stored in the {@link #TRANSCRIPTION} column of the database, and the user has
   * provided a quality rating for the transcription.
   */
  public static final int TRANSCRIPTION_AVAILABLE_AND_RATED = -3;

  /**
   * Voicemail transcription quality rating value sent to the server indicating a good transcription
   */
  public static final int TRANSCRIPTION_QUALITY_RATING_GOOD = 1;

  /**
   * Voicemail transcription quality rating value sent to the server indicating a bad transcription
   */
  public static final int TRANSCRIPTION_QUALITY_RATING_BAD = 2;
}
