/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.common.database;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Utility to build SQL selections. Handles string concatenation, nested statements, empty
 * statements, and tracks the selection arguments.
 *
 * <p>A selection can be build from a string, factory methods like {@link #column(String)}, or use
 * {@link Builder} to build complex nested selection with multiple operators. The Selection manages
 * the {@code selection} and {@code selectionArgs} passed into {@link
 * android.content.ContentResolver#query(android.net.Uri, String[], String, String[], String)}.
 *
 * <p>Example:
 *
 * <pre><code>
 *   fromString("foo = 1")
 * </code></pre>
 *
 * expands into "(foo = 1)", {}
 *
 * <p>
 *
 * <pre><code>
 *   column("foo").is("LIKE", "bar")
 * </code></pre>
 *
 * expands into "(foo LIKE ?)", {"bar"}
 *
 * <p>
 *
 * <pre><code>
 *   builder()
 *     .and(
 *       fromString("foo = ?", "1").buildUpon()
 *       .or(column("bar").is("<", 2))
 *       .build())
 *     .and(not(column("baz").is("!= 3")))
 *     .build();
 * </code></pre>
 *
 * expands into "(((foo = ?) OR (bar < ?)) AND (NOT (baz != 3)))", {"1", "2"}
 */
public final class Selection {

  private final String selection;
  private final String[] selectionArgs;

  private Selection(@NonNull String selection, @NonNull String[] selectionArgs) {
    this.selection = selection;
    this.selectionArgs = selectionArgs;
  }

  @NonNull
  public String getSelection() {
    return selection;
  }

  @NonNull
  public String[] getSelectionArgs() {
    return selectionArgs;
  }

  public boolean isEmpty() {
    return selection.isEmpty();
  }

  /**
   * @return a mutable builder that appends to the selection. The selection will be parenthesized
   *     before anything is appended to it.
   */
  @NonNull
  public Builder buildUpon() {
    return new Builder(this);
  }

  /** @return a builder that is empty. */
  @NonNull
  public static Builder builder() {
    return new Builder();
  }

  /**
   * @return a Selection built from regular selection string/args pair. The result selection will be
   *     enclosed in a parenthesis.
   */
  @NonNull
  @SuppressWarnings("rawtypes")
  public static Selection fromString(@Nullable String selection, @Nullable String... args) {
    return new Builder(selection, args == null ? Collections.emptyList() : Arrays.asList(args))
        .build();
  }

  @NonNull
  public static Selection fromString(@Nullable String selection, Collection<String> args) {
    return new Builder(selection, args).build();
  }

  /** @return a selection that is negated */
  @NonNull
  public static Selection not(@NonNull Selection selection) {
    Assert.checkArgument(!selection.isEmpty());
    return fromString("NOT " + selection.getSelection(), selection.getSelectionArgs());
  }

  /**
   * Build a selection based on condition upon a column. is() should be called to complete the
   * selection.
   */
  @NonNull
  public static Column column(@NonNull String column) {
    return new Column(column);
  }

  /** Helper class to build a selection based on condition upon a column. */
  public static class Column {

    @NonNull private final String column;

    private Column(@NonNull String column) {
      this.column = Assert.isNotNull(column);
    }

    /** Expands to "<column> <operator> ?" and add {@code value} to the arguments. */
    @NonNull
    public Selection is(@NonNull String operator, @NonNull Object value) {
      return fromString(column + " " + Assert.isNotNull(operator) + " ?", value.toString());
    }

    /**
     * Expands to "<column> <operator>". {@link #is(String, Object)} should be used if the condition
     * is comparing to a string or a user input value, which must be sanitized.
     */
    @NonNull
    public Selection is(@NonNull String condition) {
      return fromString(column + " " + Assert.isNotNull(condition));
    }

    public Selection in(String... values) {
      return in(values == null ? Collections.emptyList() : Arrays.asList(values));
    }

    public Selection in(Collection<String> values) {
      return fromString(
          column + " IN (" + TextUtils.join(",", Collections.nCopies(values.size(), "?")) + ")",
          values);
    }
  }

  /** Builder for {@link Selection} */
  public static final class Builder {

    private final StringBuilder selection = new StringBuilder();
    private final List<String> selectionArgs = new ArrayList<>();

    private Builder() {}

    private Builder(@Nullable String selection, Collection<String> args) {
      if (selection == null) {
        return;
      }
      checkArgsCount(selection, args);
      this.selection.append(parenthesized(selection));
      if (args != null) {
        selectionArgs.addAll(args);
      }
    }

    private Builder(@NonNull Selection selection) {
      this.selection.append(selection.getSelection());
      Collections.addAll(selectionArgs, selection.selectionArgs);
    }

    @NonNull
    public Selection build() {
      if (selection.length() == 0) {
        return new Selection("", new String[] {});
      }
      return new Selection(
          parenthesized(selection.toString()),
          selectionArgs.toArray(new String[selectionArgs.size()]));
    }

    @NonNull
    public Builder and(@NonNull Selection selection) {
      if (selection.isEmpty()) {
        return this;
      }

      if (this.selection.length() > 0) {
        this.selection.append(" AND ");
      }
      this.selection.append(selection.getSelection());
      Collections.addAll(selectionArgs, selection.getSelectionArgs());
      return this;
    }

    @NonNull
    public Builder or(@NonNull Selection selection) {
      if (selection.isEmpty()) {
        return this;
      }

      if (this.selection.length() > 0) {
        this.selection.append(" OR ");
      }
      this.selection.append(selection.getSelection());
      Collections.addAll(selectionArgs, selection.getSelectionArgs());
      return this;
    }

    private static void checkArgsCount(@NonNull String selection, Collection<String> args) {
      int argsInSelection = 0;
      for (int i = 0; i < selection.length(); i++) {
        if (selection.charAt(i) == '?') {
          argsInSelection++;
        }
      }
      Assert.checkArgument(argsInSelection == (args == null ? 0 : args.size()));
    }
  }

  /**
   * Parenthesized the {@code string}. Will not parenthesized if {@code string} is empty or is
   * already parenthesized (top level parenthesis encloses the whole string).
   */
  @NonNull
  private static String parenthesized(@NonNull String string) {
    if (string.isEmpty()) {
      return "";
    }
    if (!string.startsWith("(")) {
      return "(" + string + ")";
    }
    int depth = 1;
    for (int i = 1; i < string.length() - 1; i++) {
      switch (string.charAt(i)) {
        case '(':
          depth++;
          break;
        case ')':
          depth--;
          if (depth == 0) {
            // First '(' closed before the string has ended,need an additional level of nesting.
            // For example "(A) AND (B)" should become "((A) AND (B))"
            return "(" + string + ")";
          }
          break;
        default:
          continue;
      }
    }
    Assert.checkArgument(depth == 1);
    return string;
  }
}
