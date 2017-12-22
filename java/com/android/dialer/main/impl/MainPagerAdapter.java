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

package com.android.dialer.main.impl;

import android.content.Context;
import android.support.annotation.IntDef;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import com.android.dialer.calllog.ui.NewCallLogFragment;
import com.android.dialer.common.Assert;
import com.android.dialer.voicemail.listui.NewVoicemailFragment;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Adapter for {@link MainActivity} ViewPager. */
final class MainPagerAdapter extends FragmentPagerAdapter {

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    TabIndex.SPEED_DIAL,
    TabIndex.HISTORY,
    TabIndex.VOICEMAIL,
  })
  private @interface TabIndex {
    int SPEED_DIAL = 0;
    int HISTORY = 1;
    int VOICEMAIL = 2;
  }

  private final Context context;

  MainPagerAdapter(Context context, FragmentManager fragmentManager) {
    super(fragmentManager);
    this.context = context;
  }

  @Override
  public int getCount() {
    // TODO(calderwoodra): add logic to hide/show voicemail tab
    return 3;
  }

  @Override
  public Fragment getItem(@TabIndex int position) {
    // TODO(calderwoodra): implement tabs
    switch (position) {
      case TabIndex.VOICEMAIL:
        return new NewVoicemailFragment();
      case TabIndex.HISTORY:
        return new NewCallLogFragment();
      default:
        return new StubFragment();
    }
  }

  @Override
  public CharSequence getPageTitle(int position) {
    switch (position) {
      case TabIndex.SPEED_DIAL:
        return context.getString(R.string.tab_title_speed_dial);
      case TabIndex.HISTORY:
        return context.getString(R.string.tab_title_call_history);
      case TabIndex.VOICEMAIL:
        return context.getString(R.string.tab_title_voicemail);
      default:
        throw Assert.createIllegalStateFailException("Tab position with no title: " + position);
    }
  }
}
