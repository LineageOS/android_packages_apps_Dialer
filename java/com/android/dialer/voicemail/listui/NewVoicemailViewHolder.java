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
package com.android.dialer.voicemail.listui;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.voicemail.model.VoicemailEntry;

/** {@link RecyclerView.ViewHolder} for the new voicemail tab. */
final class NewVoicemailViewHolder extends RecyclerView.ViewHolder {

  private final Context context;
  private final TextView primaryTextView;
  private final QuickContactBadge quickContactBadge;

  NewVoicemailViewHolder(View view) {
    super(view);
    this.context = view.getContext();
    primaryTextView = (TextView) view.findViewById(R.id.primary_text);
    quickContactBadge = view.findViewById(R.id.quick_contact_photo);
  }

  void bind(Cursor cursor) {
    VoicemailEntry voicemailEntry = VoicemailCursorLoader.toVoicemailEntry(cursor);
    primaryTextView.setText(VoicemailEntryText.buildPrimaryVoicemailText(context, voicemailEntry));
    setPhoto(voicemailEntry);
  }

  // TODO(uabdullah): Consider/Implement TYPE (e.g Spam, TYPE_VOICEMAIL)
  private void setPhoto(VoicemailEntry voicemailEntry) {
    ContactPhotoManager.getInstance(context)
        .loadDialerThumbnailOrPhoto(
            quickContactBadge,
            voicemailEntry.lookupUri() == null ? null : Uri.parse(voicemailEntry.lookupUri()),
            voicemailEntry.photoId(),
            voicemailEntry.photoUri() == null ? null : Uri.parse(voicemailEntry.photoUri()),
            voicemailEntry.name(),
            LetterTileDrawable.TYPE_DEFAULT);
  }
}
