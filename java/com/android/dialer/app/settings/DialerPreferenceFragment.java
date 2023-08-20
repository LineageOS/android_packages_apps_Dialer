/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.app.settings;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.view.View;
import android.widget.ListView;

import androidx.annotation.Nullable;

public class DialerPreferenceFragment extends PreferenceFragment {
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        ListView lv = (ListView) view.findViewById(android.R.id.list);
        if (lv != null) {
            lv.setDivider(null);
        }
    }
}
