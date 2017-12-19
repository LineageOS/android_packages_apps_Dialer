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

package com.android.dialer.speeddial.room;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;
import android.support.annotation.VisibleForTesting;
import javax.inject.Singleton;

/** Database of {@link SpeedDialEntry}. */
@Database(
  entities = {SpeedDialEntry.class},
  // Version should not change unless SpeedDialEntry schema changes, then it should be incremented
  version = 3
)
@Singleton
public abstract class SpeedDialEntryDatabase extends RoomDatabase {

  private static final String DB_NAME = "speedDialEntryoDatabase.db";
  private static boolean allowMainThreadQueriesForTesting;

  /* package-private */ static SpeedDialEntryDatabase create(Context appContext) {
    RoomDatabase.Builder<SpeedDialEntryDatabase> builder =
        Room.databaseBuilder(appContext, SpeedDialEntryDatabase.class, DB_NAME)
            // TODO(calderwoodra): implement migration plan for database upgrades
            .fallbackToDestructiveMigration();
    if (allowMainThreadQueriesForTesting) {
      builder.allowMainThreadQueries();
    }
    return builder.build();
  }

  public abstract SpeedDialEntryDao getSpeedDialEntryDao();

  @VisibleForTesting
  public static void allowMainThreadQueriesForTesting() {
    allowMainThreadQueriesForTesting = true;
  }
}
