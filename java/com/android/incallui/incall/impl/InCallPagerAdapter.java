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

package com.android.incallui.incall.impl;

import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import com.android.dialer.multimedia.MultimediaData;
import com.android.incallui.sessiondata.MultimediaFragment;

/** View pager adapter for in call ui. */
public class InCallPagerAdapter extends FragmentStatePagerAdapter {

  @Nullable private MultimediaData attachments;

  public InCallPagerAdapter(FragmentManager fragmentManager, @Nullable MultimediaData attachments) {
    super(fragmentManager);
    this.attachments = attachments;
  }

  @Override
  public Fragment getItem(int position) {
    if (position == getButtonGridPosition()) {
      return InCallButtonGridFragment.newInstance();
    } else {
      // TODO: handle fragment invalidation for when the data changes.
      return MultimediaFragment.newInstance(attachments, true, false, false);
    }
  }

  @Override
  public int getCount() {
    if (attachments != null && attachments.hasData()) {
      return 2;
    }
    return 1;
  }

  public void setAttachments(@Nullable MultimediaData attachments) {
    if (this.attachments != attachments) {
      this.attachments = attachments;
      notifyDataSetChanged();
    }
  }

  public int getButtonGridPosition() {
    return getCount() - 1;
  }

  //this is called when notifyDataSetChanged() is called
  @Override
  public int getItemPosition(Object object) {
    // refresh all fragments when data set changed
    return PagerAdapter.POSITION_NONE;
  }
}
