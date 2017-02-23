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
import com.android.dialer.callintent.nano.CallSpecificAppData;
import com.android.dialer.common.Assert;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import com.google.protobuf.nano.MessageNano;

/** Parses data for a call extra to get any dialer specific app data. */
public class CallIntentParser {
  @Nullable
  public static CallSpecificAppData getCallSpecificAppData(@Nullable Bundle extras) {
    if (extras == null) {
      return null;
    }

    byte[] flatArray = extras.getByteArray(Constants.EXTRA_CALL_SPECIFIC_APP_DATA);
    if (flatArray == null) {
      return null;
    }
    try {
      return CallSpecificAppData.parseFrom(flatArray);
    } catch (InvalidProtocolBufferNanoException e) {
      Assert.fail("unexpected exception: " + e);
      return null;
    }
  }

  public static void putCallSpecificAppData(
      @NonNull Bundle extras, @NonNull CallSpecificAppData callSpecificAppData) {
    extras.putByteArray(
        Constants.EXTRA_CALL_SPECIFIC_APP_DATA, MessageNano.toByteArray(callSpecificAppData));
  }

  private CallIntentParser() {}
}
