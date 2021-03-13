/*
 * Copyright (C) 2019-2021 The LineageOS Project
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
import android.content.res.Resources;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import org.lineageos.lib.phone.SensitivePhoneNumbers;
import org.lineageos.lib.phone.spn.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LoadHelplinesTask extends AsyncTask<Void, Integer, List<HelplineItem>> {

    @NonNull
    private final Resources mResources;
    @NonNull
    private final SubscriptionManager mSubManager;
    @NonNull
    private final Callback mCallback;

    LoadHelplinesTask(@NonNull Resources resources, @NonNull SubscriptionManager subManager,
                      @NonNull Callback callback) {
        mResources = resources;
        mSubManager = subManager;
        mCallback = callback;
    }

    @Override
    protected List<HelplineItem> doInBackground(Void... voids) {
        List<HelplineItem> helplineList = new ArrayList<>();
        /* when the network's and the user's country iso differ from each other,
         * include the iso code in the name so one can be sure that the number is the correct one
         * (think of accidential roaming close to the country border) */
        boolean addCountryCode = false;

        List<SubscriptionInfo> subList = getSubscriptionInfos();
        if (subList != null) {
            String localeCountryIso =
                    mResources.getConfiguration().locale.getCountry().toLowerCase();
            List<String> alreadyProcessedMccs = new ArrayList<>();
            for (SubscriptionInfo subInfo : subList) {
                String subCountryIso = subInfo.getCountryIso();
                if (!subCountryIso.equals(localeCountryIso)) {
                    addCountryCode = true;
                }

                String mcc = String.valueOf(subInfo.getMcc());
                if (alreadyProcessedMccs.contains(mcc)) {
                    continue;
                }
                alreadyProcessedMccs.add(mcc);

                SensitivePhoneNumbers spn = SensitivePhoneNumbers.getInstance();
                ArrayList<Item> pns = spn.getSensitivePnInfosForMcc(mcc);
                int numPns = pns.size();
                for (int i = 0; i < numPns; i++) {
                    Item item = pns.get(i);
                    helplineList.add(new HelplineItem(mResources, item,
                            addCountryCode ? subCountryIso : ""));
                    publishProgress(Math.round(i * 100 / numPns / subList.size()));
                }
            }
        }

        Collections.sort(helplineList, (a, b) -> a.getName().compareTo(b.getName()));

        return helplineList;
    }

    private List<SubscriptionInfo> getSubscriptionInfos() {
        List<SubscriptionInfo> subList = mSubManager.getActiveSubscriptionInfoList();
        if (subList == null) {
            SubscriptionInfo info = mSubManager.getActiveSubscriptionInfo(
                    SubscriptionManager.getDefaultVoiceSubscriptionId());
            if (info != null) {
                subList = new ArrayList<>();
                subList.add(info);
            }
        }
        return subList;
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
