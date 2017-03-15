/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.voicemail.impl.utils;

import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import com.android.voicemail.impl.OmtpVvmCarrierConfigHelper;
import com.android.voicemail.impl.VvmLog;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public class VvmDumpHandler {

  public static void dump(Context context, FileDescriptor fd, PrintWriter writer, String[] args) {
    IndentingPrintWriter indentedWriter = new IndentingPrintWriter(writer, "  ");
    indentedWriter.println("******* OmtpVvm *******");
    indentedWriter.println("======= Configs =======");
    indentedWriter.increaseIndent();
    for (PhoneAccountHandle handle :
        context.getSystemService(TelecomManager.class).getCallCapablePhoneAccounts()) {
      OmtpVvmCarrierConfigHelper config = new OmtpVvmCarrierConfigHelper(context, handle);
      indentedWriter.println(config.toString());
    }
    indentedWriter.decreaseIndent();
    indentedWriter.println("======== Logs =========");
    VvmLog.dump(fd, indentedWriter, args);
  }
}
