package com.android.dialer.dialpad.util;

import android.text.TextUtils;

public class NameToNumberChinese implements NameLatinizer {
    protected String t9Chars;
    protected String t9Digits;

    public NameToNumberChinese(String t9Chars, String t9Digits) {
        this.t9Chars = t9Chars;
        this.t9Digits = t9Digits;
    }

    public String convertToT9(String name) {
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

    @Override
    public String[] getNameLatinizations(String name) {
        final HanziToPinyin pinyin = HanziToPinyin.getInstance();
        String hzFirstPinYin = pinyin.getFirstPinYin(name).toLowerCase();

        if (TextUtils.isEmpty(hzFirstPinYin)) {
            return new String[] {this.convertToT9(name)};
        }

        String firstPinYinT9 = this.convertToT9(hzFirstPinYin);

        //Append the full ping yin at the end of the first ping yin
        String hzFullPinYin = pinyin.getFullPinYin(name).toLowerCase();
        if (!TextUtils.isEmpty(hzFullPinYin)) {
            return new String[] {firstPinYinT9, this.convertToT9(hzFullPinYin)};
        } else {
            return new String[] {firstPinYinT9};
        }
    }
}
