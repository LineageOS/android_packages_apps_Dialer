/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.blockreportspam;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.ReportingLocation;

/**
 * Notifies that a dialog for blocking a number and/or marking it as spam/not spam should be shown.
 */
public final class ShowBlockReportSpamDialogNotifier {

  private ShowBlockReportSpamDialogNotifier() {}

  /**
   * Notifies that a dialog for blocking a number and optionally report it as spam should be shown.
   *
   * @param context Context
   * @param normalizedNumber The number to be blocked/marked as spam
   * @param countryIso The ISO 3166-1 two letters country code for the number
   * @param callType Call type defined in {@link android.provider.CallLog.Calls}
   * @param reportingLocation The location where the number is reported. See {@link
   *     ReportingLocation.Type}.
   */
  public static void notifyShowDialogToBlockNumberAndOptionallyReportSpam(
      Context context,
      String normalizedNumber,
      String countryIso,
      int callType,
      ReportingLocation.Type reportingLocation) {
    LogUtil.enterBlock(
        "ShowBlockReportSpamDialogNotifier.notifyShowDialogToBlockNumberAndOptionallyReportSpam");

    Intent intent = new Intent();
    intent.setAction(
        ShowBlockReportSpamDialogReceiver
            .ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER_AND_OPTIONALLY_REPORT_SPAM);

    intent.putExtra(ShowBlockReportSpamDialogReceiver.EXTRA_NUMBER, normalizedNumber);
    intent.putExtra(ShowBlockReportSpamDialogReceiver.EXTRA_COUNTRY_ISO, countryIso);
    intent.putExtra(ShowBlockReportSpamDialogReceiver.EXTRA_CALL_TYPE, callType);
    intent.putExtra(
        ShowBlockReportSpamDialogReceiver.EXTRA_REPORTING_LOCATION, reportingLocation.getNumber());

    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
  }

  /**
   * Notifies that a dialog for reporting a number as not spam should be shown.
   *
   * @param context Context
   * @param normalizedNumber The number to be reported as not spam
   * @param countryIso The ISO 3166-1 two letters country code for the number
   * @param callType Call type defined in {@link android.provider.CallLog.Calls}
   * @param reportingLocation The location where the number is reported. See {@link
   *     ReportingLocation.Type}.
   */
  public static void notifyShowDialogToReportNotSpam(
      Context context,
      String normalizedNumber,
      String countryIso,
      int callType,
      ReportingLocation.Type reportingLocation) {
    LogUtil.enterBlock("ShowBlockReportSpamDialogNotifier.notifyShowDialogToReportNotSpam");

    Intent intent = new Intent();
    intent.setAction(ShowBlockReportSpamDialogReceiver.ACTION_SHOW_DIALOG_TO_REPORT_NOT_SPAM);
    intent.putExtra(ShowBlockReportSpamDialogReceiver.EXTRA_NUMBER, normalizedNumber);
    intent.putExtra(ShowBlockReportSpamDialogReceiver.EXTRA_COUNTRY_ISO, countryIso);
    intent.putExtra(ShowBlockReportSpamDialogReceiver.EXTRA_CALL_TYPE, callType);
    intent.putExtra(ShowBlockReportSpamDialogReceiver.EXTRA_REPORTING_LOCATION, reportingLocation);

    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
  }
}
