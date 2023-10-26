/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.dialer.shortcuts;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import android.widget.Toast;

import com.android.dialer.R;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.common.LogUtil;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.interactions.PhoneNumberInteraction.InteractionErrorCode;
import com.android.dialer.util.TransactionSafeActivity;

/**
 * Invisible activity launched when a shortcut is selected by user. Calls a contact based on URI.
 */
public class CallContactActivity extends TransactionSafeActivity
    implements PhoneNumberInteraction.DisambigDialogDismissedListener,
        PhoneNumberInteraction.InteractionErrorListener {

  private static final String CONTACT_URI_KEY = "uri_key";

  private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
          new ActivityResultContracts.RequestMultiplePermissions(), grantResults -> {
            if (grantResults.values().iterator().next()) {
              makeCall();
            } else {
              Toast.makeText(this, R.string.dialer_shortcut_no_permissions, Toast.LENGTH_SHORT).show();
              finish();
            }
          });

  private Uri contactUri;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if ("com.android.dialer.shortcuts.CALL_CONTACT".equals(getIntent().getAction())) {
        LogUtil.i("CallContactActivity.onCreate", "shortcut clicked");
        contactUri = getIntent().getData();
        makeCall();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    LogUtil.enterBlock("CallContactActivity.onDestroy");
  }

  /**
   * Attempt to make a call, finishing the activity if the required permissions are already granted.
   * If the required permissions are not already granted, the activity is not finished so that the
   * user can choose to grant or deny them.
   */
  private void makeCall() {
    CallSpecificAppData callSpecificAppData =
        CallSpecificAppData.newBuilder()
            .setAllowAssistedDialing(true)
            .setCallInitiationType(CallInitiationType.Type.LAUNCHER_SHORTCUT)
            .build();
    PhoneNumberInteraction.startInteractionForPhoneCall(
        this, contactUri, false /* isVideoCall */, callSpecificAppData, permissionLauncher);
  }

  @Override
  public void onDisambigDialogDismissed() {
    finish();
  }

  @Override
  public void interactionError(@InteractionErrorCode int interactionErrorCode) {
    // Note: There is some subtlety to how contact lookup keys work that make it difficult to
    // distinguish the case of the contact missing from the case of the a contact not having a
    // number. For example, if a contact's phone number is deleted, subsequent lookups based on
    // lookup key will actually return no results because the phone number was part of the
    // lookup key. In this case, it would be inaccurate to say the contact can't be found though, so
    // in all cases we just say the contact can't be found or the contact doesn't have a number.
    switch (interactionErrorCode) {
      case InteractionErrorCode.CONTACT_NOT_FOUND:
      case InteractionErrorCode.CONTACT_HAS_NO_NUMBER:
        Toast.makeText(
                this,
                R.string.dialer_shortcut_contact_not_found_or_has_no_number,
                Toast.LENGTH_SHORT)
            .show();
        break;
      case InteractionErrorCode.USER_LEAVING_ACTIVITY:
      case InteractionErrorCode.OTHER_ERROR:
      default:
        // If the user is leaving the activity or the error code was "other" there's no useful
        // information to display but we still need to finish this invisible activity.
        break;
    }
    finish();
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putParcelable(CONTACT_URI_KEY, contactUri);
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    if (savedInstanceState == null) {
      return;
    }
    contactUri = savedInstanceState.getParcelable(CONTACT_URI_KEY, Uri.class);
  }
}
