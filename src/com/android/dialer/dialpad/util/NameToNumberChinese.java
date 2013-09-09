package com.android.dialer.dialpad.util;

import android.text.TextUtils;

public class NameToNumberChinese extends NameToNumber {
    public NameToNumberChinese(String t9Chars, String t9Digits) {
        super(t9Chars, t9Digits);
    }

    @Override
    public String convert(String name) {
        final HanziToPinyin pinyin = HanziToPinyin.getInstance();
        String hzPinYin = pinyin.getFirstPinYin(name).toLowerCase();

        if (TextUtils.isEmpty(hzPinYin)) {
            return super.convert(name);
        }

        String result = super.convert(hzPinYin);

        //Append the full ping yin at the end of the first ping yin
        hzPinYin = pinyin.getFullPinYin(name).toLowerCase();
        if (!TextUtils.isEmpty(hzPinYin)) {
            result += " " + super.convert(hzPinYin);
        }

        return result;
    }
}
