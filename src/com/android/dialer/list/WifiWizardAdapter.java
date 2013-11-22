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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;

public class WifiWizardAdapter extends BaseAdapter {

    public interface WifiWizardModel {
        public static final int WIFI_CALL_STATE_ALWAYS = 0;
        public static final int WIFI_CALL_STATE_NEVER = 1;
        public static final int WIFI_CALL_STATE_ASK = 2;

        void setWifiCallState(int state);

        int getWifiCallState();

        boolean shouldDisplayWifiSelection();
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
            }
            if (mView.getParent() != null && (mView.getParent() instanceof ViewGroup)) {
                ((ViewGroup) mView.getParent()).removeView(mView);
            }
            configureView(mView);
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
                @Override
                protected void configureView(View view) {
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mStep = mCompletionStep;
                            notifyDataSetChanged();
                        }
                    });
                }
            };

    private WifiWizardStep mCompletionStep =
            new WifiWizardStep(R.layout.wifi_call_enable_completion) {
                @Override
                protected void configureView(View view) {
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mStep = mTeaserStep;
                            notifyDataSetChanged();
                        }
                    });
                }
            };

    private final WifiWizardModel mModel;
    private final Context mContext;
    private WifiWizardStep mStep = mTeaserStep;

    public WifiWizardAdapter(Context context, WifiWizardModel model) {
        this.mContext = context;
        this.mModel = model;
    }

    @Override
    public int getCount() {
        return 1;
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
}
