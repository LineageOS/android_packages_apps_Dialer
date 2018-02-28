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

import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Toast;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.OnConfirmListener;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.OnSpamDialogClickListener;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.ContactSource;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.ReportingLocation;
import com.android.dialer.spam.Spam;
import com.android.dialer.spam.SpamComponent;
import java.util.Locale;

/**
 * A {@link BroadcastReceiver} that shows an appropriate dialog upon receiving notifications from
 * {@link ShowBlockReportSpamDialogNotifier}.
 */
public final class ShowBlockReportSpamDialogReceiver extends BroadcastReceiver {

  static final String ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER_AND_OPTIONALLY_REPORT_SPAM =
      "show_dialog_to_block_number_and_optionally_report_spam";
  static final String ACTION_SHOW_DIALOG_TO_REPORT_NOT_SPAM = "show_dialog_to_report_not_spam";
  static final String EXTRA_NUMBER = "number";
  static final String EXTRA_COUNTRY_ISO = "country_iso";
  static final String EXTRA_CALL_TYPE = "call_type";
  static final String EXTRA_REPORTING_LOCATION = "reporting_location";

  /** {@link FragmentManager} needed to show a {@link android.app.DialogFragment}. */
  private final FragmentManager fragmentManager;

  /** Returns an {@link IntentFilter} containing all actions accepted by this broadcast receiver. */
  public static IntentFilter getIntentFilter() {
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER_AND_OPTIONALLY_REPORT_SPAM);
    intentFilter.addAction(ACTION_SHOW_DIALOG_TO_REPORT_NOT_SPAM);
    return intentFilter;
  }

  public ShowBlockReportSpamDialogReceiver(FragmentManager fragmentManager) {
    this.fragmentManager = fragmentManager;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    LogUtil.enterBlock("ShowBlockReportSpamDialogReceiver.onReceive");

    String action = intent.getAction();

    switch (Assert.isNotNull(action)) {
      case ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER_AND_OPTIONALLY_REPORT_SPAM:
        showDialogToBlockNumberAndOptionallyReportSpam(context, intent);
        break;
      case ACTION_SHOW_DIALOG_TO_REPORT_NOT_SPAM:
        showDialogToReportNotSpam(context, intent);
        break;
      default:
        throw new IllegalStateException("Unsupported action: " + action);
    }
  }

  private void showDialogToBlockNumberAndOptionallyReportSpam(Context context, Intent intent) {
    LogUtil.enterBlock(
        "ShowBlockReportSpamDialogReceiver.showDialogToBlockNumberAndOptionallyReportSpam");

    Assert.checkArgument(intent.hasExtra(EXTRA_NUMBER));
    Assert.checkArgument(intent.hasExtra(EXTRA_COUNTRY_ISO));
    Assert.checkArgument(intent.hasExtra(EXTRA_CALL_TYPE));
    Assert.checkArgument(intent.hasExtra(EXTRA_REPORTING_LOCATION));

    String normalizedNumber = intent.getStringExtra(EXTRA_NUMBER);
    String countryIso = intent.getStringExtra(EXTRA_COUNTRY_ISO);
    int callType = intent.getIntExtra(EXTRA_CALL_TYPE, 0);
    ReportingLocation.Type reportingLocation =
        ReportingLocation.Type.forNumber(
            intent.getIntExtra(
                EXTRA_REPORTING_LOCATION,
                ReportingLocation.Type.UNKNOWN_REPORTING_LOCATION.getNumber()));

    Spam spam = SpamComponent.get(context).spam();

    // Set up the positive listener for the dialog.
    OnSpamDialogClickListener onSpamDialogClickListener =
        reportSpam -> {
          LogUtil.i(
              "ShowBlockReportSpamDialogReceiver.showDialogToBlockNumberAndOptionallyReportSpam",
              "confirmed");

          if (reportSpam && spam.isSpamEnabled()) {
            LogUtil.i(
                "ShowBlockReportSpamDialogReceiver.showDialogToBlockNumberAndOptionallyReportSpam",
                "report spam");
            Logger.get(context)
                .logImpression(
                    DialerImpression.Type
                        .REPORT_CALL_AS_SPAM_VIA_CALL_LOG_BLOCK_REPORT_SPAM_SENT_VIA_BLOCK_NUMBER_DIALOG);
            spam.reportSpamFromCallHistory(
                normalizedNumber,
                countryIso,
                callType,
                reportingLocation,
                ContactSource.Type.UNKNOWN_SOURCE_TYPE /* TODO(a bug): Fix. */);
          }

          // TODO(a bug): Block the number.
          Toast.makeText(
                  context,
                  String.format(Locale.ENGLISH, "TODO: " + "Block number %s.", normalizedNumber),
                  Toast.LENGTH_SHORT)
              .show();
        };

    // Create and show the dialog.
    BlockReportSpamDialogs.BlockReportSpamDialogFragment.newInstance(
            normalizedNumber,
            spam.isDialogReportSpamCheckedByDefault(),
            onSpamDialogClickListener,
            /* dismissListener = */ null)
        .show(fragmentManager, BlockReportSpamDialogs.BLOCK_REPORT_SPAM_DIALOG_TAG);
  }

  private void showDialogToReportNotSpam(Context context, Intent intent) {
    LogUtil.enterBlock("ShowBlockReportSpamDialogReceiver.showDialogToReportNotSpam");

    Assert.checkArgument(intent.hasExtra(EXTRA_NUMBER));
    Assert.checkArgument(intent.hasExtra(EXTRA_COUNTRY_ISO));
    Assert.checkArgument(intent.hasExtra(EXTRA_CALL_TYPE));
    Assert.checkArgument(intent.hasExtra(EXTRA_REPORTING_LOCATION));

    String normalizedNumber = intent.getStringExtra(EXTRA_NUMBER);
    String countryIso = intent.getStringExtra(EXTRA_COUNTRY_ISO);
    int callType = intent.getIntExtra(EXTRA_CALL_TYPE, 0);
    ReportingLocation.Type reportingLocation =
        ReportingLocation.Type.forNumber(
            intent.getIntExtra(
                EXTRA_REPORTING_LOCATION,
                ReportingLocation.Type.UNKNOWN_REPORTING_LOCATION.getNumber()));

    // Set up the positive listener for the dialog.
    OnConfirmListener onConfirmListener =
        () -> {
          LogUtil.i("ShowBlockReportSpamDialogReceiver.showDialogToReportNotSpam", "confirmed");

          Spam spam = SpamComponent.get(context).spam();
          if (spam.isSpamEnabled()) {
            Logger.get(context)
                .logImpression(DialerImpression.Type.DIALOG_ACTION_CONFIRM_NUMBER_NOT_SPAM);
            spam.reportNotSpamFromCallHistory(
                normalizedNumber,
                countryIso,
                callType,
                reportingLocation,
                ContactSource.Type.UNKNOWN_SOURCE_TYPE /* TODO(a bug): Fix. */);
          }
        };

    // Create & show the dialog.
    BlockReportSpamDialogs.ReportNotSpamDialogFragment.newInstance(
            normalizedNumber, onConfirmListener, /* dismissListener = */ null)
        .show(fragmentManager, BlockReportSpamDialogs.NOT_SPAM_DIALOG_TAG);
  }
}
