/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.dialer.util;

import com.android.dialer.PhoneCallDetailsHelper;
import com.google.common.collect.Lists;

import android.content.Context;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;

/**
 * Performs tests of the DialerUtils class.
 */
@SmallTest
public class DialerUtilsTest extends AndroidTestCase {

    private Resources mResources;

    /**
     * List of items to be concatenated together for CharSequence join tests.
     */
    private ArrayList<CharSequence> mItems = Lists.newArrayList();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = getContext();
        mResources = context.getResources();
    }

    /**
     * Tests joining an empty list of {@link CharSequence}.
     */
    public void testJoinEmpty() {
        mItems.clear();
        CharSequence joined = DialerUtils.join(mResources, mItems);
        assertEquals("", joined);
    }

    /**
     * Tests joining a list of {@link CharSequence} with a single entry.
     */
    public void testJoinOne() {
        mItems.clear();
        mItems.add("Hello");
        CharSequence joined = DialerUtils.join(mResources, mItems);
        assertEquals("Hello", joined);
    }

    /**
     * Tests joining a list of {@link CharSequence} with a multiple entries.
     */
    public void testJoinTwo() {
        mItems.clear();
        mItems.add("Hello");
        mItems.add("there");
        CharSequence joined = DialerUtils.join(mResources, mItems);
        assertEquals("Hello, there", joined);
    }
}
