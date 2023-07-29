/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.dialer.main.impl;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.android.dialer.R;
import com.android.dialer.widget.EmptyContentView;

public class DefaultDialerActivity extends AppCompatActivity implements
        EmptyContentView.OnEmptyViewActionButtonClickedListener {

    private static final int REQUEST_DEFAULT_DIALER = 1;

    private RoleManager mRoleManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.MainActivityTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.default_dialer_view);

        mRoleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);

        EmptyContentView emptyContentView = findViewById(R.id.empty_list_view);
        emptyContentView.setDescription(R.string.default_dialer_text);
        emptyContentView.setImage(R.drawable.quantum_ic_call_vd_theme_24);
        emptyContentView.setActionLabel(R.string.default_dialer_action);
        emptyContentView.setActionClickedListener(this);
        emptyContentView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mRoleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
            finish();
        }
    }

    @Override
    public void onEmptyViewActionButtonClicked() {
        Intent roleRequest = mRoleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER);
        roleRequest.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                getPackageName());
        startActivityForResult(roleRequest, REQUEST_DEFAULT_DIALER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_DEFAULT_DIALER) {
            if (resultCode == Activity.RESULT_OK) {
                finish();
            }
        }
    }
}
