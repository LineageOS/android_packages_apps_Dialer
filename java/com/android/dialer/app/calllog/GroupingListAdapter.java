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

package com.android.dialer.app.calllog;

import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.util.SparseIntArray;

/**
 * Maintains a list that groups items into groups of consecutive elements which are disjoint, that
 * is, an item can only belong to one group. This is leveraged for grouping calls in the call log
 * received from or made to the same phone number.
 *
 * <p>There are two integers stored as metadata for every list item in the adapter.
 */
abstract class GroupingListAdapter extends RecyclerView.Adapter {

  protected ContentObserver changeObserver =
      new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
          return true;
        }

        @Override
        public void onChange(boolean selfChange) {
          onContentChanged();
        }
      };
  protected DataSetObserver dataSetObserver =
      new DataSetObserver() {
        @Override
        public void onChanged() {
          notifyDataSetChanged();
        }
      };
  private Cursor cursor;
  /**
   * SparseIntArray, which maps the cursor position of the first element of a group to the size of
   * the group. The index of a key in this map corresponds to the list position of that group.
   */
  private SparseIntArray groupMetadata;

  private int itemCount;

  public GroupingListAdapter() {
    reset();
  }

  /**
   * Finds all groups of adjacent items in the cursor and calls {@link #addGroup} for each of them.
   */
  protected abstract void addGroups(Cursor cursor);

  protected abstract void onContentChanged();

  public void changeCursor(Cursor cursor) {
    if (cursor == this.cursor) {
      return;
    }

    if (this.cursor != null) {
      this.cursor.unregisterContentObserver(changeObserver);
      this.cursor.unregisterDataSetObserver(dataSetObserver);
      this.cursor.close();
    }

    // Reset whenever the cursor is changed.
    reset();
    this.cursor = cursor;

    if (cursor != null) {
      addGroups(this.cursor);

      // Calculate the item count by subtracting group child counts from the cursor count.
      itemCount = groupMetadata.size();

      cursor.registerContentObserver(changeObserver);
      cursor.registerDataSetObserver(dataSetObserver);
      notifyDataSetChanged();
    }
  }

  /**
   * Records information about grouping in the list. Should be called by the overridden {@link
   * #addGroups} method.
   */
  public void addGroup(int cursorPosition, int groupSize) {
    int lastIndex = groupMetadata.size() - 1;
    if (lastIndex < 0 || cursorPosition <= groupMetadata.keyAt(lastIndex)) {
      groupMetadata.put(cursorPosition, groupSize);
    } else {
      // Optimization to avoid binary search if adding groups in ascending cursor position.
      groupMetadata.append(cursorPosition, groupSize);
    }
  }

  @Override
  public int getItemCount() {
    return itemCount;
  }

  /**
   * Given the position of a list item, returns the size of the group of items corresponding to that
   * position.
   */
  public int getGroupSize(int listPosition) {
    if (listPosition < 0 || listPosition >= groupMetadata.size()) {
      return 0;
    }

    return groupMetadata.valueAt(listPosition);
  }

  /**
   * Given the position of a list item, returns the the first item in the group of items
   * corresponding to that position.
   */
  public Object getItem(int listPosition) {
    if (cursor == null || listPosition < 0 || listPosition >= groupMetadata.size()) {
      return null;
    }

    int cursorPosition = groupMetadata.keyAt(listPosition);
    if (cursor.moveToPosition(cursorPosition)) {
      return cursor;
    } else {
      return null;
    }
  }

  private void reset() {
    itemCount = 0;
    groupMetadata = new SparseIntArray();
  }
}
