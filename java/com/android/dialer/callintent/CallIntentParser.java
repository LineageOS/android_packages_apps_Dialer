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

package com.android.dialer.callintent;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.common.LogUtil;
import com.android.dialer.protos.ProtoParsers;

/** Parses data for a call extra to get any dialer specific app data. */
public class CallIntentParser {


  @Nullable
  public static CallSpecificAppData getCallSpecificAppData(@Nullable Bundle extras) {
    if (extras == null) {
      return null;
    }

    if (!extras.containsKey(Constants.EXTRA_CALL_SPECIFIC_APP_DATA)) {
      return null;
    }

    if (extras.getByteArray(Constants.EXTRA_CALL_SPECIFIC_APP_DATA) == null) {
      LogUtil.i(
          "CallIntentParser.getCallSpecificAppData",
          "unexpected null byte array for call specific app data proto");
      return null;
    }

    return ProtoParsers.getTrusted(
        extras, Constants.EXTRA_CALL_SPECIFIC_APP_DATA, CallSpecificAppData.getDefaultInstance());
  }

  public static void putCallSpecificAppData(
      @NonNull Bundle extras, @NonNull CallSpecificAppData callSpecificAppData) {
    ProtoParsers.put(extras, Constants.EXTRA_CALL_SPECIFIC_APP_DATA, callSpecificAppData);
  }

  private CallIntentParser() {}
}
