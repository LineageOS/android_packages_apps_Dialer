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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.android.dialer.R;
import com.android.dialer.app.calllog.IntentProvider;
import com.android.server.telecom.SensitivePhoneNumberInfo;

import java.util.List;

public class HelplineActivity extends Activity implements
        LoadHelplinesTask.Callback,
        HelplineAdapter.Listener {

    public static final String SHARED_PREFERENCES_KEY = "com.android.dialer.prefs";
    private static final String KEY_FIRST_LAUNCH = "pref_first_launch";
    private RecyclerView mRecyclerView;
    private LinearLayout mLoadingView;
    private ProgressBar mProgressBar;

    private HelplineAdapter mAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstance) {
        super.onCreate(savedInstance);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        setContentView(R.layout.activity_helplines);
        mRecyclerView = findViewById(R.id.helplines_list);
        mLoadingView = findViewById(R.id.helplines_loading);
        mProgressBar = findViewById(R.id.helplines_progress_bar);

        mAdapter = new HelplineAdapter(this, this);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(mAdapter);

        showUi();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_helplines, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.menu_helpline_help) {
            showOnBoarding(true);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onLoadListProgress(int progress) {
        mProgressBar.setProgress(progress);
    }

    @Override
    public void onLoadCompleted(List<SensitivePhoneNumberInfo> result) {
        mLoadingView.setVisibility(View.GONE);
        mRecyclerView.setVisibility(View.VISIBLE);
        mAdapter.update(result);
    }

    private void showUi() {
        mLoadingView.setVisibility(View.VISIBLE);

        showOnBoarding(false);

        new LoadHelplinesTask(this).execute();
    }

    private void showOnBoarding(boolean forceShow) {
        SharedPreferences preferenceManager = getPrefs();
        if (!forceShow && preferenceManager.getBoolean(KEY_FIRST_LAUNCH, false)) {
            return;
        }

        preferenceManager.edit()
                .putBoolean(KEY_FIRST_LAUNCH, true)
                .apply();

        new AlertDialog.Builder(this)
                .setTitle(R.string.helplines_firstlaunch_title)
                .setMessage(R.string.helplines_firstlaunch_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public SharedPreferences getPrefs() {
        return this.getSharedPreferences(SHARED_PREFERENCES_KEY,
                Context.MODE_PRIVATE);
    }

    @Override
    public void initiateCall(@NonNull String number) {
        IntentProvider provider = IntentProvider.getReturnCallIntentProvider(number);
        Intent intent = provider.getClickIntent(this);
        // Start the call and finish this activity - we don't want to leave traces of the call
        startActivity(intent);
        finish();
    }
}
