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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.dialer.R;
import com.android.dialer.util.MetricsHelper;
import com.android.phone.common.ambient.AmbientConnection;
import com.cyanogen.ambient.callerinfo.util.CallerInfoHelper;
import com.cyanogen.ambient.callerinfo.util.ProviderInfo;
import com.cyanogen.ambient.callerinfo.CallerInfoServices;
import com.cyanogen.ambient.callerinfo.results.IsAuthenticatedResult;
import com.cyanogen.ambient.common.api.PendingResult;
import com.cyanogen.ambient.common.api.Result;
import com.cyanogen.ambient.common.api.ResultCallback;

public class CallerInfoProviderPicker extends Activity {

    private static final String TAG = "CallerInfoProviderPicker";

    public static final String METRICS_REASON_EXTRA = "reason_extra";
    public static final int REASON_FIRST_LAUNCH_DIALER = 0;
    public static final int REASON_INCOMING_CALL = 1;
    public static final int REASON_INCOMING_CALL_FINAL_PROMPT = 2;
    public static final int REASON_DIALER_SETTINGS = 3;

    private AlertDialog mDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Resources res = getResources();
        final CallerInfoHelper.ResolvedProvider[] providers =
                CallerInfoHelper.getInstalledProviders(this);
        if (providers.length == 0) {
            finish();
            return;
        }

        View view = View.inflate(this, R.layout.callerinfo_provider_picker, null);
        ImageView logo = (ImageView) view.findViewById(R.id.logo);
        TextView description = (TextView) view.findViewById(R.id.description);
        TextView disclaimer = (TextView) view.findViewById(R.id.disclaimer);

        // Assume only one provider
        ProviderInfo info = CallerInfoHelper.getProviderInfo(this, providers[0].getComponent());
        if (info != null) {
            if (info.hasProperty(ProviderInfo.PROPERTY_NEEDS_CONTACTS)) {
                String text = res.getString(R.string.callerinfo_provider_auth_access, info.getTitle());
                if (info.getPrivacyPolicyUrl() != null) {
                    String learnMore = " <a href=\"%s\">%s</a>";
                    text += String.format(learnMore, info.getPrivacyPolicyUrl(),
                            res.getString(R.string.callerinfo_provider_auth_learn_more));
                }
                disclaimer.setMovementMethod(LinkMovementMethod.getInstance());
                disclaimer.setText(Html.fromHtml(text));
            } else {
                disclaimer.setVisibility(View.GONE);
            }

            logo.setImageDrawable(info.getBrandLogo());

            int resId = info.hasProperty(ProviderInfo.PROPERTY_SUPPORTS_SPAM) ?
                    R.string.callerinfo_provider_auth_desc : R.string.callerinfo_provider_auth_desc_no_spam;
            description.setText(res.getString(resId, info.getTitle()));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);
        builder.setPositiveButton(res.getString(R.string.callerinfo_provider_auth_yes),
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                sendMetrics(true, providers[0].getPackageName());

                CallerInfoHelper.setActiveProvider(CallerInfoProviderPicker.this,
                        providers[0].getComponent());
                PendingResult<IsAuthenticatedResult> result =
                        CallerInfoServices.CallerInfoApi.isAuthenticated(
                                AmbientConnection.CLIENT.get(getApplicationContext()));
                result.setResultCallback(new ResultCallback<IsAuthenticatedResult>() {
                    @Override
                    public void onResult(IsAuthenticatedResult response) {
                        Log.d(TAG, "isAuthenticated = " + response.getIsAuthenticated());
                        if (!response.getIsAuthenticated()) {
                            PendingResult<Result> result =
                                    CallerInfoServices.CallerInfoApi.setupAuthentication
                                            (AmbientConnection.CLIENT.get(getApplicationContext()));
                        }
                        finish();
                    }
                });
            }
        }).setNegativeButton(res.getString(R.string.callerinfo_provider_auth_no),
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                sendMetrics(false, providers[0].getPackageName());
                finish();
            }
        }).setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        });
        mDialog = builder.create();
        mDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                int buttonColor = res.getColor(R.color.callerinfo_provider_picker_negative_color);
                mDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonColor);
            }
        });
        mDialog.show();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    void sendMetrics(boolean onAccept, String providerPackageName) {
        if (!getIntent().hasExtra(METRICS_REASON_EXTRA)) {
            return;
        }
        int reason = getIntent().getIntExtra(METRICS_REASON_EXTRA, -1);
        MetricsHelper.Field field = new MetricsHelper.Field(
                MetricsHelper.Fields.PROVIDER_PACKAGE_NAME, providerPackageName);
        MetricsHelper.Actions action = onAccept ?
                MetricsHelper.Actions.OPTED_IN : MetricsHelper.Actions.OPTED_OUT;
        switch (reason) {
            case REASON_FIRST_LAUNCH_DIALER:
                MetricsHelper.sendEvent(MetricsHelper.Categories.PROVIDER_STATE_CHANGES,
                        action, MetricsHelper.State.FIRST_LAUNCH_DIALER, field);
                break;
            case REASON_INCOMING_CALL_FINAL_PROMPT:
                MetricsHelper.sendEvent(MetricsHelper.Categories.PROVIDER_STATE_CHANGES,
                        action, MetricsHelper.State.AFTER_FINAL_PROMPT, field);
                break;
            case REASON_INCOMING_CALL:
                MetricsHelper.sendEvent(MetricsHelper.Categories.PROVIDER_STATE_CHANGES,
                        action, MetricsHelper.State.AFTER_CALL_ENDED, field);
                break;
            case REASON_DIALER_SETTINGS:
                MetricsHelper.sendEvent(MetricsHelper.Categories.PROVIDER_STATE_CHANGES,
                        action, MetricsHelper.State.SETTINGS, field);
                break;
        }
    }
}
