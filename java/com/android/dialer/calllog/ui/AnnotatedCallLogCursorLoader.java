/*
 * Copyright (C) 2018 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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
 * limitations under the License.
 */

package com.android.dialer.calllog.ui;

import android.content.Context;
import android.provider.CallLog.Calls;

import androidx.loader.content.CursorLoader;

import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;

/** Cursor loader for {@link AnnotatedCallLog}. */
final class AnnotatedCallLogCursorLoader extends CursorLoader {

  AnnotatedCallLogCursorLoader(Context context) {
    super(
        context,
        AnnotatedCallLog.CONTENT_URI,
        /* projection = */ null,
        /* selection = */ AnnotatedCallLog.CALL_TYPE + " != ?",
        /* selectionArgs = */ new String[] {Integer.toString(Calls.VOICEMAIL_TYPE)},
        /* sortOrder = */ AnnotatedCallLog.TIMESTAMP + " DESC");
  }
}
