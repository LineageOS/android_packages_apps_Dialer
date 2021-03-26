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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.dialer.R;
import com.android.dialer.app.calllog.IntentProvider;
import com.android.dialer.helplines.utils.HelplineUtils;

import org.lineageos.lib.phone.spn.Item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.graphics.Paint.UNDERLINE_TEXT_FLAG;

public class HelplineActivity extends Activity {

    public static final String SHARED_PREFERENCES_KEY = "com.android.dialer.prefs";

    private static final String KEY_FIRST_LAUNCH = "pref_first_launch";

    private RecyclerView mRecyclerView;
    private LinearLayout mLoadingView;
    private LinearLayout mEmptyView;
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
        mEmptyView = findViewById(R.id.empty_view);
        mProgressBar = findViewById(R.id.helplines_progress_bar);

        mAdapter = new HelplineAdapter(getResources(), mListener);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(mAdapter);

        showUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
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
            showHelp(true);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showUi() {
        mLoadingView.setVisibility(View.VISIBLE);

        showHelp(false);
        SubscriptionManager subManager = getSystemService(SubscriptionManager.class);
        new LoadHelplinesTask(getResources(), subManager, mCallback).execute();
    }

    private void showHelp(boolean forceShow) {
        SharedPreferences preferenceManager = getPrefs();
        if (!forceShow && preferenceManager.getBoolean(KEY_FIRST_LAUNCH, false)) {
            return;
        }

        preferenceManager.edit()
                .putBoolean(KEY_FIRST_LAUNCH, true)
                .apply();

        new AlertDialog.Builder(this)
                .setTitle(R.string.helplines_help_title)
                .setMessage(R.string.helplines_help_message)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.helpline_button_more, (dialog, which) -> {
                    showMoreInfo(); })
                .show();
    }

    private void showMoreInfo() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.helplines_help_more_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public SharedPreferences getPrefs() {
        return this.getSharedPreferences(SHARED_PREFERENCES_KEY,
                Context.MODE_PRIVATE);
    }

    private LoadHelplinesTask.Callback mCallback = new LoadHelplinesTask.Callback () {
        @Override
        public void onLoadListProgress(int progress) {
            mProgressBar.setProgress(progress);
        }

        @Override
        public void onLoadCompleted(List<HelplineItem> result) {
            mLoadingView.setVisibility(View.GONE);
            if (result.size() == 0) {
                mEmptyView.setVisibility(View.VISIBLE);
            } else {
                mRecyclerView.setVisibility(View.VISIBLE);
            }
            mAdapter.update(result);
        }
    };

    private HelplineAdapter.Listener mListener = new HelplineAdapter.Listener() {
        private AlertDialog mDialog;

        @Override
        public void initiateCall(@NonNull String number) {
            IntentProvider provider = IntentProvider.getReturnCallIntentProvider(number);
            Intent intent = provider.getClickIntent(HelplineActivity.this);
            // Start the call and finish this activity - we don't want to leave traces of the call
            startActivity(intent);
            finish();
        }

        @Override
        public void onItemClicked(@NonNull HelplineItem helplineItem) {
            LayoutInflater inflater = LayoutInflater.from(HelplineActivity.this);
            final View dialogView = inflater.inflate(R.layout.dialog_helpline_details, null);
            Item item = helplineItem.getItem();

            fillOrHideDialogRow(helplineItem.getName(), dialogView, R.id.name_title, R.id.name);
            fillOrHideDialogRow(item.getOrganization(), dialogView, R.id.org_title, R.id.org);
            fillOrHideDialogRow(HelplineUtils.getCategories(getResources(), helplineItem),
                    dialogView, R.id.categories_title, R.id.categories);
            fillOrHideDialogRow(item.getNumber(), dialogView, R.id.number_title, R.id.number);
            fillOrHideDialogRow(item.getWebsite(), dialogView, R.id.website_title, R.id.website,
                    true);

            mDialog = new AlertDialog.Builder(HelplineActivity.this)
                    .setView(dialogView)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }

        private void fillOrHideDialogRow(String content, View dialog, int headerViewId,
                                         int contentViewId) {
            fillOrHideDialogRow(content, dialog, headerViewId, contentViewId, false);
        }

        private void fillOrHideDialogRow(String content, View dialogView, int headerViewId,
                                         int contentViewId, boolean isUrl) {
            if (dialogView == null) {
                return;
            }
            TextView headerView = dialogView.findViewById(headerViewId);
            TextView contentView = dialogView.findViewById(contentViewId);
            if (headerView == null || contentView == null) {
                return;
            }
            if (TextUtils.isEmpty(content)) {
                headerView.setVisibility(View.GONE);
                contentView.setVisibility(View.GONE);
                return;
            }

            contentView.setText(content);
            if (isUrl) {
                contentView.setPaintFlags(contentView.getPaintFlags() | UNDERLINE_TEXT_FLAG);

                LayoutInflater inflater = HelplineActivity.this.getLayoutInflater();
                contentView.setOnClickListener(v -> {
                    AlertDialog.Builder dialogBuilder =
                            new AlertDialog.Builder(HelplineActivity.this);
                    View webviewDlgView = inflater.inflate(R.layout.dialog_webview, null);
                    dialogBuilder.setView(webviewDlgView);
                    LinearLayout loadingLayout = webviewDlgView.findViewById(R.id.webview_loading);

                    // Disable cookies
                    CookieManager.getInstance().setAcceptCookie(false);
                    // Setup WebView
                    WebView webView = webviewDlgView.findViewById(R.id.webview);
                    webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public boolean shouldOverrideUrlLoading(WebView view,
                                WebResourceRequest request) {
                          return false;
                        }

                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);
                            loadingLayout.setVisibility(ProgressBar.INVISIBLE);
                            webView.setVisibility(View.VISIBLE);
                        }
                    });
                    // Override headers to disable cache and add "Do not track"
                    Map<String, String> headers = new HashMap<>(3);
                    headers.put("Pragma", "no-cache");
                    headers.put("Cache-Control", "no-cache");
                    headers.put("DNT", "1");
                    // Start loading the URL
                    webView.loadUrl(content, headers);
                    // Clear any WebView history
                    dialogBuilder.setPositiveButton(android.R.string.ok, (dlg, which) -> {
                        webView.clearHistory();
                        dlg.dismiss();
                    });
                    dialogBuilder.setOnDismissListener(dialog -> webView.clearHistory());
                    dialogBuilder.show();
                    // dismiss the dialog, we show a new one already
                    mDialog.dismiss();
                });
            }
        }
    };
}
