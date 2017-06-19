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
 * limitations under the License.
 */

package com.android.dialer.p13n.inference;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.p13n.inference.protocol.P13nRanker;
import com.android.dialer.p13n.inference.protocol.P13nRankerFactory;
import java.util.List;

/** Single entry point for all personalized ranking. */
public final class P13nRanking {

  private static P13nRanker ranker;

  private P13nRanking() {}

  @MainThread
  @NonNull
  public static P13nRanker get(@NonNull Context context) {
    Assert.isNotNull(context);
    Assert.isMainThread();

    if (ranker != null) {
      return ranker;
    }

    if (!ConfigProviderBindings.get(context).getBoolean("p13n_ranker_should_enable", false)) {
      setToIdentityRanker();
      return ranker;
    }

    Context application = context.getApplicationContext();
    if (application instanceof P13nRankerFactory) {
      ranker = ((P13nRankerFactory) application).newP13nRanker();
    }

    if (ranker == null) {
      setToIdentityRanker();
    }
    return ranker;
  }

  private static void setToIdentityRanker() {
    ranker =
        new P13nRanker() {
          @Override
          public void refresh(@Nullable P13nRefreshCompleteListener listener) {}

          @Override
          public List<String> rankList(List<String> phoneNumbers) {
            return phoneNumbers;
          }

          @NonNull
          @Override
          public Cursor rankCursor(@NonNull Cursor phoneQueryResults, int queryLength) {
            return phoneQueryResults;
          }

          @Override
          public boolean shouldShowEmptyListForNullQuery() {
            return true;
          }
        };
  }

  public static void setForTesting(@NonNull P13nRanker ranker) {
    P13nRanking.ranker = ranker;
  }
}
