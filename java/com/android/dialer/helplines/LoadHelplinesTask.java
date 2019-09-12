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

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.android.server.telecom.SensitivePhoneNumberInfo;
import com.android.server.telecom.SensitivePhoneNumbers;

import java.util.ArrayList;
import java.util.List;

public class LoadHelplinesTask extends AsyncTask<Void, Integer, List<SensitivePhoneNumberInfo>> {

    @NonNull
    private Callback mCallback;

    LoadHelplinesTask(@NonNull Callback callback) {
        mCallback = callback;
    }

    @Override
    protected List<SensitivePhoneNumberInfo> doInBackground(Void... voids) {
        List<SensitivePhoneNumberInfo> list = new ArrayList<>();

        SensitivePhoneNumbers sensitivePn = SensitivePhoneNumbers.getInstance();
        ArrayList<SensitivePhoneNumberInfo> pns = sensitivePn.getSensitivePnInfosForMcc("262");
        publishProgress(50);
        int numPns = pns.size();
        for (int i = 0; i < numPns; i++) {
            SensitivePhoneNumberInfo info = pns.get(i);
            list.add(info);
            publishProgress(Math.round(50 + i * 100 / numPns / 2));
        }

        //Collections.sort(list, (a, b) -> a.getName().compareTo(b.getName()));

        return list;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (values.length > 0) {
            mCallback.onLoadListProgress(values[0]);
        }
    }

    @Override
    protected void onPostExecute(List<SensitivePhoneNumberInfo> list) {
        mCallback.onLoadCompleted(list);
    }

    interface Callback {
        void onLoadListProgress(int progress);
        void onLoadCompleted(List<SensitivePhoneNumberInfo> result);
    }
}
