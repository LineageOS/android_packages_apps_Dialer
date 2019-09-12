/*
 * Copyright (C) 2019 The LineageOS Project
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
package com.android.dialer.helplines;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import com.android.server.telecom.SensitivePhoneNumberInfo;
import com.android.server.telecom.SensitivePhoneNumbers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LoadHelplinesTask extends AsyncTask<Void, Integer, List<HelplineItem>> {

    @NonNull Context mContext;
    @NonNull
    private Callback mCallback;

    LoadHelplinesTask(Context context, @NonNull Callback callback) {
        mContext = context;
        mCallback = callback;
    }

    @Override
    protected List<HelplineItem> doInBackground(Void... voids) {
        List<HelplineItem> list = new ArrayList<>();

        SensitivePhoneNumbers sensitivePn = SensitivePhoneNumbers.getInstance();
//        ArrayList<SensitivePhoneNumberInfo> pns = sensitivePn.getSensitivePnInfosForMcc("262");
//        publishProgress(50);
//        int numPns = pns.size();
//        for (int i = 0; i < numPns; i++) {
//            SensitivePhoneNumberInfo info = pns.get(i);
//            list.add(info);
//            publishProgress(Math.round(50 + i * 100 / numPns / 2));
//        }

        //Collections.sort(list, (a, b) -> a.getName().compareTo(b.getName()));

        SubscriptionManager subManager = mContext.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> subList = subManager.getActiveSubscriptionInfoList();
        if (subList == null) {
            SubscriptionInfo info = subManager.getActiveSubscriptionInfo(
                    SubscriptionManager.getDefaultVoiceSubscriptionId());
            if (info != null) {
                subList = new ArrayList<>();
                subList.add(info);
            }
        }
        if (subList != null) {
            /* Get the sensitive numbers for the mcc of all subscriptions (or at least the default
            subscription */
            for (SubscriptionInfo subInfo : subList) {
                String mcc = String.valueOf(subInfo.getMcc());
                ArrayList<SensitivePhoneNumberInfo> pns =
                        sensitivePn.getSensitivePnInfosForMcc(mcc);
                int numPns = pns.size();
                for (int i = 0; i < numPns; i++) {
                    SensitivePhoneNumberInfo info = pns.get(i);
                    list.add(new HelplineItem(mContext.getResources(), info));
                    publishProgress(Math.round(i * 100 / numPns / list.size()));
                }
            }
        }

        Collections.sort(list, (a, b) -> a.getName().compareTo(b.getName()));

        return list;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (values.length > 0) {
            mCallback.onLoadListProgress(values[0]);
        }
    }

    @Override
    protected void onPostExecute(List<HelplineItem> list) {
        mCallback.onLoadCompleted(list);
    }

    interface Callback {
        void onLoadListProgress(int progress);
        void onLoadCompleted(List<HelplineItem> result);
    }
}
