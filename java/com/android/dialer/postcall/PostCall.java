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

package com.android.dialer.postcall;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.BaseTransientBottomBar.BaseCallback;
import android.support.design.widget.Snackbar;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.View.OnClickListener;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProvider;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.enrichedcall.EnrichedCallCapabilities;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.enrichedcall.EnrichedCallManager;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.performancereport.PerformanceReport;
import com.android.dialer.storage.StorageComponent;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;

/** Helper class to handle all post call actions. */
public class PostCall {

  private static final String KEY_POST_CALL_CALL_DISCONNECT_TIME = "post_call_call_disconnect_time";
  private static final String KEY_POST_CALL_CALL_CONNECT_TIME = "post_call_call_connect_time";
  private static final String KEY_POST_CALL_CALL_NUMBER = "post_call_call_number";
  private static final String KEY_POST_CALL_MESSAGE_SENT = "post_call_message_sent";
  private static final String KEY_POST_CALL_DISCONNECT_PRESSED = "post_call_disconnect_pressed";

  private static Snackbar activeSnackbar;

  public static void promptUserForMessageIfNecessary(Activity activity, View rootView) {
    if (isEnabled(activity)) {
      if (shouldPromptUserToViewSentMessage(activity)) {
        promptUserToViewSentMessage(activity, rootView);
      } else if (shouldPromptUserToSendMessage(activity)) {
        promptUserToSendMessage(activity, rootView);
      } else {
        clear(activity);
      }
    }
  }

  public static void closePrompt() {
    if (activeSnackbar != null && activeSnackbar.isShown()) {
      activeSnackbar.dismiss();
      activeSnackbar = null;
    }
  }

  private static void promptUserToSendMessage(Activity activity, View rootView) {
    LogUtil.i("PostCall.promptUserToSendMessage", "returned from call, showing post call SnackBar");
    String message = activity.getString(R.string.post_call_message);
    EnrichedCallManager manager = EnrichedCallComponent.get(activity).getEnrichedCallManager();
    EnrichedCallCapabilities capabilities = manager.getCapabilities(getPhoneNumber(activity));
    LogUtil.i(
        "PostCall.promptUserToSendMessage",
        "number: %s, capabilities: %s",
        LogUtil.sanitizePhoneNumber(getPhoneNumber(activity)),
        capabilities);

    boolean isRcsPostCall = capabilities != null && capabilities.isPostCallCapable();
    String actionText =
        isRcsPostCall
            ? activity.getString(R.string.post_call_add_message)
            : activity.getString(R.string.post_call_send_message);

    String number = Assert.isNotNull(getPhoneNumber(activity));
    OnClickListener onClickListener =
        v -> {
          Logger.get(activity)
              .logImpression(DialerImpression.Type.POST_CALL_PROMPT_USER_TO_SEND_MESSAGE_CLICKED);
          activity.startActivity(PostCallActivity.newIntent(activity, number, isRcsPostCall));
        };

    int durationMs =
        (int) ConfigProviderBindings.get(activity).getLong("post_call_prompt_duration_ms", 8_000);
    activeSnackbar =
        Snackbar.make(rootView, message, durationMs)
            .setAction(actionText, onClickListener)
            .setActionTextColor(
                activity.getResources().getColor(R.color.dialer_snackbar_action_text_color));
    activeSnackbar.show();
    Logger.get(activity).logImpression(DialerImpression.Type.POST_CALL_PROMPT_USER_TO_SEND_MESSAGE);
    StorageComponent.get(activity)
        .unencryptedSharedPrefs()
        .edit()
        .remove(KEY_POST_CALL_CALL_DISCONNECT_TIME)
        .apply();
  }

  private static void promptUserToViewSentMessage(Activity activity, View rootView) {
    LogUtil.i(
        "PostCall.promptUserToViewSentMessage",
        "returned from sending a post call message, message sent.");
    String message = activity.getString(R.string.post_call_message_sent);
    String addMessage = activity.getString(R.string.view);
    String number = Assert.isNotNull(getPhoneNumber(activity));
    OnClickListener onClickListener =
        v -> {
          Logger.get(activity)
              .logImpression(
                  DialerImpression.Type.POST_CALL_PROMPT_USER_TO_VIEW_SENT_MESSAGE_CLICKED);
          Intent intent = IntentUtil.getSendSmsIntent(number);
          DialerUtils.startActivityWithErrorToast(activity, intent);
        };

    activeSnackbar =
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
            .setAction(addMessage, onClickListener)
            .setActionTextColor(
                activity.getResources().getColor(R.color.dialer_snackbar_action_text_color))
            .addCallback(
                new BaseCallback<Snackbar>() {
                  @Override
                  public void onDismissed(Snackbar snackbar, int i) {
                    super.onDismissed(snackbar, i);
                    clear(snackbar.getContext());
                  }
                });
    activeSnackbar.show();
    Logger.get(activity)
        .logImpression(DialerImpression.Type.POST_CALL_PROMPT_USER_TO_VIEW_SENT_MESSAGE);
    StorageComponent.get(activity)
        .unencryptedSharedPrefs()
        .edit()
        .remove(KEY_POST_CALL_MESSAGE_SENT)
        .apply();
  }

  public static void onDisconnectPressed(Context context) {
    StorageComponent.get(context)
        .unencryptedSharedPrefs()
        .edit()
        .putBoolean(KEY_POST_CALL_DISCONNECT_PRESSED, true)
        .apply();
  }

  public static void onCallDisconnected(Context context, String number, long callConnectedMillis) {
    StorageComponent.get(context)
        .unencryptedSharedPrefs()
        .edit()
        .putLong(KEY_POST_CALL_CALL_CONNECT_TIME, callConnectedMillis)
        .putLong(KEY_POST_CALL_CALL_DISCONNECT_TIME, System.currentTimeMillis())
        .putString(KEY_POST_CALL_CALL_NUMBER, number)
        .apply();
  }

  public static void onMessageSent(Context context, String number) {
    StorageComponent.get(context)
        .unencryptedSharedPrefs()
        .edit()
        .putString(KEY_POST_CALL_CALL_NUMBER, number)
        .putBoolean(KEY_POST_CALL_MESSAGE_SENT, true)
        .apply();
  }

  /**
   * Restart performance recording if there is a recent call (disconnect time to now is under
   * threshold)
   */
  public static void restartPerformanceRecordingIfARecentCallExist(Context context) {
    long disconnectTimeMillis =
        StorageComponent.get(context)
            .unencryptedSharedPrefs()
            .getLong(PostCall.KEY_POST_CALL_CALL_DISCONNECT_TIME, -1);
    if (disconnectTimeMillis != -1 && PerformanceReport.isRecording()) {
      PerformanceReport.startRecording();
    }
  }

  private static void clear(Context context) {
    activeSnackbar = null;

    StorageComponent.get(context)
        .unencryptedSharedPrefs()
        .edit()
        .remove(KEY_POST_CALL_CALL_DISCONNECT_TIME)
        .remove(KEY_POST_CALL_CALL_NUMBER)
        .remove(KEY_POST_CALL_MESSAGE_SENT)
        .remove(KEY_POST_CALL_CALL_CONNECT_TIME)
        .remove(KEY_POST_CALL_DISCONNECT_PRESSED)
        .apply();
  }

  private static boolean shouldPromptUserToSendMessage(Context context) {
    SharedPreferences manager = StorageComponent.get(context).unencryptedSharedPrefs();
    long disconnectTimeMillis = manager.getLong(KEY_POST_CALL_CALL_DISCONNECT_TIME, -1);
    long connectTimeMillis = manager.getLong(KEY_POST_CALL_CALL_CONNECT_TIME, -1);

    long timeSinceDisconnect = System.currentTimeMillis() - disconnectTimeMillis;
    long callDurationMillis = disconnectTimeMillis - connectTimeMillis;

    boolean callDisconnectedByUser = manager.getBoolean(KEY_POST_CALL_DISCONNECT_PRESSED, false);

    ConfigProvider binding = ConfigProviderBindings.get(context);
    return disconnectTimeMillis != -1
        && connectTimeMillis != -1
        && isSimReady(context)
        && binding.getLong("postcall_last_call_threshold", 30_000) > timeSinceDisconnect
        && (connectTimeMillis == 0
            || binding.getLong("postcall_call_duration_threshold", 35_000) > callDurationMillis)
        && getPhoneNumber(context) != null
        && callDisconnectedByUser;
  }

  private static boolean shouldPromptUserToViewSentMessage(Context context) {
    return StorageComponent.get(context)
        .unencryptedSharedPrefs()
        .getBoolean(KEY_POST_CALL_MESSAGE_SENT, false);
  }

  @Nullable
  private static String getPhoneNumber(Context context) {
    return StorageComponent.get(context)
        .unencryptedSharedPrefs()
        .getString(KEY_POST_CALL_CALL_NUMBER, null);
  }

  private static boolean isEnabled(Context context) {

    SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);

    boolean mEnabled = mPrefs.getBoolean("enable_post_call", true);

    return mEnabled;
  }

  private static boolean isSimReady(Context context) {
    return context.getSystemService(TelephonyManager.class).getSimState()
        == TelephonyManager.SIM_STATE_READY;
  }
}
