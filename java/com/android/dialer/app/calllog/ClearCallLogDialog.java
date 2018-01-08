/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.app.calllog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.support.annotation.NonNull;
import com.android.dialer.app.R;
import com.android.dialer.common.Assert;
import com.android.dialer.lookup.LookupCache;
import com.android.dialer.phonenumbercache.CachedNumberLookupService;
import com.android.dialer.phonenumbercache.PhoneNumberCache;

/** Dialog that clears the call log after confirming with the user */
public class ClearCallLogDialog extends DialogFragment {

  private Listener listener;

  /** Preferred way to show this dialog */
  public static void show(FragmentManager fragmentManager, @NonNull Listener listener) {
    ClearCallLogDialog dialog = new ClearCallLogDialog();
    dialog.listener = Assert.isNotNull(listener);
    dialog.show(fragmentManager, "deleteCallLog");
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    final ContentResolver resolver = getActivity().getContentResolver();
    final Context context = getActivity().getApplicationContext();
    final OnClickListener okListener =
        new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            final ProgressDialog progressDialog =
                ProgressDialog.show(
                    getActivity(), getString(R.string.clearCallLogProgress_title), "", true, false);
            progressDialog.setOwnerActivity(getActivity());
            CallLogNotificationsService.cancelAllMissedCalls(getContext());
            final AsyncTask<Void, Void, Void> task =
                new AsyncTask<Void, Void, Void>() {
                  @Override
                  protected Void doInBackground(Void... params) {
                    resolver.delete(Calls.CONTENT_URI, null, null);
                    CachedNumberLookupService cachedNumberLookupService =
                        PhoneNumberCache.get(context).getCachedNumberLookupService();
                    if (cachedNumberLookupService != null) {
                      cachedNumberLookupService.clearAllCacheEntries(context);
                    }
                    LookupCache.deleteCachedContacts(context);
                    return null;
                  }

                  @Override
                  protected void onPostExecute(Void result) {
                    final Activity activity = progressDialog.getOwnerActivity();

                    if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
                      return;
                    }

                    listener.callHistoryDeleted();
                    if (progressDialog != null && progressDialog.isShowing()) {
                      progressDialog.dismiss();
                    }
                  }
                };
            // TODO: Once we have the API, we should configure this ProgressDialog
            // to only show up after a certain time (e.g. 150ms)
            progressDialog.show();
            task.execute();
          }
        };
    return new AlertDialog.Builder(getActivity())
        .setTitle(R.string.clearCallLogConfirmation_title)
        .setIconAttribute(android.R.attr.alertDialogIcon)
        .setMessage(R.string.clearCallLogConfirmation)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok, okListener)
        .setCancelable(true)
        .create();
  }

  interface Listener {
    void callHistoryDeleted();
  }
}
