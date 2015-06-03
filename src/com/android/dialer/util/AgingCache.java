/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.android.dialer.util;

import android.os.SystemClock;
import android.util.ArrayMap;
import android.util.Pair;

public class AgingCache<K, V> {
    private ArrayMap<K, Pair<V, Long>> mCache;
    private long mMaxAge;

    public AgingCache(long maxAge) {
        mCache = new ArrayMap<>();
        mMaxAge = maxAge;
    }

    public V get(K key) {
        Pair<V, Long> entry = mCache.get(key);
        if (entry != null) {
            long age = SystemClock.elapsedRealtime() - entry.second;
            if (age < mMaxAge) {
                return entry.first;
            }
        }
        return null;
    }

    public void put(K key, V value) {
        mCache.put(key, Pair.create(value, SystemClock.elapsedRealtime()));
    }

    public void clear() {
        mCache.clear();
    }
}
