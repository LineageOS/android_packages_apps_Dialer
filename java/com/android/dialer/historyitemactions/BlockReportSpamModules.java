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
 * limitations under the License.
 */

package com.android.dialer.historyitemactions;

import android.content.Context;
import com.android.dialer.blockreportspam.BlockReportSpamDialogInfo;
import com.android.dialer.blockreportspam.ShowBlockReportSpamDialogNotifier;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import java.util.Optional;

/** Modules for blocking/unblocking a number and/or reporting it as spam/not spam. */
final class BlockReportSpamModules {

  private BlockReportSpamModules() {}

  static HistoryItemActionModule moduleForMarkingNumberAsNotSpam(
      Context context,
      BlockReportSpamDialogInfo blockReportSpamDialogInfo,
      Optional<DialerImpression.Type> impression) {

    return new HistoryItemActionModule() {
      @Override
      public int getStringId() {
        return R.string.not_spam;
      }

      @Override
      public int getDrawableId() {
        return R.drawable.quantum_ic_report_off_vd_theme_24;
      }

      @Override
      public boolean onClick() {
        ShowBlockReportSpamDialogNotifier.notifyShowDialogToReportNotSpam(
            context, blockReportSpamDialogInfo);

        impression.ifPresent(Logger.get(context)::logImpression);
        return true; // Close the bottom sheet.
      }
    };
  }

  static HistoryItemActionModule moduleForBlockingNumber(
      Context context,
      BlockReportSpamDialogInfo blockReportSpamDialogInfo,
      Optional<DialerImpression.Type> impression) {

    return new HistoryItemActionModule() {
      @Override
      public int getStringId() {
        return R.string.block_number;
      }

      @Override
      public int getDrawableId() {
        return R.drawable.quantum_ic_block_vd_theme_24;
      }

      @Override
      public boolean onClick() {
        ShowBlockReportSpamDialogNotifier.notifyShowDialogToBlockNumber(
            context, blockReportSpamDialogInfo);

        impression.ifPresent(Logger.get(context)::logImpression);
        return true; // Close the bottom sheet.
      }
    };
  }

  static HistoryItemActionModule moduleForUnblockingNumber(
      Context context,
      BlockReportSpamDialogInfo blockReportSpamDialogInfo,
      Optional<DialerImpression.Type> impression) {

    return new HistoryItemActionModule() {
      @Override
      public int getStringId() {
        return R.string.unblock_number;
      }

      @Override
      public int getDrawableId() {
        return R.drawable.quantum_ic_unblock_vd_theme_24;
      }

      @Override
      public boolean onClick() {
        ShowBlockReportSpamDialogNotifier.notifyShowDialogToUnblockNumber(
            context, blockReportSpamDialogInfo);

        impression.ifPresent(Logger.get(context)::logImpression);
        return true; // Close the bottom sheet.
      }
    };
  }

  static HistoryItemActionModule moduleForBlockingNumberAndOptionallyReportingSpam(
      Context context,
      BlockReportSpamDialogInfo blockReportSpamDialogInfo,
      Optional<DialerImpression.Type> impression) {

    return new HistoryItemActionModule() {
      @Override
      public int getStringId() {
        return R.string.block_and_optionally_report_spam;
      }

      @Override
      public int getDrawableId() {
        return R.drawable.quantum_ic_block_vd_theme_24;
      }

      @Override
      public boolean onClick() {
        ShowBlockReportSpamDialogNotifier.notifyShowDialogToBlockNumberAndOptionallyReportSpam(
            context, blockReportSpamDialogInfo);

        impression.ifPresent(Logger.get(context)::logImpression);
        return true; // Close the bottom sheet.
      }
    };
  }
}
