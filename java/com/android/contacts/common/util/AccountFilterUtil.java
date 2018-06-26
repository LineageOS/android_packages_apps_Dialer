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
 * limitations under the License.
 */

package com.android.contacts.common.util;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import com.android.contacts.common.list.ContactListFilter;
import com.android.dialer.contacts.resources.R;

/** Utility class for account filter manipulation. */
public class AccountFilterUtil {

  /**
   * Similar to {@link #updateAccountFilterTitleForPeople(View, ContactListFilter, boolean,
   * boolean)}, but for Phone UI.
   */
  public static boolean updateAccountFilterTitleForPhone(
      View filterContainer, ContactListFilter filter, boolean showTitleForAllAccounts) {
    return updateAccountFilterTitle(filterContainer, filter, showTitleForAllAccounts, true);
  }

  private static boolean updateAccountFilterTitle(
      View filterContainer,
      ContactListFilter filter,
      boolean showTitleForAllAccounts,
      boolean forPhone) {
    final Context context = filterContainer.getContext();
    final TextView headerTextView =
        (TextView) filterContainer.findViewById(R.id.account_filter_header);

    boolean textWasSet = false;
    if (filter != null) {
      if (forPhone) {
        if (filter.filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
          if (showTitleForAllAccounts) {
            headerTextView.setText(R.string.list_filter_phones);
            textWasSet = true;
          }
        } else if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
          headerTextView.setText(
              context.getString(R.string.listAllContactsInAccount, filter.accountName));
          textWasSet = true;
        } else if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
          headerTextView.setText(R.string.listCustomView);
          textWasSet = true;
        }
      } else {
        if (filter.filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
          if (showTitleForAllAccounts) {
            headerTextView.setText(R.string.list_filter_all_accounts);
            textWasSet = true;
          }
        } else if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
          headerTextView.setText(
              context.getString(R.string.listAllContactsInAccount, filter.accountName));
          textWasSet = true;
        } else if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
          headerTextView.setText(R.string.listCustomView);
          textWasSet = true;
        } else if (filter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
          headerTextView.setText(R.string.listSingleContact);
          textWasSet = true;
        }
      }
    }
    return textWasSet;
  }
}
