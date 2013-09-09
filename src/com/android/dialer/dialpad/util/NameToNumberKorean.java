package com.android.dialer.dialpad.util;


/**
 * @author Barami
 * Implementation for Korean normalization.
 * This will change Hangul character to number by Choseong(Korean word of initial character).
 */
public class NameToNumberKorean implements NameLatinizer {
    protected String t9Chars;
    protected String t9Digits;
    // Hangul Chosung (Initial letters of Hangul).
    // Note : Don't change order of initial alphabets. index will be used to calculate.
    private static final String HANGUL_INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final int UNICODE_HANGUL_START = 0xAC00;
    private static final int UNICODE_HANGUL_END = 0xD7AF;
    private static int buffer = 0;

    public NameToNumberKorean(String t9Chars, String t9Digits) {
        this.t9Chars = t9Chars;
        this.t9Digits = t9Digits;
    }

    @Override
    public String[] getNameLatinizations(String name) {
        int len = name.length();
        StringBuilder sb = new StringBuilder(len);

        // i will make using unicode codepoint.
        for (int i = 0; i < len; i++){
            buffer = name.codePointAt(i);

            int pos;
            if (buffer >= UNICODE_HANGUL_START && buffer <= UNICODE_HANGUL_END) {
                // number of initial character = (codepoint - 0xAC00) / (21 * 28)
                buffer = (buffer - UNICODE_HANGUL_START) / 588;
                pos = t9Chars.indexOf(HANGUL_INITIALS.charAt(buffer));
            } else {
                pos = t9Chars.indexOf(Character.toLowerCase(buffer));
            }

            if (pos == -1) {
                pos = 0;
            }

            sb.append(t9Digits.charAt(pos));
        }
        return new String[] {sb.toString()};
    }
}
