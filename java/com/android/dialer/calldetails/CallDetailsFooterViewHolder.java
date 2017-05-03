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

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import com.android.contacts.common.ClipboardUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.util.CallUtil;
import com.android.dialer.util.DialerUtils;

/** ViewHolder container for {@link CallDetailsActivity} footer. */
public class CallDetailsFooterViewHolder extends RecyclerView.ViewHolder
    implements OnClickListener {

  private final View container;
  private final View copy;
  private final View edit;

  private String number;

  public CallDetailsFooterViewHolder(View view) {
    super(view);
    container = view.findViewById(R.id.footer_container);
    copy = view.findViewById(R.id.call_detail_action_copy);
    edit = view.findViewById(R.id.call_detail_action_edit_before_call);

    copy.setOnClickListener(this);
    edit.setOnClickListener(this);
  }

  public void setPhoneNumber(String number) {
    this.number = number;
    if (TextUtils.isEmpty(number)) {
      container.setVisibility(View.GONE);
    }
  }

  @Override
  public void onClick(View view) {
    Context context = view.getContext();
    if (view == copy) {
      Logger.get(context).logImpression(DialerImpression.Type.CALL_DETAILS_COPY_NUMBER);
      ClipboardUtils.copyText(context, null, number, true);
    } else if (view == edit) {
      Logger.get(context).logImpression(DialerImpression.Type.CALL_DETAILS_EDIT_BEFORE_CALL);
      Intent dialIntent = new Intent(Intent.ACTION_DIAL, CallUtil.getCallUri(number));
      DialerUtils.startActivityWithErrorToast(context, dialIntent);
    } else {
      Assert.fail("View on click not implemented: " + view);
    }
  }
}
