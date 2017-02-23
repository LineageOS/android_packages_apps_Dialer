/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.dialer.app.calllog;

import android.content.Context;
import android.os.AsyncTask;
import android.provider.CallLog.Calls;
import com.android.dialer.common.Assert;

/**
 * Class to access the call log asynchronously to avoid carrying out database operations on the UI
 * thread, using an {@link AsyncTask}.
 *
 * <pre class="prettyprint"> Typical usage: ==============
 *
 * // From an activity... String mLastNumber = "";
 *
 * CallLogAsync log = new CallLogAsync();
 *
 * CallLogAsync.GetLastOutgoingCallArgs lastCallArgs = new CallLogAsync.GetLastOutgoingCallArgs(
 * this, new CallLogAsync.OnLastOutgoingCallComplete() { public void lastOutgoingCall(String number)
 * { mLastNumber = number; } }); log.getLastOutgoingCall(lastCallArgs); </pre>
 */
public class CallLogAsync {

  /** CallLog.getLastOutgoingCall(...) */
  public AsyncTask getLastOutgoingCall(GetLastOutgoingCallArgs args) {
    Assert.isMainThread();
    return new GetLastOutgoingCallTask(args.callback).execute(args);
  }

  /** Interface to retrieve the last dialed number asynchronously. */
  public interface OnLastOutgoingCallComplete {

    /** @param number The last dialed number or an empty string if none exists yet. */
    void lastOutgoingCall(String number);
  }

  /** Parameter object to hold the args to get the last outgoing call from the call log DB. */
  public static class GetLastOutgoingCallArgs {

    public final Context context;
    public final OnLastOutgoingCallComplete callback;

    public GetLastOutgoingCallArgs(Context context, OnLastOutgoingCallComplete callback) {
      this.context = context;
      this.callback = callback;
    }
  }

  /** AsyncTask to get the last outgoing call from the DB. */
  private class GetLastOutgoingCallTask extends AsyncTask<GetLastOutgoingCallArgs, Void, String> {

    private final OnLastOutgoingCallComplete mCallback;

    public GetLastOutgoingCallTask(OnLastOutgoingCallComplete callback) {
      mCallback = callback;
    }

    // Happens on a background thread. We cannot run the callback
    // here because only the UI thread can modify the view
    // hierarchy (e.g enable/disable the dial button). The
    // callback is ran rom the post execute method.
    @Override
    protected String doInBackground(GetLastOutgoingCallArgs... list) {
      String number = "";
      for (GetLastOutgoingCallArgs args : list) {
        // May block. Select only the last one.
        number = Calls.getLastOutgoingCall(args.context);
      }
      return number; // passed to the onPostExecute method.
    }

    // Happens on the UI thread, it is safe to run the callback
    // that may do some work on the views.
    @Override
    protected void onPostExecute(String number) {
      Assert.isMainThread();
      mCallback.lastOutgoingCall(number);
    }
  }
}
