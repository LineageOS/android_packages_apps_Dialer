/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.dialer.dialpad;

import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.TestCase;

@SmallTest
public class SmartDialCacheTest extends TestCase {
    public void testIsCountryNanp_CaseInsensitive() {
        assertFalse(SmartDialCache.isCountryNanp(null));
        assertFalse(SmartDialCache.isCountryNanp("CN"));
        assertFalse(SmartDialCache.isCountryNanp("HK"));
        assertFalse(SmartDialCache.isCountryNanp("uk"));
        assertFalse(SmartDialCache.isCountryNanp("sg"));
        assertTrue(SmartDialCache.isCountryNanp("US"));
        assertTrue(SmartDialCache.isCountryNanp("CA"));
        assertTrue(SmartDialCache.isCountryNanp("AS"));
        assertTrue(SmartDialCache.isCountryNanp("AI"));
        assertTrue(SmartDialCache.isCountryNanp("AG"));
        assertTrue(SmartDialCache.isCountryNanp("BS"));
        assertTrue(SmartDialCache.isCountryNanp("BB"));
        assertTrue(SmartDialCache.isCountryNanp("bm"));
        assertTrue(SmartDialCache.isCountryNanp("vg"));
        assertTrue(SmartDialCache.isCountryNanp("ky"));
        assertTrue(SmartDialCache.isCountryNanp("dm"));
        assertTrue(SmartDialCache.isCountryNanp("do"));
        assertTrue(SmartDialCache.isCountryNanp("gd"));
        assertTrue(SmartDialCache.isCountryNanp("gu"));
        assertTrue(SmartDialCache.isCountryNanp("jm"));
        assertTrue(SmartDialCache.isCountryNanp("pr"));
        assertTrue(SmartDialCache.isCountryNanp("ms"));
        assertTrue(SmartDialCache.isCountryNanp("mp"));
        assertTrue(SmartDialCache.isCountryNanp("kn"));
        assertTrue(SmartDialCache.isCountryNanp("lc"));
        assertTrue(SmartDialCache.isCountryNanp("vc"));
        assertTrue(SmartDialCache.isCountryNanp("tt"));
        assertTrue(SmartDialCache.isCountryNanp("tc"));
        assertTrue(SmartDialCache.isCountryNanp("vi"));
    }
}
