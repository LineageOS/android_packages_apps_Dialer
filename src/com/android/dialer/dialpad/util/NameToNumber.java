package com.android.dialer.dialpad.util;

import android.util.Log;

/**
 * @author Barami
 * Default implements of normalization for alphabet search.
 * Other languages need additional work for search may inherite this and overriding convert function.
 */
public class NameToNumber {
    protected String t9Chars;
    protected String t9Digits;

    // Work is based t9 characters and digits map.
    public NameToNumber(final String t9Chars, final String t9Digits) {
        this.t9Chars = t9Chars;
        this.t9Digits = t9Digits;
    }

    // Copied from https://github.com/CyanogenMod/android_packages_apps_Contacts/commit/63a531957818d631e957e8e0157d45298906e3fb
    public String convert(final String name) {
        int len = name.length();
        StringBuilder sb = new StringBuilder(len);

        for (int i = 0; i < len; i++){
            int pos = t9Chars.indexOf(Character.toLowerCase(name.charAt(i)));
            if (pos == -1) {
                pos = 0;
            }
            sb.append(t9Digits.charAt(pos));
        }
        return sb.toString();
    }
}
