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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.storage.StorageComponent;
import com.android.incallui.answer.impl.hint.PawSecretCodeListener.PawType;

/** Decrypt the event payload to be shown if in a specific time range and the key is received. */
public final class PawImageLoaderImpl implements PawImageLoader {

  @Override
  @Nullable
  public Drawable loadPayload(@NonNull Context context) {
    Assert.isNotNull(context);

    SharedPreferences preferences = StorageComponent.get(context).unencryptedSharedPrefs();
    if (!preferences.getBoolean(PawSecretCodeListener.PAW_ENABLED_WITH_SECRET_CODE_KEY, false)) {
      return null;
    }
    @PawType
    int pawType =
        preferences.getInt(PawSecretCodeListener.PAW_TYPE, PawSecretCodeListener.PAW_TYPE_INVALID);

    if (pawType == PawSecretCodeListener.PAW_TYPE_INVALID) {
      LogUtil.i("PawImageLoaderImpl.loadPayload", "paw type not found, rerolling");
      PawSecretCodeListener.selectPawType(preferences);
      pawType =
          preferences.getInt(
              PawSecretCodeListener.PAW_TYPE, PawSecretCodeListener.PAW_TYPE_INVALID);
    }

    switch (pawType) {
      case PawSecretCodeListener.PAW_TYPE_CAT:
        return context.getDrawable(R.drawable.cat_paw);
      case PawSecretCodeListener.PAW_TYPE_DOG:
        return context.getDrawable(R.drawable.dog_paw);
      default:
        throw Assert.createAssertionFailException("unknown paw type " + pawType);
    }
  }
}
