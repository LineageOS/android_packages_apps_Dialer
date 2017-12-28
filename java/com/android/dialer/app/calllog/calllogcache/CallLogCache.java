/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.dialer.app.calllog.CallLogAdapter;
import com.android.dialer.calllogutils.PhoneAccountUtils;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.util.CallUtil;
import java.util.Map;
import javax.annotation.concurrent.ThreadSafe;

/**
 * This is the base class for the CallLogCaches.
 *
 * <p>Keeps a cache of recently made queries to the Telecom/Telephony processes. The aim of this
 * cache is to reduce the number of cross-process requests to TelecomManager, which can negatively
 * affect performance.
 *
 * <p>This is designed with the specific use case of the {@link CallLogAdapter} in mind.
 */
@ThreadSafe
public class CallLogCache {
  // TODO: Dialer should be fixed so as not to check isVoicemail() so often but at the time of
  // this writing, that was a much larger undertaking than creating this cache.

  protected final Context context;

  private boolean hasCheckedForVideoAvailability;
  private int videoAvailability;
  private final Map<PhoneAccountHandle, String> phoneAccountLabelCache = new ArrayMap<>();
  private final Map<PhoneAccountHandle, Integer> phoneAccountColorCache = new ArrayMap<>();
  private final Map<PhoneAccountHandle, Boolean> phoneAccountCallWithNoteCache = new ArrayMap<>();

  public CallLogCache(Context context) {
    this.context = context;
  }

  public synchronized void reset() {
    phoneAccountLabelCache.clear();
    phoneAccountColorCache.clear();
    phoneAccountCallWithNoteCache.clear();
    hasCheckedForVideoAvailability = false;
    videoAvailability = 0;
  }

  /**
   * Returns true if the given number is the number of the configured voicemail. To be able to
   * mock-out this, it is not a static method.
   */
  public synchronized boolean isVoicemailNumber(
      PhoneAccountHandle accountHandle, @Nullable CharSequence number) {
    if (TextUtils.isEmpty(number)) {
      return false;
    }
    return TelecomUtil.isVoicemailNumber(context, accountHandle, number.toString());
  }

  /**
   * Returns {@code true} when the current sim supports checking video calling capabilities via the
   * {@link android.provider.ContactsContract.CommonDataKinds.Phone#CARRIER_PRESENCE} column.
   */
  public boolean canRelyOnVideoPresence() {
    if (!hasCheckedForVideoAvailability) {
      videoAvailability = CallUtil.getVideoCallingAvailability(context);
      hasCheckedForVideoAvailability = true;
    }
    return (videoAvailability & CallUtil.VIDEO_CALLING_PRESENCE) != 0;
  }

  /** Extract account label from PhoneAccount object. */
  public synchronized String getAccountLabel(PhoneAccountHandle accountHandle) {
    if (phoneAccountLabelCache.containsKey(accountHandle)) {
      return phoneAccountLabelCache.get(accountHandle);
    } else {
      String label = PhoneAccountUtils.getAccountLabel(context, accountHandle);
      phoneAccountLabelCache.put(accountHandle, label);
      return label;
    }
  }

  /** Extract account color from PhoneAccount object. */
  public synchronized int getAccountColor(PhoneAccountHandle accountHandle) {
    if (phoneAccountColorCache.containsKey(accountHandle)) {
      return phoneAccountColorCache.get(accountHandle);
    } else {
      Integer color = PhoneAccountUtils.getAccountColor(context, accountHandle);
      phoneAccountColorCache.put(accountHandle, color);
      return color;
    }
  }

  /**
   * Determines if the PhoneAccount supports specifying a call subject (i.e. calling with a note)
   * for outgoing calls.
   *
   * @param accountHandle The PhoneAccount handle.
   * @return {@code true} if calling with a note is supported, {@code false} otherwise.
   */
  public synchronized boolean doesAccountSupportCallSubject(PhoneAccountHandle accountHandle) {
    if (phoneAccountCallWithNoteCache.containsKey(accountHandle)) {
      return phoneAccountCallWithNoteCache.get(accountHandle);
    } else {
      Boolean supportsCallWithNote =
          PhoneAccountUtils.getAccountSupportsCallSubject(context, accountHandle);
      phoneAccountCallWithNoteCache.put(accountHandle, supportsCallWithNote);
      return supportsCallWithNote;
    }
  }
}
