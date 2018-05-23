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
 * limitations under the License.
 */

package com.android.dialer.calldetails;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.phonenumbercache.CachedNumberLookupService;
import com.android.dialer.phonenumbercache.CachedNumberLookupService.CachedContactInfo;
import com.android.dialer.phonenumbercache.PhoneNumberCache;
import com.android.dialer.theme.base.ThemeComponent;

/** Dialog for reporting an inaccurate caller id information. */
public class ReportDialogFragment extends DialogFragment {

  private static final String KEY_NUMBER = "number";
  private TextView name;
  private TextView numberView;

  private CachedNumberLookupService cachedNumberLookupService;
  private CachedNumberLookupService.CachedContactInfo info;
  private String number;

  public static ReportDialogFragment newInstance(String number) {
    ReportDialogFragment fragment = new ReportDialogFragment();
    Bundle bundle = new Bundle();
    bundle.putString(KEY_NUMBER, number);
    fragment.setArguments(bundle);
    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    setRetainInstance(true);
    number = getArguments().getString(KEY_NUMBER);
    cachedNumberLookupService = PhoneNumberCache.get(getContext()).getCachedNumberLookupService();
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    LayoutInflater inflater = getActivity().getLayoutInflater();
    View view = inflater.inflate(R.layout.caller_id_report_dialog, null, false);
    name = view.findViewById(R.id.name);
    numberView = view.findViewById(R.id.number);

    lookupContactInfo(number);

    AlertDialog reportDialog =
        new AlertDialog.Builder(getActivity())
            .setTitle(R.string.report_caller_id_dialog_title)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> positiveClick(dialog))
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
            .setView(view)
            .create();

    reportDialog.setOnShowListener(dialog -> onShow(getContext(), reportDialog));
    return reportDialog;
  }

  private void positiveClick(DialogInterface dialog) {
    startReportCallerIdWorker();
    dialog.dismiss();
  }

  private static void onShow(Context context, AlertDialog dialog) {
    int buttonTextColor = ThemeComponent.get(context).theme().getColorPrimary();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonTextColor);
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonTextColor);
  }

  private void lookupContactInfo(String number) {
    Worker<String, CachedContactInfo> worker =
        number1 -> cachedNumberLookupService.lookupCachedContactFromNumber(getContext(), number1);
    SuccessListener<CachedContactInfo> successListener = this::setCachedContactInfo;
    DialerExecutorComponent.get(getContext())
        .dialerExecutorFactory()
        .createUiTaskBuilder(getFragmentManager(), "lookup_contact_info", worker)
        .onSuccess(successListener)
        .build()
        .executeParallel(number);
  }

  private void setCachedContactInfo(CachedContactInfo info) {
    this.info = info;
    if (info != null) {
      name.setText(info.getContactInfo().name);
      numberView.setText(info.getContactInfo().number);
    } else {
      numberView.setText(number);
      name.setVisibility(View.GONE);
    }
  }

  private void startReportCallerIdWorker() {
    Worker<Context, Pair<Context, Boolean>> worker = this::reportCallerId;
    SuccessListener<Pair<Context, Boolean>> successListener = this::onReportCallerId;
    DialerExecutorComponent.get(getContext())
        .dialerExecutorFactory()
        .createUiTaskBuilder(getFragmentManager(), "report_caller_id", worker)
        .onSuccess(successListener)
        .build()
        .executeParallel(getActivity());
  }

  private Pair<Context, Boolean> reportCallerId(Context context) {
    if (cachedNumberLookupService.reportAsInvalid(context, info)) {
      info.getContactInfo().isBadData = true;
      cachedNumberLookupService.addContact(context, info);
      LogUtil.d("ReportUploadTask.doInBackground", "Contact reported.");
      return new Pair<>(context, true);
    } else {
      return new Pair<>(context, false);
    }
  }

  private void onReportCallerId(Pair<Context, Boolean> output) {
    Context context = output.first;
    boolean wasReport = output.second;
    if (wasReport) {
      Logger.get(context).logImpression(DialerImpression.Type.CALLER_ID_REPORTED);
      Toast.makeText(context, R.string.report_caller_id_toast, Toast.LENGTH_SHORT).show();
    } else {
      Logger.get(context).logImpression(DialerImpression.Type.CALLER_ID_REPORT_FAILED);
      Toast.makeText(context, R.string.report_caller_id_failed, Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onDestroyView() {
    if (getDialog() != null && getRetainInstance()) {
      // Prevent dialog from dismissing on rotate.
      getDialog().setDismissMessage(null);
    }
    super.onDestroyView();
  }
}
