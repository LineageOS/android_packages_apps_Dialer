/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.common.concurrent;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.Atomics;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Static utility methods related to futures. */
public class DialerFutures {

  /**
   * Returns a future that will complete with the same value as the first matching the supplied
   * predicate, cancelling all inputs upon completion. If none match, {@code defaultValue} is
   * returned.
   *
   * <p>If an input fails before a match is found, the returned future also fails.
   *
   * <p>Cancellation of the output future will cause cancellation of all input futures.
   *
   * @throws IllegalArgumentException if {@code futures} is empty.
   */
  public static <T> ListenableFuture<T> firstMatching(
      Iterable<? extends ListenableFuture<? extends T>> futures,
      Predicate<T> predicate,
      T defaultValue) {
    return firstMatchingImpl(futures, predicate, defaultValue);
  }

  private static <T> ListenableFuture<T> firstMatchingImpl(
      Iterable<? extends ListenableFuture<? extends T>> futures,
      Predicate<T> predicate,
      T defaultValue) {
    AggregateFuture<T> output = new AnyOfFuture<>(futures);
    final AtomicReference<AggregateFuture<T>> ref = Atomics.newReference(output);
    final AtomicInteger pending = new AtomicInteger(output.futures.size());
    for (final ListenableFuture<? extends T> future : output.futures) {
      future.addListener(
          new Runnable() {
            @Override
            public void run() {
              // Call get() and then set() instead of getAndSet() because a volatile read/write is
              // cheaper than a CAS and atomicity is guaranteed by setFuture.
              AggregateFuture<T> output = ref.get();
              if (output != null) {
                T value = null;
                try {
                  value = Futures.getDone(future);
                } catch (ExecutionException e) {
                  ref.set(null); // unpin
                  output.setException(e);
                  return;
                }
                if (!predicate.apply(value)) {
                  if (pending.decrementAndGet() == 0) {
                    // we are the last future (and every other future hasn't matched or failed).
                    output.set(defaultValue);
                    // no point in clearing the ref, every other listener has already run
                  }
                } else {
                  ref.set(null); // unpin
                  output.set(value);
                }
              }
            }
          },
          MoreExecutors.directExecutor());
    }
    return output;
  }

  private static class AggregateFuture<T> extends AbstractFuture<T> {
    ImmutableList<ListenableFuture<? extends T>> futures;

    AggregateFuture(Iterable<? extends ListenableFuture<? extends T>> futures) {
      ImmutableList<ListenableFuture<? extends T>> futuresCopy = ImmutableList.copyOf(futures);
      if (futuresCopy.isEmpty()) {
        throw new IllegalArgumentException("Expected at least one future, got 0.");
      }
      this.futures = futuresCopy;
    }

    // increase visibility
    @Override
    protected boolean set(T t) {
      return super.set(t);
    }

    @Override
    protected boolean setException(Throwable throwable) {
      return super.setException(throwable);
    }

    @Override
    protected boolean setFuture(ListenableFuture<? extends T> t) {
      return super.setFuture(t);
    }
  }

  // Propagates cancellation to all inputs cancels all inputs upon completion
  private static final class AnyOfFuture<T> extends AggregateFuture<T> {
    AnyOfFuture(Iterable<? extends ListenableFuture<? extends T>> futures) {
      super(futures);
    }

    @SuppressWarnings("ShortCircuitBoolean")
    @Override
    protected void afterDone() {
      ImmutableList<ListenableFuture<? extends T>> localFutures = futures;
      futures = null; // unpin
      // even though afterDone is only called once, it is possible that the 'futures' field is null
      // because it isn't final and thus the write might not be visible if the future instance was
      // unsafely published.  See the comment at the top of Futures.java on memory visibility.
      if (localFutures != null) {
        boolean interrupt = !isCancelled() | wasInterrupted();
        for (ListenableFuture<? extends T> future : localFutures) {
          future.cancel(interrupt);
        }
      }
    }
  }
}
