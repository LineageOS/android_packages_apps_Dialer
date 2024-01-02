/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.contacts.common;

import android.content.Context;

import java.util.List;
import java.util.Objects;

/**
 * Class used for collapsing data items into groups of similar items. The data items that should be
 * collapsible should implement the Collapsible interface. The class also contains a utility
 * function that takes an ArrayList of items and returns a list of the same items collapsed into
 * groups.
 */
public final class Collapser {

  /*
   * The Collapser uses an n^2 algorithm so we don't want it to run on
   * lists beyond a certain size. This specifies the maximum size to collapse.
   */
  private static final int MAX_LISTSIZE_TO_COLLAPSE = 20;

  /*
   * This utility class cannot be instantiated.
   */
  private Collapser() {}

  /**
   * Collapses a list of Collapsible items into a list of collapsed items. Items are collapsed if
   * {@link Collapsible#shouldCollapseWith(Object)} returns true, and are collapsed through the
   * {@Link Collapsible#collapseWith(Object)} function implemented by the data item.
   *
   * @param list List of Objects of type <T extends Collapsible<T>> to be collapsed.
   */
  public static <T extends Collapsible<T>> void collapseList(List<T> list, Context context) {

    int listSize = list.size();
    // The algorithm below is n^2 so don't run on long lists
    if (listSize > MAX_LISTSIZE_TO_COLLAPSE) {
      return;
    }

    for (int i = 0; i < listSize; i++) {
      T iItem = list.get(i);
      if (iItem != null) {
        for (int j = i + 1; j < listSize; j++) {
          T jItem = list.get(j);
          if (jItem != null) {
            if (iItem.shouldCollapseWith(jItem, context)) {
              iItem.collapseWith(jItem);
              list.set(j, null);
            } else if (jItem.shouldCollapseWith(iItem, context)) {
              jItem.collapseWith(iItem);
              list.set(i, null);
              break;
            }
          }
        }
      }
    }

    // Remove the null items
    list.removeIf(Objects::isNull);
  }

  /*
   * Interface implemented by data types that can be collapsed into groups of similar data. This
   * can be used for example to collapse similar contact data items into a single item.
   */
  public interface Collapsible<T> {

    void collapseWith(T t);

    boolean shouldCollapseWith(T t, Context context);
  }
}
