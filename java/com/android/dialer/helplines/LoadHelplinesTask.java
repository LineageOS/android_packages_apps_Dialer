/*
 * Copyright (C) 2019-2023 The LineageOS Project
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

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.annotation.NonNull;

import org.lineageos.lib.phone.SensitivePhoneNumbers;
import org.lineageos.lib.phone.spn.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoadHelplinesTask {

    @NonNull
    private final Resources mResources;
    @NonNull
    private final SubscriptionManager mSubManager;
    @NonNull
    private final Callback mCallback;

    private final ExecutorService mExecutor;
    private final Handler mHandler;

    LoadHelplinesTask(@NonNull Resources resources, @NonNull SubscriptionManager subManager,
                      @NonNull Callback callback) {
        mResources = resources;
        mSubManager = subManager;
        mCallback = callback;

        mExecutor = Executors.newSingleThreadExecutor();
        mHandler = new Handler(Looper.getMainLooper());
    }

    public void execute() {
        mExecutor.execute(() -> {
            final List<HelplineItem> helplineList = new ArrayList<>();
            /* when the network's and the user's country iso differ from each other,
             * include the iso code in the name so one can be sure that the number is the correct
             * one (think of accidental roaming close to the country border) */
            boolean addCountryCode = false;

            List<SubscriptionInfo> subList = getSubscriptionInfos();
            if (subList != null) {
                String localeCountryIso = mResources.getConfiguration().getLocales().get(0)
                        .getCountry().toLowerCase();
                List<String> alreadyProcessedMccs = new ArrayList<>();
                for (SubscriptionInfo subInfo : subList) {
                    String subCountryIso = subInfo.getCountryIso();
                    if (!subCountryIso.equals(localeCountryIso)) {
                        addCountryCode = true;
                    }

                    String mcc = subInfo.getMccString();
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
                        final int currentItem = i;
                        mHandler.post(() -> {
                            int progress = Math.round(currentItem * 100f / numPns / subList.size());
                            mCallback.onLoadListProgress(progress);
                        });
                    }
                }
            }

            helplineList.sort(Comparator.comparing(HelplineItem::getName));

            mHandler.post(() -> mCallback.onLoadCompleted(helplineList));
        });
    }

    @SuppressLint("MissingPermission")
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

    interface Callback {
        void onLoadListProgress(int progress);

        void onLoadCompleted(List<HelplineItem> result);
    }
}
