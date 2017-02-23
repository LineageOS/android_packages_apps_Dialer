/**
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

package com.android.voicemailomtp.settings;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;

import com.android.voicemailomtp.OmtpConstants;
import com.android.voicemailomtp.OmtpVvmCarrierConfigHelper;
import com.android.voicemailomtp.R;
import com.android.voicemailomtp.SubscriptionInfoHelper;
import com.android.voicemailomtp.VisualVoicemailPreferences;

public class VoicemailSettingsActivity extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {
    private static final String LOG_TAG = VoicemailSettingsActivity.class.getSimpleName();
    private static final boolean DBG = true;

    /**
     * Intent action to bring up Voicemail Provider settings
     * DO NOT RENAME. There are existing apps which use this intent value.
     */
    public static final String ACTION_ADD_VOICEMAIL =
            "com.android.voicemailomtp.CallFeaturesSetting.ADD_VOICEMAIL";

    /**
     * Intent action to bring up the {@code VoicemailSettingsActivity}.
     * DO NOT RENAME. There are existing apps which use this intent value.
     */
    public static final String ACTION_CONFIGURE_VOICEMAIL =
            "com.android.voicemailomtp.CallFeaturesSetting.CONFIGURE_VOICEMAIL";

    // Extra put in the return from VM provider config containing voicemail number to set
    public static final String VM_NUMBER_EXTRA = "com.android.voicemailomtp.VoicemailNumber";
    // Extra put in the return from VM provider config containing call forwarding number to set
    public static final String FWD_NUMBER_EXTRA = "com.android.voicemailomtp.ForwardingNumber";
    // Extra put in the return from VM provider config containing call forwarding number to set
    public static final String FWD_NUMBER_TIME_EXTRA = "com.android.voicemailomtp.ForwardingNumberTime";
    // If the VM provider returns non null value in this extra we will force the user to
    // choose another VM provider
    public static final String SIGNOUT_EXTRA = "com.android.voicemailomtp.Signout";

    /**
     * String Extra put into ACTION_ADD_VOICEMAIL call to indicate which provider should be hidden
     * in the list of providers presented to the user. This allows a provider which is being
     * disabled (e.g. GV user logging out) to force the user to pick some other provider.
     */
    public static final String IGNORE_PROVIDER_EXTRA = "com.android.voicemailomtp.ProviderToIgnore";

    /**
     * String Extra put into ACTION_ADD_VOICEMAIL to indicate that the voicemail setup screen should
     * be opened.
     */
    public static final String SETUP_VOICEMAIL_EXTRA = "com.android.voicemailomtp.SetupVoicemail";

    /** Event for Async voicemail change call */
    private static final int EVENT_VOICEMAIL_CHANGED        = 500;
    private static final int EVENT_FORWARDING_CHANGED       = 501;
    private static final int EVENT_FORWARDING_GET_COMPLETED = 502;

    /** Handle to voicemail pref */
    private static final int VOICEMAIL_PREF_ID = 1;
    private static final int VOICEMAIL_PROVIDER_CFG_ID = 2;

    /**
     * Used to indicate that the voicemail preference should be shown.
     */
    private boolean mShowVoicemailPreference = false;

    private int mSubId;
    private PhoneAccountHandle mPhoneAccountHandle;
    private SubscriptionInfoHelper mSubscriptionInfoHelper;
    private OmtpVvmCarrierConfigHelper mOmtpVvmCarrierConfigHelper;

    private SwitchPreference mVoicemailVisualVoicemail;
    private Preference mVoicemailChangePinPreference;

    //*********************************************************************************************
    // Preference Activity Methods
    //*********************************************************************************************

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Show the voicemail preference in onResume if the calling intent specifies the
        // ACTION_ADD_VOICEMAIL action.
        mShowVoicemailPreference = (icicle == null) &&
                TextUtils.equals(getIntent().getAction(), ACTION_ADD_VOICEMAIL);

        mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mSubscriptionInfoHelper.setActionBarTitle(
                getActionBar(), getResources(), R.string.voicemail_settings_with_label);
        mSubId = mSubscriptionInfoHelper.getSubId();
        // TODO: scrap this activity.
        /*
        mPhoneAccountHandle = PhoneAccountHandleConverter
                .fromSubId(this, mSubId);

        mOmtpVvmCarrierConfigHelper = new OmtpVvmCarrierConfigHelper(
                this, mSubId);
        */
    }

    @Override
    protected void onResume() {
        super.onResume();

        PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            preferenceScreen.removeAll();
        }

        addPreferencesFromResource(R.xml.voicemail_settings);

        PreferenceScreen prefSet = getPreferenceScreen();

        mVoicemailVisualVoicemail = (SwitchPreference) findPreference(
                getResources().getString(R.string.voicemail_visual_voicemail_key));

        mVoicemailChangePinPreference = findPreference(
                getResources().getString(R.string.voicemail_change_pin_key));
        Intent changePinIntent = new Intent(new Intent(this, VoicemailChangePinActivity.class));
        changePinIntent.putExtra(VoicemailChangePinActivity.EXTRA_PHONE_ACCOUNT_HANDLE,
                mPhoneAccountHandle);

        mVoicemailChangePinPreference.setIntent(changePinIntent);
        if (VoicemailChangePinActivity.isDefaultOldPinSet(this, mPhoneAccountHandle)) {
            mVoicemailChangePinPreference.setTitle(R.string.voicemail_set_pin_dialog_title);
        } else {
            mVoicemailChangePinPreference.setTitle(R.string.voicemail_change_pin_dialog_title);
        }

        if (mOmtpVvmCarrierConfigHelper.isValid()) {
            mVoicemailVisualVoicemail.setOnPreferenceChangeListener(this);
            mVoicemailVisualVoicemail.setChecked(
                    VisualVoicemailSettingsUtil.isEnabled(this, mPhoneAccountHandle));
            if (!isVisualVoicemailActivated()) {
                prefSet.removePreference(mVoicemailChangePinPreference);
            }
        } else {
            prefSet.removePreference(mVoicemailVisualVoicemail);
            prefSet.removePreference(mVoicemailChangePinPreference);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Implemented to support onPreferenceChangeListener to look for preference changes.
     *
     * @param preference is the preference to be changed
     * @param objValue should be the value of the selection, NOT its localized
     * display value.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (DBG) log("onPreferenceChange: \"" + preference + "\" changed to \"" + objValue + "\"");
        if (preference.getKey().equals(mVoicemailVisualVoicemail.getKey())) {
            boolean isEnabled = (boolean) objValue;
            VisualVoicemailSettingsUtil
                    .setEnabled(this, mPhoneAccountHandle, isEnabled);
            PreferenceScreen prefSet = getPreferenceScreen();
            if (isVisualVoicemailActivated()) {
                prefSet.addPreference(mVoicemailChangePinPreference);
            } else {
                prefSet.removePreference(mVoicemailChangePinPreference);
            }
        }

        // Always let the preference setting proceed.
        return true;
    }

    private boolean isVisualVoicemailActivated() {
        if (!VisualVoicemailSettingsUtil.isEnabled(this, mPhoneAccountHandle)) {
            return false;
        }
        VisualVoicemailPreferences preferences = new VisualVoicemailPreferences(this,
                mPhoneAccountHandle);
        return preferences.getString(OmtpConstants.SERVER_ADDRESS, null) != null;

    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
