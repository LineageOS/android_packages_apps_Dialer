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

import javax.annotation.concurrent.ThreadSafe;

/**
 * {@link PausableExecutor} for use in tests. It is intended to be used between one test thread
 * and one prod thread.
 */
@ThreadSafe
public final class SingleProdThreadExecutor implements PausableExecutor {

    private int mMilestonesReached;
    private int mMilestonesAcked;

    @Override
    public synchronized void milestone() {
        ++mMilestonesReached;
        notify();
        while (mMilestonesReached > mMilestonesAcked) {
            try {
                wait();
            } catch (InterruptedException e) {}
        }
    }

    @Override
    public synchronized void ackMilestoneForTesting() {
        ++mMilestonesAcked;
        notify();
    }

    @Override
    public synchronized void awaitMilestoneForTesting() throws InterruptedException {
        while (mMilestonesReached <= mMilestonesAcked) {
            wait();
        }
    }

    @Override
    public synchronized void execute(Runnable command) {
        Executors.newSingleThreadExecutor().execute(command);
    }
}
