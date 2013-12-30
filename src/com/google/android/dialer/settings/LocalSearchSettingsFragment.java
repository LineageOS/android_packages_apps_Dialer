/*
  * Copyright (C) 2013 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
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

/*
 * This is a reverse-engineered implementation of com.google.android.Dialer.
 * There is no guarantee that this implementation will work correctly or even
 * work at all. Use at your own risk.
 */

package com.google.android.dialer.settings;

import com.android.dialer.R;

import com.google.android.dialer.util.HelpUrl;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class LocalSearchSettingsFragment extends ActionBarSwitchSettingsFragment
        implements View.OnClickListener {
    private View mButton;

    public LocalSearchSettingsFragment() {
        super("local_search", true);
    }

    @Override
    public void onClick(View v) {
        if (v == mButton) {
            startActivity(new Intent(
                    "com.google.android.gms.location.settings.GOOGLE_LOCATION_SETTINGS"));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.local_search_setting, container, false);
        TextView textView = (TextView) view.findViewById(R.id.text);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setText(Html.fromHtml(getResources().getString(
                R.string.local_search_settings_text,
                new Object[] {
                        HelpUrl.getHelpUrl(getActivity(), "dialer_data_attribution"),
                        HelpUrl.getHelpUrl(getActivity(), "dialer_local_search") })));
        mButton = view.findViewById(R.id.view_location_settings);
        mButton.setOnClickListener(this);
        return view;
    }
}
