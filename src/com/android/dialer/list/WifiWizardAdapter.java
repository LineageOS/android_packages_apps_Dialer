/*
 * Copyright (C) 2013 Google Inc.
 * Licensed to The Android Open Source Project.
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
package com.android.dialer.list;

import com.android.dialer.R;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.RadioButton;

public class WifiWizardAdapter extends BaseAdapter {

    /** Used to open Call Setting */
    private static final String PHONE_PACKAGE = "com.android.phone";
    private static final String CALL_SETTINGS_CLASS_NAME =
            "com.android.phone.CallFeaturesSetting";

    public interface WifiWizardModel {
        /** @see android.telephony.TelephonyManager.WifiCallingChoices */
        int getWhenToMakeWifiCalls();
        /** @see android.telephony.TelephonyManager.WifiCallingChoices */
        void setWhenToMakeWifiCalls(int preference);

        /** Whether a Wi-Fi selection shortcut should be displayed */
        boolean getShouldDisplayWifiSelection();
        /** @see #getShouldDisplayWifiSelection() */
        void setShouldDisplayWifiSelection(boolean preference);

        /** Commit any changes made to persistent settings storage */
        void commitWhenToMakeWifiCalls();
    }

    private abstract class WifiWizardStep {
        private final int mResourceId;
        private View mView;
        protected WifiWizardStep(int resourceId) {
            mResourceId = resourceId;
        }
        public final View getView() {
            if (mView == null) {
                mView = inflate(mResourceId);
                configureView(mView);
            }
            if (mView.getParent() != null && (mView.getParent() instanceof ViewGroup)) {
                ((ViewGroup) mView.getParent()).removeView(mView);
            }
            return mView;
        }
        protected abstract void configureView(View view);
    }

    private WifiWizardStep mTeaserStep =
            new WifiWizardStep(R.layout.wifi_call_enable_teaser) {
                @Override
                protected void configureView(View view) {
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mStep = mSettingsStep;
                            notifyDataSetChanged();
                        }
                    });
                }
            };

    private WifiWizardStep mSettingsStep =
            new WifiWizardStep(R.layout.wifi_call_enable_settings) {
                private RadioButton rb(View view, int id) {
                    return (RadioButton) view.findViewById(id);
                }

                private void update(View view) {
                    rb(view, R.id.wifi_never_use_wifi).setChecked(false);
                    rb(view, R.id.wifi_ask_use_wifi).setChecked(false);
                    rb(view, R.id.wifi_always_use_wifi).setChecked(false);
                    switch (mModel.getWhenToMakeWifiCalls()) {
                        case TelephonyManager.WifiCallingChoices.NEVER_USE:
                            rb(view, R.id.wifi_never_use_wifi).setChecked(true);
                            break;
                        case TelephonyManager.WifiCallingChoices.ASK_EVERY_TIME:
                            rb(view, R.id.wifi_ask_use_wifi).setChecked(true);
                            break;
                        case TelephonyManager.WifiCallingChoices.ALWAYS_USE:
                            rb(view, R.id.wifi_always_use_wifi).setChecked(true);
                            break;
                    }
                }

                private void listen(final View view, int id, final int prefValue) {
                    rb(view, id).setOnCheckedChangeListener(
                            new CompoundButton.OnCheckedChangeListener() {
                                @Override
                                public void onCheckedChanged(
                                        CompoundButton buttonView,
                                        boolean isChecked) {
                                    if (isChecked) {
                                        mModel.setWhenToMakeWifiCalls(prefValue);
                                        update(view);
                                    }
                                }
                            });
                }

                private void listen(View view) {
                    listen(view, R.id.wifi_never_use_wifi,
                            TelephonyManager.WifiCallingChoices.NEVER_USE);
                    listen(view, R.id.wifi_ask_use_wifi,
                            TelephonyManager.WifiCallingChoices.ASK_EVERY_TIME);
                    listen(view, R.id.wifi_always_use_wifi,
                            TelephonyManager.WifiCallingChoices.ALWAYS_USE);
                }

                @Override
                protected void configureView(View view) {
                    update(view);
                    listen(view);
                    view.findViewById(R.id.wifi_next_setup_screen).setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    mModel.setShouldDisplayWifiSelection(false);
                                    mModel.commitWhenToMakeWifiCalls();
                                    mStep = mCompletionStep;
                                    notifyDataSetChanged();
                                }
                            });
                }
            };

    private WifiWizardStep mCompletionStep =
            new WifiWizardStep(R.layout.wifi_call_enable_completion) {
                private void finish() {
                    // Keep 'mStep' non-null even if unused, to avoid user visible NPE
                    // in case there may be some other bug in the logic
                    mStep = mTeaserStep;
                    notifyDataSetChanged();
                }

                @Override
                protected void configureView(View view) {
                    view.findViewById(R.id.wifi_setup_ok).setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    finish();
                                }
                            });
                    view.findViewById(R.id.wifi_setup_settings_shortcut).setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    mContext.startActivity(getCallSettingsIntent());
                                    finish();
                                }
                            });
                }
            };

    private final SwipeHelper.OnItemGestureListener mOnItemSwipeListener =
            new SwipeHelper.OnItemGestureListener() {
                @Override
                public void onSwipe(View view) {
                    mModel.setShouldDisplayWifiSelection(false);
                    notifyDataSetChanged();
                }

                @Override
                public void onTouch() {}

                @Override
                public boolean isSwipeEnabled() {
                    // TODO: This never gets called by the swipe framework; why?
                    return mStep == mTeaserStep;
                }
            };

    private final WifiWizardModel mModel;
    private final Context mContext;
    private WifiWizardStep mStep = mTeaserStep;

    public WifiWizardAdapter(Context context, WifiWizardModel model) {
        this.mContext = context;
        this.mModel = model;
    }

    public SwipeHelper.OnItemGestureListener getOnItemSwipeListener() {
        return mOnItemSwipeListener;
    }

    @Override
    public int getCount() {
        if (mModel.getShouldDisplayWifiSelection() && mModel.getWhenToMakeWifiCalls() ==
                TelephonyManager.WifiCallingChoices.NEVER_USE) {
            return 1;
        }
        return 0;
    }

    @Override
    public Object getItem(int position) {
        return this;
    }

    @Override
    public long getItemId(int position) {
        return 1L;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return mStep.getView();
    }

    private View inflate(int resource) {
        return ((LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .inflate(resource, null);
    }

    /** Returns an Intent to launch Call Settings screen */
    public static Intent getCallSettingsIntent() {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(PHONE_PACKAGE, CALL_SETTINGS_CLASS_NAME);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }
}
