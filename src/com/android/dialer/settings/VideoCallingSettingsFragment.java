/**
 * Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * * Neither the name of The Linux Foundation nor the names of its
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/

package com.android.dialer.settings;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.util.Log;

import java.lang.Object;
import java.lang.Override;
import java.lang.String;

import com.android.contacts.common.CallUtil;
import com.android.dialer.R;

public class VideoCallingSettingsFragment extends PreferenceFragment implements
        Preference.OnPreferenceClickListener {

    private final static String KEY_VIDEO_CALL = "video_calling_preference";
    private SwitchPreference mVideoCallingPreference;
    private Context mContext;
    private static final String TAG = "VideoCallingSettingsFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.video_calling_settings);

        mContext = getActivity();
        mVideoCallingPreference = (SwitchPreference)findPreference(KEY_VIDEO_CALL);
        mVideoCallingPreference.setOnPreferenceClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        int enable = Settings.System.getInt(mContext.getContentResolver(),
                CallUtil.CONFIG_VIDEO_CALLING, CallUtil.DISABLE_VIDEO_CALLING);
        if(mVideoCallingPreference != null)
            mVideoCallingPreference.setChecked(enable == CallUtil.ENABLE_VIDEO_CALLING);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mVideoCallingPreference) {
            boolean isCheck = mVideoCallingPreference.isChecked();
            CallUtil.createVideoCallingDialog(isCheck , mContext);
            boolean isSaved = CallUtil.saveVideoCallConfig(mContext,isCheck);
            Log.d(TAG, "onPreferenceChange isSaved = " + isSaved);
        }
        return true;
    }

}
