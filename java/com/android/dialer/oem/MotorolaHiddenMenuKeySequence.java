/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * This file is derived in part from code issued under the following license.
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
 *
 */
package com.android.dialer.oem;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.support.annotation.VisibleForTesting;
import com.android.dialer.common.LogUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Util class to handle special char sequence and launch corresponding intent based the sequence.
 */
public class MotorolaHiddenMenuKeySequence {
  private static final String EXTRA_HIDDEN_MENU_CODE = "HiddenMenuCode";

  private static MotorolaHiddenMenuKeySequence instance = null;

  @VisibleForTesting final List<String> hiddenKeySequences = new ArrayList<>();
  @VisibleForTesting final List<String> hiddenKeySequenceIntents = new ArrayList<>();
  @VisibleForTesting final List<String> hiddenKeyPatterns = new ArrayList<>();
  @VisibleForTesting final List<String> hiddenKeyPatternIntents = new ArrayList<>();
  @VisibleForTesting boolean featureHiddenMenuEnabled = false;

  /**
   * Handle input char sequence.
   *
   * @param context context
   * @param input input sequence
   * @return true if the input matches any pattern
   */
  static boolean handleCharSequence(Context context, String input) {
    if (!getInstance(context).featureHiddenMenuEnabled) {
      return false;
    }
    return handleKeySequence(context, input) || handleKeyPattern(context, input);
  }

  /**
   * Public interface to return the Singleton instance
   *
   * @param context the Context
   * @return the MotorolaHiddenMenuKeySequence singleton instance
   */
  private static synchronized MotorolaHiddenMenuKeySequence getInstance(Context context) {
    if (null == instance) {
      instance = new MotorolaHiddenMenuKeySequence(context, new SystemPropertiesAccessor());
    }
    return instance;
  }

  @VisibleForTesting
  static void setInstanceForTest(MotorolaHiddenMenuKeySequence instance) {
    MotorolaHiddenMenuKeySequence.instance = instance;
  }

  @VisibleForTesting
  MotorolaHiddenMenuKeySequence(
      Context context, SystemPropertiesAccessor systemPropertiesAccessor) {
    if (MotorolaUtils.isSupportingHiddenMenu(context)) {
      Collections.addAll(
          hiddenKeySequences,
          context.getResources().getStringArray(R.array.motorola_hidden_menu_key_sequence));
      Collections.addAll(
          hiddenKeySequenceIntents,
          context.getResources().getStringArray(R.array.motorola_hidden_menu_key_sequence_intents));
      Collections.addAll(
          hiddenKeyPatterns,
          context.getResources().getStringArray(R.array.motorola_hidden_menu_key_pattern));
      Collections.addAll(
          hiddenKeyPatternIntents,
          context.getResources().getStringArray(R.array.motorola_hidden_menu_key_pattern_intents));
      featureHiddenMenuEnabled = true;
    }

    if ("tracfone".equals(systemPropertiesAccessor.get("ro.carrier"))) {
      addHiddenKeySequence("#83865625#", "com.motorola.extensions.TFUnlock");
      addHiddenKeySequence("#83782887#", "com.motorola.extensions.TFStatus");
      featureHiddenMenuEnabled = true;
    }

    if (hiddenKeySequences.size() != hiddenKeySequenceIntents.size()
        || hiddenKeyPatterns.size() != hiddenKeyPatternIntents.size()
        || (hiddenKeySequences.isEmpty() && hiddenKeyPatterns.isEmpty())) {
      LogUtil.e(
          "MotorolaHiddenMenuKeySequence",
          "the key sequence array is not matching, turn off feature."
              + "key sequence: %d != %d, key pattern %d != %d",
          hiddenKeySequences.size(),
          hiddenKeySequenceIntents.size(),
          hiddenKeyPatterns.size(),
          hiddenKeyPatternIntents.size());
      featureHiddenMenuEnabled = false;
    }
  }

  private void addHiddenKeySequence(String keySequence, String intentAction) {
    hiddenKeySequences.add(keySequence);
    hiddenKeySequenceIntents.add(intentAction);
  }

  private static boolean handleKeyPattern(Context context, String input) {
    MotorolaHiddenMenuKeySequence instance = getInstance(context);

    int len = input.length();
    if (len <= 3
        || instance.hiddenKeyPatterns == null
        || instance.hiddenKeyPatternIntents == null) {
      return false;
    }

    for (int i = 0; i < instance.hiddenKeyPatterns.size(); i++) {
      if (Pattern.matches(instance.hiddenKeyPatterns.get(i), input)) {
        return sendIntent(context, input, instance.hiddenKeyPatternIntents.get(i));
      }
    }
    return false;
  }

  private static boolean handleKeySequence(Context context, String input) {
    MotorolaHiddenMenuKeySequence instance = getInstance(context);

    int len = input.length();
    if (len <= 3
        || instance.hiddenKeySequences == null
        || instance.hiddenKeySequenceIntents == null) {
      return false;
    }

    for (int i = 0; i < instance.hiddenKeySequences.size(); i++) {
      if (instance.hiddenKeySequences.get(i).equals(input)) {
        return sendIntent(context, input, instance.hiddenKeySequenceIntents.get(i));
      }
    }
    return false;
  }

  private static boolean sendIntent(
      final Context context, final String input, final String action) {
    LogUtil.d("MotorolaHiddenMenuKeySequence.sendIntent", "input: %s", input);
    try {
      Intent intent = new Intent(action);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.putExtra(EXTRA_HIDDEN_MENU_CODE, input);

      ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(intent, 0);

      if (resolveInfo != null
          && resolveInfo.activityInfo != null
          && resolveInfo.activityInfo.enabled) {
        context.startActivity(intent);
        return true;
      } else {
        LogUtil.w("MotorolaHiddenMenuKeySequence.sendIntent", "not able to resolve the intent");
      }
    } catch (ActivityNotFoundException e) {
      LogUtil.e(
          "MotorolaHiddenMenuKeySequence.sendIntent", "handleHiddenMenu Key Pattern Exception", e);
    }
    return false;
  }
}
