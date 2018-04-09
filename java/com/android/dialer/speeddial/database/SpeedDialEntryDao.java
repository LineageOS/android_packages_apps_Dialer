/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.speeddial.database;

import java.util.List;

/**
 * Interface that databases support speed dial entries should implement.
 *
 * <p>This database is only used for favorite/starred contacts.
 */
public interface SpeedDialEntryDao {

  /** Return all entries in the database */
  List<SpeedDialEntry> getAllEntries();

  /**
   * Insert new entries.
   *
   * <p>Fails if any of the {@link SpeedDialEntry#id()} already exist.
   */
  void insert(List<SpeedDialEntry> entries);

  /**
   * Insert a new entry.
   *
   * <p>Fails if the {@link SpeedDialEntry#id()} already exists.
   */
  long insert(SpeedDialEntry entry);

  /**
   * Updates existing entries based on {@link SpeedDialEntry#id}.
   *
   * <p>Fails if the {@link SpeedDialEntry#id()} doesn't exist.
   */
  void update(List<SpeedDialEntry> entries);

  /**
   * Delete the passed in entries based on {@link SpeedDialEntry#id}.
   *
   * <p>Fails if the {@link SpeedDialEntry#id()} doesn't exist.
   */
  void delete(List<Long> entries);

  /** Delete all entries in the database. */
  void deleteAll();
}
