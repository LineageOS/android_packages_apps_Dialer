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

package com.android.dialer.app.list;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.view.View;
import android.view.ViewGroup;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.PhoneNumberListAdapter;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.app.R;
import com.android.dialer.location.GeoUtil;

/**
 * {@link PhoneNumberListAdapter} with the following added shortcuts, that are displayed as list
 * items: 1) Directly calling the phone number query 2) Adding the phone number query to a contact
 *
 * <p>These shortcuts can be enabled or disabled to toggle whether or not they show up in the list.
 */
public class DialerPhoneNumberListAdapter extends PhoneNumberListAdapter {

  public static final int SHORTCUT_INVALID = -1;
  public static final int SHORTCUT_DIRECT_CALL = 0;
  public static final int SHORTCUT_CREATE_NEW_CONTACT = 1;
  public static final int SHORTCUT_ADD_TO_EXISTING_CONTACT = 2;
  public static final int SHORTCUT_SEND_SMS_MESSAGE = 3;
  public static final int SHORTCUT_MAKE_VIDEO_CALL = 4;
  public static final int SHORTCUT_BLOCK_NUMBER = 5;
  public static final int SHORTCUT_COUNT = 6;

  private final boolean[] shortcutEnabled = new boolean[SHORTCUT_COUNT];
  private final BidiFormatter bidiFormatter = BidiFormatter.getInstance();
  private final String countryIso;

  private String formattedQueryString;

  public DialerPhoneNumberListAdapter(Context context) {
    super(context);

    countryIso = GeoUtil.getCurrentCountryIso(context);
  }

  @Override
  public int getCount() {
    return super.getCount() + getShortcutCount();
  }

  /** @return The number of enabled shortcuts. Ranges from 0 to a maximum of SHORTCUT_COUNT */
  public int getShortcutCount() {
    int count = 0;
    for (int i = 0; i < shortcutEnabled.length; i++) {
      if (shortcutEnabled[i]) {
        count++;
      }
    }
    return count;
  }

  public void disableAllShortcuts() {
    for (int i = 0; i < shortcutEnabled.length; i++) {
      shortcutEnabled[i] = false;
    }
  }

  @Override
  public int getItemViewType(int position) {
    final int shortcut = getShortcutTypeFromPosition(position);
    if (shortcut >= 0) {
      // shortcutPos should always range from 1 to SHORTCUT_COUNT
      return super.getViewTypeCount() + shortcut;
    } else {
      return super.getItemViewType(position);
    }
  }

  @Override
  public int getViewTypeCount() {
    // Number of item view types in the super implementation + 2 for the 2 new shortcuts
    return super.getViewTypeCount() + SHORTCUT_COUNT;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    final int shortcutType = getShortcutTypeFromPosition(position);
    if (shortcutType >= 0) {
      if (convertView != null) {
        assignShortcutToView((ContactListItemView) convertView, shortcutType);
        return convertView;
      } else {
        final ContactListItemView v =
            new ContactListItemView(getContext(), null, mIsImsVideoEnabled);
        assignShortcutToView(v, shortcutType);
        return v;
      }
    } else {
      return super.getView(position, convertView, parent);
    }
  }

  @Override
  protected ContactListItemView newView(
      Context context, int partition, Cursor cursor, int position, ViewGroup parent) {
    final ContactListItemView view = super.newView(context, partition, cursor, position, parent);

    view.setSupportVideoCallIcon(mIsImsVideoEnabled);
    return view;
  }

  /**
   * @param position The position of the item
   * @return The enabled shortcut type matching the given position if the item is a shortcut, -1
   *     otherwise
   */
  public int getShortcutTypeFromPosition(int position) {
    int shortcutCount = position - super.getCount();
    if (shortcutCount >= 0) {
      // Iterate through the array of shortcuts, looking only for shortcuts where
      // mShortcutEnabled[i] is true
      for (int i = 0; shortcutCount >= 0 && i < shortcutEnabled.length; i++) {
        if (shortcutEnabled[i]) {
          shortcutCount--;
          if (shortcutCount < 0) {
            return i;
          }
        }
      }
      throw new IllegalArgumentException(
          "Invalid position - greater than cursor count " + " but not a shortcut.");
    }
    return SHORTCUT_INVALID;
  }

  @Override
  public boolean isEmpty() {
    return getShortcutCount() == 0 && super.isEmpty();
  }

  @Override
  public boolean isEnabled(int position) {
    final int shortcutType = getShortcutTypeFromPosition(position);
    if (shortcutType >= 0) {
      return true;
    } else {
      return super.isEnabled(position);
    }
  }

  private void assignShortcutToView(ContactListItemView v, int shortcutType) {
    final CharSequence text;
    final Drawable drawable;
    final Resources resources = getContext().getResources();
    final String number = getFormattedQueryString();
    switch (shortcutType) {
      case SHORTCUT_DIRECT_CALL:
        text =
            ContactDisplayUtils.getTtsSpannedPhoneNumber(
                resources,
                R.string.search_shortcut_call_number,
                bidiFormatter.unicodeWrap(number, TextDirectionHeuristics.LTR));
        drawable = ContextCompat.getDrawable(getContext(), R.drawable.quantum_ic_call_vd_theme_24);
        break;
      case SHORTCUT_CREATE_NEW_CONTACT:
        text = resources.getString(R.string.search_shortcut_create_new_contact);
        drawable =
            ContextCompat.getDrawable(getContext(), R.drawable.quantum_ic_person_add_vd_theme_24);
        drawable.setAutoMirrored(true);
        break;
      case SHORTCUT_ADD_TO_EXISTING_CONTACT:
        text = resources.getString(R.string.search_shortcut_add_to_contact);
        drawable =
            ContextCompat.getDrawable(getContext(), R.drawable.quantum_ic_person_add_vd_theme_24);
        break;
      case SHORTCUT_SEND_SMS_MESSAGE:
        text = resources.getString(R.string.search_shortcut_send_sms_message);
        drawable =
            ContextCompat.getDrawable(getContext(), R.drawable.quantum_ic_message_vd_theme_24);
        break;
      case SHORTCUT_MAKE_VIDEO_CALL:
        text = resources.getString(R.string.search_shortcut_make_video_call);
        drawable =
            ContextCompat.getDrawable(getContext(), R.drawable.quantum_ic_videocam_vd_theme_24);
        break;
      case SHORTCUT_BLOCK_NUMBER:
        text = resources.getString(R.string.search_shortcut_block_number);
        drawable =
            ContextCompat.getDrawable(getContext(), R.drawable.ic_not_interested_googblue_24dp);
        break;
      default:
        throw new IllegalArgumentException("Invalid shortcut type");
    }
    v.setDrawable(drawable);
    v.setDisplayName(text);
    v.setAdjustSelectionBoundsEnabled(false);
  }

  /** @return True if the shortcut state (disabled vs enabled) was changed by this operation */
  public boolean setShortcutEnabled(int shortcutType, boolean visible) {
    final boolean changed = shortcutEnabled[shortcutType] != visible;
    shortcutEnabled[shortcutType] = visible;
    return changed;
  }

  public String getFormattedQueryString() {
    return formattedQueryString;
  }

  @Override
  public void setQueryString(String queryString) {
    formattedQueryString =
        PhoneNumberUtils.formatNumber(PhoneNumberUtils.normalizeNumber(queryString), countryIso);
    super.setQueryString(queryString);
  }
}
