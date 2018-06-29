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

package com.android.dialer.contacts.displaypreference;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.text.TextUtils;
import java.util.Arrays;

/** Handles name ordering of a contact (Given name first or family name first.) */
public interface ContactDisplayPreferences {

  /**
   * A enum whose value is a String from a Android string resource which can only be resolved at run
   * time.
   */
  interface StringResEnum {

    @StringRes
    int getStringRes();

    default String getValue(Context context) {
      return context.getString(getStringRes());
    }

    static <T extends Enum<T> & StringResEnum> T fromValue(
        Context context, T[] values, String value) {
      return Arrays.stream(values)
          .filter(enumValue -> TextUtils.equals(enumValue.getValue(context), value))
          // MoreCollectors.onlyElement() is not available to android guava.
          .reduce(
              (a, b) -> {
                throw new AssertionError("multiple result");
              })
          .get();
    }
  }

  /** Order when displaying the name; */
  enum DisplayOrder implements StringResEnum {

    /**
     * The default display order of a name. For western names it will be "Given Family". For
     * unstructured names like east asian this will be the only order.
     *
     * @see android.provider.ContactsContract.Contacts#DISPLAY_NAME_PRIMARY
     */
    PRIMARY(R.string.display_options_view_given_name_first_value),
    /**
     * The alternative display order of a name. For western names it will be "Family, Given". For
     * unstructured names like east asian this order will be ignored and treated as primary.
     *
     * @see android.provider.ContactsContract.Contacts#DISPLAY_NAME_ALTERNATIVE
     */
    ALTERNATIVE(R.string.display_options_view_family_name_first_value);

    @StringRes private final int value;

    DisplayOrder(@StringRes int value) {
      this.value = value;
    }

    @Override
    @StringRes
    public int getStringRes() {
      return value;
    }

    static DisplayOrder fromValue(Context context, String value) {
      return StringResEnum.fromValue(context, DisplayOrder.values(), value);
    }
  }

  /**
   * Order when sorting the name. In some conventions, names are displayed as given name first, but
   * sorted by family name.
   */
  enum SortOrder implements StringResEnum {
    /**
     * Sort by the default display order of a name. For western names it will be "Given Family". For
     * unstructured names like east asian this will be the only order.
     *
     * @see android.provider.ContactsContract.Contacts#DISPLAY_NAME_PRIMARY
     */
    BY_PRIMARY(R.string.display_options_sort_by_given_name_value),
    /**
     * Sort by the alternative display order of a name. For western names it will be "Family,
     * Given". For unstructured names like east asian this order will be ignored and treated as
     * primary.
     *
     * @see android.provider.ContactsContract.Contacts#DISPLAY_NAME_ALTERNATIVE
     */
    BY_ALTERNATIVE(R.string.display_options_sort_by_family_name_value);

    @StringRes private final int value;

    SortOrder(@StringRes int value) {
      this.value = value;
    }

    @Override
    @StringRes
    public int getStringRes() {
      return value;
    }

    static SortOrder fromValue(Context context, String value) {
      return StringResEnum.fromValue(context, SortOrder.values(), value);
    }
  }

  DisplayOrder getDisplayOrder();

  void setDisplayOrder(DisplayOrder displayOrder);

  SortOrder getSortOrder();

  void setSortOrder(SortOrder sortOrder);

  /** Selects display name based on {@link DisplayOrder} */
  default String getDisplayName(@Nullable String primaryName, @Nullable String alternativeName) {
    if (TextUtils.isEmpty(alternativeName)) {
      return primaryName;
    }
    switch (getDisplayOrder()) {
      case PRIMARY:
        return primaryName;
      case ALTERNATIVE:
        return alternativeName;
    }
    throw new AssertionError("exhaustive switch");
  }

  /** Selects sort name based on {@link SortOrder} */
  default String getSortName(@Nullable String primaryName, @Nullable String alternativeName) {
    if (TextUtils.isEmpty(alternativeName)) {
      return primaryName;
    }
    switch (getSortOrder()) {
      case BY_PRIMARY:
        return primaryName;
      case BY_ALTERNATIVE:
        return alternativeName;
    }
    throw new AssertionError("exhaustive switch");
  }
}
