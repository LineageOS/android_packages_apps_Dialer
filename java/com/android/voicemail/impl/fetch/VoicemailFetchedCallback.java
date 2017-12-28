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
package com.android.voicemail.impl.fetch;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.VoicemailContract.Voicemails;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.voicemail.impl.R;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.imap.VoicemailPayload;
import com.android.voicemail.impl.transcribe.TranscriptionService;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.commons.io.IOUtils;

/**
 * Callback for when a voicemail payload is fetched. It copies the returned stream to the data file
 * corresponding to the voicemail.
 */
public class VoicemailFetchedCallback {
  private static final String TAG = "VoicemailFetchedCallback";

  private final Context context;
  private final ContentResolver contentResolver;
  private final Uri uri;
  private final PhoneAccountHandle phoneAccountHandle;

  public VoicemailFetchedCallback(Context context, Uri uri, PhoneAccountHandle phoneAccountHandle) {
    this.context = context;
    contentResolver = context.getContentResolver();
    this.uri = uri;
    this.phoneAccountHandle = phoneAccountHandle;
  }

  /**
   * Saves the voicemail payload data into the voicemail provider then sets the "has_content" bit of
   * the voicemail to "1".
   *
   * @param voicemailPayload The object containing the content data for the voicemail
   */
  public void setVoicemailContent(@Nullable VoicemailPayload voicemailPayload) {
    Assert.isWorkerThread();
    if (voicemailPayload == null) {
      VvmLog.i(TAG, "Payload not found, message has unsupported format");
      ContentValues values = new ContentValues();
      values.put(
          Voicemails.TRANSCRIPTION,
          context.getString(
              R.string.vvm_unsupported_message_format,
              context
                  .getSystemService(TelecomManager.class)
                  .getVoiceMailNumber(phoneAccountHandle)));
      updateVoicemail(values);
      return;
    }

    VvmLog.d(TAG, String.format("Writing new voicemail content: %s", uri));
    OutputStream outputStream = null;

    try {
      outputStream = contentResolver.openOutputStream(uri);
      byte[] inputBytes = voicemailPayload.getBytes();
      if (inputBytes != null) {
        outputStream.write(inputBytes);
      }
    } catch (IOException e) {
      VvmLog.w(TAG, String.format("File not found for %s", uri));
      return;
    } finally {
      IOUtils.closeQuietly(outputStream);
    }

    // Update mime_type & has_content after we are done with file update.
    ContentValues values = new ContentValues();
    values.put(Voicemails.MIME_TYPE, voicemailPayload.getMimeType());
    values.put(Voicemails.HAS_CONTENT, true);
    if (updateVoicemail(values)) {
      ThreadUtil.postOnUiThread(
          () -> {
            if (!TranscriptionService.scheduleNewVoicemailTranscriptionJob(
                context, uri, phoneAccountHandle, true)) {
              VvmLog.w(TAG, String.format("Failed to schedule transcription for %s", uri));
            }
          });
    }
  }

  private boolean updateVoicemail(ContentValues values) {
    int updatedCount = contentResolver.update(uri, values, null, null);
    if (updatedCount != 1) {
      VvmLog.e(TAG, "Updating voicemail should have updated 1 row, was: " + updatedCount);
      return false;
    } else {
      return true;
    }
  }
}
