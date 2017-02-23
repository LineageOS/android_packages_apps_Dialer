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
import android.support.v4.app.FragmentPagerAdapter;
import android.text.TextUtils;
import com.android.dialer.multimedia.MultimediaData;
import com.android.incallui.sessiondata.MultimediaFragment;

/** View pager adapter for in call ui. */
public class InCallPagerAdapter extends FragmentPagerAdapter {

  @Nullable private final MultimediaData attachments;

  public InCallPagerAdapter(FragmentManager fragmentManager, MultimediaData attachments) {
    super(fragmentManager);
    this.attachments = attachments;
  }

  @Override
  public Fragment getItem(int position) {
    if (position == getButtonGridPosition()) {
      return InCallButtonGridFragment.newInstance();
    } else {
      // TODO: handle fragment invalidation for when the data changes.
      return MultimediaFragment.newInstance(attachments, true, false);
    }
  }

  @Override
  public int getCount() {
    if (attachments != null
        && (!TextUtils.isEmpty(attachments.getSubject()) || attachments.hasImageData())) {
      return 2;
    }
    return 1;
  }

  public int getButtonGridPosition() {
    return getCount() - 1;
  }
}
