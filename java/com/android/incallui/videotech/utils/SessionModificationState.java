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

package com.android.incallui.videotech.utils;

import android.support.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Defines different states of session modify requests, which are used to upgrade to video, or
 * downgrade to audio.
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef({
  SessionModificationState.NO_REQUEST,
  SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE,
  SessionModificationState.REQUEST_FAILED,
  SessionModificationState.RECEIVED_UPGRADE_TO_VIDEO_REQUEST,
  SessionModificationState.UPGRADE_TO_VIDEO_REQUEST_TIMED_OUT,
  SessionModificationState.UPGRADE_TO_VIDEO_REQUEST_FAILED,
  SessionModificationState.REQUEST_REJECTED,
  SessionModificationState.WAITING_FOR_RESPONSE
})
public @interface SessionModificationState {
  int NO_REQUEST = 0;
  int WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE = 1;
  int REQUEST_FAILED = 2;
  int RECEIVED_UPGRADE_TO_VIDEO_REQUEST = 3;
  int UPGRADE_TO_VIDEO_REQUEST_TIMED_OUT = 4;
  int UPGRADE_TO_VIDEO_REQUEST_FAILED = 5;
  int REQUEST_REJECTED = 6;
  int WAITING_FOR_RESPONSE = 7;
}
