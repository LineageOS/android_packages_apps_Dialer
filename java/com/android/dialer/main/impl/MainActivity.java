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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import com.android.dialer.blockreportspam.ShowBlockReportSpamDialogReceiver;
import com.android.dialer.calllog.config.CallLogConfigComponent;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.interactions.PhoneNumberInteraction.DisambigDialogDismissedListener;
import com.android.dialer.interactions.PhoneNumberInteraction.InteractionErrorCode;
import com.android.dialer.interactions.PhoneNumberInteraction.InteractionErrorListener;
import com.android.dialer.main.MainActivityPeer;
import com.android.dialer.main.impl.bottomnav.BottomNavBar.TabIndex;
import com.android.dialer.R;
import com.android.dialer.util.TransactionSafeActivity;

/** This is the main activity for dialer. It hosts favorites, call log, search, dialpad, etc... */
// TODO(calderwoodra): Do not extend TransactionSafeActivity after new SpeedDial is launched
public class MainActivity extends TransactionSafeActivity
    implements MainActivityPeer.PeerSupplier,
        // TODO(calderwoodra): remove these 2 interfaces when we migrate to new speed dial fragment
        InteractionErrorListener,
        DisambigDialogDismissedListener {

  private MainActivityPeer activePeer;

  /**
   * {@link android.content.BroadcastReceiver} that shows a dialog to block a number and/or report
   * it as spam when notified.
   */
  private ShowBlockReportSpamDialogReceiver showBlockReportSpamDialogReceiver;

  public static Intent getShowCallLogIntent(Context context) {
    return getShowTabIntent(context, TabIndex.CALL_LOG);
  }

  /** Returns intent that will open MainActivity to the specified tab. */
  public static Intent getShowTabIntent(Context context, @TabIndex int tabIndex) {
    if (CallLogConfigComponent.get(context).callLogConfig().isNewPeerEnabled()) {
      // TODO(calderwoodra): implement this in NewMainActivityPeer
      return null;
    }
    return OldMainActivityPeer.getShowTabIntent(context, tabIndex);
  }

  /**
   * @param context Context of the application package implementing MainActivity class.
   * @return intent for MainActivity.class
   */
  public static Intent getIntent(Context context) {
    return new Intent(context, MainActivity.class)
        .setAction(Intent.ACTION_VIEW)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    setTheme(R.style.MainActivityTheme);
    super.onCreate(savedInstanceState);
    LogUtil.enterBlock("MainActivity.onCreate");
    // If peer was set by the super, don't reset it.
    activePeer = getNewPeer();
    activePeer.onActivityCreate(savedInstanceState);

    showBlockReportSpamDialogReceiver =
        new ShowBlockReportSpamDialogReceiver(getSupportFragmentManager());
  }

  protected MainActivityPeer getNewPeer() {
    if (CallLogConfigComponent.get(this).callLogConfig().isNewPeerEnabled()) {
      return new NewMainActivityPeer(this);
    } else {
      return new OldMainActivityPeer(this);
    }
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
  protected void onSaveInstanceState(Bundle bundle) {
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
  public void interactionError(@InteractionErrorCode int interactionErrorCode) {
    switch (interactionErrorCode) {
      case InteractionErrorCode.USER_LEAVING_ACTIVITY:
        // This is expected to happen if the user exits the activity before the interaction occurs.
        return;
      case InteractionErrorCode.CONTACT_NOT_FOUND:
      case InteractionErrorCode.CONTACT_HAS_NO_NUMBER:
      case InteractionErrorCode.OTHER_ERROR:
      default:
        // All other error codes are unexpected. For example, it should be impossible to start an
        // interaction with an invalid contact from this activity.
        throw Assert.createIllegalStateFailException(
            "PhoneNumberInteraction error: " + interactionErrorCode);
    }
  }

  @Override
  public void onDisambigDialogDismissed() {
    // Don't do anything; the app will remain open with favorites tiles displayed.
  }

  @Override
  public MainActivityPeer getPeer() {
    return activePeer;
  }
}
