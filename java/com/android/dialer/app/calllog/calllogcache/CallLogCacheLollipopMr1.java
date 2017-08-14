/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.dialer.app.calllog.calllogcache;

import android.content.Context;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.util.ArrayMap;
import com.android.dialer.calllogutils.PhoneAccountUtils;
import com.android.dialer.telecom.TelecomUtil;
import java.util.Map;

/**
 * This is the CallLogCache for versions of dialer Lollipop Mr1 and above with support for multi-SIM
 * devices.
 *
 * <p>This class should not be initialized directly and instead be acquired from {@link
 * CallLogCache#getCallLogCache}.
 */
class CallLogCacheLollipopMr1 extends CallLogCache {

  private final Map<PhoneAccountHandle, String> mPhoneAccountLabelCache = new ArrayMap<>();
  private final Map<PhoneAccountHandle, Integer> mPhoneAccountColorCache = new ArrayMap<>();
  private final Map<PhoneAccountHandle, Boolean> mPhoneAccountCallWithNoteCache = new ArrayMap<>();

  /* package */ CallLogCacheLollipopMr1(Context context) {
    super(context);
  }

  @Override
  public void reset() {
    mPhoneAccountLabelCache.clear();
    mPhoneAccountColorCache.clear();
    mPhoneAccountCallWithNoteCache.clear();

    super.reset();
  }

  @Override
  public boolean isVoicemailNumber(
      PhoneAccountHandle accountHandle, @Nullable CharSequence number) {
    if (TextUtils.isEmpty(number)) {
      return false;
    }
    return TelecomUtil.isVoicemailNumber(mContext, accountHandle, number.toString());
  }

  @Override
  public String getAccountLabel(PhoneAccountHandle accountHandle) {
    if (mPhoneAccountLabelCache.containsKey(accountHandle)) {
      return mPhoneAccountLabelCache.get(accountHandle);
    } else {
      String label = PhoneAccountUtils.getAccountLabel(mContext, accountHandle);
      mPhoneAccountLabelCache.put(accountHandle, label);
      return label;
    }
  }

  @Override
  public int getAccountColor(PhoneAccountHandle accountHandle) {
    if (mPhoneAccountColorCache.containsKey(accountHandle)) {
      return mPhoneAccountColorCache.get(accountHandle);
    } else {
      Integer color = PhoneAccountUtils.getAccountColor(mContext, accountHandle);
      mPhoneAccountColorCache.put(accountHandle, color);
      return color;
    }
  }

  @Override
  public boolean doesAccountSupportCallSubject(PhoneAccountHandle accountHandle) {
    if (mPhoneAccountCallWithNoteCache.containsKey(accountHandle)) {
      return mPhoneAccountCallWithNoteCache.get(accountHandle);
    } else {
      Boolean supportsCallWithNote =
          PhoneAccountUtils.getAccountSupportsCallSubject(mContext, accountHandle);
      mPhoneAccountCallWithNoteCache.put(accountHandle, supportsCallWithNote);
      return supportsCallWithNote;
    }
  }
}
