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

package com.android.dialer.preferredsim.impl;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.android.dialer.common.LogUtil;
import com.android.dialer.preferredsim.PreferredSimFallbackContract.PreferredSim;

/** Database helper class for preferred SIM. */
public class PreferredSimDatabaseHelper extends SQLiteOpenHelper {

  static final String TABLE = "preferred_sim";

  private static final String CREATE_TABLE_SQL =
      "create table if not exists "
          + TABLE
          + " ("
          + (PreferredSim.DATA_ID + " integer primary key, ")
          + (PreferredSim.PREFERRED_PHONE_ACCOUNT_COMPONENT_NAME + " text, ")
          + (PreferredSim.PREFERRED_PHONE_ACCOUNT_ID + " text")
          + ");";

  PreferredSimDatabaseHelper(Context appContext) {
    super(appContext, "preferred_sim.db", null, 1);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    LogUtil.enterBlock("PreferredSimDatabaseHelper.onCreate");
    long startTime = System.currentTimeMillis();
    db.execSQL(CREATE_TABLE_SQL);
    LogUtil.i(
        "PreferredSimDatabaseHelper.onCreate",
        "took: %dms",
        System.currentTimeMillis() - startTime);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
}
