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

package com.android.incallui.answer.impl.hint;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.android.dialer.util.DialerUtils;

/** Decrypt the event payload to be shown if in a specific time range and the key is received. */
@TargetApi(VERSION_CODES.M)
public final class PawImageLoaderImpl implements PawImageLoader {

  @Override
  @Nullable
  public Drawable loadPayload(@NonNull Context context) {
    Assert.isNotNull(context);

    SharedPreferences preferences =
        DialerUtils.getDefaultSharedPreferenceForDeviceProtectedStorageContext(context);
    if (!preferences.getBoolean(PawSecretCodeListener.PAW_ENABLED_WITH_SECRET_CODE_KEY, false)) {
      return null;
    }
    int drawableId = preferences.getInt(PawSecretCodeListener.PAW_DRAWABLE_ID_KEY, 0);
    if (drawableId == 0) {
      return null;
    }
    return context.getDrawable(drawableId);
  }
}
