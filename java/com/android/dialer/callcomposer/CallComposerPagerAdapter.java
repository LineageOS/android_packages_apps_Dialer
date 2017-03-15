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
 * limitations under the License.
 */

package com.android.dialer.callcomposer;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import com.android.dialer.common.Assert;

/** ViewPager adapter for call compose UI. */
public class CallComposerPagerAdapter extends FragmentPagerAdapter {

  public static final int INDEX_CAMERA = 0;
  public static final int INDEX_GALLERY = 1;
  public static final int INDEX_MESSAGE = 2;

  private final int messageComposerCharLimit;

  public CallComposerPagerAdapter(FragmentManager fragmentManager, int messageComposerCharLimit) {
    super(fragmentManager);
    this.messageComposerCharLimit = messageComposerCharLimit;
  }

  @Override
  public Fragment getItem(int position) {
    switch (position) {
      case INDEX_MESSAGE:
        return MessageComposerFragment.newInstance(messageComposerCharLimit);
      case INDEX_GALLERY:
        return GalleryComposerFragment.newInstance();
      case INDEX_CAMERA:
        return new CameraComposerFragment();
      default:
        Assert.fail();
        return null;
    }
  }

  @Override
  public int getCount() {
    return 3;
  }
}
