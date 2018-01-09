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
 * limitations under the License
 */

package com.android.dialer.main.impl;

import android.content.Context;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Dialer Bottom Nav Bar for {@link MainActivity}. */
final class BottomNavBar extends LinearLayout {

  /** Index for each tab in the bottom nav. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    TabIndex.SPEED_DIAL,
    TabIndex.HISTORY,
    TabIndex.CONTACTS,
    TabIndex.VOICEMAIL,
  })
  public @interface TabIndex {
    int SPEED_DIAL = 0;
    int HISTORY = 1;
    int CONTACTS = 2;
    int VOICEMAIL = 3;
  }

  private BottomNavItem speedDial;
  private BottomNavItem callLog;
  private BottomNavItem contacts;
  private BottomNavItem voicemail;
  private OnBottomNavTabSelectedListener listener;

  public BottomNavBar(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    speedDial = findViewById(R.id.speed_dial_tab);
    callLog = findViewById(R.id.call_log_tab);
    contacts = findViewById(R.id.contacts_tab);
    voicemail = findViewById(R.id.voicemail_tab);

    speedDial.setup(R.string.tab_title_speed_dial, R.drawable.quantum_ic_star_vd_theme_24);
    callLog.setup(R.string.tab_title_call_history, R.drawable.quantum_ic_history_vd_theme_24);
    contacts.setup(R.string.tab_title_contacts, R.drawable.quantum_ic_people_vd_theme_24);
    voicemail.setup(R.string.tab_title_voicemail, R.drawable.quantum_ic_voicemail_vd_theme_24);

    speedDial.setOnClickListener(
        v -> {
          setSelected(speedDial);
          listener.onSpeedDialSelected();
        });
    callLog.setOnClickListener(
        v -> {
          setSelected(callLog);
          listener.onCallLogSelected();
        });
    contacts.setOnClickListener(
        v -> {
          setSelected(contacts);
          listener.onContactsSelected();
        });
    voicemail.setOnClickListener(
        v -> {
          setSelected(voicemail);
          listener.onVoicemailSelected();
        });
  }

  private void setSelected(View view) {
    speedDial.setSelected(view == speedDial);
    callLog.setSelected(view == callLog);
    contacts.setSelected(view == contacts);
    voicemail.setSelected(view == voicemail);
  }

  /**
   * Calls {@link View#performClick()} on the desired tab.
   *
   * @param tab {@link TabIndex}
   */
  void selectTab(@TabIndex int tab) {
    if (tab == TabIndex.SPEED_DIAL) {
      speedDial.performClick();
    } else if (tab == TabIndex.HISTORY) {
      callLog.performClick();
    } else if (tab == TabIndex.CONTACTS) {
      contacts.performClick();
    } else if (tab == TabIndex.VOICEMAIL) {
      voicemail.performClick();
    } else {
      throw new IllegalStateException("Invalid tab: " + tab);
    }
  }

  void setOnTabSelectedListener(OnBottomNavTabSelectedListener listener) {
    this.listener = listener;
  }

  /** Listener for bottom nav tab's on click events. */
  public interface OnBottomNavTabSelectedListener {

    /** Speed dial tab was clicked. */
    void onSpeedDialSelected();

    /** Call Log tab was clicked. */
    void onCallLogSelected();

    /** Contacts tab was clicked. */
    void onContactsSelected();

    /** Voicemail tab was clicked. */
    void onVoicemailSelected();
  }
}
