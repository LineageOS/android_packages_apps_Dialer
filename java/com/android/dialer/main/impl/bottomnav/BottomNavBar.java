/*
 * Copyright (C) 2018 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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
import android.util.AttributeSet;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.android.dialer.R;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.main.impl.MainActivity;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/** Dialer Bottom Nav Bar for {@link MainActivity}. */
public final class BottomNavBar extends BottomNavigationView {

  /** Index for each tab in the bottom nav. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    TabIndex.NONE,
    TabIndex.SPEED_DIAL,
    TabIndex.CALL_LOG,
    TabIndex.CONTACTS,
    TabIndex.VOICEMAIL,
  })
  public @interface TabIndex {
    int NONE = -1;
    int SPEED_DIAL = 0;
    int CALL_LOG = 1;
    int CONTACTS = 2;
    int VOICEMAIL = 3;
  }

  private final List<OnBottomNavTabSelectedListener> listeners = new ArrayList<>();

  private Runnable openKeypad = () -> {};
  private Runnable closeKeypad = () -> {};
  private @TabIndex int selectedTab;

  public BottomNavBar(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    setOnItemSelectedListener(
        item -> {
          if (item.getItemId() == R.id.keypad_tab) {
            openKeypad.run();
            return false;
          }
          closeKeypad.run();
          selectTab(item.getItemId() == R.id.voicemail_tab ? TabIndex.VOICEMAIL : TabIndex.CALL_LOG);
          return true;
        });
  }

  public void setKeypadActions(Runnable open, Runnable close) {
    openKeypad = open;
    closeKeypad = close;
  }

  public void setKeypadSelected(boolean selected) {
    getMenu().findItem(selected ? R.id.keypad_tab : getTabId(selectedTab)).setChecked(true);
  }

  public boolean isKeypadSelected() {
    return getSelectedItemId() == R.id.keypad_tab;
  }

  /**
   * Select tab for uesr and non-user click.
   *
   * @param tab {@link TabIndex}
   */
  public void selectTab(@TabIndex int tab) {
    if (tab == TabIndex.NONE) {
      throw new IllegalStateException("Invalid tab: " + tab);
    }
    selectedTab = tab == TabIndex.VOICEMAIL ? TabIndex.VOICEMAIL : TabIndex.CALL_LOG;
    getMenu().findItem(getTabId(selectedTab)).setChecked(true);
    updateListeners(selectedTab);
  }

  /**
   * Displays or hides the voicemail tab.
   *
   * <p>In the event that the voicemail tab was earlier visible but is now no longer visible, we
   * move to the call log tab.
   *
   * @param showTab whether to hide or show the voicemail
   */
  public void showVoicemail(boolean showTab) {
    LogUtil.i("OldMainActivityPeer.showVoicemail", "showing Tab:%b", showTab);
    getMenu().findItem(R.id.voicemail_tab).setVisible(showTab);
    if (!showTab && selectedTab == TabIndex.VOICEMAIL) {
      selectTab(TabIndex.CALL_LOG);
    }
  }

  public void setNotificationCount(@TabIndex int tab, int count) {
    BadgeDrawable badge = getOrCreateBadge(getTabId(tab));
    badge.setNumber(count);
    badge.setVisible(count > 0);
  }

  private static int getTabId(@TabIndex int tab) {
    return tab == TabIndex.VOICEMAIL ? R.id.voicemail_tab : R.id.call_log_tab;
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
