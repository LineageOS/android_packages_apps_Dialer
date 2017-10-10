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

package com.android.incallui.incalluilock;

/**
 * Prevents the {@link com.android.incallui.InCallActivity} from auto-finishing where there are no
 * calls left. Acquired through {@link
 * com.android.incallui.InCallPresenter#acquireInCallUiLock(String)}. Example: when a dialog is
 * still being displayed to the user the InCallActivity should not disappear abruptly when the call
 * ends, this lock should be held to keep the activity alive until it is dismissed.
 */
public interface InCallUiLock {

  void release();
}
