/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.dialer.app.filterednumber;

import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.app.R;
import com.android.dialer.database.FilteredNumberContract;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.theme.base.ThemeComponent;
import com.android.dialer.voicemailstatus.VisualVoicemailEnabledChecker;

/** TODO(calderwoodra): documentation */
public class BlockedNumbersFragment extends ListFragment
    implements LoaderManager.LoaderCallbacks<Cursor>,
        VisualVoicemailEnabledChecker.Callback {

  private static final char ADD_BLOCKED_NUMBER_ICON_LETTER = '+';
  protected View migratePromoView;
  private BlockedNumbersAdapter adapter;
  private VisualVoicemailEnabledChecker voicemailEnabledChecker;

  @Override
  public Context getContext() {
    return getActivity();
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    LayoutInflater inflater =
        (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    getListView().addHeaderView(inflater.inflate(R.layout.blocked_number_header, null));
    getListView().addFooterView(inflater.inflate(R.layout.blocked_number_footer, null));
    //replace the icon for add number with LetterTileDrawable(), so it will have identical style
    LetterTileDrawable drawable = new LetterTileDrawable(getResources());
    drawable.setLetter(ADD_BLOCKED_NUMBER_ICON_LETTER);
    drawable.setColor(ThemeComponent.get(getContext()).theme().getColorIcon());
    drawable.setIsCircular(true);

    if (adapter == null) {
      adapter =
          BlockedNumbersAdapter.newBlockedNumbersAdapter(
              getContext(), getActivity().getFragmentManager());
    }
    setListAdapter(adapter);

    migratePromoView = getListView().findViewById(R.id.migrate_promo);

    voicemailEnabledChecker = new VisualVoicemailEnabledChecker(getContext(), this);
    voicemailEnabledChecker.asyncUpdate();
  }

  @Override
  public void onDestroy() {
    setListAdapter(null);
    super.onDestroy();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getLoaderManager().initLoader(0, null, this);
  }

  @Override
  public void onResume() {
    super.onResume();

    ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
    ColorDrawable backgroundDrawable =
        new ColorDrawable(ThemeComponent.get(getContext()).theme().getColorPrimary());
    actionBar.setBackgroundDrawable(backgroundDrawable);
    actionBar.setDisplayShowCustomEnabled(false);
    actionBar.setDisplayHomeAsUpEnabled(true);
    actionBar.setDisplayShowHomeEnabled(true);
    actionBar.setDisplayShowTitleEnabled(true);
    actionBar.setTitle(R.string.manage_blocked_numbers_label);

    // If the device can use the framework blocking solution, users should not be able to add
    // new blocked numbers from the Blocked Management UI. They will be shown a promo card
    // asking them to migrate to new blocking instead.
    migratePromoView.setVisibility(View.VISIBLE);

    voicemailEnabledChecker.asyncUpdate();
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.blocked_number_fragment, container, false);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    final String[] projection = {
      FilteredNumberContract.FilteredNumberColumns._ID,
      FilteredNumberContract.FilteredNumberColumns.COUNTRY_ISO,
      FilteredNumberContract.FilteredNumberColumns.NUMBER,
      FilteredNumberContract.FilteredNumberColumns.NORMALIZED_NUMBER
    };
    final String selection =
        FilteredNumberContract.FilteredNumberColumns.TYPE
            + "="
            + FilteredNumberContract.FilteredNumberTypes.BLOCKED_NUMBER;
    return new CursorLoader(
        getContext(),
        FilteredNumberContract.FilteredNumber.CONTENT_URI,
        projection,
        selection,
        null,
        null);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    adapter.swapCursor(data);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    adapter.swapCursor(null);
  }

  @Override
  public void onVisualVoicemailEnabledStatusChanged(boolean newStatus) {

  }
}
