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

package com.android.dialer.common;

import android.support.annotation.Nullable;
import javax.annotation.Generated;


 final class AutoValue_FallibleAsyncTask_FallibleTaskResult<ResultT> extends FallibleAsyncTask.FallibleTaskResult<ResultT> {

  private final Throwable throwable;
  private final ResultT result;

  AutoValue_FallibleAsyncTask_FallibleTaskResult(
      @Nullable Throwable throwable,
      @Nullable ResultT result) {
    this.throwable = throwable;
    this.result = result;
  }

  @Nullable
  @Override
  public Throwable getThrowable() {
    return throwable;
  }

  @Nullable
  @Override
  public ResultT getResult() {
    return result;
  }

  @Override
  public String toString() {
    return "FallibleTaskResult{"
        + "throwable=" + throwable + ", "
        + "result=" + result
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof FallibleAsyncTask.FallibleTaskResult) {
      FallibleAsyncTask.FallibleTaskResult<?> that = (FallibleAsyncTask.FallibleTaskResult<?>) o;
      return ((this.throwable == null) ? (that.getThrowable() == null) : this.throwable.equals(that.getThrowable()))
           && ((this.result == null) ? (that.getResult() == null) : this.result.equals(that.getResult()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (throwable == null) ? 0 : this.throwable.hashCode();
    h *= 1000003;
    h ^= (result == null) ? 0 : this.result.hashCode();
    return h;
  }

}

