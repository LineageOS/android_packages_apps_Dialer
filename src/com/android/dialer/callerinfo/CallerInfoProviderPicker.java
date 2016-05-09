/*
 *      Copyright (C) 2013-2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.dialer.callerinfo;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Html;

import com.android.dialer.R;
import com.android.dialer.util.ImageUtils;
import com.android.phone.common.ambient.AmbientConnection;
import com.cyanogen.ambient.callerinfo.util.CallerInfoHelper;
import com.cyanogen.ambient.callerinfo.util.ProviderInfo;
import com.cyanogen.ambient.common.CyanogenAmbientUtil;
import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.discovery.DiscoveryManagerServices;
import com.cyanogen.ambient.discovery.nudge.DialogNudge;
import com.cyanogen.ambient.discovery.results.BooleanResult;

public class CallerInfoProviderPicker {
    private static final String NUDGE_ID = "callerInfoPickerDialogNudge";

    private static final int REQUEST_CODE_SUCCESS = 0;
    private static final int REQUEST_CODE_FAILURE = 1;

    private static final String PREF_FIRST_LAUNCH = "first_launch";
    private static final String PREF_UNKNOWN_CALL_COUNT = "unknown_call_count";
    private static final int UNKNOWN_CALL_FIRST_COUNT = 0;
    private static final int UNKNOWN_CALL_FINAL_COUNT = 4;

    public static void onAppLaunched(Context context) {
        if (checkPreconditions(context)) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (prefs.getBoolean(PREF_FIRST_LAUNCH, true)) {
                enableProvider(context, true, ProviderActivationService.REASON_FIRST_LAUNCH_DIALER,
                        new ResultCallback<BooleanResult>() {
                            @Override
                            public void onResult(BooleanResult result) {
                                // Don't count this as first launch,
                                // unless discovery actually shows our dialog.
                                if (result.bool) {
                                    prefs.edit().putBoolean(PREF_FIRST_LAUNCH, false).commit();
                                }
                            }
                        }
                );
            }
        }
    }

    public static void onUnknownCallEnded(Context context) {
        if (checkPreconditions(context)) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final int currentCount = prefs.getInt(PREF_UNKNOWN_CALL_COUNT, 0);

            int metricsReason;
            if (currentCount == UNKNOWN_CALL_FIRST_COUNT) {
                metricsReason = ProviderActivationService.REASON_INCOMING_CALL_FIRST_PROMPT;
            } else if (currentCount == UNKNOWN_CALL_FINAL_COUNT) {
                metricsReason = ProviderActivationService.REASON_INCOMING_CALL_FINAL_PROMPT;
            } else {
                prefs.edit().putInt(PREF_UNKNOWN_CALL_COUNT, currentCount + 1).commit();
                return;
            }

            enableProvider(context, true, metricsReason,
                    new ResultCallback<BooleanResult>() {
                        @Override
                        public void onResult(BooleanResult result) {
                            // Don't count this event, unless discovery actually shows our dialog.
                            if (result.bool) {
                                prefs.edit().putInt(PREF_UNKNOWN_CALL_COUNT, currentCount + 1).
                                        commit();
                            }
                        }
                    });
        }
    }

    public static void onSettingEnabled(Context context) {
        enableProvider(context, false, ProviderActivationService.REASON_DIALER_SETTINGS, null);
    }

    private static void enableProvider(Context context, boolean withDialog, int metricsReason,
            ResultCallback<BooleanResult> callback) {
        CallerInfoHelper.ResolvedProvider[] providers =
                CallerInfoHelper.getInstalledProviders(context);
        if (providers.length == 0) {
            return;
        }

        // Assume only one provider
        ComponentName component = providers[0].getComponent();
        ProviderInfo info = CallerInfoHelper.getProviderInfo(context, component);

        if (withDialog) {
            showDialog(context, metricsReason, component, info, callback);
        } else {
            context.startService(buildEnableIntent(context, component, info, metricsReason));
        }
    }

    private static boolean checkPreconditions(Context context) {
        boolean setupCompleted = Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
        boolean ambientAvailable = CyanogenAmbientUtil.isCyanogenAmbientAvailable(context)
                == CyanogenAmbientUtil.SUCCESS;
        boolean providerEnabled = CallerInfoHelper.getActiveProviderInfo2(context) != null;
        boolean providerAvailable = CallerInfoHelper.getInstalledProviders(context).length > 0;
        return setupCompleted && ambientAvailable && !providerEnabled && providerAvailable;
    }

    private static void showDialog(Context context, int metricsReason, ComponentName component,
            ProviderInfo info, ResultCallback<BooleanResult> callback) {
        Resources res = context.getResources();
        CharSequence subText = null;
        if (info.hasProperty(ProviderInfo.PROPERTY_NEEDS_CONTACTS)) {
            String text = res.getString(R.string.callerinfo_provider_auth_access, info.getTitle());
            if (info.getPrivacyPolicyUrl() != null) {
                String learnMore = " <a href=\"%s\">%s</a>";
                text += String.format(learnMore, info.getPrivacyPolicyUrl(),
                        res.getString(R.string.callerinfo_provider_auth_learn_more));
            }
            subText = Html.fromHtml(text);
        }

        Bitmap logo = ImageUtils.drawableToBitmap(info.getBrandLogo());

        int resId = info.hasProperty(ProviderInfo.PROPERTY_SUPPORTS_SPAM) ?
                R.string.callerinfo_provider_auth_desc :
                R.string.callerinfo_provider_auth_desc_no_spam;
        String bodyText = res.getString(resId, info.getTitle());

        DialogNudge nudge = new DialogNudge(NUDGE_ID, DialogNudge.SubheadPosition.BOTTOM,
                info.getTitle(), bodyText);

        if (subText != null) {
            nudge.setSubhead(subText.toString());
        }

        if (logo != null) {
            nudge.setTitleImage(logo);
        }

        Intent enableIntent = buildEnableIntent(context, component, info, metricsReason);
        PendingIntent positivePendingIntent = PendingIntent.getService(context,
                REQUEST_CODE_SUCCESS, enableIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        Intent cancelIntent = (Intent) enableIntent.clone();
        cancelIntent.putExtra(ProviderActivationService.INTENT_EXTRA_SUCCESS, false);
        PendingIntent negativePendingIntent = PendingIntent.getService(context,
                REQUEST_CODE_FAILURE, cancelIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        nudge.addButton(new DialogNudge.Button(res.getString(R.string.callerinfo_provider_auth_yes),
                AlertDialog.BUTTON_POSITIVE, positivePendingIntent));
        nudge.addButton(new DialogNudge.Button(res.getString(R.string.callerinfo_provider_auth_no),
                AlertDialog.BUTTON_NEGATIVE, negativePendingIntent));

        DiscoveryManagerServices.DiscoveryManagerApi.
                publishNudge(AmbientConnection.CLIENT.get(context), nudge).
                setResultCallback(callback);
    }

    private static Intent buildEnableIntent(Context context, ComponentName component,
            ProviderInfo info, int metricsReason) {
        Intent intent = new Intent(context, ProviderActivationService.class);
        intent.putExtra(ProviderActivationService.INTENT_EXTRA_PACKAGE, info.getPackageName());
        intent.putExtra(ProviderActivationService.INTENT_EXTRA_COMPONENT, component);
        intent.putExtra(ProviderActivationService.INTENT_EXTRA_METRIC_REASON, metricsReason);
        intent.putExtra(ProviderActivationService.INTENT_EXTRA_SUCCESS, true);
        return intent;
    }
}
