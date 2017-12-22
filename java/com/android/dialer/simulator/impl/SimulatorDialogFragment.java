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

package com.android.dialer.simulator.impl;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.telecom.TelecomManager;
import android.widget.EditText;

/** Holds dialog logic for creating different types of voice calls. */
public final class SimulatorDialogFragment extends DialogFragment {

  private final String[] callerIdPresentationItems = {
    "ALLOWED", "PAYPHONE", "RESTRICTED", "UNKNOWN"
  };
  private int callerIdPresentationChoice = 1;

  private DialogCallback dialogCallback;

  static SimulatorDialogFragment newInstance(DialogCallback dialogCallback) {
    SimulatorDialogFragment fragment = new SimulatorDialogFragment();
    fragment.setCallBack(dialogCallback);
    return fragment;
  }

  public void setCallBack(DialogCallback dialogCallback) {
    this.dialogCallback = dialogCallback;
  }

  @Override
  public Dialog onCreateDialog(Bundle bundle) {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    final EditText input = new EditText(getActivity());
    input.setHint("Please input phone number");
    builder
        .setTitle("Phone Number:")
        .setView(input)
        .setSingleChoiceItems(
            callerIdPresentationItems,
            0,
            (dialog, id) -> {
              switch (id) {
                case 0:
                  callerIdPresentationChoice = TelecomManager.PRESENTATION_ALLOWED;
                  break;
                case 1:
                  callerIdPresentationChoice = TelecomManager.PRESENTATION_PAYPHONE;
                  break;
                case 2:
                  callerIdPresentationChoice = TelecomManager.PRESENTATION_RESTRICTED;
                  break;
                case 3:
                  callerIdPresentationChoice = TelecomManager.PRESENTATION_UNKNOWN;
                  break;
                default:
                  throw new IllegalStateException("Unknown presentation choice selected!");
              }
            })
        .setPositiveButton(
            R.string.call,
            (dialog, id) -> {
              dialogCallback.createCustomizedCall(
                  input.getText().toString(), callerIdPresentationChoice);
              dialog.cancel();
              SimulatorDialogFragment.this.dismiss();
            });
    AlertDialog dialog = builder.create();
    dialog.show();
    return dialog;
  }

  /** Callback for after clicking enter button on dialog. */
  public interface DialogCallback {
    void createCustomizedCall(String callerId, int callerIdPresentation);
  }
}
