/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.common.database;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An {@AsyncQueryHandler} that will never return a null cursor.
 *
 * <p>Instead, will return a {@link Cursor} with 0 records.
 */
public abstract class NoNullCursorAsyncQueryHandler extends AsyncQueryHandler {
  private static final AtomicInteger pendingQueryCount = new AtomicInteger();
  @Nullable private static PendingQueryCountChangedListener pendingQueryCountChangedListener;

  public NoNullCursorAsyncQueryHandler(ContentResolver cr) {
    super(cr);
  }

  @Override
  public void startQuery(
      int token,
      Object cookie,
      Uri uri,
      String[] projection,
      String selection,
      String[] selectionArgs,
      String orderBy) {
    pendingQueryCount.getAndIncrement();
    if (pendingQueryCountChangedListener != null) {
      pendingQueryCountChangedListener.onPendingQueryCountChanged();
    }

    final CookieWithProjection projectionCookie = new CookieWithProjection(cookie, projection);
    super.startQuery(token, projectionCookie, uri, projection, selection, selectionArgs, orderBy);
  }

  @Override
  protected final void onQueryComplete(int token, Object cookie, Cursor cursor) {
    CookieWithProjection projectionCookie = (CookieWithProjection) cookie;

    super.onQueryComplete(token, projectionCookie.originalCookie, cursor);

    if (cursor == null) {
      cursor = new EmptyCursor(projectionCookie.projection);
    }
    onNotNullableQueryComplete(token, projectionCookie.originalCookie, cursor);

    pendingQueryCount.getAndDecrement();
    if (pendingQueryCountChangedListener != null) {
      pendingQueryCountChangedListener.onPendingQueryCountChanged();
    }
  }

  protected abstract void onNotNullableQueryComplete(int token, Object cookie, Cursor cursor);

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public static void setPendingQueryCountChangedListener(
      @Nullable PendingQueryCountChangedListener listener) {
    pendingQueryCountChangedListener = listener;
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  public static int getPendingQueryCount() {
    return pendingQueryCount.get();
  }

  /** Callback to listen for changes in the number of queries that have not completed. */
  public interface PendingQueryCountChangedListener {
    void onPendingQueryCountChanged();
  }

  /** Class to add projection to an existing cookie. */
  private static class CookieWithProjection {

    public final Object originalCookie;
    public final String[] projection;

    public CookieWithProjection(Object cookie, String[] projection) {
      this.originalCookie = cookie;
      this.projection = projection;
    }
  }
}
