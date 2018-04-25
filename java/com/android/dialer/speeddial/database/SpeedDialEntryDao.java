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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Interface that databases support speed dial entries should implement.
 *
 * <p>This database is only used for favorite/starred contacts.
 */
public interface SpeedDialEntryDao {

  /** Return all entries in the database */
  ImmutableList<SpeedDialEntry> getAllEntries();

  /**
   * Insert new entries.
   *
   * <p>{@link SpeedDialEntry#id() ids} must be null.
   *
   * @return a map of the inserted entries to their new ids.
   */
  ImmutableMap<SpeedDialEntry, Long> insert(ImmutableList<SpeedDialEntry> entries);

  /**
   * Insert a new entry.
   *
   * <p>{@link SpeedDialEntry#id() ids} must be null.
   */
  long insert(SpeedDialEntry entry);

  /**
   * Updates existing entries based on {@link SpeedDialEntry#id}.
   *
   * <p>Fails if the {@link SpeedDialEntry#id()} doesn't exist.
   */
  void update(ImmutableList<SpeedDialEntry> entries);

  /**
   * Delete the passed in entries based on {@link SpeedDialEntry#id}.
   *
   * <p>Fails if the {@link SpeedDialEntry#id()} doesn't exist.
   */
  void delete(ImmutableList<Long> entries);

  /**
   * Inserts, updates and deletes rows all in on transaction.
   *
   * @return a map of the inserted entries to their new ids.
   * @see #insert(ImmutableList)
   * @see #update(ImmutableList)
   * @see #delete(ImmutableList)
   */
  ImmutableMap<SpeedDialEntry, Long> insertUpdateAndDelete(
      ImmutableList<SpeedDialEntry> entriesToInsert,
      ImmutableList<SpeedDialEntry> entriesToUpdate,
      ImmutableList<Long> entriesToDelete);

  /** Delete all entries in the database. */
  void deleteAll();
}
