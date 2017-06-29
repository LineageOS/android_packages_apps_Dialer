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

package com.android.dialer.app.list;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.support.annotation.IntDef;
import android.support.v13.app.FragmentPagerAdapter;
import android.view.ViewGroup;
import com.android.dialer.app.calllog.CallLogFragment;
import com.android.dialer.app.calllog.VisualVoicemailCallLogFragment;
import com.android.dialer.calllog.CallLogComponent;
import com.android.dialer.calllog.CallLogFramework;
import com.android.dialer.calllog.ui.NewCallLogFragment;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.configprovider.ConfigProviderBindings;
import com.android.dialer.contactsfragment.ContactsFragment;
import com.android.dialer.database.CallLogQueryHandler;
import com.android.dialer.speeddial.SpeedDialFragment;
import com.android.dialer.util.ViewUtil;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** ViewPager adapter for {@link com.android.dialer.app.DialtactsActivity}. */
public class DialtactsPagerAdapter extends FragmentPagerAdapter {

  /** IntDef for indices of ViewPager tabs. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TAB_INDEX_SPEED_DIAL, TAB_INDEX_HISTORY, TAB_INDEX_ALL_CONTACTS, TAB_INDEX_VOICEMAIL})
  public @interface TabIndex {}

  public static final int TAB_INDEX_SPEED_DIAL = 0;
  public static final int TAB_INDEX_HISTORY = 1;
  public static final int TAB_INDEX_ALL_CONTACTS = 2;
  public static final int TAB_INDEX_VOICEMAIL = 3;
  public static final int TAB_COUNT_DEFAULT = 3;
  public static final int TAB_COUNT_WITH_VOICEMAIL = 4;

  private final List<Fragment> fragments = new ArrayList<>();
  private final String[] tabTitles;
  private final boolean useNewSpeedDialTab;
  private final boolean useNewCallLogTab;
  private final boolean useNewContactsTab;
  private OldSpeedDialFragment oldSpeedDialFragment;
  private SpeedDialFragment speedDialFragment;
  private CallLogFragment callLogFragment;
  private NewCallLogFragment newCallLogFragment;
  private AllContactsFragment oldContactsFragment;
  private ContactsFragment contactsFragment;
  private CallLogFragment voicemailFragment;

  public boolean hasActiveVoicemailProvider;

  public DialtactsPagerAdapter(
      Context context, FragmentManager fm, String[] tabTitles, boolean hasVoicemailProvider) {
    super(fm);
    useNewSpeedDialTab =
        ConfigProviderBindings.get(context).getBoolean("enable_new_favorites_tab", false);
    CallLogFramework callLogFramework = CallLogComponent.get(context).callLogFramework();
    useNewCallLogTab = callLogFramework.isNewCallLogEnabled(context);
    useNewContactsTab =
        ConfigProviderBindings.get(context).getBoolean("enable_new_contacts_tab", true);
    this.tabTitles = tabTitles;
    hasActiveVoicemailProvider = hasVoicemailProvider;
    fragments.addAll(Collections.nCopies(TAB_COUNT_WITH_VOICEMAIL, null));
  }

  @Override
  public long getItemId(int position) {
    return getRtlPosition(position);
  }

  @Override
  public Fragment getItem(int position) {
    LogUtil.d("ViewPagerAdapter.getItem", "position: %d", position);
    switch (getRtlPosition(position)) {
      case TAB_INDEX_SPEED_DIAL:
        if (useNewSpeedDialTab) {
          if (speedDialFragment == null) {
            speedDialFragment = SpeedDialFragment.newInstance();
          }
          return speedDialFragment;
        } else {
          if (oldSpeedDialFragment == null) {
            oldSpeedDialFragment = new OldSpeedDialFragment();
          }
          return oldSpeedDialFragment;
        }
      case TAB_INDEX_HISTORY:
        if (useNewCallLogTab) {
          if (newCallLogFragment == null) {
            newCallLogFragment = new NewCallLogFragment();
          }
          return newCallLogFragment;
        } else {
          if (callLogFragment == null) {
            callLogFragment = new CallLogFragment(CallLogQueryHandler.CALL_TYPE_ALL);
          }
          return callLogFragment;
        }
      case TAB_INDEX_ALL_CONTACTS:
        if (useNewContactsTab) {
          if (contactsFragment == null) {
            contactsFragment = new ContactsFragment();
          }
          return contactsFragment;
        } else {
          if (oldContactsFragment == null) {
            oldContactsFragment = new AllContactsFragment();
          }
          return oldContactsFragment;
        }
      case TAB_INDEX_VOICEMAIL:
        if (voicemailFragment == null) {
          voicemailFragment = new VisualVoicemailCallLogFragment();
          LogUtil.v(
              "ViewPagerAdapter.getItem",
              "new VisualVoicemailCallLogFragment: %s",
              voicemailFragment);
        }
        return voicemailFragment;
      default:
        throw Assert.createIllegalStateFailException("No fragment at position " + position);
    }
  }

  @Override
  public Fragment instantiateItem(ViewGroup container, int position) {
    LogUtil.d("ViewPagerAdapter.instantiateItem", "position: %d", position);
    // On rotation the FragmentManager handles rotation. Therefore getItem() isn't called.
    // Copy the fragments that the FragmentManager finds so that we can store them in
    // instance variables for later.
    final Fragment fragment = (Fragment) super.instantiateItem(container, position);
    if (fragment instanceof OldSpeedDialFragment) {
      oldSpeedDialFragment = (OldSpeedDialFragment) fragment;
    } else if (fragment instanceof SpeedDialFragment) {
      speedDialFragment = (SpeedDialFragment) fragment;
    } else if (fragment instanceof CallLogFragment && position == TAB_INDEX_HISTORY) {
      callLogFragment = (CallLogFragment) fragment;
    } else if (fragment instanceof NewCallLogFragment) {
      newCallLogFragment = (NewCallLogFragment) fragment;
    } else if (fragment instanceof ContactsFragment) {
      contactsFragment = (ContactsFragment) fragment;
    } else if (fragment instanceof AllContactsFragment) {
      oldContactsFragment = (AllContactsFragment) fragment;
    } else if (fragment instanceof CallLogFragment && position == TAB_INDEX_VOICEMAIL) {
      voicemailFragment = (CallLogFragment) fragment;
      LogUtil.v("ViewPagerAdapter.instantiateItem", voicemailFragment.toString());
    }
    fragments.set(position, fragment);
    return fragment;
  }

  /**
   * When {@link android.support.v4.view.PagerAdapter#notifyDataSetChanged} is called, this method
   * is called on all pages to determine whether they need to be recreated. When the voicemail tab
   * is removed, the view needs to be recreated by returning POSITION_NONE. If notifyDataSetChanged
   * is called for some other reason, the voicemail tab is recreated only if it is active. All other
   * tabs do not need to be recreated and POSITION_UNCHANGED is returned.
   */
  @Override
  public int getItemPosition(Object object) {
    return !hasActiveVoicemailProvider && fragments.indexOf(object) == TAB_INDEX_VOICEMAIL
        ? POSITION_NONE
        : POSITION_UNCHANGED;
  }

  @Override
  public int getCount() {
    return hasActiveVoicemailProvider ? TAB_COUNT_WITH_VOICEMAIL : TAB_COUNT_DEFAULT;
  }

  @Override
  public CharSequence getPageTitle(@TabIndex int position) {
    return tabTitles[position];
  }

  public int getRtlPosition(int position) {
    if (ViewUtil.isRtl()) {
      return getCount() - 1 - position;
    }
    return position;
  }

  public void removeVoicemailFragment(FragmentManager manager) {
    if (voicemailFragment != null) {
      manager.beginTransaction().remove(voicemailFragment).commitAllowingStateLoss();
      voicemailFragment = null;
    }
  }

  public boolean hasActiveVoicemailProvider() {
    return hasActiveVoicemailProvider;
  }

  public void setHasActiveVoicemailProvider(boolean hasActiveVoicemailProvider) {
    this.hasActiveVoicemailProvider = hasActiveVoicemailProvider;
  }
}
