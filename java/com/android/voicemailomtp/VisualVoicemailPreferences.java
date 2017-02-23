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
 * limitations under the License
 */
package com.android.voicemailomtp;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import java.util.Set;

/**
 * Save visual voicemail values in shared preferences to be retrieved later. Because a voicemail
 * source is tied 1:1 to a phone account, the phone account handle is used in the key for each
 * voicemail source and the associated data.
 */
public class VisualVoicemailPreferences {

    private static final String VISUAL_VOICEMAIL_SHARED_PREFS_KEY_PREFIX =
            "visual_voicemail_";

    private final SharedPreferences mPreferences;
    private final PhoneAccountHandle mPhoneAccountHandle;

    public VisualVoicemailPreferences(Context context, PhoneAccountHandle phoneAccountHandle) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mPhoneAccountHandle = phoneAccountHandle;
    }

    public class Editor {

        private final SharedPreferences.Editor mEditor;

        private Editor() {
            mEditor = mPreferences.edit();
        }

        public void apply() {
            mEditor.apply();
        }

        public Editor putBoolean(String key, boolean value) {
            mEditor.putBoolean(getKey(key), value);
            return this;
        }

        @NeededForTesting
        public Editor putFloat(String key, float value) {
            mEditor.putFloat(getKey(key), value);
            return this;
        }

        public Editor putInt(String key, int value) {
            mEditor.putInt(getKey(key), value);
            return this;
        }

        @NeededForTesting
        public Editor putLong(String key, long value) {
            mEditor.putLong(getKey(key), value);
            return this;
        }

        public Editor putString(String key, String value) {
            mEditor.putString(getKey(key), value);
            return this;
        }

        @NeededForTesting
        public Editor putStringSet(String key, Set<String> value) {
            mEditor.putStringSet(getKey(key), value);
            return this;
        }
    }

    public Editor edit() {
        return new Editor();
    }

    public boolean getBoolean(String key, boolean defValue) {
        return getValue(key, defValue);
    }

    @NeededForTesting
    public float getFloat(String key, float defValue) {
        return getValue(key, defValue);
    }

    public int getInt(String key, int defValue) {
        return getValue(key, defValue);
    }

    @NeededForTesting
    public long getLong(String key, long defValue) {
        return getValue(key, defValue);
    }

    public String getString(String key, String defValue) {
        return getValue(key, defValue);
    }

    @Nullable
    public String getString(String key) {
        return getValue(key, null);
    }

    @NeededForTesting
    public Set<String> getStringSet(String key, Set<String> defValue) {
        return getValue(key, defValue);
    }

    public boolean contains(String key) {
        return mPreferences.contains(getKey(key));
    }

    private <T> T getValue(String key, T defValue) {
        if (!contains(key)) {
            return defValue;
        }
        Object object = mPreferences.getAll().get(getKey(key));
        if (object == null) {
            return defValue;
        }
        return (T) object;
    }

    private String getKey(String key) {
        return VISUAL_VOICEMAIL_SHARED_PREFS_KEY_PREFIX + key + "_" + mPhoneAccountHandle.getId();
    }
}
