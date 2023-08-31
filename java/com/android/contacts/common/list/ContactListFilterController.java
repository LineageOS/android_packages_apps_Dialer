/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.common.list;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountWithDataSet;
import java.util.ArrayList;
import java.util.List;

/** Manages {@link ContactListFilter}. All methods must be called from UI thread. */
public abstract class ContactListFilterController {

  // singleton to cache the filter controller
  private static ContactListFilterControllerImpl sFilterController = null;

  public static ContactListFilterController getInstance(Context context) {
    // We may need to synchronize this in the future if background task will call this.
    if (sFilterController == null) {
      sFilterController = new ContactListFilterControllerImpl(context);
    }
    return sFilterController;
  }

  public abstract void addListener(ContactListFilterListener listener);

  public abstract void removeListener(ContactListFilterListener listener);

  /** Return the currently-active filter. */
  public abstract ContactListFilter getFilter();

  /**
   * Checks if the current filter is valid and reset the filter if not. It may happen when an
   * account is removed while the filter points to the account with {@link
   * ContactListFilter#FILTER_TYPE_ACCOUNT} type, for example. It may also happen if the current
   * filter is {@link ContactListFilter#FILTER_TYPE_SINGLE_CONTACT}, in which case, we should switch
   * to the last saved filter in {@link SharedPreferences}.
   */
  public abstract void checkFilterValidity(boolean notifyListeners);

  public interface ContactListFilterListener {

    void onContactListFilterChanged();
  }
}

/**
 * Stores the {@link ContactListFilter} selected by the user and saves it to {@link
 * SharedPreferences} if necessary.
 */
class ContactListFilterControllerImpl extends ContactListFilterController {

  private final Context mAppContext;
  private final List<ContactListFilterListener> mListeners =
      new ArrayList<ContactListFilterListener>();
  private ContactListFilter mFilter;

  public ContactListFilterControllerImpl(Context context) {
    mAppContext = context.getApplicationContext();
    mFilter = ContactListFilter.restoreDefaultPreferences(getSharedPreferences());
    checkFilterValidity(true /* notify listeners */);
  }

  @Override
  public void addListener(ContactListFilterListener listener) {
    mListeners.add(listener);
  }

  @Override
  public void removeListener(ContactListFilterListener listener) {
    mListeners.remove(listener);
  }

  @Override
  public ContactListFilter getFilter() {
    return mFilter;
  }

  private SharedPreferences getSharedPreferences() {
    return PreferenceManager.getDefaultSharedPreferences(mAppContext);
  }

  private void setContactListFilter(
      ContactListFilter filter, boolean persistent, boolean notifyListeners) {
    if (!filter.equals(mFilter)) {
      mFilter = filter;
      if (persistent) {
        ContactListFilter.storeToPreferences(getSharedPreferences(), mFilter);
      }
      if (notifyListeners && !mListeners.isEmpty()) {
        notifyContactListFilterChanged();
      }
    }
  }

  private void notifyContactListFilterChanged() {
    for (ContactListFilterListener listener : mListeners) {
      listener.onContactListFilterChanged();
    }
  }

  @Override
  public void checkFilterValidity(boolean notifyListeners) {
    if (mFilter == null) {
      return;
    }

    switch (mFilter.filterType) {
      case ContactListFilter.FILTER_TYPE_SINGLE_CONTACT:
        setContactListFilter(
            ContactListFilter.restoreDefaultPreferences(getSharedPreferences()),
            false,
            notifyListeners);
        break;
      case ContactListFilter.FILTER_TYPE_ACCOUNT:
        if (!filterAccountExists()) {
          // The current account filter points to invalid account. Use "all" filter
          // instead.
          setContactListFilter(
              ContactListFilter.createFilterWithType(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS),
              true,
              notifyListeners);
        }
    }
  }

  /** @return true if the Account for the current filter exists. */
  private boolean filterAccountExists() {
    final AccountTypeManager accountTypeManager = AccountTypeManager.getInstance(mAppContext);
    final AccountWithDataSet filterAccount =
        new AccountWithDataSet(mFilter.accountName, mFilter.accountType, mFilter.dataSet);
    return accountTypeManager.contains(filterAccount, false);
  }
}
