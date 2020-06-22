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

package com.android.dialer.main.impl.bottomnav;

import android.content.Context;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/** Dialer Bottom Nav Bar for {@link MainActivity}. */
public final class BottomNavBar extends LinearLayout {

  /** Index for each tab in the bottom nav. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    TabIndex.SPEED_DIAL,
    TabIndex.CALL_LOG,
    TabIndex.CONTACTS,
    TabIndex.VOICEMAIL,
  })
  public @interface TabIndex {
    int SPEED_DIAL = 0;
    int CALL_LOG = 1;
    int CONTACTS = 2;
    int VOICEMAIL = 3;
  }

  private final List<OnBottomNavTabSelectedListener> listeners = new ArrayList<>();

  private BottomNavItem speedDial;
  private BottomNavItem callLog;
  private BottomNavItem contacts;
  private BottomNavItem voicemail;
  private @TabIndex int selectedTab;

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
    callLog.setup(R.string.tab_title_call_history, R.drawable.quantum_ic_access_time_vd_theme_24);
    contacts.setup(R.string.tab_all_contacts, R.drawable.quantum_ic_people_vd_theme_24);
    voicemail.setup(R.string.tab_title_voicemail, R.drawable.quantum_ic_voicemail_vd_theme_24);

    speedDial.setOnClickListener(
        v -> {
          if (selectedTab != TabIndex.SPEED_DIAL) {
            Logger.get(getContext())
                .logImpression(DialerImpression.Type.MAIN_SWITCH_TAB_TO_FAVORITE);
          }
          selectTab(TabIndex.SPEED_DIAL);
        });
    callLog.setOnClickListener(
        v -> {
          if (selectedTab != TabIndex.CALL_LOG) {
            Logger.get(getContext())
                .logImpression(DialerImpression.Type.MAIN_SWITCH_TAB_TO_CALL_LOG);
          }
          selectTab(TabIndex.CALL_LOG);
        });
    contacts.setOnClickListener(
        v -> {
          if (selectedTab != TabIndex.CONTACTS) {
            Logger.get(getContext())
                .logImpression(DialerImpression.Type.MAIN_SWITCH_TAB_TO_CONTACTS);
          }
          selectTab(TabIndex.CONTACTS);
        });
    voicemail.setOnClickListener(
        v -> {
          if (selectedTab != TabIndex.VOICEMAIL) {
            Logger.get(getContext())
                .logImpression(DialerImpression.Type.MAIN_SWITCH_TAB_TO_VOICEMAIL);
          }
          selectTab(TabIndex.VOICEMAIL);
        });
  }

  private void setSelected(View view) {
    speedDial.setSelected(view == speedDial);
    callLog.setSelected(view == callLog);
    contacts.setSelected(view == contacts);
    voicemail.setSelected(view == voicemail);
  }

  /**
   * Select tab for uesr and non-user click.
   *
   * @param tab {@link TabIndex}
   */
  public void selectTab(@TabIndex int tab) {
    if (tab == TabIndex.SPEED_DIAL) {
      selectedTab = TabIndex.SPEED_DIAL;
      setSelected(speedDial);
    } else if (tab == TabIndex.CALL_LOG) {
      selectedTab = TabIndex.CALL_LOG;
      setSelected(callLog);
    } else if (tab == TabIndex.CONTACTS) {
      selectedTab = TabIndex.CONTACTS;
      setSelected(contacts);
    } else if (tab == TabIndex.VOICEMAIL) {
      selectedTab = TabIndex.VOICEMAIL;
      setSelected(voicemail);
    } else {
      throw new IllegalStateException("Invalid tab: " + tab);
    }

    updateListeners(selectedTab);
  }

  /**
   * Displays or hides the voicemail tab.
   *
   * <p>In the event that the voicemail tab was earlier visible but is now no longer visible, we
   * move to the speed dial tab.
   *
   * @param showTab whether to hide or show the voicemail
   */
  public void showVoicemail(boolean showTab) {
    LogUtil.i("OldMainActivityPeer.showVoicemail", "showing Tab:%b", showTab);
    int voicemailpreviousVisibility = voicemail.getVisibility();
    voicemail.setVisibility(showTab ? View.VISIBLE : View.GONE);
    int voicemailcurrentVisibility = voicemail.getVisibility();

    if (voicemailpreviousVisibility != voicemailcurrentVisibility
        && voicemailpreviousVisibility == View.VISIBLE
        && getSelectedTab() == TabIndex.VOICEMAIL) {
      LogUtil.i("OldMainActivityPeer.showVoicemail", "hid VM tab and moved to speed dial tab");
      selectTab(TabIndex.SPEED_DIAL);
    }
  }

  public void setNotificationCount(@TabIndex int tab, int count) {
    if (tab == TabIndex.SPEED_DIAL) {
      speedDial.setNotificationCount(count);
    } else if (tab == TabIndex.CALL_LOG) {
      callLog.setNotificationCount(count);
    } else if (tab == TabIndex.CONTACTS) {
      contacts.setNotificationCount(count);
    } else if (tab == TabIndex.VOICEMAIL) {
      voicemail.setNotificationCount(count);
    } else {
      throw new IllegalStateException("Invalid tab: " + tab);
    }
  }

  public void addOnTabSelectedListener(OnBottomNavTabSelectedListener listener) {
    listeners.add(listener);
  }

  private void updateListeners(@TabIndex int tabIndex) {
    for (OnBottomNavTabSelectedListener listener : listeners) {
      switch (tabIndex) {
        case TabIndex.SPEED_DIAL:
          listener.onSpeedDialSelected();
          break;
        case TabIndex.CALL_LOG:
          listener.onCallLogSelected();
          break;
        case TabIndex.CONTACTS:
          listener.onContactsSelected();
          break;
        case TabIndex.VOICEMAIL:
          listener.onVoicemailSelected();
          break;
        default:
          throw Assert.createIllegalStateFailException("Invalid tab: " + tabIndex);
      }
    }
  }

  @TabIndex
  public int getSelectedTab() {
    return selectedTab;
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
