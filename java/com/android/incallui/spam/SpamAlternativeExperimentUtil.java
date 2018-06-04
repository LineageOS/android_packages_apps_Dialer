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

package com.android.incallui.spam;

import android.content.Context;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderComponent;

/** Returns resource id based on experiment number. */
public final class SpamAlternativeExperimentUtil {

  /**
   * Returns the resource id using a resource name for an experiment where we want to use
   * alternative words for the keyword spam.
   */
  public static int getResourceIdByName(String resourceName, Context context) {
    long experiment =
        ConfigProviderComponent.get(context)
            .getConfigProvider()
            .getLong("experiment_for_alternative_spam_word", 230150);
    LogUtil.i(
        "SpamAlternativeExperimentUtil.getResourceIdByName", "using experiment %d", experiment);
    String modifiedResourceName = resourceName;
    if (experiment != 230150) {
      modifiedResourceName = resourceName + "_" + experiment;
    }
    int resourceId =
        context
            .getResources()
            .getIdentifier(modifiedResourceName, "string", context.getPackageName());
    if (resourceId == 0) {
      LogUtil.i(
          "SpamAlternativeExperimentUtil.getResourceIdByName",
          "not found experiment %d",
          experiment);
      return context.getResources().getIdentifier(resourceName, "string", context.getPackageName());
    }
    return resourceId;
  }
}
