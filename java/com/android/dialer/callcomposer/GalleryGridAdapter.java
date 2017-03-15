/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.callcomposer;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import java.util.ArrayList;
import java.util.List;

/** Bridges between the image cursor loaded by GalleryBoundCursorLoader and the GalleryGridView. */
public class GalleryGridAdapter extends CursorAdapter {

  @NonNull private final OnClickListener onClickListener;
  @NonNull private final List<GalleryGridItemView> views = new ArrayList<>();
  @NonNull private final Context context;

  private GalleryGridItemData selectedData;

  public GalleryGridAdapter(
      @NonNull Context context, Cursor cursor, @NonNull OnClickListener onClickListener) {
    super(context, cursor, 0);
    this.onClickListener = Assert.isNotNull(onClickListener);
    this.context = Assert.isNotNull(context);
  }

  @Override
  public int getCount() {
    // Add one for the header.
    return super.getCount() + 1;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    // At position 0, we want to insert a header. If position == 0, we don't need the cursor.
    // If position != 0, then we need to move the cursor to position - 1 to account for the offset
    // of the header.
    if (position != 0 && !getCursor().moveToPosition(position - 1)) {
      Assert.fail("couldn't move cursor to position " + (position - 1));
    }
    View view;
    if (convertView == null) {
      view = newView(context, getCursor(), parent);
    } else {
      view = convertView;
    }
    bindView(view, context, getCursor(), position);
    return view;
  }

  private void bindView(View view, Context context, Cursor cursor, int position) {
    if (position == 0) {
      GalleryGridItemView gridView = (GalleryGridItemView) view;
      gridView.showGallery(true);
    } else {
      bindView(view, context, cursor);
    }
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    GalleryGridItemView gridView = (GalleryGridItemView) view;
    gridView.bind(cursor);
    gridView.setSelected(gridView.getData().equals(selectedData));
  }

  @Override
  public View newView(Context context, Cursor cursor, ViewGroup parent) {
    GalleryGridItemView view =
        (GalleryGridItemView)
            LayoutInflater.from(context).inflate(R.layout.gallery_grid_item_view, parent, false);
    view.setOnClickListener(onClickListener);
    views.add(view);
    return view;
  }

  public void setSelected(GalleryGridItemData selectedData) {
    this.selectedData = selectedData;
    for (GalleryGridItemView view : views) {
      view.setSelected(view.getData().equals(selectedData));
    }
  }

  public void insertEntries(@NonNull List<GalleryGridItemData> entries) {
    Assert.checkArgument(entries.size() != 0);
    LogUtil.i("GalleryGridAdapter.insertRows", "inserting %d rows", entries.size());
    MatrixCursor extraRow = new MatrixCursor(GalleryGridItemData.IMAGE_PROJECTION);
    for (GalleryGridItemData entry : entries) {
      extraRow.addRow(new Object[] {0L, entry.getFilePath(), entry.getMimeType(), ""});
    }
    extraRow.moveToFirst();
    Cursor extendedCursor = new MergeCursor(new Cursor[] {extraRow, getCursor()});
    swapCursor(extendedCursor);
  }

  public GalleryGridItemData insertEntry(String filePath, String mimeType) {
    LogUtil.i("GalleryGridAdapter.insertRow", mimeType + " " + filePath);

    MatrixCursor extraRow = new MatrixCursor(GalleryGridItemData.IMAGE_PROJECTION);
    extraRow.addRow(new Object[] {0L, filePath, mimeType, ""});
    extraRow.moveToFirst();
    Cursor extendedCursor = new MergeCursor(new Cursor[] {extraRow, getCursor()});
    swapCursor(extendedCursor);

    return new GalleryGridItemData(extraRow);
  }
}
