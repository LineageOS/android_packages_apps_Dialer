/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.dialer.main.impl;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import com.android.dialer.common.LogUtil;

/** This is the main activity for dialer. It hosts favorites, call log, search, dialpad, etc... */
public final class MainActivity extends AppCompatActivity implements View.OnClickListener {

  static Intent getIntent(Context context) {
    return new Intent(context, MainActivity.class)
        .setAction(Intent.ACTION_VIEW)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    LogUtil.enterBlock("MainActivity.onCreate");
    setContentView(R.layout.main_activity);
    initLayout();
  }

  private void initLayout() {
    findViewById(R.id.fab).setOnClickListener(this);

    ViewPager pager = findViewById(R.id.pager);
    MainPagerAdapter pagerAdapter = new MainPagerAdapter(this, getSupportFragmentManager());
    pager.setAdapter(pagerAdapter);

    TabLayout tabLayout = findViewById(R.id.tab_layout);
    tabLayout.setupWithViewPager(pager);

    Toolbar toolbar = findViewById(R.id.toolbar);
    toolbar.setPopupTheme(android.R.style.Theme_Material_Light);
    setSupportActionBar(toolbar);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Toast.makeText(this, "Not yet implemented", Toast.LENGTH_SHORT).show();
    if (item.getItemId() == R.id.search) {
      // open search
      return true;
    } else if (item.getItemId() == R.id.contacts) {
      // open contacts
      return true;
    } else {
      // TODO handle other menu items
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.fab) {
      // open dialpad search
    }
  }
}
