/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.dialer.util;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import com.android.dialer.common.LogUtil;
import com.android.dialer.telecom.TelecomUtil;
import java.io.File;
import java.util.Iterator;
import java.util.Random;

/** General purpose utility methods for the Dialer. */
public class DialerUtils {

  /**
   * Prefix on a dialed number that indicates that the call should be placed through the Wireless
   * Priority Service.
   */
  private static final String WPS_PREFIX = "*272";

  public static final String FILE_PROVIDER_CACHE_DIR = "my_cache";

  private static final Random RANDOM = new Random();

  /**
   * Attempts to start an activity and displays a toast with the default error message if the
   * activity is not found, instead of throwing an exception.
   *
   * @param context to start the activity with.
   * @param intent to start the activity with.
   */
  public static void startActivityWithErrorToast(Context context, Intent intent) {
    startActivityWithErrorToast(context, intent, R.string.activity_not_available);
  }

  /**
   * Attempts to start an activity and displays a toast with a provided error message if the
   * activity is not found, instead of throwing an exception.
   *
   * @param context to start the activity with.
   * @param intent to start the activity with.
   * @param msgId Resource ID of the string to display in an error message if the activity is not
   *     found.
   */
  public static void startActivityWithErrorToast(
      final Context context, final Intent intent, int msgId) {
    try {
      if ((Intent.ACTION_CALL.equals(intent.getAction()))) {
        // All dialer-initiated calls should pass the touch point to the InCallUI
        Point touchPoint = TouchPointManager.getInstance().getPoint();
        if (touchPoint.x != 0 || touchPoint.y != 0) {
          Bundle extras;
          // Make sure to not accidentally clobber any existing extras
          if (intent.hasExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)) {
            extras = intent.getParcelableExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS);
          } else {
            extras = new Bundle();
          }
          extras.putParcelable(TouchPointManager.TOUCH_POINT, touchPoint);
          intent.putExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);
        }

        if (shouldWarnForOutgoingWps(context, intent.getData().getSchemeSpecificPart())) {
          LogUtil.i(
              "DialUtils.startActivityWithErrorToast",
              "showing outgoing WPS dialog before placing call");
          AlertDialog.Builder builder = new AlertDialog.Builder(context);
          builder.setMessage(R.string.outgoing_wps_warning);
          builder.setPositiveButton(
              R.string.dialog_continue,
              new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  placeCallOrMakeToast(context, intent);
                }
              });
          builder.setNegativeButton(android.R.string.cancel, null);
          builder.create().show();
        } else {
          placeCallOrMakeToast(context, intent);
        }
      } else {
        context.startActivity(intent);
      }
    } catch (ActivityNotFoundException e) {
      Toast.makeText(context, msgId, Toast.LENGTH_SHORT).show();
    }
  }

  private static void placeCallOrMakeToast(Context context, Intent intent) {
    final boolean hasCallPermission = TelecomUtil.placeCall(context, intent);
    if (!hasCallPermission) {
      // TODO: Make calling activity show request permission dialog and handle
      // callback results appropriately.
      Toast.makeText(context, "Cannot place call without Phone permission", Toast.LENGTH_SHORT)
          .show();
    }
  }

  /**
   * Returns whether the user should be warned about an outgoing WPS call. This checks if there is a
   * currently active call over LTE. Regardless of the country or carrier, the radio will drop an
   * active LTE call if a WPS number is dialed, so this warning is necessary.
   */
  @SuppressLint("MissingPermission")
  private static boolean shouldWarnForOutgoingWps(Context context, String number) {
    if (number != null && number.startsWith(WPS_PREFIX)) {
      TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
      boolean isOnVolte =
          telephonyManager.getVoiceNetworkType() == TelephonyManager.NETWORK_TYPE_LTE;
      boolean hasCurrentActiveCall =
          telephonyManager.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK;
      return isOnVolte && hasCurrentActiveCall;
    }
    return false;
  }

  /**
   * Closes an {@link AutoCloseable}, silently ignoring any checked exceptions. Does nothing if
   * null.
   *
   * @param closeable to close.
   */
  public static void closeQuietly(AutoCloseable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (RuntimeException rethrown) {
        throw rethrown;
      } catch (Exception ignored) {
      }
    }
  }

  /**
   * Joins a list of {@link CharSequence} into a single {@link CharSequence} seperated by ", ".
   *
   * @param list List of char sequences to join.
   * @return Joined char sequences.
   */
  public static CharSequence join(Iterable<CharSequence> list) {
    StringBuilder sb = new StringBuilder();
    final BidiFormatter formatter = BidiFormatter.getInstance();
    final CharSequence separator = ", ";

    Iterator<CharSequence> itr = list.iterator();
    boolean firstTime = true;
    while (itr.hasNext()) {
      if (firstTime) {
        firstTime = false;
      } else {
        sb.append(separator);
      }
      // Unicode wrap the elements of the list to respect RTL for individual strings.
      sb.append(
          formatter.unicodeWrap(itr.next().toString(), TextDirectionHeuristics.FIRSTSTRONG_LTR));
    }

    // Unicode wrap the joined value, to respect locale's RTL ordering for the whole list.
    return formatter.unicodeWrap(sb.toString());
  }

  public static void showInputMethod(View view) {
    final InputMethodManager imm =
        (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.showSoftInput(view, 0);
    }
  }

  public static void hideInputMethod(View view) {
    final InputMethodManager imm =
        (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
  }

  /**
   * Create a File in the cache directory that Dialer's FileProvider knows about so they can be
   * shared to other apps.
   */
  public static File createShareableFile(Context context) {
    long fileId = Math.abs(RANDOM.nextLong());
    File parentDir = new File(context.getCacheDir(), FILE_PROVIDER_CACHE_DIR);
    if (!parentDir.exists()) {
      parentDir.mkdirs();
    }
    return new File(parentDir, String.valueOf(fileId));
  }
}
