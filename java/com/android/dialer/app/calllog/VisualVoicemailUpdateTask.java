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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.ArrayMap;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.CallLogNotificationsQueryHelper.NewCall;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.blocking.FilteredNumbersUtil;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutors;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.telecom.TelecomUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Updates voicemail notifications in the background. */
class VisualVoicemailUpdateTask implements Worker<VisualVoicemailUpdateTask.Input, Void> {
  @Nullable
  @Override
  public Void doInBackground(@NonNull Input input) throws Throwable {
    updateNotification(input.context, input.queryHelper, input.queryHandler);
    return null;
  }

  /**
   * Updates the notification and notifies of the call with the given URI.
   *
   * <p>Clears the notification if there are no new voicemails, and notifies if the given URI
   * corresponds to a new voicemail.
   */
  @WorkerThread
  private static void updateNotification(
      Context context,
      CallLogNotificationsQueryHelper queryHelper,
      FilteredNumberAsyncQueryHandler queryHandler) {
    Assert.isWorkerThread();

    List<NewCall> newCalls = queryHelper.getNewVoicemails();
    if (newCalls == null) {
      return;
    }
    newCalls = filterBlockedNumbers(context, queryHandler, newCalls);
    if (newCalls.isEmpty()) {
      return;
    }

    // This represents a list of names to include in the notification.
    String callers = null;

    // Maps each number into a name: if a number is in the map, it has already left a more
    // recent voicemail.
    Map<String, ContactInfo> contactInfos = new ArrayMap<>();
    for (NewCall newCall : newCalls) {
      if (!contactInfos.containsKey(newCall.number)) {
        ContactInfo contactInfo =
            queryHelper.getContactInfo(
                newCall.number, newCall.numberPresentation, newCall.countryIso);
        contactInfos.put(newCall.number, contactInfo);

        // This is a new caller. Add it to the back of the list of callers.
        if (TextUtils.isEmpty(callers)) {
          callers = contactInfo.name;
        } else {
          callers =
              context.getString(
                  R.string.notification_voicemail_callers_list, callers, contactInfo.name);
        }
      }
    }
    VisualVoicemailNotifier.showNotifications(context, newCalls, contactInfos, callers);
  }

  @WorkerThread
  private static List<NewCall> filterBlockedNumbers(
      Context context, FilteredNumberAsyncQueryHandler queryHandler, List<NewCall> newCalls) {
    Assert.isWorkerThread();
    if (FilteredNumbersUtil.hasRecentEmergencyCall(context)) {
      LogUtil.i(
          "VisualVoicemailUpdateTask.filterBlockedNumbers",
          "not filtering due to recent emergency call");
      return newCalls;
    }

    List<NewCall> result = new ArrayList<>();
    for (NewCall newCall : newCalls) {
      if (queryHandler.getBlockedIdSynchronous(newCall.number, newCall.countryIso) != null) {
        LogUtil.i(
            "VisualVoicemailUpdateTask.filterBlockedNumbers",
            "found voicemail from blocked number, deleting");
        if (newCall.voicemailUri != null) {
          // Delete the voicemail.
          CallLogAsyncTaskUtil.deleteVoicemailSynchronous(context, newCall.voicemailUri);
        }
      } else {
        result.add(newCall);
      }
    }
    return result;
  }

  /** Updates the voicemail notifications displayed. */
  static void scheduleTask(@NonNull Context context, @NonNull Runnable callback) {
    Assert.isNotNull(context);
    Assert.isNotNull(callback);
    if (!TelecomUtil.isDefaultDialer(context)) {
      LogUtil.i("VisualVoicemailUpdateTask.scheduleTask", "not default dialer, not running");
      callback.run();
      return;
    }

    Input input =
        new Input(
            context,
            CallLogNotificationsQueryHelper.getInstance(context),
            new FilteredNumberAsyncQueryHandler(context));
    DialerExecutors.createNonUiTaskBuilder(new VisualVoicemailUpdateTask())
        .onSuccess(
            output -> {
              LogUtil.i("VisualVoicemailUpdateTask.scheduleTask", "update successful");
              callback.run();
            })
        .onFailure(
            throwable -> {
              LogUtil.i("VisualVoicemailUpdateTask.scheduleTask", "update failed: " + throwable);
              callback.run();
            })
        .build()
        .executeParallel(input);
  }

  static class Input {
    @NonNull final Context context;
    @NonNull final CallLogNotificationsQueryHelper queryHelper;
    @NonNull final FilteredNumberAsyncQueryHandler queryHandler;

    Input(
        Context context,
        CallLogNotificationsQueryHelper queryHelper,
        FilteredNumberAsyncQueryHandler queryHandler) {
      this.context = context;
      this.queryHelper = queryHelper;
      this.queryHandler = queryHandler;
    }
  }
}
