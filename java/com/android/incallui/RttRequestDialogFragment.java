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

package com.android.incallui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.contacts.ContactsComponent;
import com.android.incallui.ContactInfoCache.ContactCacheEntry;
import com.android.incallui.ContactInfoCache.ContactInfoCacheCallback;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import java.lang.ref.WeakReference;

/** Dialog that shown to user when receiving RTT request mid call. */
public class RttRequestDialogFragment extends DialogFragment {

  /**
   * Returns a new instance of {@link RttRequestDialogFragment} with the given callback.
   *
   * <p>Prefer this method over the default constructor.
   */
  public static RttRequestDialogFragment newInstance(String callId, int rttRequestId) {
    RttRequestDialogFragment fragment = new RttRequestDialogFragment();
    Bundle args = new Bundle();
    args.putString(ARG_CALL_ID, Assert.isNotNull(callId));
    args.putInt(ARG_RTT_REQUEST_ID, rttRequestId);
    fragment.setArguments(args);
    return fragment;
  }

  /** Key in the arguments bundle for call id. */
  private static final String ARG_CALL_ID = "call_id";

  private static final String ARG_RTT_REQUEST_ID = "rtt_request_id";

  private TextView detailsTextView;

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle bundle) {
    super.onCreateDialog(bundle);
    LogUtil.enterBlock("RttRequestDialogFragment.onCreateDialog");

    View dialogView = View.inflate(getActivity(), R.layout.frag_rtt_request_dialog, null);
    detailsTextView = dialogView.findViewById(R.id.details);

    ContactInfoCache cache = ContactInfoCache.getInstance(getContext());
    DialerCall dialerCall =
        CallList.getInstance().getCallById(getArguments().getString(ARG_CALL_ID));
    cache.findInfo(dialerCall, false, new ContactLookupCallback(this));

    dialogView
        .findViewById(R.id.rtt_button_decline_request)
        .setOnClickListener(v -> onNegativeButtonClick());
    dialogView
        .findViewById(R.id.rtt_button_accept_request)
        .setOnClickListener(v -> onPositiveButtonClick());

    AlertDialog alertDialog =
        new AlertDialog.Builder(getActivity())
            .setCancelable(false)
            .setView(dialogView)
            .setTitle(R.string.rtt_request_dialog_title)
            .create();

    alertDialog.setCanceledOnTouchOutside(false);
    return alertDialog;
  }

  private void onPositiveButtonClick() {
    LogUtil.enterBlock("RttRequestDialogFragment.onPositiveButtonClick");

    DialerCall call = CallList.getInstance().getCallById(getArguments().getString(ARG_CALL_ID));
    call.respondToRttRequest(true, getArguments().getInt(ARG_RTT_REQUEST_ID));
    dismiss();
  }

  private void onNegativeButtonClick() {
    LogUtil.enterBlock("RttRequestDialogFragment.onNegativeButtonClick");

    DialerCall call = CallList.getInstance().getCallById(getArguments().getString(ARG_CALL_ID));
    call.respondToRttRequest(false, getArguments().getInt(ARG_RTT_REQUEST_ID));
    dismiss();
  }

  private void setNameOrNumber(CharSequence nameOrNumber) {
    detailsTextView.setText(getString(R.string.rtt_request_dialog_details, nameOrNumber));
  }

  private static class ContactLookupCallback implements ContactInfoCacheCallback {
    private final WeakReference<RttRequestDialogFragment> rttRequestDialogFragmentWeakReference;

    private ContactLookupCallback(RttRequestDialogFragment rttRequestDialogFragment) {
      rttRequestDialogFragmentWeakReference = new WeakReference<>(rttRequestDialogFragment);
    }

    @Override
    public void onContactInfoComplete(String callId, ContactCacheEntry entry) {
      RttRequestDialogFragment fragment = rttRequestDialogFragmentWeakReference.get();
      if (fragment != null) {
        fragment.setNameOrNumber(getNameOrNumber(entry, fragment.getContext()));
      }
    }

    private CharSequence getNameOrNumber(ContactCacheEntry entry, Context context) {
      String preferredName =
          ContactsComponent.get(context)
              .contactDisplayPreferences()
              .getDisplayName(entry.namePrimary, entry.nameAlternative);
      if (TextUtils.isEmpty(preferredName)) {
        return TextUtils.isEmpty(entry.number)
            ? null
            : PhoneNumberUtils.createTtsSpannable(
                BidiFormatter.getInstance().unicodeWrap(entry.number, TextDirectionHeuristics.LTR));
      }
      return preferredName;
    }

    @Override
    public void onImageLoadComplete(String callId, ContactCacheEntry entry) {}
  }
}
