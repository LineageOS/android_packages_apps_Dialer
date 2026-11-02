/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.app.settings;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.preference.ListPreference;
import androidx.preference.ListPreferenceDialogFragmentCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MaterialListPreferenceDialogFragment extends ListPreferenceDialogFragmentCompat {

  private static final String TAG = "androidx.preference.PreferenceFragment.DIALOG";

  public static boolean show(PreferenceFragmentCompat caller, Preference pref) {
    if (!(pref instanceof ListPreference)) {
      return false;
    }
    FragmentManager fm = caller.getParentFragmentManager();
    if (fm.findFragmentByTag(TAG) == null) {
      MaterialListPreferenceDialogFragment fragment = new MaterialListPreferenceDialogFragment();
      Bundle args = new Bundle(1);
      args.putString(ARG_KEY, pref.getKey());
      fragment.setArguments(args);
      fragment.setTargetFragment(caller, 0);
      fragment.show(fm, TAG);
    }
    return true;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
    builder
        .setTitle(getPreference().getDialogTitle())
        .setNegativeButton(getPreference().getNegativeButtonText(), this);
    onPrepareDialogBuilder(builder);
    return builder.create();
  }
}
