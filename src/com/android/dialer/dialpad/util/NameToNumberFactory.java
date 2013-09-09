/*
 * Copyright (C) 2011 The CyanogenMod Project
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

package com.android.dialer.dialpad.util;

import android.content.Context;

import java.util.Locale;

/**
 * @author barami
 * Return a instance of NameToNumber class or inherited classes.
 *
 * Default instance is NameToNumber class that acts like previous nameToNumber function of T9Search class.
 * This will be added to support complex T9 search. (ex: In a hangul, '가나다' will be searched by 'ㄱ', 'ㄴ', 'ㄱㄴ', etc.)
 */
public class NameToNumberFactory {
    public static NameLatinizer create(Context context, final String t9Chars, final String t9Digits) {
        Locale lc = context.getResources().getConfiguration().locale;

        // Check locale and returns matched class inherited from NameToNumber class.
        NameLatinizer instance = null;
        if (lc.getLanguage().equalsIgnoreCase("ko")) {
            instance = new NameToNumberKorean(t9Chars, t9Digits);
        } else if (lc.equals(Locale.CHINA)) {
            instance = new NameToNumberChinese(t9Chars, t9Digits);
        }

        return instance;
    }
}
