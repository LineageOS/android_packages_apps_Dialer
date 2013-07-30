/*

 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.PhoneNumberPickerFragment;
import com.android.dialer.R;

// TODO{klp}: Wrap this fragment with an activity.
/**
 * Fragments to show all contacts with phone numbers.
 */
public class ShowAllContactsFragment extends PhoneNumberPickerFragment{

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Customizes the listview according to the dialer specifics.
        setQuickContactEnabled(true);
        setDarkTheme(false);
        setPhotoPosition(ContactListItemView.getDefaultPhotoPosition(true /* opposite */));
        setUseCallableUri(true);
    }

    @Override
    public void onStart() {
        // Displays action bar for quick navigation.
        final ActionBar actionBar = getActivity().getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);

        final SpannableString s = new SpannableString(getString(R.string.show_all_contacts_title));
        s.setSpan(new TypefaceSpan(getString(R.string.show_all_contacts_title)), 0,
                s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        actionBar.setTitle(s);

        super.onStart();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        // Hides the action bar as it is hidden in the main activity
        if (getActivity() != null) {
            if (hidden) {
                getActivity().getActionBar().hide();
            } else {
                getActivity().getActionBar().show();
            }
        }
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.show_all_contacts_fragment, null);
    }
}
