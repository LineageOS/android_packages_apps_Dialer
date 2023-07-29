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

package com.android.dialer.main.impl;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.dialer.R;
import com.android.dialer.blockreportspam.ShowBlockReportSpamDialogReceiver;
import com.android.dialer.common.LogUtil;
import com.android.dialer.main.MainActivityPeer;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.TransactionSafeActivity;

/** This is the main activity for dialer. It hosts favorites, call log, search, dialpad, etc... */
// TODO(calderwoodra): Do not extend TransactionSafeActivity after new SpeedDial is launched
public class MainActivity extends TransactionSafeActivity
    implements MainActivityPeer.PeerSupplier {

  private MainActivityPeer activePeer;

  /**
   * {@link android.content.BroadcastReceiver} that shows a dialog to block a number and/or report
   * it as spam when notified.
   */
  private ShowBlockReportSpamDialogReceiver showBlockReportSpamDialogReceiver;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    setTheme(R.style.MainActivityTheme);
    super.onCreate(savedInstanceState);
    LogUtil.enterBlock("MainActivity.onCreate");

    activePeer = new OldMainActivityPeer(this);
    activePeer.onActivityCreate(savedInstanceState);

    showBlockReportSpamDialogReceiver =
        new ShowBlockReportSpamDialogReceiver(getSupportFragmentManager());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    activePeer.onNewIntent(intent);
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (!TelecomUtil.isDefaultDialer(this)) {
      startActivity(new Intent(this, DefaultDialerActivity.class));
      return;
    }

    activePeer.onActivityResume();

    LocalBroadcastManager.getInstance(this)
        .registerReceiver(
            showBlockReportSpamDialogReceiver, ShowBlockReportSpamDialogReceiver.getIntentFilter());
  }

  @Override
  protected void onUserLeaveHint() {
    super.onUserLeaveHint();
    activePeer.onUserLeaveHint();
  }

  @Override
  protected void onPause() {
    super.onPause();
    activePeer.onActivityPause();

    LocalBroadcastManager.getInstance(this).unregisterReceiver(showBlockReportSpamDialogReceiver);
  }

  @Override
  protected void onStop() {
    super.onStop();
    activePeer.onActivityStop();
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle bundle) {
    super.onSaveInstanceState(bundle);
    activePeer.onSaveInstanceState(bundle);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    activePeer.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public void onBackPressed() {
    if (activePeer.onBackPressed()) {
      return;
    }
    super.onBackPressed();
  }

  @Override
  public MainActivityPeer getPeer() {
    return activePeer;
  }
}
