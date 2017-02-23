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

package com.android.incallui.async;

import java.util.concurrent.Executors;

/** {@link PausableExecutor} intended for use in production environments. */
public class PausableExecutorImpl implements PausableExecutor {

  @Override
  public void milestone() {}

  @Override
  public void ackMilestoneForTesting() {}

  @Override
  public void ackAllMilestonesForTesting() {}

  @Override
  public void awaitMilestoneForTesting() {}

  @Override
  public void execute(Runnable command) {
    Executors.newSingleThreadExecutor().execute(command);
  }
}
