/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.dialer.app.list;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.contacts.common.list.ContactListItemView;
import com.android.dialer.common.LogUtil;
import com.android.dialer.smartdial.SmartDialCursorLoader;
import com.android.dialer.smartdial.util.SmartDialMatchPosition;
import com.android.dialer.smartdial.util.SmartDialNameMatcher;
import com.android.dialer.util.CallUtil;
import java.util.ArrayList;

/** List adapter to display the SmartDial search results. */
public class SmartDialNumberListAdapter extends DialerPhoneNumberListAdapter {

  private static final String TAG = SmartDialNumberListAdapter.class.getSimpleName();
  private static final boolean DEBUG = false;

  private final Context context;
  @NonNull private final SmartDialNameMatcher nameMatcher;

  public SmartDialNumberListAdapter(Context context) {
    super(context);
    this.context = context;
    nameMatcher = new SmartDialNameMatcher("");
    setShortcutEnabled(SmartDialNumberListAdapter.SHORTCUT_DIRECT_CALL, false);

    if (DEBUG) {
      LogUtil.v(TAG, "Constructing List Adapter");
    }
  }

  /** Sets query for the SmartDialCursorLoader. */
  public void configureLoader(SmartDialCursorLoader loader) {
    if (DEBUG) {
      LogUtil.v(TAG, "Configure Loader with query" + getQueryString());
    }

    if (getQueryString() == null) {
      loader.configureQuery("");
      nameMatcher.setQuery("");
    } else {
      loader.configureQuery(getQueryString());
      nameMatcher.setQuery(PhoneNumberUtils.normalizeNumber(getQueryString()));
    }
  }

  /**
   * Sets highlight options for a List item in the SmartDial search results.
   *
   * @param view ContactListItemView where the result will be displayed.
   * @param cursor Object containing information of the associated List item.
   */
  @Override
  protected void setHighlight(ContactListItemView view, Cursor cursor) {
    view.clearHighlightSequences();

    if (nameMatcher.matches(context, cursor.getString(PhoneQuery.DISPLAY_NAME))) {
      final ArrayList<SmartDialMatchPosition> nameMatches = nameMatcher.getMatchPositions();
      for (SmartDialMatchPosition match : nameMatches) {
        view.addNameHighlightSequence(match.start, match.end);
        if (DEBUG) {
          LogUtil.v(
              TAG,
              cursor.getString(PhoneQuery.DISPLAY_NAME)
                  + " "
                  + nameMatcher.getQuery()
                  + " "
                  + String.valueOf(match.start));
        }
      }
    }

    final SmartDialMatchPosition numberMatch =
        nameMatcher.matchesNumber(context, cursor.getString(PhoneQuery.PHONE_NUMBER));
    if (numberMatch != null) {
      view.addNumberHighlightSequence(numberMatch.start, numberMatch.end);
    }
  }

  @Override
  public void setQueryString(String queryString) {
    final boolean showNumberShortcuts = !TextUtils.isEmpty(getFormattedQueryString());
    boolean changed = false;
    changed |= setShortcutEnabled(SHORTCUT_CREATE_NEW_CONTACT, showNumberShortcuts);
    changed |= setShortcutEnabled(SHORTCUT_ADD_TO_EXISTING_CONTACT, showNumberShortcuts);
    changed |= setShortcutEnabled(SHORTCUT_SEND_SMS_MESSAGE, showNumberShortcuts);
    changed |=
        setShortcutEnabled(
            SHORTCUT_MAKE_VIDEO_CALL, showNumberShortcuts && CallUtil.isVideoEnabled(getContext()));
    if (changed) {
      notifyDataSetChanged();
    }
    super.setQueryString(queryString);
  }

  public void setShowEmptyListForNullQuery(boolean show) {
    nameMatcher.setShouldMatchEmptyQuery(!show);
  }
}
